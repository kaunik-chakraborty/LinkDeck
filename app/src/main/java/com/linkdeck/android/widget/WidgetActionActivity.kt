package com.linkdeck.android.widget

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.linkdeck.android.MainActivity
import com.linkdeck.android.R
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.share.ShareUrlExtractor
import com.linkdeck.android.ui.chooser.ChooserActivity
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Headless trampoline that handles widget actions: clipboard paste & route,
 * quick link save, and settings navigation. Extends platform Activity (not
 * AppCompatActivity) because it renders zero UI and must use
 * Theme.Translucent.NoTitleBar which is incompatible with AppCompat themes.
 * Clipboard access is deferred to onWindowFocusChanged to comply with
 * Android 10-16 privacy requirements across all OEMs.
 */
class WidgetActionActivity : Activity() {

    private val quickLinksStore by lazy { WidgetQuickLinksStore(this) }
    private var pendingAction: String? = null
    private var hasExecuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val directUrl = intent.getStringExtra(EXTRA_DIRECT_URL)
        if (directUrl != null) {
            launchLink(directUrl)
            return
        }

        val action = intent.action
        when (action) {
            ACTION_PASTE_AND_OPEN, ACTION_PASTE_AND_SAVE -> {
                pendingAction = action
                // Safety fallback for OEM launchers that delay window focus delivery
                window.decorView.postDelayed({
                    executePendingActionIfNeeded()
                }, 350)
            }
            ACTION_OPEN_SETTINGS -> {
                startActivity(Intent(this, WidgetSettingsActivity::class.java))
                finish()
            }
            ACTION_OPEN_TEST_LINK -> {
                startActivity(Intent(this, TestLinkActivity::class.java))
                finish()
            }
            else -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            executePendingActionIfNeeded()
        }
    }

    private fun executePendingActionIfNeeded() {
        if (hasExecuted || isFinishing) return
        hasExecuted = true

        when (pendingAction) {
            ACTION_PASTE_AND_OPEN -> handlePasteAndOpen()
            ACTION_PASTE_AND_SAVE -> handlePasteAndSave()
            else -> finish()
        }
    }

    private fun handlePasteAndOpen() {
        val pastedText = getClipboardText()
        if (pastedText.isNullOrBlank()) {
            Toast.makeText(applicationContext, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val extractedUrl = ShareUrlExtractor.extractFirstUrl(pastedText)
        if (extractedUrl == null) {
            Toast.makeText(applicationContext, R.string.toast_clipboard_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sanitized = IntentSanitizer.sanitizeUrl(extractedUrl)
        if (sanitized is SanitizationResult.Success) {
            launchLink(sanitized.link.rawUrl)
        } else {
            Toast.makeText(applicationContext, R.string.toast_clipboard_invalid_link, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handlePasteAndSave() {
        val pastedText = getClipboardText()
        if (pastedText.isNullOrBlank()) {
            Toast.makeText(applicationContext, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val extractedUrl = ShareUrlExtractor.extractFirstUrl(pastedText)
        if (extractedUrl == null) {
            Toast.makeText(applicationContext, R.string.toast_clipboard_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sanitized = IntentSanitizer.sanitizeUrl(extractedUrl)
        if (sanitized is SanitizationResult.Success) {
            val added = quickLinksStore.addQuickLink(sanitized.link.rawUrl)
            if (added) {
                WidgetUpdateHelper.updateAllWidgets(this)
                val msg = getString(R.string.widget_quick_link_saved, sanitized.link.host)
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, R.string.widget_quick_links_limit, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(applicationContext, R.string.toast_clipboard_invalid_link, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun getClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).coerceToText(this)?.toString()?.trim()
        } else {
            null
        }
    }

    private fun launchLink(url: String) {
        val chooserIntent = Intent(this, ChooserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooserIntent)
        finish()
    }

    companion object {
        const val ACTION_PASTE_AND_OPEN = "com.linkdeck.android.widget.ACTION_PASTE_AND_OPEN"
        const val ACTION_PASTE_AND_SAVE = "com.linkdeck.android.widget.ACTION_PASTE_AND_SAVE"
        const val ACTION_OPEN_SETTINGS = "com.linkdeck.android.widget.ACTION_OPEN_SETTINGS"
        const val ACTION_OPEN_TEST_LINK = "com.linkdeck.android.widget.ACTION_OPEN_TEST_LINK"
        const val EXTRA_DIRECT_URL = "extra_direct_url"
    }
}
