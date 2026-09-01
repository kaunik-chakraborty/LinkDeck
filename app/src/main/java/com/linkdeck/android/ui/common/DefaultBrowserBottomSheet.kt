package com.linkdeck.android.ui.common

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.R

/**
 * BottomSheet dialog prompting the user to configure LinkDeck as their system default browser.
 */
class DefaultBrowserBottomSheet : BottomSheetDialogFragment() {

    var onSetDefaultClicked: (() -> Unit)? = null
    var onDismissedListener: (() -> Unit)? = null

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
        return inflater.inflate(R.layout.dialog_default_browser_prompt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        view.findViewById<MaterialButton>(R.id.btnSetAsDefault).setOnClickListener {
            onSetDefaultClicked?.invoke()
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnDismissPrompt).setOnClickListener {
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissedListener?.invoke()
    }

    companion object {
        const val TAG = "DefaultBrowserBottomSheet"

        fun newInstance(): DefaultBrowserBottomSheet {
            return DefaultBrowserBottomSheet()
        }
    }
}
