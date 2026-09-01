package com.linkdeck.android.ui.share

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
import com.linkdeck.android.ui.chooser.ChooserActivity

/**
 * Bottom sheet modal displaying a cleaned URL extracted from an incoming share intent,
 * enabling 1-tap sharing to messaging apps, 1-tap clipboard copying, or opening via LinkDeck.
 */
class ShareCleanBottomSheet : BottomSheetDialogFragment() {

    private var cleanedUrl: String = ""
    private var originalUrl: String = ""
    private var removedParams: ArrayList<String> = arrayListOf()

    var onDismissedListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            cleanedUrl = it.getString(ARG_CLEANED_URL, "")
            originalUrl = it.getString(ARG_ORIGINAL_URL, "")
            removedParams = it.getStringArrayList(ARG_REMOVED_PARAMS) ?: arrayListOf()
        }
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
        return inflater.inflate(R.layout.bottom_sheet_share_clean, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        val textUrl = view.findViewById<TextView>(R.id.textCleanUrl)
        val textStatus = view.findViewById<TextView>(R.id.textTrackingCleanedStatus)
        val checkShareOriginal = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkShareOriginal)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnShareCleanLink)
        val btnCopy = view.findViewById<MaterialButton>(R.id.btnCopyCleanLink)

        var isSharingOriginal = false

        textUrl.text = cleanedUrl

        if (removedParams.isNotEmpty()) {
            val paramList = removedParams.joinToString(", ")
            textStatus.text = getString(R.string.share_clean_params_removed, removedParams.size, paramList)
            textStatus.visibility = View.VISIBLE
            checkShareOriginal.visibility = View.VISIBLE

            checkShareOriginal.setOnCheckedChangeListener { _, isChecked ->
                isSharingOriginal = isChecked
                if (isChecked) {
                    textUrl.text = originalUrl
                    btnShare.text = getString(R.string.share_original_btn_share)
                    btnCopy.text = getString(R.string.share_original_btn_copy)
                    textStatus.text = getString(R.string.share_original_active_warning)
                } else {
                    textUrl.text = cleanedUrl
                    btnShare.text = getString(R.string.share_clean_btn_share)
                    btnCopy.text = getString(R.string.share_clean_btn_copy)
                    textStatus.text = getString(R.string.share_clean_params_removed, removedParams.size, paramList)
                }
            }
        } else {
            textStatus.text = getString(R.string.share_clean_no_params)
            textStatus.visibility = View.VISIBLE
            checkShareOriginal.visibility = View.GONE
        }

        view.findViewById<View>(R.id.btnShareCleanClose).setOnClickListener {
            dismiss()
        }

        // 1. Share Link (Dispatches system share sheet to forward to chat apps)
        btnShare.setOnClickListener {
            val urlToShare = if (isSharingOriginal) originalUrl else cleanedUrl
            val title = if (isSharingOriginal) getString(R.string.share_original_btn_share) else getString(R.string.share_clean_btn_share)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, urlToShare)
            }
            startActivity(Intent.createChooser(sendIntent, title))
            dismiss()
        }

        // 2. Copy Link
        btnCopy.setOnClickListener {
            val urlToCopy = if (isSharingOriginal) originalUrl else cleanedUrl
            val toastMsg = if (isSharingOriginal) R.string.share_original_toast_copied else R.string.share_clean_toast_copied
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Link", urlToCopy))
            Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
            dismiss()
        }

        // 3. Open via LinkDeck Chooser
        view.findViewById<MaterialButton>(R.id.btnOpenInLinkDeck).setOnClickListener {
            val urlToOpen = if (isSharingOriginal) originalUrl else cleanedUrl
            val intent = Intent(requireContext(), ChooserActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(urlToOpen)
            }
            startActivity(intent)
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissedListener?.invoke()
    }

    companion object {
        const val TAG = "ShareCleanBottomSheet"
        private const val ARG_CLEANED_URL = "arg_cleaned_url"
        private const val ARG_ORIGINAL_URL = "arg_original_url"
        private const val ARG_REMOVED_PARAMS = "arg_removed_params"

        fun newInstance(
            cleanedUrl: String,
            originalUrl: String,
            removedParams: List<String>
        ): ShareCleanBottomSheet {
            return ShareCleanBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CLEANED_URL, cleanedUrl)
                    putString(ARG_ORIGINAL_URL, originalUrl)
                    putStringArrayList(ARG_REMOVED_PARAMS, ArrayList(removedParams))
                }
            }
        }
    }
}
