package com.linkdeck.android.core.cleaner

import android.content.Context
import android.content.SharedPreferences
import com.linkdeck.android.core.settings.AppSettingsStore

/**
 * Compatibility delegate for cleaner settings, backed by [AppSettingsStore].
 */
class CleanerSettingsStore(private val appSettingsStore: AppSettingsStore) {

    constructor(prefs: SharedPreferences) : this(AppSettingsStore(prefs))
    constructor(context: Context) : this(AppSettingsStore(context))

    var isTrackingCleanerEnabled: Boolean
        get() = appSettingsStore.isTrackingCleanerEnabled
        set(value) {
            appSettingsStore.isTrackingCleanerEnabled = value
        }

    companion object {
        const val PREFS_NAME = AppSettingsStore.PREFS_NAME
    }
}
