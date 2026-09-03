package com.linkdeck.android.core.clipboard

import com.linkdeck.android.core.model.SanitizationError
import com.linkdeck.android.core.model.SanitizedLink

/**
 * Encapsulates the deterministic outcome of evaluating and sanitizing clipboard content.
 */
sealed class ClipboardCleanResult {

    /** Clipboard has no primary clip data or contains exclusively blank whitespace. */
    object Empty : ClipboardCleanResult()

    /** Clipboard contains text, but no valid HTTP or HTTPS link pattern was found. */
    data class NoLinkFound(val rawText: String) : ClipboardCleanResult()

    /** Link was detected but failed safety sanitization (unsupported scheme, excessive length, or malformed). */
    data class InvalidLink(val error: SanitizationError, val message: String) : ClipboardCleanResult()

    /** The link is already canonical, tracking-free, and not wrapped in an AMP container. */
    data class AlreadyClean(val link: SanitizedLink) : ClipboardCleanResult()

    /** The link was successfully unwrapped from AMP, stripped of tracking parameters, or extracted from enclosing text. */
    data class Cleaned(
        val originalText: String,
        val cleanedLink: SanitizedLink,
        val wasDeAmped: Boolean,
        val removedParams: List<String>
    ) : ClipboardCleanResult() {
        val hasRemovedParams: Boolean get() = removedParams.isNotEmpty()
    }
}
