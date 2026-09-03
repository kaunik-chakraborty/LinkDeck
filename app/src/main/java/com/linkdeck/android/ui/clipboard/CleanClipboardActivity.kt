package com.linkdeck.android.ui.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.linkdeck.android.R
import com.linkdeck.android.core.clipboard.ClipboardCleanResult
import com.linkdeck.android.core.clipboard.ClipboardLinkCleaner
import com.linkdeck.android.core.settings.AppSettingsStore

/**
 * Headless transparent trampoline that executes clipboard cleaning with window focus.
 * Extends platform Activity (not AppCompatActivity) because it renders zero UI and
 * must use Theme.Translucent.NoTitleBar which is incompatible with AppCompat themes.
 */
class CleanClipboardActivity : Activity() {

    private var hasExecuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety fallback for OEM launchers that delay window focus delivery
        window.decorView.postDelayed({
            if (!hasExecuted && !isFinishing) {
                hasExecuted = true
                executeCleanClipboard()
                finish()
            }
        }, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasExecuted && !isFinishing) {
            hasExecuted = true
            executeCleanClipboard()
            finish()
        }
    }

    private fun executeCleanClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                showToast(getString(R.string.toast_clipboard_empty))
                return
            }

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                showToast(getString(R.string.toast_clipboard_empty))
                return
            }

            val rawText = clip.getItemAt(0)?.coerceToText(this)?.toString()
            if (rawText.isNullOrBlank()) {
                showToast(getString(R.string.toast_clipboard_empty))
                return
            }

            val settingsStore = AppSettingsStore(this)
            val result = ClipboardLinkCleaner.clean(
                rawClipboardText = rawText,
                isDeAmpingEnabled = settingsStore.isDeAmpingEnabled,
                isTrackingCleanerEnabled = settingsStore.isTrackingCleanerEnabled
            )

            handleCleanResult(result, clipboard)
        } catch (_: SecurityException) {
            showToast(getString(R.string.toast_clipboard_empty))
        } catch (_: Exception) {
            showToast(getString(R.string.error_invalid_link_title))
        }
    }

    private fun handleCleanResult(result: ClipboardCleanResult, clipboard: ClipboardManager) {
        when (result) {
            is ClipboardCleanResult.Empty -> {
                showToast(getString(R.string.toast_clipboard_empty))
            }
            is ClipboardCleanResult.NoLinkFound -> {
                showToast(getString(R.string.toast_clipboard_no_link))
            }
            is ClipboardCleanResult.InvalidLink -> {
                showToast(getString(R.string.toast_clipboard_invalid_link))
            }
            is ClipboardCleanResult.AlreadyClean -> {
                showToast(getString(R.string.toast_clipboard_already_clean))
            }
            is ClipboardCleanResult.Cleaned -> {
                val newClip = ClipData.newPlainText("Clean Link", result.cleanedLink.rawUrl)
                clipboard.setPrimaryClip(newClip)

                val message = when {
                    result.wasDeAmped && result.hasRemovedParams ->
                        getString(R.string.toast_clipboard_deamped_and_cleaned, result.removedParams.size)
                    result.wasDeAmped ->
                        getString(R.string.toast_clipboard_deamped)
                    result.hasRemovedParams ->
                        getString(R.string.toast_clipboard_params_removed, result.removedParams.size)
                    else ->
                        getString(R.string.toast_clipboard_cleaned_link)
                }
                showToast(message)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
