package com.linkdeck.android.ui.inspector

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.R
import com.linkdeck.android.core.inspector.LinkInspectionData
import com.linkdeck.android.core.inspector.RoutingExplanation
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.security.CnameCloakingDetector
import com.linkdeck.android.core.security.TlsCertificateInspector
import com.linkdeck.android.core.security.TlsInspectionResult
import com.linkdeck.android.core.settings.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        LinkInspectorSecurityBinder.bindThreatWarnings(requireContext(), view, data, settingsStore.isThreatWarningsEnabled)

        // 2. Final Destination & Original Link
        view.findViewById<TextView>(R.id.textInspectorDestinationUrl).text = UrlRedactor.redact(data.effectiveDestination)
        view.findViewById<TextView>(R.id.textInspectorOriginalUrl).text = UrlRedactor.redact(data.originalLink)

        // 3. Connection Security & On-Demand TLS Inspector
        bindConnectionSecurity(view, data)

        // 4. Redirect Chain
        LinkInspectorSecurityBinder.bindRedirectHops(requireContext(), view, data)

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
                LinkInspectorSecurityBinder.renderTlsResult(requireContext(), root, cached)
                return@setOnClickListener
            }

            btnInspect.visibility = View.GONE
            textError.visibility = View.GONE
            progress.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val result = TlsCertificateInspector.inspect(data.effectiveDestination)
                val appContext = requireContext().applicationContext
                val cnameResult = if (settingsStore.isCnameDetectionEnabled) {
                    CnameCloakingDetector.checkCname(data.effectiveDestination.host, appContext)
                } else null

                cachedTlsResult = result
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    LinkInspectorSecurityBinder.renderTlsResult(requireContext(), root, result)
                    cnameResult?.let { LinkInspectorSecurityBinder.renderCnameResult(requireContext(), root, it) }
                }
            }
        }
    }

    private fun bindTrackingStatus(root: View, data: LinkInspectionData) {
        val textTracking: TextView = root.findViewById(R.id.textInspectorTrackingStatus)
        val deAmpText = if (data.wasDeAmped) getString(R.string.inspector_de_amped_desc, data.deAmpSource ?: "AMP") else null
        val trackingText = if (data.wasCleaned && data.removedTrackingParams.isNotEmpty()) {
            getString(R.string.inspector_tracking_cleaned, data.removedTrackingParams.size) + " (${data.removedTrackingParams.joinToString(", ")})"
        } else null

        textTracking.text = when {
            deAmpText != null && trackingText != null -> "$deAmpText\n$trackingText"
            deAmpText != null -> deAmpText
            trackingText != null -> trackingText
            else -> getString(R.string.inspector_tracking_none)
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

