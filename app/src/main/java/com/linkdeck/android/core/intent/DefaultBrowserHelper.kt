package com.linkdeck.android.core.intent

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Utility for verifying whether LinkDeck is configured as the active default browser on the device,
 * and creating the appropriate system role/settings intents to prompt the user.
 */
object DefaultBrowserHelper {

    /**
     * Returns true if LinkDeck is currently configured as the operating system's default browser.
     */
    fun isDefaultBrowser(context: Context): Boolean {
        // 1. Android 10+ (API 29+) RoleManager verification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            }
        }

        // 2. Fallback resolution via PackageManager
        return try {
            val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val resolveInfo = context.packageManager.resolveActivity(genericIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultPackage = resolveInfo?.activityInfo?.packageName
            defaultPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an Intent to prompt the user to select LinkDeck as their default browser.
     */
    fun createDefaultBrowserIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
}
