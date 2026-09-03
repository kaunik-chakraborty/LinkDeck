package com.linkdeck.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.linkdeck.android.R
import com.linkdeck.android.core.clipboard.ClipboardCleanResult
import com.linkdeck.android.core.clipboard.ClipboardLinkCleaner
import com.linkdeck.android.core.settings.AppSettingsStore

/**
 * System Quick Settings tile service that enables instant 1-tap clipboard link sanitization.
 * Strips tracking parameters, unrolls AMP containers, and updates clipboard in-place.
 */
class CleanClipboardTileService : TileService() {

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { resetTileState() }

    override fun onStartListening() {
        super.onStartListening()
        resetTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        resetHandler.removeCallbacks(resetRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        resetHandler.removeCallbacks(resetRunnable)
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                executeClipboardCleaning()
            }
        } else {
            executeClipboardCleaning()
        }
    }

    private fun executeClipboardCleaning() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                showFeedback(getString(R.string.toast_clipboard_empty))
                return
            }

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                showFeedback(getString(R.string.toast_clipboard_empty))
                return
            }

            val rawText = clip.getItemAt(0)?.coerceToText(this)?.toString()
            if (rawText.isNullOrBlank()) {
                showFeedback(getString(R.string.toast_clipboard_empty))
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
            showFeedback(getString(R.string.toast_clipboard_empty))
        } catch (_: Exception) {
            showFeedback(getString(R.string.error_invalid_link_title))
        }
    }

    private fun handleCleanResult(result: ClipboardCleanResult, clipboard: ClipboardManager) {
        when (result) {
            is ClipboardCleanResult.Empty -> {
                showFeedback(getString(R.string.toast_clipboard_empty))
            }
            is ClipboardCleanResult.NoLinkFound -> {
                showFeedback(getString(R.string.toast_clipboard_no_link))
            }
            is ClipboardCleanResult.InvalidLink -> {
                showFeedback(getString(R.string.toast_clipboard_invalid_link))
            }
            is ClipboardCleanResult.AlreadyClean -> {
                showFeedback(getString(R.string.toast_clipboard_already_clean))
                pulseTileState(
                    active = false,
                    subtitle = getString(R.string.tile_clean_clipboard_already_clean)
                )
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

                showFeedback(message)
                pulseTileState(
                    active = true,
                    subtitle = getString(R.string.tile_clean_clipboard_cleaned)
                )
            }
        }
    }

    private fun pulseTileState(active: Boolean, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()

        resetHandler.removeCallbacks(resetRunnable)
        resetHandler.postDelayed(resetRunnable, 1800)
    }

    private fun resetTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_clean_clipboard_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.tile_clean_clipboard_subtitle)
        }
        tile.updateTile()
    }

    private fun showFeedback(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
