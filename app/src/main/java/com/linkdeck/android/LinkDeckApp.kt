package com.linkdeck.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.linkdeck.android.core.settings.AppSettingsStore

/**
 * Main application class initializing user-configured night mode.
 */
class LinkDeckApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val settingsStore = AppSettingsStore(this)

        // Apply saved theme mode (System Default / Light / Dark)
        AppCompatDelegate.setDefaultNightMode(settingsStore.appThemeMode)
    }
}
