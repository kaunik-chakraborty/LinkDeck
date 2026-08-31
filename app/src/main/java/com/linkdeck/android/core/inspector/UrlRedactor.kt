package com.linkdeck.android.core.inspector

import com.linkdeck.android.core.model.SanitizedLink
import java.net.URI
import java.util.Locale

/**
 * Pure, deterministic presentation-level utility for redacting sensitive query parameters,
 * authentication tokens, credentials, and userinfo before displaying URLs in the Link Inspector UI.
 *
 * Prevents accidental on-screen shoulder-surfing exposure of login tokens, OAuth codes,
 * and session secrets while keeping the raw sanitized link intact for routing.
 */
object UrlRedactor {

    /**
     * Set of sensitive query parameter keys whose values are replaced with "[redacted]".
     */
    val SENSITIVE_QUERY_KEYS = setOf(
        "token",
        "code",
        "password",
        "pass",
        "session",
        "auth",
        "key",
        "secret",
        "access_token",
        "refresh_token",
        "id_token",
        "api_key",
        "apikey",
        "jwt",
        "state",
        "sig",
        "signature",
        "client_secret",
        "nonce"
    )

    private const val MAX_DISPLAY_LENGTH = 120

    /**
     * Returns a presentation-safe string representation of [link] with sensitive parameter values
     * redacted and userinfo stripped.
     */
    fun redact(link: SanitizedLink): String {
        return redactUrlString(link.rawUrl)
    }

    /**
     * Returns a presentation-safe string representation of a raw URL string.
     */
    fun redactUrlString(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            val rawQuery = uri.rawQuery
            val rawFragment = uri.rawFragment

            val host = uri.host ?: ""
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            val path = uri.rawPath ?: ""

            val safeQuery = if (!rawQuery.isNullOrBlank()) {
                "?" + redactKeyValuePairs(rawQuery)
            } else {
                ""
            }

            val safeFragment = if (!rawFragment.isNullOrBlank()) {
                "#" + if (rawFragment.contains("=") || rawFragment.contains("&")) {
                    redactKeyValuePairs(rawFragment)
                } else {
                    rawFragment
                }
            } else {
                ""
            }

            "$scheme://$host$port$path$safeQuery$safeFragment"
        } catch (e: Exception) {
            // Fail-safe fallback: return domain or basic scheme if parsing fails
            rawUrl.take(MAX_DISPLAY_LENGTH)
        }
    }

    private fun redactKeyValuePairs(rawPairsString: String): String {
        val queryPairs = rawPairsString.split("&")
        val redactedPairs = mutableListOf<String>()

        for (pair in queryPairs) {
            if (pair.isEmpty()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx == -1) {
                val key = pair.trim().lowercase(Locale.ROOT)
                if (SENSITIVE_QUERY_KEYS.contains(key)) {
                    redactedPairs.add("$pair=[redacted]")
                } else {
                    redactedPairs.add(pair)
                }
            } else {
                val key = pair.substring(0, eqIdx)
                val normalizedKey = key.trim().lowercase(Locale.ROOT)
                if (SENSITIVE_QUERY_KEYS.contains(normalizedKey)) {
                    redactedPairs.add("$key=[redacted]")
                } else {
                    redactedPairs.add(pair)
                }
            }
        }

        return redactedPairs.joinToString("&")
    }

    /**
     * Truncates a string for compact single-line UI presentation with an ellipsis (…).
     */
    fun truncateForDisplay(text: String, maxLength: Int = MAX_DISPLAY_LENGTH): String {
        if (text.length <= maxLength) return text
        if (maxLength <= 1) return "…"
        val remaining = maxLength - 1
        val prefixLen = remaining / 2
        val suffixLen = remaining - prefixLen
        return text.take(prefixLen) + "…" + text.takeLast(suffixLen)
    }
}
