package com.linkdeck.android.core.share

import java.util.regex.Pattern

/**
 * High-performance extractor that parses shared text payloads to extract HTTP/HTTPS URLs.
 * Handles plain URLs, multiline text, and messages with embedded URLs (e.g. from WhatsApp or Twitter).
 */
object ShareUrlExtractor {

    private val URL_PATTERN = Pattern.compile(
        "https?://[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})+(?::\\d+)?(?:/[^\\s]*)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts the first valid HTTP or HTTPS URL from the given shared text payload.
     * Returns null if no valid link is present.
     */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val trimmed = text.trim()
        // Fast path: entire text is a direct URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                return cleanTrailingPunctuation(trimmed)
            }
        }

        val matcher = URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return cleanTrailingPunctuation(matcher.group())
        }

        return null
    }

    /**
     * Removes trailing sentence punctuation (dots, commas, closing brackets) that might
     * inadvertently adhere to a URL when shared from chat applications.
     */
    private fun cleanTrailingPunctuation(url: String): String {
        var clean = url
        while (clean.isNotEmpty() && (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(")") || clean.endsWith(">") || clean.endsWith("]"))) {
            clean = clean.dropLast(1)
        }
        return clean
    }
}
