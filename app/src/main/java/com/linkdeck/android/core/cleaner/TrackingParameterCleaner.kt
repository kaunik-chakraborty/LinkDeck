package com.linkdeck.android.core.cleaner

import com.linkdeck.android.core.model.SanitizedLink
import java.net.URI
import java.util.Locale

/**
 * Result of executing tracking parameter cleaning on a [SanitizedLink].
 * Kept strictly in memory during routing and never persisted.
 */
data class CleanResult(
    val originalLink: SanitizedLink,
    val cleanedLink: SanitizedLink,
    val removedParams: List<String>
) {
    val hasRemovedParams: Boolean get() = removedParams.isNotEmpty()
}

/**
 * Pure, deterministic on-device cleaner that conservatively strips known marketing
 * and analytics tracking parameters while strictly preserving functional application query parameters,
 * parameter ordering, original percent-encoding, and fragments.
 */
object TrackingParameterCleaner {

    /**
     * Set of well-known marketing/analytics query parameter names.
     * Evaluated case-insensitively. Functional parameters like 'id', 'v', 'q', 'ref', 'page', 'token'
     * are strictly excluded from this set to avoid breaking application routing or site features.
     */
    val KNOWN_TRACKING_KEYS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "twclid",
        "mc_eid",
        "igshid",
        "_hsenc",
        "_hsmi"
    )

    /**
     * Inspects and cleans known tracking parameters from [link].
     * If no tracking parameters exist or if parsing fails, returns the unmodified link.
     */
    fun clean(link: SanitizedLink): CleanResult {
        return try {
            val rawUrl = link.rawUrl
            val uri = URI(rawUrl)
            val rawQuery = uri.rawQuery ?: return CleanResult(link, link, emptyList())

            if (rawQuery.isBlank()) {
                return CleanResult(link, link, emptyList())
            }

            val queryPairs = rawQuery.split("&")
            val preservedPairs = mutableListOf<String>()
            val removedParamNames = mutableListOf<String>()

            for (pair in queryPairs) {
                if (pair.isEmpty()) continue
                val eqIdx = pair.indexOf('=')
                val key = if (eqIdx != -1) pair.substring(0, eqIdx) else pair
                val normalizedKey = key.trim().lowercase(Locale.ROOT)

                if (KNOWN_TRACKING_KEYS.contains(normalizedKey)) {
                    removedParamNames.add(key)
                } else {
                    preservedPairs.add(pair)
                }
            }

            if (removedParamNames.isEmpty()) {
                return CleanResult(link, link, emptyList())
            }

            val queryStart = rawUrl.indexOf('?')
            val fragmentStart = rawUrl.indexOf('#')

            val baseUrl = if (queryStart != -1) {
                rawUrl.substring(0, queryStart)
            } else if (fragmentStart != -1) {
                rawUrl.substring(0, fragmentStart)
            } else {
                rawUrl
            }

            val fragment = if (fragmentStart != -1 && (queryStart == -1 || fragmentStart > queryStart)) {
                rawUrl.substring(fragmentStart)
            } else {
                ""
            }

            val newRawQuery = if (preservedPairs.isNotEmpty()) {
                preservedPairs.joinToString("&")
            } else {
                null
            }

            val cleanedUrlString = if (newRawQuery != null) {
                "$baseUrl?$newRawQuery$fragment"
            } else {
                "$baseUrl$fragment"
            }

            val cleanedLink = SanitizedLink(
                rawUrl = cleanedUrlString,
                scheme = link.scheme,
                host = link.host,
                path = link.path,
                query = newRawQuery
            )

            CleanResult(
                originalLink = link,
                cleanedLink = cleanedLink,
                removedParams = removedParamNames
            )
        } catch (e: Exception) {
            // Fail-safe: if any unexpected URI manipulation error occurs, return original untouched
            CleanResult(link, link, emptyList())
        }
    }
}
