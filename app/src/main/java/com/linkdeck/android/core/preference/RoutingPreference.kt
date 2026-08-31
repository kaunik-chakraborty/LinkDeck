package com.linkdeck.android.core.preference

/**
 * Persisted routing preference associating a canonical destination domain
 * with a preferred application package.
 *
 * Persists only the destination host and package identifier. Full URLs,
 * query strings, fragments, and credentials are never stored.
 */
data class RoutingPreference(
    val domain: String,
    val packageName: String,
    val appLabel: String,
    val createdAt: Long = System.currentTimeMillis()
)
