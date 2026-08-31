package com.linkdeck.android.core.model

/**
 * Represents a launchable application destination capable of receiving a shared link via ACTION_SEND.
 *
 * Distinct from [AppTarget] (which represents ACTION_VIEW destinations) to ensure compile-time
 * separation and prevent accidental misuse between viewing and sharing operations.
 *
 * @property packageName Android package name of the target application.
 * @property activityName Fully qualified class name of the target activity.
 * @property appLabel Human-readable display name of the application.
 */
data class ShareTarget(
    val packageName: String,
    val activityName: String,
    val appLabel: String
)
