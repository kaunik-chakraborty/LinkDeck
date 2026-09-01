package com.linkdeck.android.ui.inspector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.linkdeck.android.R
import com.linkdeck.android.core.inspector.LinkInspectionData
import com.linkdeck.android.core.inspector.RoutingExplanation
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.security.LinkThreatAnalyzer
import com.linkdeck.android.core.security.LinkThreatWarning
import com.linkdeck.android.core.security.TlsCertificateInfo
import com.linkdeck.android.core.security.TlsCertificateInspector
import com.linkdeck.android.core.security.TlsInspectionResult
import com.linkdeck.android.core.settings.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ephemeral modal bottom sheet for inspecting link diagnostics, redirect chains,
 * tracking cleaning, on-device threat warnings, and on-demand TLS certificate inspection.
 */
class LinkInspectorBottomSheet : BottomSheetDialogFragment() {

    private var inspectionData: LinkInspectionData? = null
    var onOpenOriginalRequested: ((SanitizedLink) -> Unit)? = null

    private val settingsStore by lazy { AppSettingsStore(requireContext()) }
    private var cachedTlsResult: TlsInspectionResult? = null

    fun setInspectionData(data: LinkInspectionData) {
        this.inspectionData = data
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_link_inspector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        val data = inspectionData ?: return

        view.findViewById<View>(R.id.btnInspectorClose)?.setOnClickListener {
            dismiss()
        }

        // 1. On-Device Threat Warnings
        bindThreatWarnings(view, data)

        // 2. Final Destination & Original Link
        view.findViewById<TextView>(R.id.textInspectorDestinationUrl).text = UrlRedactor.redact(data.effectiveDestination)
        view.findViewById<TextView>(R.id.textInspectorOriginalUrl).text = UrlRedactor.redact(data.originalLink)

        // 3. Connection Security & On-Demand TLS Inspector
        bindConnectionSecurity(view, data)

        // 4. Redirect Chain
        bindRedirectHops(view, data)

        // 5. Tracking Protection
        bindTrackingStatus(view, data)

        // 6. Routing Decision
        bindRoutingExplanation(view, data)

        // 7. Open Original Link & Done Actions
        val btnOpenOriginal: MaterialButton = view.findViewById(R.id.btnInspectorOpenOriginal)
        val isTransformed = data.originalLink.rawUrl != data.effectiveDestination.rawUrl || data.wasCleaned
        btnOpenOriginal.visibility = if (isTransformed) View.VISIBLE else View.GONE
        btnOpenOriginal.setOnClickListener {
            dismiss()
            onOpenOriginalRequested?.invoke(data.originalLink)
        }

        view.findViewById<View>(R.id.btnInspectorDone).setOnClickListener {
            dismiss()
        }
    }

    private fun bindThreatWarnings(root: View, data: LinkInspectionData) {
        val container = root.findViewById<LinearLayout>(R.id.layoutThreatContainer) ?: return
        val itemsLayout = root.findViewById<LinearLayout>(R.id.layoutThreatItems) ?: return

        if (!settingsStore.isThreatWarningsEnabled) {
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
                    getString(R.string.threat_punycode_title),
                    getString(R.string.threat_punycode_desc, threat.asciiHost, threat.unicodeHost)
                )
                is LinkThreatWarning.UserInfoDeception -> Pair(
                    getString(R.string.threat_userinfo_title),
                    getString(R.string.threat_userinfo_desc, threat.deceptivePrefix, threat.actualHost)
                )
                is LinkThreatWarning.RawIpHost -> Pair(
                    getString(R.string.threat_raw_ip_title),
                    getString(R.string.threat_raw_ip_desc, threat.ipAddress)
                )
                is LinkThreatWarning.CleartextHttp -> Pair(
                    getString(R.string.threat_http_title),
                    getString(R.string.threat_http_desc)
                )
                is LinkThreatWarning.ExcessiveRedirects -> Pair(
                    getString(R.string.threat_redirects_title),
                    getString(R.string.threat_redirects_desc, threat.hopCount)
                )
            }

