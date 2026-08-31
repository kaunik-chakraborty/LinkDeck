package com.linkdeck.android.ui.inspector

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

/**
 * Ephemeral modal bottom sheet for inspecting link diagnostics, redirect chains,
 * tracking parameter cleaning, and routing decisions with sensitive data redaction.
 */
class LinkInspectorBottomSheet : BottomSheetDialogFragment() {

    private var inspectionData: LinkInspectionData? = null
    var onOpenOriginalRequested: ((SanitizedLink) -> Unit)? = null

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

        val textOriginal: TextView = view.findViewById(R.id.textInspectorOriginalUrl)
        val layoutHops: LinearLayout = view.findViewById(R.id.layoutRedirectHops)
        val textNoRedirects: TextView = view.findViewById(R.id.textInspectorNoRedirects)
        val textDestination: TextView = view.findViewById(R.id.textInspectorDestinationUrl)
        val textTracking: TextView = view.findViewById(R.id.textInspectorTrackingStatus)
        val textRouting: TextView = view.findViewById(R.id.textInspectorRoutingReason)
        val btnOpenOriginal: MaterialButton = view.findViewById(R.id.btnInspectorOpenOriginal)
        val btnDone: MaterialButton = view.findViewById(R.id.btnInspectorDone)
        val btnClose: View? = view.findViewById(R.id.btnInspectorClose)

        btnClose?.setOnClickListener {
            dismiss()
        }

        val onSurfaceVariantColor = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            requireContext().getColor(R.color.on_surface_light)
        )

        // 1. Original Link (presentation-only redacted)
        textOriginal.text = UrlRedactor.redact(data.originalLink)

        // 2. Redirect Chain
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
                    setTextColor(onSurfaceVariantColor)
                    setPadding(0, 4, 0, 4)
                    val safeTarget = UrlRedactor.redactUrlString(hop.targetUrl)
                    text = "${index + 1}. HTTP ${hop.statusCode} → $safeTarget"
                    contentDescription = getString(R.string.inspector_redirect_hop, index + 1, hop.statusCode) + ", " + safeTarget
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
                val errorView = TextView(requireContext()).apply {
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.error))
                    setPadding(0, 4, 0, 4)
                    text = "• $errorMsg"
                    contentDescription = errorMsg
                }
                layoutHops.addView(errorView)
            }
        }

        // 3. Final Destination (presentation-only redacted)
        textDestination.text = UrlRedactor.redact(data.effectiveDestination)

        // 4. Tracking Protection
        if (data.wasCleaned && data.removedTrackingParams.isNotEmpty()) {
            val count = data.removedTrackingParams.size
            val paramsStr = data.removedTrackingParams.joinToString(", ")
            textTracking.text = getString(R.string.inspector_tracking_cleaned, count) + " ($paramsStr)"
        } else {
            textTracking.text = getString(R.string.inspector_tracking_none)
        }

        // 5. Routing Decision
        textRouting.text = when (val reason = data.routingExplanation) {
            is RoutingExplanation.MatchedRule -> getString(R.string.inspector_rule_matched, reason.rule.displayCondition)
            is RoutingExplanation.SavedPreference -> getString(R.string.inspector_pref_matched, reason.preference.domain)
            is RoutingExplanation.ManualChoice -> getString(R.string.inspector_manual_choice)
            is RoutingExplanation.RecommendedApp -> "Recommended: ${reason.appLabel}"
        }

        // 6. Open Original Link Action
        val isTransformed = data.originalLink.rawUrl != data.effectiveDestination.rawUrl || data.wasCleaned
        if (isTransformed) {
            btnOpenOriginal.visibility = View.VISIBLE
            btnOpenOriginal.setOnClickListener {
                dismiss()
                onOpenOriginalRequested?.invoke(data.originalLink)
            }
        } else {
            btnOpenOriginal.visibility = View.GONE
        }

        btnDone.setOnClickListener {
            dismiss()
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
