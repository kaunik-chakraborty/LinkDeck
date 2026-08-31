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
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget
import com.linkdeck.android.core.preference.SharedPreferencesPinnedShareTargetStore
import com.linkdeck.android.ui.inspector.LinkInspectorBottomSheet

/**
 * Bottom sheet presentation for choosing an application to open or share a sanitized link,
 * supporting 1-tap direct launch, temporary ("Just once") and remembered ("Always") routing choices,
 * distinct "Share with" targets with pinning, tracking removal indicators,
 * "Open original link" checkbox toggle, and on-device Link Inspection diagnostics.
 */
class ChooserBottomSheet : BottomSheetDialogFragment() {

    private var sanitizedLink: SanitizedLink? = null
    private var originalLink: SanitizedLink? = null
    private var errorMessage: String? = null
    private var openTargets: List<AppTarget> = emptyList()
    private var shareTargets: List<ShareTarget> = emptyList()
    private var isLoading: Boolean = false
    private var wasCleaned: Boolean = false
    private var allowRememberChoices: Boolean = true
    private var inspectionData: LinkInspectionData? = null
    private var selectedOpenTarget: AppTarget? = null
    private var targetAdapter: TargetAdapter? = null
    private var isUsingOriginal: Boolean = false

    var onTargetLaunchRequested: ((AppTarget, SanitizedLink, Boolean) -> Unit)? = null
    var onShareRequested: ((ShareTarget, SanitizedLink) -> Unit)? = null
    var onOpenOriginalRequested: ((SanitizedLink) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    fun setLoadingData(link: SanitizedLink) {
        this.sanitizedLink = link
        this.originalLink = null
        this.openTargets = emptyList()
        this.shareTargets = emptyList()
        this.isLoading = true
        this.wasCleaned = false
        this.allowRememberChoices = true
        this.inspectionData = null
        this.errorMessage = null
        this.selectedOpenTarget = null
        this.isUsingOriginal = false
        updateView()
    }

    fun setLinkData(
        link: SanitizedLink,
        openTargets: List<AppTarget>,
        shareTargets: List<ShareTarget> = emptyList(),
        originalLink: SanitizedLink? = null,
        wasCleaned: Boolean = false,
        inspectionData: LinkInspectionData? = null,
        allowRememberChoices: Boolean = true
    ) {
        this.sanitizedLink = link
        this.originalLink = originalLink
        this.openTargets = openTargets
        this.shareTargets = shareTargets
        this.isLoading = false
        this.wasCleaned = wasCleaned
        this.allowRememberChoices = allowRememberChoices
        this.inspectionData = inspectionData
        this.errorMessage = null
        this.selectedOpenTarget = null
        this.isUsingOriginal = false
        updateView()
    }

    fun setErrorData(message: String) {
        this.errorMessage = message
        this.sanitizedLink = null
        this.originalLink = null
        this.openTargets = emptyList()
        this.shareTargets = emptyList()
        this.isLoading = false
        this.wasCleaned = false
        this.inspectionData = null
        this.selectedOpenTarget = null
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
        return inflater.inflate(R.layout.fragment_chooser_bottom_sheet, container, false)
    }

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

        val textHost: TextView = root.findViewById(R.id.textHost)
        val textPath: TextView = root.findViewById(R.id.textPath)
        val textRedirectSource: TextView = root.findViewById(R.id.textRedirectSource)
        val textCleanedBadge: TextView = root.findViewById(R.id.textCleanedBadge)
        val cbOpenOriginalLink: MaterialCheckBox = root.findViewById(R.id.cbOpenOriginalLink)
        val btnInspect: View = root.findViewById(R.id.btnInspectLink)
        val btnCopy: View = root.findViewById(R.id.btnCopyLink)
        val recyclerView: RecyclerView = root.findViewById(R.id.targetsRecyclerView)
        val emptyLayout: View = root.findViewById(R.id.emptyStateLayout)
        val errorLayout: View = root.findViewById(R.id.errorStateLayout)
        val loadingLayout: View = root.findViewById(R.id.loadingLayout)
        val actionButtonsLayout: View = root.findViewById(R.id.actionButtonsLayout)
        val btnJustOnce: MaterialButton = root.findViewById(R.id.btnJustOnce)
        val btnAlways: MaterialButton = root.findViewById(R.id.btnAlways)
        val textError: TextView = root.findViewById(R.id.textErrorMessage)
        val btnDismiss: MaterialButton = root.findViewById(R.id.btnDismissError)
        val previewBadge: View = root.findViewById(R.id.urlPreviewBadge)

        val currentError = errorMessage
        val currentLink = sanitizedLink

        if (currentError != null) {
            previewBadge.visibility = View.GONE
            loadingLayout.visibility = View.GONE
            recyclerView.visibility = View.GONE
            actionButtonsLayout.visibility = View.GONE
            emptyLayout.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            textError.text = currentError

            btnDismiss.setOnClickListener {
                dismiss()
            }
            return
        }

        if (currentLink != null) {
            previewBadge.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE

            val orig = originalLink
            val isTransformed = orig != null && (orig.rawUrl != currentLink.rawUrl || wasCleaned)

            val displayLink = if (isUsingOriginal && orig != null) orig else currentLink
            textHost.text = displayLink.host
            textPath.text = UrlRedactor.truncateForDisplay(displayLink.path.ifEmpty { "/" })

            if (!isUsingOriginal && orig != null && orig.host != currentLink.host) {
                textRedirectSource.visibility = View.VISIBLE
                textRedirectSource.text = getString(R.string.redirected_from, orig.host)
            } else {
                textRedirectSource.visibility = View.GONE
            }

            textCleanedBadge.visibility = if (wasCleaned && !isUsingOriginal) View.VISIBLE else View.GONE

            if (isTransformed) {
                cbOpenOriginalLink.visibility = View.VISIBLE
                cbOpenOriginalLink.setOnCheckedChangeListener(null)
                cbOpenOriginalLink.isChecked = isUsingOriginal
                cbOpenOriginalLink.setOnCheckedChangeListener { _, isChecked ->
                    isUsingOriginal = isChecked
                    val active = getActiveLink() ?: currentLink
                    textHost.text = active.host
                    textPath.text = UrlRedactor.truncateForDisplay(active.path.ifEmpty { "/" })
                    if (!isChecked && orig != null && orig.host != currentLink.host) {
                        textRedirectSource.visibility = View.VISIBLE
                    } else {
                        textRedirectSource.visibility = View.GONE
                    }
                    textCleanedBadge.visibility = if (wasCleaned && !isChecked) View.VISIBLE else View.GONE
                    val toastMsg = if (isChecked) R.string.toast_using_original_link else R.string.toast_using_cleaned_link
                    Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
                }
            } else {
                cbOpenOriginalLink.visibility = View.GONE
            }

            btnInspect.setOnClickListener {
                val data = inspectionData
                if (data != null) {
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

            btnCopy.setOnClickListener {
                val active = getActiveLink() ?: currentLink
                copyUrlToClipboard(active.rawUrl)
            }

            if (allowRememberChoices) {
                btnJustOnce.text = getString(R.string.action_just_once)
                btnAlways.visibility = View.VISIBLE
            } else {
                btnJustOnce.text = getString(R.string.action_open)
                btnAlways.visibility = View.GONE
            }

            btnJustOnce.setOnClickListener {
                selectedOpenTarget?.let { target ->
                    val active = getActiveLink() ?: currentLink
                    onTargetLaunchRequested?.invoke(target, active, false)
                }
            }

            btnAlways.setOnClickListener {
                selectedOpenTarget?.let { target ->
                    val active = getActiveLink() ?: currentLink
                    onTargetLaunchRequested?.invoke(target, active, true)
                }
            }

            if (isLoading) {
                loadingLayout.visibility = View.VISIBLE
                btnInspect.visibility = View.GONE
                recyclerView.visibility = View.GONE
                actionButtonsLayout.visibility = View.GONE
                emptyLayout.visibility = View.GONE
            } else {
                loadingLayout.visibility = View.GONE
                btnInspect.visibility = if (inspectionData != null) View.VISIBLE else View.GONE

                val hasAnyTargets = openTargets.isNotEmpty() || shareTargets.isNotEmpty()

                if (!hasAnyTargets) {
                    recyclerView.visibility = View.GONE
                    actionButtonsLayout.visibility = View.GONE
                    emptyLayout.visibility = View.VISIBLE
                } else {
                    emptyLayout.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())

                    val pinnedStore = SharedPreferencesPinnedShareTargetStore(requireContext())
                    val adapter = TargetAdapter(
                        context = requireContext(),
                        packageManager = requireContext().packageManager,
                        pinnedStore = pinnedStore,
                        onOpenTargetSelected = { target ->
                            selectedOpenTarget = target
                            val active = getActiveLink() ?: currentLink
                            // 1-Tap on any target opens immediately!
                            onTargetLaunchRequested?.invoke(target, active, false)
                        },
                        onShareTargetClicked = { shareTarget ->
                            val active = getActiveLink() ?: currentLink
                            onShareRequested?.invoke(shareTarget, active)
                        }
                    )
                    targetAdapter = adapter
                    recyclerView.adapter = adapter
                    adapter.submitData(openTargets, shareTargets)

                    actionButtonsLayout.visibility = if (selectedOpenTarget != null) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    private fun copyUrlToClipboard(url: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Link", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "ChooserBottomSheet"

        fun newInstance(): ChooserBottomSheet {
            return ChooserBottomSheet()
        }
    }
}
