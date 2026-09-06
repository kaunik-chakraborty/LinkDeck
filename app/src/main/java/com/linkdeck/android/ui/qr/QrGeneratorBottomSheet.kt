package com.linkdeck.android.ui.qr

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.cleaner.rules.CustomParameterRulesStore
import com.linkdeck.android.core.deamp.DeAmpEngine
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.qr.QrCodeGenerator

/**
 * Interactive modal bottom sheet for offline high-resolution QR generation.
 * Automatically sanitizes tracking tags & De-AMPs links with toggle control.
 */
class QrGeneratorBottomSheet : BottomSheetDialogFragment() {

    private var generatedBitmap: Bitmap? = null
    private var currentEncodedContent: String = ""

    private lateinit var editInput: TextInputEditText
    private lateinit var imageQrCode: ImageView
    private lateinit var textUrlPreview: TextView
    private lateinit var chipDeAmped: Chip
    private lateinit var chipTrackersCleaned: Chip
    private lateinit var checkboxCleanLink: MaterialCheckBox
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnShare: MaterialButton

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
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_qr_generator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        view.findViewById<View>(R.id.btnQrGenClose)?.setOnClickListener {
            dismiss()
        }

        editInput = view.findViewById(R.id.editQrInput)
        imageQrCode = view.findViewById(R.id.imageQrCode)
        textUrlPreview = view.findViewById(R.id.textQrUrlPreview)
        chipDeAmped = view.findViewById(R.id.chipDeAmped)
        chipTrackersCleaned = view.findViewById(R.id.chipTrackersCleaned)
        checkboxCleanLink = view.findViewById(R.id.checkboxCleanLink)
        btnCopy = view.findViewById(R.id.btnCopyQrLink)
        btnShare = view.findViewById(R.id.btnShareQrImage)

        view.findViewById<View>(R.id.btnQrPaste)?.setOnClickListener {
            pasteFromClipboard()
        }

        checkboxCleanLink.setOnCheckedChangeListener { _, _ ->
            processAndGenerateQr()
        }

        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                processAndGenerateQr()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCopy.setOnClickListener {
            if (currentEncodedContent.isNotBlank()) {
                copyUrlToClipboard(currentEncodedContent)
            }
        }

        btnShare.setOnClickListener {
            val bmp = generatedBitmap ?: return@setOnClickListener
            shareQrCodeBitmap(bmp)
        }

        val initialUrl = arguments?.getString(ARG_URL).orEmpty()
        if (initialUrl.isNotBlank()) {
            editInput.setText(initialUrl)
        } else {
            processAndGenerateQr()
        }
    }

    private fun processAndGenerateQr() {
        val raw = editInput.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            chipDeAmped.visibility = View.GONE
            chipTrackersCleaned.visibility = View.GONE
            checkboxCleanLink.visibility = View.GONE
            textUrlPreview.text = getString(R.string.qr_gen_empty_prompt)
            imageQrCode.setImageBitmap(null)
            btnCopy.isEnabled = false
            btnShare.isEnabled = false
            currentEncodedContent = ""
            generatedBitmap = null
            return
        }

        val shouldSanitize = checkboxCleanLink.isChecked
        val sanitization = IntentSanitizer.sanitizeUrl(raw)

        val finalPayload = if (sanitization is SanitizationResult.Success) {
            checkboxCleanLink.visibility = View.VISIBLE
            if (shouldSanitize) {
                var candidate = sanitization.link
                val deAmpResult = DeAmpEngine.deAmp(candidate)
                if (deAmpResult.wasDeAmped) {
                    candidate = deAmpResult.deAmpedLink
                    chipDeAmped.visibility = View.VISIBLE
                } else {
                    chipDeAmped.visibility = View.GONE
                }

                val customRules = CustomParameterRulesStore(requireContext()).getEnabledRules()
                val cleanResult = TrackingParameterCleaner.clean(candidate, customRules)
                if (cleanResult.hasRemovedParams) {
                    candidate = cleanResult.cleanedLink
                    chipTrackersCleaned.text = getString(R.string.inspector_tracking_cleaned, cleanResult.removedParams.size)
                    chipTrackersCleaned.visibility = View.VISIBLE
                } else {
                    chipTrackersCleaned.visibility = View.GONE
                }

                candidate.rawUrl
            } else {
                chipDeAmped.visibility = View.GONE
                chipTrackersCleaned.visibility = View.GONE
                raw
            }
        } else {
            checkboxCleanLink.visibility = View.GONE
            chipDeAmped.visibility = View.GONE
            chipTrackersCleaned.visibility = View.GONE
            raw
        }

        currentEncodedContent = finalPayload
        textUrlPreview.text = finalPayload

        val bitmap = QrCodeGenerator.generateBitmap(
            content = finalPayload,
            sizePx = 600,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE
        )
        generatedBitmap = bitmap

        if (bitmap != null) {
            imageQrCode.setImageBitmap(bitmap)
            btnCopy.isEnabled = true
            btnShare.isEnabled = true
        } else {
            btnCopy.isEnabled = false
            btnShare.isEnabled = false
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (!clip.isNullOrBlank()) {
            editInput.setText(clip)
        }
    }

    private fun copyUrlToClipboard(content: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("LinkDeck QR", content)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun shareQrCodeBitmap(bitmap: Bitmap) {
        val uri = QrCodeGenerator.saveQrBitmapToCache(requireContext(), bitmap)
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.qr_scanner_gallery_read_err, Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, currentEncodedContent)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.qr_generator_share_image)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        generatedBitmap = null
    }

    companion object {
        const val TAG = "QrGeneratorBottomSheet"
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String = ""): QrGeneratorBottomSheet {
            return QrGeneratorBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
