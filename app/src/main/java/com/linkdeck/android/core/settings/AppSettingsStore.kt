package com.linkdeck.android.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.linkdeck.android.core.typography.AppFont

/**
 * Thread-safe persistent store for LinkDeck application, routing, typography, and theming settings.
 * Backed by private SharedPreferences excluded from cloud backups for user privacy.
 */
class AppSettingsStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    /**
     * App theme mode: System Default, Light Theme, or Dark Theme.
     */
    var appThemeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
        }

    /**
     * Selected application typography font key (e.g. "satoshi", "outfit", "system").
     */
    var appFontKey: String
        get() = prefs.getString(KEY_APP_FONT, AppFont.SATOSHI.key) ?: AppFont.SATOSHI.key
        set(value) {
            prefs.edit().putString(KEY_APP_FONT, value).apply()
        }

    /**
     * Whether Android 12+ (API 31+) Material You Dynamic Colors (Monet wallpaper tinting) is active.
     */
    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    /**
     * When true, links matching a saved preference or custom routing rule are
     * launched automatically in the target app without presenting the chooser.
     */
    var isAutomaticRoutingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOMATIC_ROUTING, DEFAULT_AUTOMATIC_ROUTING)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOMATIC_ROUTING, value).apply()
        }

    /**
     * When true, the chooser bottom sheet presents the "Always" action allowing
     * users to remember per-domain routing preferences.
     */
    var isRememberChoicesEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_CHOICES, DEFAULT_REMEMBER_CHOICES)
        set(value) {
            prefs.edit().putBoolean(KEY_REMEMBER_CHOICES, value).apply()
        }

    /**
     * When true, common marketing and analytics tracking parameters (utm_*, fbclid, etc.)
     * are stripped from URLs before routing.
     */
    var isTrackingCleanerEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_CLEANER, DEFAULT_TRACKING_CLEANER)
        set(value) {
            prefs.edit().putBoolean(KEY_TRACKING_CLEANER, value).apply()
        }

    /**
     * When true, shortened URLs and redirects are probed safely on-device prior
     * to application resolution.
     */
    var isRedirectCheckingEnabled: Boolean
        get() = prefs.getBoolean(KEY_REDIRECT_CHECKING, DEFAULT_REDIRECT_CHECKING)
        set(value) {
            prefs.edit().putBoolean(KEY_REDIRECT_CHECKING, value).apply()
        }

    /**
     * When true, the first-time user onboarding walkthrough has been viewed or completed.
     */
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
        }

    /**
     * Resets all configurable preferences to default values.
     */
    fun resetSettings(): Boolean {
        return prefs.edit().clear().commit()
    }

    companion object {
        const val PREFS_NAME = "linkdeck_settings"

        const val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        const val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
        const val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES

        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_APP_FONT = "app_font_key"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
        private const val KEY_AUTOMATIC_ROUTING = "automatic_routing_enabled"
        private const val KEY_REMEMBER_CHOICES = "remember_choices_enabled"
        private const val KEY_TRACKING_CLEANER = "tracking_cleaner_enabled"
        private const val KEY_REDIRECT_CHECKING = "redirect_checking_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        const val DEFAULT_AUTOMATIC_ROUTING = true
        const val DEFAULT_REMEMBER_CHOICES = true
        const val DEFAULT_TRACKING_CLEANER = true
        const val DEFAULT_REDIRECT_CHECKING = true
    }
}
