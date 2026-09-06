package com.linkdeck.android.ui.qr

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.R
import com.linkdeck.android.core.qr.QrPayloadProcessor
import com.linkdeck.android.core.qr.QrScanResult
import com.linkdeck.android.core.security.LinkThreatWarning
import com.linkdeck.android.ui.chooser.ChooserActivity
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Bottom sheet presenting parsed and sanitized QR scan results with instant
 * on-device routing, De-AMP breakdown, and threat security inspection.
 */
class QrScanResultBottomSheet : BottomSheetDialogFragment() {

    var onDismissCallback: (() -> Unit)? = null
    private var rawPayload: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
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
        return inflater.inflate(R.layout.bottom_sheet_qr_scan_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rawPayload = arguments?.getString(ARG_PAYLOAD).orEmpty()

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        view.findViewById<View>(R.id.btnQrResultClose)?.setOnClickListener {
            dismiss()
        }

        val scanResult = QrPayloadProcessor.process(rawPayload, deAmpEnabled = true)
        bindScanResult(view, scanResult)
    }

    private fun bindScanResult(root: View, result: QrScanResult) {
        val textTitle: TextView = root.findViewById(R.id.textScanResultTitle)
        val textType: TextView = root.findViewById(R.id.textPayloadType)
        val textDest: TextView = root.findViewById(R.id.textDestinationUrl)
        val textDeAmp: TextView = root.findViewById(R.id.textDeAmpSource)
        val textThreat: TextView = root.findViewById(R.id.textThreatDetails)
        val layoutBadges: View = root.findViewById(R.id.layoutBadges)
        val badgeThreat: View = root.findViewById(R.id.badgeThreatWarning)
        val badgeDeAmp: View = root.findViewById(R.id.badgeDeAmped)

        val layoutWeb: View = root.findViewById(R.id.layoutWebActions)
        val layoutPlain: View = root.findViewById(R.id.layoutPlainActions)

        when (result) {
            is QrScanResult.WebLink -> {
                textTitle.text = getString(R.string.qr_result_title)
                textType.text = getString(R.string.qr_result_weblink_title)
                textDest.text = result.targetUrl
                layoutWeb.visibility = View.VISIBLE
                layoutPlain.visibility = View.GONE

                val hasDeAmp = result.deAmpedUrl != null
                badgeDeAmp.visibility = if (hasDeAmp) View.VISIBLE else View.GONE
                textDeAmp.visibility = if (hasDeAmp) View.VISIBLE else View.GONE
                if (hasDeAmp) {
                    textDeAmp.text = getString(R.string.inspector_de_amped_desc, result.rawPayload)
                }

                val hasThreats = result.threatWarnings.isNotEmpty()
                badgeThreat.visibility = if (hasThreats) View.VISIBLE else View.GONE
                textThreat.visibility = if (hasThreats) View.VISIBLE else View.GONE
                if (hasThreats) {
                    textThreat.text = result.threatWarnings.joinToString("\n") { formatThreatWarning(it) }
                }

                layoutBadges.visibility = if (hasDeAmp || hasThreats) View.VISIBLE else View.GONE
                setupWebActions(root, result)
            }
            is QrScanResult.PlainText -> {
                textTitle.text = getString(R.string.qr_result_title)
                textType.text = getString(R.string.qr_result_text_title)
                textDest.text = result.rawPayload
                layoutBadges.visibility = View.GONE
                badgeDeAmp.visibility = View.GONE
                badgeThreat.visibility = View.GONE
                textDeAmp.visibility = View.GONE
                textThreat.visibility = View.GONE

                layoutWeb.visibility = View.GONE
                layoutPlain.visibility = View.VISIBLE
                setupPlainActions(root, result)
            }
        }
    }

    private fun setupWebActions(root: View, result: QrScanResult.WebLink) {
        val btnOpen: MaterialButton = root.findViewById(R.id.btnOpenWithLinkDeck)
        val btnInspect: MaterialButton = root.findViewById(R.id.btnInspectLink)
        val btnCopy: MaterialButton = root.findViewById(R.id.btnCopyCleanLink)

        btnOpen.setOnClickListener {
            val intent = Intent(requireContext(), ChooserActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(result.targetUrl)
            }
            startActivity(intent)
            dismiss()
        }

        btnInspect.setOnClickListener {
            val intent = Intent(requireContext(), TestLinkActivity::class.java).apply {
                putExtra(TestLinkActivity.EXTRA_INITIAL_URL, result.targetUrl)
            }
            startActivity(intent)
            dismiss()
        }

        btnCopy.setOnClickListener {
            copyToClipboard(result.targetUrl)
        }
    }

    private fun setupPlainActions(root: View, result: QrScanResult.PlainText) {
        val btnCopy: MaterialButton = root.findViewById(R.id.btnCopyPlainText)
        val btnShare: MaterialButton = root.findViewById(R.id.btnSharePlainText)

        btnCopy.setOnClickListener {
            copyToClipboard(result.rawPayload)
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.rawPayload)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
        }
    }

    private fun formatThreatWarning(warning: LinkThreatWarning): String {
        return when (warning) {
            is LinkThreatWarning.PunycodePhishing -> getString(R.string.threat_punycode_title)
            is LinkThreatWarning.UserInfoDeception -> getString(R.string.threat_userinfo_title)
            is LinkThreatWarning.RawIpHost -> getString(R.string.threat_raw_ip_title)
            is LinkThreatWarning.CleartextHttp -> getString(R.string.threat_http_title)
            is LinkThreatWarning.ExcessiveRedirects -> getString(R.string.threat_redirects_title)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Scanned Content", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    companion object {
        const val TAG = "QrScanResultBottomSheet"
        private const val ARG_PAYLOAD = "arg_payload"

        fun newInstance(rawPayload: String): QrScanResultBottomSheet {
            return QrScanResultBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAYLOAD, rawPayload)
                }
            }
        }
    }
}

