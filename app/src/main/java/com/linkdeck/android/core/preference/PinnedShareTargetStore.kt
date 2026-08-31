package com.linkdeck.android.core.preference

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user-pinned share targets so frequently used share apps (e.g. WhatsApp, Telegram)
 * always appear at the top of the "Share with" list.
 */
interface PinnedShareTargetStore {
    fun getPinnedTargets(): Set<String>
    fun isPinned(componentSignature: String): Boolean
    fun togglePin(componentSignature: String): Boolean
    fun setPinned(componentSignature: String, isPinned: Boolean)
}

class SharedPreferencesPinnedShareTargetStore(context: Context) : PinnedShareTargetStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getPinnedTargets(): Set<String> {
        return prefs.getStringSet(KEY_PINNED_TARGETS, emptySet()) ?: emptySet()
    }

    override fun isPinned(componentSignature: String): Boolean {
        return getPinnedTargets().contains(componentSignature)
    }

    override fun togglePin(componentSignature: String): Boolean {
        val current = getPinnedTargets().toMutableSet()
        val willBePinned = if (current.contains(componentSignature)) {
            current.remove(componentSignature)
            false
        } else {
            current.add(componentSignature)
            true
        }
        prefs.edit().putStringSet(KEY_PINNED_TARGETS, current).apply()
        return willBePinned
    }

    override fun setPinned(componentSignature: String, isPinned: Boolean) {
        val current = getPinnedTargets().toMutableSet()
        if (isPinned) {
            current.add(componentSignature)
        } else {
            current.remove(componentSignature)
        }
        prefs.edit().putStringSet(KEY_PINNED_TARGETS, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "linkdeck_pinned_share_prefs"
        private const val KEY_PINNED_TARGETS = "pinned_targets"
    }
}
