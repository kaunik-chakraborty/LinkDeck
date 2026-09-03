package com.linkdeck.android.core.intent

import android.content.Intent
import com.linkdeck.android.core.model.SanitizationError
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Validates and sanitizes untrusted incoming Android intents and raw URL strings.
 *
 * Security guarantees:
 * 1. Strictly enforces HTTP and HTTPS schemes; rejects javascript:, file:, content:, intent:, etc.
 * 2. Enforces a maximum length constraint ([MAX_URL_LENGTH]) to prevent pathological input DoS.
 * 3. Strips all incoming intent extras to prevent intent injection attacks.
 * 4. Normalizes scheme and host to lower-case for deterministic downstream matching.
 * 5. Automatically prefixes missing schemes with https:// for user-friendly text entry.
 */
object IntentSanitizer {

    const val MAX_URL_LENGTH = 4096

    private val SUPPORTED_SCHEMES = setOf("http", "https")

    // Regex to locate HTTP/HTTPS URLs in shared text
    private val URL_EXTRACT_REGEX = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    // Trailing punctuation characters commonly attached to URLs in chat/social shares
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ')', ']', '}', '>', ';', '!', '?')

    /**
     * Sanitizes an incoming Android [Intent] from [Intent.ACTION_VIEW] or [Intent.ACTION_SEND].
     */
    fun sanitizeIntent(intent: Intent?): SanitizationResult {
        if (intent == null) {
            return SanitizationResult.Error(SanitizationError.MISSING_URI, "Intent is null")
        }

        val rawUrl: String? = when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString()
            }
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                extractUrlFromText(text) ?: text
            }
            else -> {
                intent.dataString
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString()
            }
        }

        return sanitizeUrl(rawUrl)
    }

    /**
     * Sanitizes a raw URL string against length, scheme, and structure requirements.
     * Automatically prepends https:// if no scheme is specified (e.g. "youtube.com").
     */
    fun sanitizeUrl(rawInput: String?): SanitizationResult {
        if (rawInput.isNullOrBlank()) {
            return SanitizationResult.Error(SanitizationError.MISSING_URI, "URL is empty or null")
        }

        var trimmed = rawInput.trim()

        if (trimmed.isEmpty()) {
            return SanitizationResult.Error(SanitizationError.MISSING_URI, "URL is empty")
        }

        if (trimmed.length > MAX_URL_LENGTH) {
            return SanitizationResult.Error(
                SanitizationError.EXCEEDS_MAX_LENGTH,
                "URL length exceeds maximum limit of $MAX_URL_LENGTH characters"
            )
        }

        // Scheme validation
        val rawScheme = extractScheme(trimmed)
        if (rawScheme != null) {
            val schemeLower = rawScheme.lowercase(Locale.ROOT)
            if (!SUPPORTED_SCHEMES.contains(schemeLower)) {
                return SanitizationResult.Error(
                    SanitizationError.UNSUPPORTED_SCHEME,
                    "Unsupported scheme: $rawScheme. Only HTTP and HTTPS are permitted."
                )
            }
        } else {
            // Auto-prepend https:// if scheme is absent (e.g. "youtube.com", "example.com/path")
            if (trimmed.startsWith("//")) {
                trimmed = "https:$trimmed"
            } else {
                trimmed = "https://$trimmed"
            }
        }

        val scheme = extractScheme(trimmed)?.lowercase(Locale.ROOT) ?: "https"
        val parsedComponents = parseUriSafely(trimmed, scheme)
            ?: return SanitizationResult.Error(
                SanitizationError.MALFORMED_URI,
                "Invalid URL structure"
            )

        val host = parsedComponents.host?.trim()?.removeSuffix(".")?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank() || (!host.contains(".") && host != "localhost")) {
            return SanitizationResult.Error(
                SanitizationError.MISSING_HOST,
                "URL does not contain a valid host authority"
            )
        }

        return SanitizationResult.Success(
            SanitizedLink(
                rawUrl = trimmed,
                scheme = scheme,
                host = host,
                path = parsedComponents.path,
                query = parsedComponents.query
            )
        )
    }

    /**
     * Extracts and cleans the first valid HTTP/HTTPS URL from a multi-line or decorated shared text.
     */
    fun extractUrlFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_EXTRACT_REGEX.find(text)?.value ?: return null
        return match.trimEnd(*TRAILING_PUNCTUATION)
    }

    private fun extractScheme(url: String): String? {
        val colonIndex = url.indexOf(':')
        if (colonIndex <= 0) return null
        val slashIndex = url.indexOf('/')
        // If a slash appears before the colon, it's a path like "domain.com/path:123", not a scheme
        if (slashIndex in 0 until colonIndex) return null

        val candidate = url.substring(0, colonIndex).trim()
        // Scheme must start with a letter and contain only alphanumeric, plus, dot, or hyphen (RFC 3986)
        if (!candidate.first().isLetter()) return null
        if (!candidate.all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' }) return null
        return candidate
    }

    /**
     * Parses a URI string safely after the scheme has been validated.
     */
    private fun parseUriSafely(url: String, validatedScheme: String): ParsedUriComponents? {
        // Attempt standard RFC URI parsing first
        try {
            val uri = URI(url)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                return ParsedUriComponents(
                    host = host,
                    path = uri.path ?: "",
                    query = uri.query
                )
            }
        } catch (e: URISyntaxException) {
            // Fallback for URLs with unencoded query/fragment characters
        }

        // Lightweight fallback parser for robust host, path, and query extraction
        return try {
            val prefix = "$validatedScheme://"
            if (!url.startsWith(prefix, ignoreCase = true)) return null

            val afterScheme = url.substring(prefix.length)
            val pathStart = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
            val hostPort = if (pathStart >= 0) afterScheme.substring(0, pathStart) else afterScheme

            val host = if (hostPort.contains("@")) {
                hostPort.substringAfterLast("@")
            } else {
                hostPort
            }.substringBefore(":")

            if (host.isBlank()) return null

            val remaining = if (pathStart >= 0) afterScheme.substring(pathStart) else ""
            val query = if (remaining.contains("?")) remaining.substringAfter("?").substringBefore("#") else null
            val path = remaining.substringBefore("?").substringBefore("#")

            ParsedUriComponents(
                host = host,
                path = path,
                query = query
            )
        } catch (e: Exception) {
            null
        }
    }

    private data class ParsedUriComponents(
        val host: String?,
        val path: String,
        val query: String?
    )
}
