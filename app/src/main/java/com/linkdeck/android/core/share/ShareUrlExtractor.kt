package com.linkdeck.android.core.share

import java.util.regex.Pattern

/**
 * High-performance extractor that parses shared text payloads to extract HTTP/HTTPS URLs.
 * Handles plain URLs, multiline text, and messages with embedded URLs (e.g. from WhatsApp or Twitter).
 * Rejects plain conversational text without valid domain authorities or schemes.
 */
object ShareUrlExtractor {

    private val URL_PATTERN = Pattern.compile(
        "https?://[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})+(?::\\d+)?(?:/[^\\s]*)?",
        Pattern.CASE_INSENSITIVE
    )

    private val SCHEMELESS_WWW_PATTERN = Pattern.compile(
        "www\\.[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})+(?::\\d+)?(?:/[^\\s]*)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts the first valid HTTP or HTTPS URL from the given shared text payload.
     * Returns null if no valid link is present.
     */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val trimmed = text.trim()

        // 1. Direct URL with explicit scheme
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                val cleaned = cleanTrailingPunctuation(trimmed)
                val hostPart = cleaned.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
                if (hostPart.contains(".") || hostPart.equals("localhost", ignoreCase = true)) {
                    return cleaned
                }
            }
        }

        // 2. Direct schemeless URL starting with www.
        if (trimmed.startsWith("www.", ignoreCase = true) && !trimmed.contains(" ") && !trimmed.contains("\n")) {
            val cleaned = cleanTrailingPunctuation(trimmed)
            return "https://$cleaned"
        }

        // 3. Regex scan for embedded http/https links in conversational text
        val matcher = URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return cleanTrailingPunctuation(matcher.group())
        }

        // 4. Regex scan for embedded www. links in conversational text
        val wwwMatcher = SCHEMELESS_WWW_PATTERN.matcher(trimmed)
        if (wwwMatcher.find()) {
            return "https://${cleanTrailingPunctuation(wwwMatcher.group())}"
        }

        // 5. Standalone domain check without scheme (e.g. "google.com" or "youtube.com/watch")
        if (!trimmed.contains(" ") && !trimmed.contains("\n") && !trimmed.contains("@")) {
            val domainCandidate = trimmed.substringBefore('/').substringBefore('?').substringBefore(':')
            val parts = domainCandidate.split('.')
            if (parts.size >= 2 && parts.last().length >= 2 && parts.all { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }) {
                return "https://${cleanTrailingPunctuation(trimmed)}"
            }
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
