package com.linkdeck.android.widget

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.linkdeck.android.MainActivity
import com.linkdeck.android.R
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.ui.chooser.ChooserActivity
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Ephemeral trampoline activity that handles widget actions such as
 * 1-tap clipboard paste & route, quick link save, and widget settings navigation.
 */
class WidgetActionActivity : AppCompatActivity() {

    private val quickLinksStore by lazy { WidgetQuickLinksStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        val directUrl = intent.getStringExtra(EXTRA_DIRECT_URL)

        when {
            directUrl != null -> {
                launchLink(directUrl)
            }
            action == ACTION_PASTE_AND_OPEN -> {
                handlePasteAndOpen()
            }
            action == ACTION_PASTE_AND_SAVE -> {
                handlePasteAndSave()
            }
            action == ACTION_OPEN_SETTINGS -> {
                startActivity(Intent(this, WidgetSettingsActivity::class.java))
                finish()
            }
            action == ACTION_OPEN_TEST_LINK -> {
                startActivity(Intent(this, TestLinkActivity::class.java))
                finish()
            }
            else -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun handlePasteAndOpen() {
        val pastedText = getClipboardText()
        if (pastedText.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty. Copy a link first!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, WidgetSettingsActivity::class.java))
            finish()
            return
        }

        val sanitized = IntentSanitizer.sanitizeUrl(pastedText)
        if (sanitized is SanitizationResult.Success) {
            launchLink(sanitized.link.rawUrl)
        } else {
            Toast.makeText(this, "No valid link found in clipboard", Toast.LENGTH_SHORT).show()
            val testIntent = Intent(this, TestLinkActivity::class.java).apply {
                putExtra(Intent.EXTRA_TEXT, pastedText)
            }
            startActivity(testIntent)
            finish()
        }
    }

    private fun handlePasteAndSave() {
        val pastedText = getClipboardText()
        if (pastedText.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty. Copy a link first!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, WidgetSettingsActivity::class.java))
            finish()
            return
        }

        val sanitized = IntentSanitizer.sanitizeUrl(pastedText)
        if (sanitized is SanitizationResult.Success) {
            val added = quickLinksStore.addQuickLink(sanitized.link.rawUrl)
            if (added) {
                WidgetUpdateHelper.updateAllWidgets(this)
                Toast.makeText(this, "Saved ${sanitized.link.host} to Widget Quick Links", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Widget quick links limit reached", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Invalid link in clipboard", Toast.LENGTH_SHORT).show()
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
