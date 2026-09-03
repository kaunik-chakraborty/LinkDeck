package com.linkdeck.android.ui.chooser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.linkdeck.android.R
import com.linkdeck.android.core.inspector.LinkInspectionData
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.preference.SharedPreferencesPinnedShareTargetStore
import com.linkdeck.android.ui.inspector.LinkInspectorBottomSheet

/**
 * BottomSheet presenting the target app chooser, domain preview, safety badges,
 * and optional per-domain preference actions ("Just once" / "Always").
 */
class ChooserBottomSheet : BottomSheetDialogFragment() {

    private var sanitizedLink: SanitizedLink? = null
    private var originalLink: SanitizedLink? = null
    private var errorMessage: String? = null
    private var openTargets: List<AppTarget> = emptyList()
    private var shareTargets: List<ShareTarget> = emptyList()
    private var isLoading = false
    private var wasCleaned = false
    private var wasDeAmped = false
    private var allowRememberChoices = true
    private var inspectionData: LinkInspectionData? = null
    private var selectedOpenTarget: AppTarget? = null
    private var isUsingOriginal = false
    private val settingsStore by lazy { com.linkdeck.android.core.settings.AppSettingsStore(requireContext()) }

    var onTargetLaunchRequested: ((AppTarget, SanitizedLink, Boolean) -> Unit)? = null
    var onShareRequested: ((ShareTarget, SanitizedLink) -> Unit)? = null
    var onOpenOriginalRequested: ((SanitizedLink) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    fun setLoadingData(link: SanitizedLink) {
        this.sanitizedLink = link
        this.originalLink = null
        this.openTargets = emptyList(); this.shareTargets = emptyList()
        this.isLoading = true; this.wasCleaned = false; this.wasDeAmped = false
        this.inspectionData = null; this.errorMessage = null
        this.selectedOpenTarget = null; this.isUsingOriginal = false
        updateView()
    }

    fun setLinkData(
        link: SanitizedLink, openTargets: List<AppTarget>, shareTargets: List<ShareTarget> = emptyList(),
        originalLink: SanitizedLink? = null, wasCleaned: Boolean = false, wasDeAmped: Boolean = false,
        inspectionData: LinkInspectionData? = null, allowRememberChoices: Boolean = true
    ) {
        this.sanitizedLink = link; this.originalLink = originalLink
        this.openTargets = openTargets; this.shareTargets = shareTargets
        this.isLoading = false; this.wasCleaned = wasCleaned; this.wasDeAmped = wasDeAmped
        this.allowRememberChoices = allowRememberChoices; this.inspectionData = inspectionData
        this.errorMessage = null; this.selectedOpenTarget = null; this.isUsingOriginal = false
        updateView()
    }

    fun setErrorData(message: String) {
        this.errorMessage = message
        this.sanitizedLink = null; this.originalLink = null
        this.openTargets = emptyList(); this.shareTargets = emptyList()
        this.isLoading = false; this.wasCleaned = false; this.wasDeAmped = false
        this.inspectionData = null; this.selectedOpenTarget = null
        this.isUsingOriginal = false
        updateView()
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
                BottomSheetBehavior.from(sheet).skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_chooser_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }
        updateView()
    }

    private fun getActiveLink(): SanitizedLink? {
        val currentLink = sanitizedLink ?: return null
        val orig = originalLink
        return if (isUsingOriginal && orig != null) orig else currentLink
    }

    private fun updateView() {
        val root = view ?: return
        val currentError = errorMessage
        val currentLink = sanitizedLink

        if (currentError != null) {
            root.findViewById<View>(R.id.urlPreviewBadge).visibility = View.GONE
            root.findViewById<View>(R.id.loadingLayout).visibility = View.GONE
            root.findViewById<View>(R.id.targetsRecyclerView).visibility = View.GONE
            root.findViewById<View>(R.id.emptyStateLayout).visibility = View.GONE
            root.findViewById<View>(R.id.errorStateLayout).visibility = View.VISIBLE
            root.findViewById<TextView>(R.id.textErrorMessage).text = currentError
            root.findViewById<View>(R.id.btnDismissError).setOnClickListener { dismiss() }
            return
        }

        if (currentLink != null) {
            root.findViewById<View>(R.id.urlPreviewBadge).visibility = View.VISIBLE
            root.findViewById<View>(R.id.errorStateLayout).visibility = View.GONE
            bindHeader(root, currentLink)
            bindActions(root, currentLink)
            bindTargets(root, currentLink)
        }
    }

