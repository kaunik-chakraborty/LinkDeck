package com.linkdeck.android.core.model

/**
 * Categorization of application targets discovered for a URL.
 *
 * Used by the chooser UI to group destinations into distinct, easily scannable sections:
 * - [RECOMMENDED]: A native application with a verified or declared host filter matching the target domain.
 * - [BROWSER]: A web browser or browser-like handler capable of rendering general web content.
 * - [OTHER]: Generic web intent handlers and fallback applications.
 */
enum class TargetCategory {
    RECOMMENDED,
    BROWSER,
    OTHER
}
