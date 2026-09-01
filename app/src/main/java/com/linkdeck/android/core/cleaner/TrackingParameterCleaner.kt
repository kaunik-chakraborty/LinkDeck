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
    val KNOWN_TRACKING_PREFIXES = listOf(
        "utm_",
        "cq_",
        "gad_",
        "thg_",
        "sc_",
        "mat_",
        "ad_",
        "camp_",
        "pk_",
        "piwik_",
        "mtm_",
        "itm_",
        "trk_",
        "tracking_",
        "ig_",
        "fb_",
        "tw_",
        "ga_",
        "mc_"
    )

    val KNOWN_TRACKING_KEYS = setOf(
        // Google / DoubleClick / YouTube
        "gclid",
        "gclsrc",
        "dclid",
        "gbraid",
        "wbraid",
        "si",
        "feature",

        // Meta / Facebook / Instagram
        "fbclid",
        "igsi",
        "igsh",
        "igshid",
        "mibextid",
        "fb_action_ids",
        "fb_action_types",
        "fb_source",
        "fb_ref",

        // Microsoft / Bing
        "msclkid",

        // Twitter / X
        "twclid",
        "ref_src",
        "ref_url",

        // TikTok & LinkedIn
        "ttclid",
        "li_fat_id",

        // Pinterest & Yandex
        "epik",
        "yclid",
        "ysclid",

        // MailChimp, HubSpot, Email Marketing
        "mc_cid",
        "mc_eid",
        "_hsenc",
        "_hsmi",
        "vero_id",
        "vero_conv",
        "ml_subscriber",
        "ml_subscriber_hash",
        "wickedid",
        "klaviyo_id",
        "_kx",

        // Affiliate, E-commerce & Ad Networks
        "affil",
        "affiliate",
        "affiliate_id",
        "aff_id",
        "kwds",
        "adtype",
        "click_id",
        "clickid",
        "sub_id",
        "subid",
        "sub_id1",
        "sub_id2",
        "irgwc",
        "zanpid",
        "s_kwcid",
        "spm",
        "scm",
        "pvid",
        "wt_mc",
        "wt_zmc",
        "ndclid",
        "tag",
        "ascsubtag",
        "linkcode",
        "creative",
        "creativeasin"
    )

    /**
     * Evaluates whether a query parameter key represents an adtech, marketing, or tracking token.
     */
    fun isTrackingKey(rawKey: String, rawValue: String?): Boolean {
        val key = rawKey.trim().lowercase(Locale.ROOT)
        if (KNOWN_TRACKING_KEYS.contains(key)) return true
        for (prefix in KNOWN_TRACKING_PREFIXES) {
            if (key.startsWith(prefix)) return true
        }
        // Prune empty tracking artifacts
        if (rawValue.isNullOrBlank() && (key == "product_id" || key == "item_id" || key == "campaign")) {
            return true
        }
        return false
    }

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
                val value = if (eqIdx != -1) pair.substring(eqIdx + 1) else null

                if (isTrackingKey(key, value)) {
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
