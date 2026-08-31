package com.linkdeck.android.core.model

import com.linkdeck.android.core.inspector.UrlRedactor

/**
 * Encapsulates the complete URL pipeline lifecycle with strict fidelity distinction:
 * 1. [originalSanitized]: The original safe HTTP/HTTPS URL received by LinkDeck (untransformed).
 * 2. [resolvedDestination]: The destination URL after on-device HTTP redirect resolution (if followed).
 * 3. [cleanedDestination]: The destination URL after tracking parameters have been cleaned.
 * 4. [effectiveLaunchLink]: The final validated, safe link to be dispatched to open/share handlers.
 * 5. [displayRedactedEffectiveUrl]: Strictly presentation-only redacted string for UI rendering (NEVER used to launch).
 * 6. [displayRedactedOriginalUrl]: Strictly presentation-only redacted string for the original URL (NEVER used to launch).
 */
data class LinkExecutionContext(
    val originalSanitized: SanitizedLink,
    val resolvedDestination: SanitizedLink,
    val cleanedDestination: SanitizedLink,
    val wasRedirected: Boolean = false,
    val wasCleaned: Boolean = false,
    val removedTrackingParams: List<String> = emptyList()
) {
    /**
     * The effective, safe link to launch under standard routing flow.
     */
    val effectiveLaunchLink: SanitizedLink
        get() = cleanedDestination

    /**
     * True if the original URL differs from the effective destination (due to redirect or tracking cleaning).
     */
    val hasTransformations: Boolean
        get() = wasRedirected || wasCleaned || (originalSanitized.rawUrl != effectiveLaunchLink.rawUrl)

    /**
     * Presentation-only redacted representation for the effective destination.
     * MUST NEVER BE LAUNCHED AS AN INTENT URI.
     */
    val displayRedactedEffectiveUrl: String
        get() = UrlRedactor.redact(effectiveLaunchLink)

    /**
     * Presentation-only redacted representation for the original URL.
     * MUST NEVER BE LAUNCHED AS AN INTENT URI.
     */
    val displayRedactedOriginalUrl: String
        get() = UrlRedactor.redact(originalSanitized)
}
