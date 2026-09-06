package com.linkdeck.android.ui.inspector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.linkdeck.android.R
import com.linkdeck.android.core.inspector.LinkInspectionData
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.security.CnameCloakingDetector
import com.linkdeck.android.core.security.LinkThreatAnalyzer
import com.linkdeck.android.core.security.LinkThreatWarning
import com.linkdeck.android.core.security.TlsCertificateInfo
import com.linkdeck.android.core.security.TlsInspectionResult
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Helper object for rendering on-demand TLS certificate inspection results,
 * DNS CNAME cloaking tracker detections, and threat warnings in Link Inspector.
 */
object LinkInspectorSecurityBinder {

    fun bindThreatWarnings(context: Context, root: View, data: LinkInspectionData, isThreatWarningsEnabled: Boolean) {
        val container = root.findViewById<LinearLayout>(R.id.layoutThreatContainer) ?: return
        val itemsLayout = root.findViewById<LinearLayout>(R.id.layoutThreatItems) ?: return

        if (!isThreatWarningsEnabled) {
            container.visibility = View.GONE
            return
        }

        val threats = LinkThreatAnalyzer.analyze(data.effectiveDestination, data.redirectHops.size)
        if (threats.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        itemsLayout.removeAllViews()

        for (threat in threats) {
            val (title, desc) = when (threat) {
                is LinkThreatWarning.PunycodePhishing -> Pair(
                    context.getString(R.string.threat_punycode_title),
                    context.getString(R.string.threat_punycode_desc, threat.asciiHost, threat.unicodeHost)
                )
                is LinkThreatWarning.UserInfoDeception -> Pair(
                    context.getString(R.string.threat_userinfo_title),
                    context.getString(R.string.threat_userinfo_desc, threat.deceptivePrefix, threat.actualHost)
                )
                is LinkThreatWarning.RawIpHost -> Pair(
                    context.getString(R.string.threat_raw_ip_title),
                    context.getString(R.string.threat_raw_ip_desc, threat.ipAddress)
                )
                is LinkThreatWarning.CleartextHttp -> Pair(
                    context.getString(R.string.threat_http_title),
                    context.getString(R.string.threat_http_desc)
                )
                is LinkThreatWarning.ExcessiveRedirects -> Pair(
                    context.getString(R.string.threat_redirects_title),
                    context.getString(R.string.threat_redirects_desc, threat.hopCount)
                )
            }

            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4, 0, 8)
                addView(TextView(context).apply {
                    text = "• $title"
                    textSize = 13f
                    setTextColor(context.getColor(R.color.error))
                })
                addView(TextView(context).apply {
                    text = desc
                    textSize = 12f
                    setPadding(14, 2, 0, 0)
                    setTextColor(context.getColor(R.color.on_surface_variant_light))
                })
            }
            itemsLayout.addView(itemView)
        }
    }

    fun bindRedirectHops(context: Context, root: View, data: LinkInspectionData) {
        val layoutHops: LinearLayout = root.findViewById(R.id.layoutRedirectHops)
        val textNoRedirects: TextView = root.findViewById(R.id.textInspectorNoRedirects)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

        layoutHops.removeAllViews()
        val hops = data.redirectHops
        if (hops.isEmpty() && data.redirectResult !is RedirectResult.Error) {
            textNoRedirects.visibility = View.VISIBLE
            layoutHops.visibility = View.GONE
        } else {
            textNoRedirects.visibility = View.GONE
            layoutHops.visibility = View.VISIBLE
            for ((index, hop) in hops.withIndex()) {
                val hopView = TextView(context).apply {
                    textSize = 13f
                    setTextColor(onSurfaceVariant)
                    setPadding(0, 4, 0, 4)
                    val safeTarget = UrlRedactor.redactUrlString(hop.targetUrl)
                    text = "${index + 1}. HTTP ${hop.statusCode} → $safeTarget"
                }
                layoutHops.addView(hopView)
            }
            if (data.redirectResult is RedirectResult.Error) {
                val errorMsg = when (data.redirectResult.errorType) {
                    RedirectErrorType.BLOCKED_PRIVATE_ADDRESS -> context.getString(R.string.inspector_redirect_blocked)
                    RedirectErrorType.REDIRECT_LOOP -> context.getString(R.string.inspector_redirect_loop)
                    RedirectErrorType.TOO_MANY_REDIRECTS -> context.getString(R.string.inspector_redirect_too_many)
                    RedirectErrorType.TIMEOUT -> context.getString(R.string.inspector_redirect_timeout)
                    else -> context.getString(R.string.inspector_redirect_unreachable)
                }
                layoutHops.addView(TextView(context).apply {
                    textSize = 13f
                    setTextColor(context.getColor(R.color.error))
                    setPadding(0, 4, 0, 4)
                    text = "• $errorMsg"
                })
            }
        }
    }

    fun renderTlsResult(context: Context, root: View, result: TlsInspectionResult) {
        val btnInspect = root.findViewById<MaterialButton>(R.id.btnInspectCertificate)
        val textError = root.findViewById<TextView>(R.id.textTlsError)
        val detailsContainer = root.findViewById<LinearLayout>(R.id.layoutCertificateDetails)

        when (result) {
            is TlsInspectionResult.Success -> {
                btnInspect.visibility = View.GONE
                textError.visibility = View.GONE
                detailsContainer.visibility = View.VISIBLE
                populateCertificateViews(context, root, result.certInfo)
            }
            is TlsInspectionResult.HandshakeFailed -> {
                textError.visibility = View.VISIBLE
                textError.text = "${context.getString(R.string.inspector_tls_handshake_failed)}: ${result.errorMessage}"
                if (result.partialCertInfo != null) {
                    detailsContainer.visibility = View.VISIBLE
                    populateCertificateViews(context, root, result.partialCertInfo)
                }
            }
            is TlsInspectionResult.Error -> {
                textError.visibility = View.VISIBLE
                textError.text = result.message
                btnInspect.visibility = View.VISIBLE
                btnInspect.setText(R.string.inspector_btn_retry_tls)
            }
            else -> {}
        }
    }

    fun renderCnameResult(context: Context, root: View, result: CnameCloakingDetector.CnameResult) {
        val textStatus = root.findViewById<TextView>(R.id.textCnameStatus) ?: return
        val textDesc = root.findViewById<TextView>(R.id.textCnameDesc) ?: return

        when (result) {
            is CnameCloakingDetector.CnameResult.CloakingDetected -> {
                textStatus.text = context.getString(R.string.inspector_cname_detected)
                textStatus.setTextColor(context.getColor(R.color.error))
                textDesc.text = context.getString(R.string.inspector_cname_detected_desc, result.cnameHost, result.trackerName)
            }
            is CnameCloakingDetector.CnameResult.Clean -> {
                textStatus.text = context.getString(R.string.inspector_cname_clean)
                textStatus.setTextColor(context.getColor(R.color.on_surface_light))
                textDesc.text = context.getString(R.string.inspector_cname_canonical_clean, result.cnameHost)
            }
            is CnameCloakingDetector.CnameResult.DirectResolution -> {
                textStatus.text = context.getString(R.string.inspector_cname_clean)
                textStatus.setTextColor(context.getColor(R.color.on_surface_light))
                textDesc.text = context.getString(R.string.inspector_cname_clean_desc)
            }
            is CnameCloakingDetector.CnameResult.Error -> {
                textStatus.text = context.getString(R.string.inspector_cname_clean)
                textDesc.text = context.getString(R.string.inspector_cname_unavailable, result.message)
            }
        }
    }

    private fun populateCertificateViews(context: Context, root: View, cert: TlsCertificateInfo) {
        root.findViewById<TextView>(R.id.textTlsProtocolCipher).text = "${cert.protocol} • ${cert.cipherSuite}"
        root.findViewById<TextView>(R.id.textTlsIssuer).text = cert.issuerOrg?.let { "$it (${cert.issuerCn})" } ?: cert.issuerCn
        root.findViewById<TextView>(R.id.textTlsSubject).text = cert.subjectOrg?.let { "$it (${cert.subjectCn})" } ?: cert.subjectCn

        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val validUntil = dateFormat.format(cert.validTo)
        val validityText = if (cert.isExpired) {
            "${context.getString(R.string.inspector_tls_expired)} ($validUntil)"
        } else {
            "${context.getString(R.string.inspector_tls_valid_until, validUntil)} • ${context.getString(R.string.inspector_tls_days_left, cert.daysRemaining)}"
        }
        root.findViewById<TextView>(R.id.textTlsValidity).text = validityText

        root.findViewById<TextView>(R.id.textTlsPublicKey).text = "${cert.publicKeyAlgorithm} (${cert.publicKeySizeBits} bits) • ${cert.signatureAlgorithm}"
        root.findViewById<TextView>(R.id.textTlsFingerprint).text = cert.sha256Fingerprint

        root.findViewById<ImageButton>(R.id.btnCopyFingerprint).setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("SHA-256 Fingerprint", cert.sha256Fingerprint))
            Toast.makeText(context, R.string.inspector_toast_fingerprint_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
