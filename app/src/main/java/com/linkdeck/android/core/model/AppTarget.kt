package com.linkdeck.android.core.model

/**
 * Represents a launchable application destination capable of handling a sanitized link.
 *
 * This model is intentionally decoupled from Android UI objects (such as Drawables)
 * to keep the core domain lightweight, testable, and memory-safe.
 *
 * @property packageName Android package name of the target application.
 * @property activityName Fully qualified class name of the target activity.
 * @property appLabel Human-readable display name of the application.
 * @property matchedHost The specific host authority matched from intent filters, if any.
 * @property isBrowser True if this target represents a web browser.
 * @property isNativeMatch True if this target explicitly matches the domain host of the link.
 * @property category The visual section category for this target in the chooser UI.
 */
data class AppTarget(
    val packageName: String,
    val activityName: String,
    val appLabel: String,
    val matchedHost: String? = null,
    val isBrowser: Boolean = false,
    val isNativeMatch: Boolean = false,
    val category: TargetCategory = TargetCategory.OTHER
)