    private fun bindHeader(root: View, currentLink: SanitizedLink) {
        val orig = originalLink
        val isTransformed = orig != null && (orig.rawUrl != currentLink.rawUrl || wasCleaned || wasDeAmped)
        val displayLink = if (isUsingOriginal && orig != null) orig else currentLink

        root.findViewById<TextView>(R.id.textHost).text = displayLink.host
        root.findViewById<TextView>(R.id.textPath).text = UrlRedactor.truncateForDisplay(displayLink.path.ifEmpty { "/" })

        val textRedirectSource = root.findViewById<TextView>(R.id.textRedirectSource)
        textRedirectSource.visibility = if (!isUsingOriginal && orig != null && orig.host != currentLink.host) View.VISIBLE else View.GONE
        if (orig != null) textRedirectSource.text = getString(R.string.redirected_from, orig.host)

        root.findViewById<TextView>(R.id.textCleanedBadge).visibility = if (wasCleaned && !isUsingOriginal) View.VISIBLE else View.GONE
        root.findViewById<TextView?>(R.id.textDeAmpedBadge)?.visibility = if (wasDeAmped && !isUsingOriginal) View.VISIBLE else View.GONE

        bindThreats(root, displayLink)

        val cbOriginal = root.findViewById<MaterialCheckBox>(R.id.cbOpenOriginalLink)
        if (isTransformed) {
            cbOriginal.visibility = View.VISIBLE
            cbOriginal.setOnCheckedChangeListener(null)
            cbOriginal.isChecked = isUsingOriginal
            cbOriginal.setOnCheckedChangeListener { _, isChecked ->
                isUsingOriginal = isChecked
                val active = getActiveLink() ?: currentLink
                root.findViewById<TextView>(R.id.textHost).text = active.host
                root.findViewById<TextView>(R.id.textPath).text = UrlRedactor.truncateForDisplay(active.path.ifEmpty { "/" })
                textRedirectSource.visibility = if (!isChecked && orig != null && orig.host != currentLink.host) View.VISIBLE else View.GONE
                root.findViewById<TextView>(R.id.textCleanedBadge).visibility = if (wasCleaned && !isChecked) View.VISIBLE else View.GONE
                root.findViewById<TextView?>(R.id.textDeAmpedBadge)?.visibility = if (wasDeAmped && !isChecked) View.VISIBLE else View.GONE
                val toastMsg = if (isChecked) R.string.toast_using_original_link else R.string.toast_using_cleaned_link
                Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
            }
        } else {
            cbOriginal.visibility = View.GONE
        }
    }

    private fun bindThreats(root: View, displayLink: SanitizedLink) {
        val threats = if (settingsStore.isThreatWarningsEnabled) com.linkdeck.android.core.security.LinkThreatAnalyzer.analyze(displayLink) else emptyList()
        val criticalThreat = threats.firstOrNull { it.severity == com.linkdeck.android.core.security.LinkThreatWarning.Severity.CRITICAL }
            ?: threats.firstOrNull { it.severity == com.linkdeck.android.core.security.LinkThreatWarning.Severity.HIGH }
            ?: threats.firstOrNull { it is com.linkdeck.android.core.security.LinkThreatWarning.CleartextHttp }

        val badge = root.findViewById<TextView>(R.id.textSecurityBadge)
        badge.visibility = if (criticalThreat != null) View.VISIBLE else View.GONE
        if (criticalThreat != null) {
            badge.text = when (criticalThreat) {
                is com.linkdeck.android.core.security.LinkThreatWarning.PunycodePhishing -> "Punycode Phishing"
                is com.linkdeck.android.core.security.LinkThreatWarning.UserInfoDeception -> "Deceptive Link"
                is com.linkdeck.android.core.security.LinkThreatWarning.CleartextHttp -> "HTTP Insecure"
                else -> "Security Alert"
            }
        }
    }

    private fun bindActions(root: View, currentLink: SanitizedLink) {
        root.findViewById<View>(R.id.btnInspectLink).setOnClickListener {
            inspectionData?.let { data ->
                val inspector = LinkInspectorBottomSheet.newInstance(data)
                inspector.onOpenOriginalRequested = { origLink ->
                    isUsingOriginal = true
                    updateView()
                    val selected = selectedOpenTarget
                    if (selected != null) {
                        onTargetLaunchRequested?.invoke(selected, origLink, false)
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_using_original_link, Toast.LENGTH_SHORT).show()
                        onOpenOriginalRequested?.invoke(origLink)
                    }
                }
                inspector.show(parentFragmentManager, LinkInspectorBottomSheet.TAG)
            }
        }

        root.findViewById<View>(R.id.btnCopyLink).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Link", (getActiveLink() ?: currentLink).rawUrl))
            Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindTargets(root: View, currentLink: SanitizedLink) {
        val loadingLayout = root.findViewById<View>(R.id.loadingLayout)
        val btnInspect = root.findViewById<View>(R.id.btnInspectLink)
        val recyclerView = root.findViewById<RecyclerView>(R.id.targetsRecyclerView)
        val emptyLayout = root.findViewById<View>(R.id.emptyStateLayout)

        if (isLoading) {
            loadingLayout.visibility = View.VISIBLE
            btnInspect.visibility = View.GONE; recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.GONE
            return
        }

        loadingLayout.visibility = View.GONE
        btnInspect.visibility = if (inspectionData != null) View.VISIBLE else View.GONE
        val hasTargets = openTargets.isNotEmpty() || shareTargets.isNotEmpty()

        if (!hasTargets) {
            recyclerView.visibility = View.GONE; emptyLayout.visibility = View.VISIBLE
        } else {
            emptyLayout.visibility = View.GONE; recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = LinearLayoutManager(requireContext())

            val pinnedStore = SharedPreferencesPinnedShareTargetStore(requireContext())
            val adapter = TargetAdapter(
                context = requireContext(),
                packageManager = requireContext().packageManager,
                pinnedStore = pinnedStore,
                allowRememberChoices = allowRememberChoices,
                onOpenTargetLaunch = { target, isAlways ->
                    selectedOpenTarget = target
                    onTargetLaunchRequested?.invoke(target, getActiveLink() ?: currentLink, isAlways)
                },
                onShareTargetClicked = { shareTarget ->
                    onShareRequested?.invoke(shareTarget, getActiveLink() ?: currentLink)
                }
            )
            recyclerView.adapter = adapter
            adapter.submitData(openTargets, shareTargets)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    companion object {
        const val TAG = "ChooserBottomSheet"
        fun newInstance(): ChooserBottomSheet = ChooserBottomSheet()
    }
}
