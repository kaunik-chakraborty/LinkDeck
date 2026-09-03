package com.linkdeck.android.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.linkdeck.android.R
import com.linkdeck.android.ui.clipboard.CleanClipboardActivity

/**
 * System Quick Settings tile service that triggers 1-tap clipboard link sanitization.
 * Dispatches a lightweight transparent trampoline activity so clipboard access
 * is granted with foreground window focus in compliance with Android 10-16 privacy rules.
 */
class CleanClipboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        resetTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        resetTileState()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                launchCleanAction()
            }
        } else {
            launchCleanAction()
        }
    }

    private fun launchCleanAction() {
        val intent = Intent(this, CleanClipboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
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
}