            val itemView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4, 0, 8)
                addView(TextView(requireContext()).apply {
                    text = "• $title"
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.error))
                })
                addView(TextView(requireContext()).apply {
                    text = desc
                    textSize = 12f
                    setPadding(14, 2, 0, 0)
                    setTextColor(requireContext().getColor(R.color.on_surface_variant_light))
                })
            }
            itemsLayout.addView(itemView)
        }
    }

    private fun bindConnectionSecurity(root: View, data: LinkInspectionData) {
        val icon = root.findViewById<ImageView>(R.id.iconSecurityStatus)
        val textStatus = root.findViewById<TextView>(R.id.textSecurityTransportStatus)
        val textDesc = root.findViewById<TextView>(R.id.textSecurityTransportDesc)
        val btnInspect = root.findViewById<MaterialButton>(R.id.btnInspectCertificate)
        val progress = root.findViewById<LinearLayout>(R.id.layoutTlsProgress)
        val textError = root.findViewById<TextView>(R.id.textTlsError)
        val detailsContainer = root.findViewById<LinearLayout>(R.id.layoutCertificateDetails)

        val isHttp = data.effectiveDestination.scheme.equals("http", ignoreCase = true)

        if (isHttp) {
            icon.setImageResource(R.drawable.shieldcheck)
            icon.setColorFilter(requireContext().getColor(R.color.error))
            textStatus.setText(R.string.inspector_tls_insecure)
            textStatus.setTextColor(requireContext().getColor(R.color.error))
            textDesc.setText(R.string.inspector_tls_insecure_desc)
            btnInspect.visibility = View.GONE
            detailsContainer.visibility = View.GONE
            return
        }

        // HTTPS Connection
        if (!settingsStore.isTlsInspectionEnabled) {
            btnInspect.visibility = View.GONE
            return
        }

        btnInspect.visibility = View.VISIBLE
        btnInspect.setOnClickListener {
            val cached = cachedTlsResult
            if (cached != null) {
                renderTlsResult(root, cached)
                return@setOnClickListener
            }

            btnInspect.visibility = View.GONE
            textError.visibility = View.GONE
            progress.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val result = TlsCertificateInspector.inspect(data.effectiveDestination)
                val appContext = requireContext().applicationContext
                val cnameResult = if (settingsStore.isCnameDetectionEnabled) {
                    com.linkdeck.android.core.security.CnameCloakingDetector.checkCname(data.effectiveDestination.host, appContext)
                } else null

                cachedTlsResult = result
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    renderTlsResult(root, result)
                    cnameResult?.let { renderCnameResult(root, it) }
                }
            }
        }
    }

    private fun renderCnameResult(root: View, result: com.linkdeck.android.core.security.CnameCloakingDetector.CnameResult) {
        val textStatus = root.findViewById<TextView>(R.id.textCnameStatus) ?: return
        val textDesc = root.findViewById<TextView>(R.id.textCnameDesc) ?: return

        when (result) {
            is com.linkdeck.android.core.security.CnameCloakingDetector.CnameResult.CloakingDetected -> {
                textStatus.text = getString(R.string.inspector_cname_detected)
                textStatus.setTextColor(requireContext().getColor(R.color.error))
                textDesc.text = getString(R.string.inspector_cname_detected_desc, result.cnameHost, result.trackerName)
            }
            is com.linkdeck.android.core.security.CnameCloakingDetector.CnameResult.Clean -> {
                textStatus.text = getString(R.string.inspector_cname_clean)
                textStatus.setTextColor(requireContext().getColor(R.color.on_surface_light))
                textDesc.text = getString(R.string.inspector_cname_canonical_clean, result.cnameHost)
            }
            is com.linkdeck.android.core.security.CnameCloakingDetector.CnameResult.DirectResolution -> {
                textStatus.text = getString(R.string.inspector_cname_clean)
                textStatus.setTextColor(requireContext().getColor(R.color.on_surface_light))
                textDesc.text = getString(R.string.inspector_cname_clean_desc)
            }
            is com.linkdeck.android.core.security.CnameCloakingDetector.CnameResult.Error -> {
                textStatus.text = getString(R.string.inspector_cname_clean)
                textDesc.text = getString(R.string.inspector_cname_unavailable, result.message)
            }
        }
    }

    private fun renderTlsResult(root: View, result: TlsInspectionResult) {
        val btnInspect = root.findViewById<MaterialButton>(R.id.btnInspectCertificate)
        val textError = root.findViewById<TextView>(R.id.textTlsError)
        val detailsContainer = root.findViewById<LinearLayout>(R.id.layoutCertificateDetails)

        when (result) {
            is TlsInspectionResult.Success -> {
                btnInspect.visibility = View.GONE
                textError.visibility = View.GONE
                detailsContainer.visibility = View.VISIBLE
                populateCertificateViews(root, result.certInfo)
            }
            is TlsInspectionResult.HandshakeFailed -> {
                textError.visibility = View.VISIBLE
                textError.text = getString(R.string.inspector_tls_handshake_failed) + ": " + result.errorMessage
                if (result.partialCertInfo != null) {
                    detailsContainer.visibility = View.VISIBLE
                    populateCertificateViews(root, result.partialCertInfo)
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

    private fun populateCertificateViews(root: View, cert: TlsCertificateInfo) {
        root.findViewById<TextView>(R.id.textTlsProtocolCipher).text = "${cert.protocol} • ${cert.cipherSuite}"
        root.findViewById<TextView>(R.id.textTlsIssuer).text = cert.issuerOrg?.let { "$it (${cert.issuerCn})" } ?: cert.issuerCn
        root.findViewById<TextView>(R.id.textTlsSubject).text = cert.subjectOrg?.let { "$it (${cert.subjectCn})" } ?: cert.subjectCn

        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val validUntil = dateFormat.format(cert.validTo)
        val validityText = if (cert.isExpired) {
            getString(R.string.inspector_tls_expired) + " ($validUntil)"
        } else {
            getString(R.string.inspector_tls_valid_until, validUntil) + " • " + getString(R.string.inspector_tls_days_left, cert.daysRemaining)
        }
        root.findViewById<TextView>(R.id.textTlsValidity).text = validityText

        root.findViewById<TextView>(R.id.textTlsPublicKey).text = "${cert.publicKeyAlgorithm} (${cert.publicKeySizeBits} bits) • ${cert.signatureAlgorithm}"
        root.findViewById<TextView>(R.id.textTlsFingerprint).text = cert.sha256Fingerprint

        root.findViewById<ImageButton>(R.id.btnCopyFingerprint).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256 Fingerprint", cert.sha256Fingerprint))
            Toast.makeText(requireContext(), R.string.inspector_toast_fingerprint_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindRedirectHops(root: View, data: LinkInspectionData) {
        val layoutHops: LinearLayout = root.findViewById(R.id.layoutRedirectHops)
        val textNoRedirects: TextView = root.findViewById(R.id.textInspectorNoRedirects)
        val onSurfaceVariant = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

        layoutHops.removeAllViews()
        val hops = data.redirectHops
        if (hops.isEmpty() && data.redirectResult !is RedirectResult.Error) {
            textNoRedirects.visibility = View.VISIBLE
            layoutHops.visibility = View.GONE
        } else {
            textNoRedirects.visibility = View.GONE
            layoutHops.visibility = View.VISIBLE
            for ((index, hop) in hops.withIndex()) {
                val hopView = TextView(requireContext()).apply {
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
                    RedirectErrorType.BLOCKED_PRIVATE_ADDRESS -> getString(R.string.inspector_redirect_blocked)
                    RedirectErrorType.REDIRECT_LOOP -> getString(R.string.inspector_redirect_loop)
                    RedirectErrorType.TOO_MANY_REDIRECTS -> getString(R.string.inspector_redirect_too_many)
                    RedirectErrorType.TIMEOUT -> getString(R.string.inspector_redirect_timeout)
                    else -> getString(R.string.inspector_redirect_unreachable)
                }
                layoutHops.addView(TextView(requireContext()).apply {
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.error))
                    setPadding(0, 4, 0, 4)
                    text = "• $errorMsg"
                })
            }
        }
    }

    private fun bindTrackingStatus(root: View, data: LinkInspectionData) {
        val textTracking: TextView = root.findViewById(R.id.textInspectorTrackingStatus)
        if (data.wasCleaned && data.removedTrackingParams.isNotEmpty()) {
            val count = data.removedTrackingParams.size
            val paramsStr = data.removedTrackingParams.joinToString(", ")
            textTracking.text = getString(R.string.inspector_tracking_cleaned, count) + " ($paramsStr)"
        } else {
            textTracking.text = getString(R.string.inspector_tracking_none)
        }
    }

    private fun bindRoutingExplanation(root: View, data: LinkInspectionData) {
        val textRouting: TextView = root.findViewById(R.id.textInspectorRoutingReason)
        textRouting.text = when (val reason = data.routingExplanation) {
            is RoutingExplanation.MatchedRule -> getString(R.string.inspector_rule_matched, reason.rule.displayCondition)
            is RoutingExplanation.SavedPreference -> getString(R.string.inspector_pref_matched, reason.preference.domain)
            is RoutingExplanation.ManualChoice -> getString(R.string.inspector_manual_choice)
            is RoutingExplanation.RecommendedApp -> "Recommended: ${reason.appLabel}"
        }
    }

    companion object {
        const val TAG = "LinkInspectorBottomSheet"

        fun newInstance(data: LinkInspectionData): LinkInspectorBottomSheet {
            return LinkInspectorBottomSheet().apply {
                setInspectionData(data)
            }
        }
    }
}
