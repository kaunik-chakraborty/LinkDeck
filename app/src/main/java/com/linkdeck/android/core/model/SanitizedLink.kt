package com.linkdeck.android.core.model

/**
 * Represents a sanitized and validated HTTP/HTTPS link.
 *
 * @property rawUrl The normalized string representation of the URL.
 * @property scheme The URL scheme (strictly "http" or "https").
 * @property host The parsed domain host (e.g. "example.com").
 * @property path The path component of the URL.
 * @property query Optional query parameters string.
 */
data class SanitizedLink(
    val rawUrl: String,
    val scheme: String,
    val host: String,
    val path: String = "",
    val query: String? = null
)

/**
 * Categorization of sanitization errors when parsing incoming intents or URLs.
 */
enum class SanitizationError {
    MISSING_URI,
    UNSUPPORTED_SCHEME,
    EXCEEDS_MAX_LENGTH,
    MALFORMED_URI,
    MISSING_HOST
}

/**
 * Result wrapper for link sanitization.
 */
sealed class SanitizationResult {
    data class Success(val link: SanitizedLink) : SanitizationResult()
    data class Error(val error: SanitizationError, val message: String) : SanitizationResult()
}
