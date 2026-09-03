package com.linkdeck.android.core.deamp

import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * On-device engine that unrolls Accelerated Mobile Pages (AMP) wrappers into
 * canonical publisher URLs with zero network calls and zero telemetry.
 */
object DeAmpEngine {

    private val GOOGLE_HOST_PATTERN = Regex("^(?:www\\.)?google\\.[a-z]{2,}(?:\\.[a-z]{2})?$", RegexOption.IGNORE_CASE)
    private val AMP_PROJECT_CDN_PATTERN = Regex("^[a-zA-Z0-9-]+\\.cdn\\.ampproject\\.org$", RegexOption.IGNORE_CASE)
    private val CLOUDFLARE_AMP_PATTERN = Regex("^[a-zA-Z0-9-]+\\.amp\\.cloudflare\\.com$", RegexOption.IGNORE_CASE)
    private val BING_AMP_PATTERN = Regex("^(?:www\\.)?bing-amp\\.com$", RegexOption.IGNORE_CASE)

    private val AMP_SPECIFIC_PARAMS = setOf(
        "amp", "amp_js_v", "amp_gsa", "amp_kit", "usqp", "outputtype"
    )

    /**
     * Inspects a sanitized link and resolves the canonical non-AMP destination.
     * Returns the original link if the link is not an AMP container or if parsing fails.
     */
    fun deAmp(link: SanitizedLink): DeAmpResult {
        val host = link.host.lowercase(Locale.ROOT)
        val uri = runCatching { URI(link.rawUrl) }.getOrNull()
            ?: return DeAmpResult(link, link, false)

        // 1. Google AMP Viewer (e.g. google.com/amp/s/example.com/...)
        if (GOOGLE_HOST_PATTERN.matches(host)) {
            val googleResult = unwrapGoogleAmp(link, uri)
            if (googleResult != null) return googleResult

            val googleRedirectResult = unwrapGoogleSearchRedirect(link, uri)
            if (googleRedirectResult != null) return googleRedirectResult
        }

        // 2. Google AMP Project CDN (e.g. *.cdn.ampproject.org/c/s/...)
        if (AMP_PROJECT_CDN_PATTERN.matches(host)) {
            val cdnResult = unwrapCdnCache(link, uri, AmpSource.AMPROJECT_CDN)
            if (cdnResult != null) return cdnResult
        }

        // 3. Cloudflare & Bing AMP Caches
        if (CLOUDFLARE_AMP_PATTERN.matches(host)) {
            val cfResult = unwrapCdnCache(link, uri, AmpSource.CLOUDFLARE_CACHE)
            if (cfResult != null) return cfResult
        }
        if (BING_AMP_PATTERN.matches(host)) {
            val bingResult = unwrapCdnCache(link, uri, AmpSource.BING_CACHE)
            if (bingResult != null) return bingResult
        }

        // 4. Publisher Subdomain (e.g. amp.reddit.com, amp.theguardian.com)
        if (host.startsWith("amp.") && host.count { it == '.' } >= 2) {
            val subdomainResult = unwrapPublisherSubdomain(link, uri)
            if (subdomainResult != null) return subdomainResult
        }

        // 5. Publisher Terminal Path Suffix (e.g. /article/amp/ or /article/amp)
        val pathResult = unwrapPathSuffix(link, uri)
        if (pathResult != null) return pathResult

        // 6. Standalone AMP Query Parameters (e.g. ?amp=1, ?outputType=amp)
        val queryResult = unwrapAmpQueryParams(link, uri)
        if (queryResult != null) return queryResult

        return DeAmpResult(originalLink = link, deAmpedLink = link, wasDeAmped = false)
    }

    private fun unwrapGoogleAmp(link: SanitizedLink, uri: URI): DeAmpResult? {
        val path = uri.rawPath ?: return null
        if (!path.startsWith("/amp/", ignoreCase = true)) return null

        val remainder = path.substring(5)
        val isHttps = remainder.startsWith("s/", ignoreCase = true)
        val rawTarget = if (isHttps) remainder.substring(2) else remainder
        if (rawTarget.isBlank()) return null

        val targetUrl = assembleExtractedUrl(rawTarget, isHttps, uri) ?: return null
        return buildResult(link, targetUrl, AmpSource.GOOGLE_VIEWER)
    }

    private fun unwrapGoogleSearchRedirect(link: SanitizedLink, uri: URI): DeAmpResult? {
        val path = uri.rawPath ?: return null
        if (path != "/url") return null

        val query = uri.rawQuery ?: return null
        val target = extractQueryParam(query, "q") ?: extractQueryParam(query, "url") ?: return null
        val decoded = runCatching { URLDecoder.decode(target, StandardCharsets.UTF_8.name()) }.getOrDefault(target)
        val sanitized = IntentSanitizer.sanitizeUrl(decoded)
        if (sanitized !is SanitizationResult.Success) return null

        // Recurse once in case the redirected target itself is an AMP link
        val nested = deAmp(sanitized.link)
        val finalLink = if (nested.wasDeAmped) nested.deAmpedLink else sanitized.link
        return DeAmpResult(
            originalLink = link,
            deAmpedLink = finalLink,
            wasDeAmped = true,
            source = AmpSource.GOOGLE_SEARCH_REDIRECT
        )
    }

    private fun unwrapCdnCache(link: SanitizedLink, uri: URI, source: AmpSource): DeAmpResult? {
        val path = uri.rawPath ?: return null
        val prefixMatch = Regex("^/(?:c|v)/(s/)?", RegexOption.IGNORE_CASE).find(path) ?: return null
        val isHttps = prefixMatch.groupValues[1].isNotEmpty()
        val rawTarget = path.substring(prefixMatch.range.last + 1)
        if (rawTarget.isBlank()) return null

        val targetUrl = assembleExtractedUrl(rawTarget, isHttps, uri) ?: return null
        return buildResult(link, targetUrl, source)
    }

    private fun unwrapPublisherSubdomain(link: SanitizedLink, uri: URI): DeAmpResult? {
        val host = link.host
        val baseHost = host.removePrefix("amp.")
        if (baseHost.isBlank() || !baseHost.contains(".")) return null

        val cleanedQuery = filterAmpQuery(uri.rawQuery)
        val queryPart = if (cleanedQuery.isNullOrEmpty()) "" else "?$cleanedQuery"
        val fragmentPart = if (uri.rawFragment.isNullOrEmpty()) "" else "#${uri.rawFragment}"
        val rawPath = uri.rawPath ?: "/"
        val candidate = "${link.scheme}://$baseHost$rawPath$queryPart$fragmentPart"
        return buildResult(link, candidate, AmpSource.PUBLISHER_SUBDOMAIN)
    }

    private fun unwrapPathSuffix(link: SanitizedLink, uri: URI): DeAmpResult? {
        val path = uri.rawPath ?: return null
        val trimmed = path.trimEnd('/')
        if (!trimmed.endsWith("/amp", ignoreCase = true)) return null

        val newPath = trimmed.substring(0, trimmed.length - 4).ifEmpty { "/" }
        val finalPath = if (path.endsWith('/') && !newPath.endsWith('/')) "$newPath/" else newPath

        val cleanedQuery = filterAmpQuery(uri.rawQuery)
        val queryPart = if (cleanedQuery.isNullOrEmpty()) "" else "?$cleanedQuery"
        val fragmentPart = if (uri.rawFragment.isNullOrEmpty()) "" else "#${uri.rawFragment}"
        val candidate = "${link.scheme}://${link.host}$finalPath$queryPart$fragmentPart"
        return buildResult(link, candidate, AmpSource.PATH_SUFFIX)
    }

    private fun unwrapAmpQueryParams(link: SanitizedLink, uri: URI): DeAmpResult? {
        val rawQuery = uri.rawQuery ?: return null
        val cleaned = filterAmpQuery(rawQuery) ?: return null
        if (cleaned == rawQuery) return null

        val queryPart = if (cleaned.isEmpty()) "" else "?$cleaned"
        val fragmentPart = if (uri.rawFragment.isNullOrEmpty()) "" else "#${uri.rawFragment}"
        val rawPath = uri.rawPath ?: "/"
        val candidate = "${link.scheme}://${link.host}$rawPath$queryPart$fragmentPart"
        return buildResult(link, candidate, AmpSource.QUERY_PARAM)
    }

    private fun assembleExtractedUrl(rawTarget: String, isHttps: Boolean, sourceUri: URI): String? {
        val decodedTarget = if (rawTarget.contains("%3A%2F%2F", ignoreCase = true) || rawTarget.contains("%2F", ignoreCase = true)) {
            runCatching { URLDecoder.decode(rawTarget, StandardCharsets.UTF_8.name()) }.getOrDefault(rawTarget)
        } else {
            rawTarget
        }

        val urlWithScheme = when {
            decodedTarget.startsWith("https://", ignoreCase = true) ||
            decodedTarget.startsWith("http://", ignoreCase = true) -> decodedTarget
            isHttps -> "https://$decodedTarget"
            else -> "http://$decodedTarget"
        }

        val parsed = runCatching { URI(urlWithScheme) }.getOrNull() ?: return null
        val baseWithoutQuery = "${parsed.scheme}://${parsed.rawAuthority}${parsed.rawPath ?: "/"}"

        // Combine non-AMP query parameters from both target and source wrapper
        val targetQuery = filterAmpQuery(parsed.rawQuery)
        val sourceQuery = filterAmpQuery(sourceUri.rawQuery)

        val combinedQuery = when {
            targetQuery.isNullOrEmpty() && sourceQuery.isNullOrEmpty() -> ""
            targetQuery.isNullOrEmpty() -> "?$sourceQuery"
            sourceQuery.isNullOrEmpty() -> "?$targetQuery"
            else -> "?$targetQuery&$sourceQuery"
        }

        val fragment = when {
            !parsed.rawFragment.isNullOrEmpty() -> "#${parsed.rawFragment}"
            !sourceUri.rawFragment.isNullOrEmpty() -> "#${sourceUri.rawFragment}"
            else -> ""
        }

        return "$baseWithoutQuery$combinedQuery$fragment"
    }

    private fun filterAmpQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val pairs = rawQuery.split('&')
        val preserved = mutableListOf<String>()
        var foundAmp = false

        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val eqIdx = pair.indexOf('=')
            val key = if (eqIdx != -1) pair.substring(0, eqIdx) else pair
            if (key.lowercase(Locale.ROOT) in AMP_SPECIFIC_PARAMS) {
                foundAmp = true
            } else {
                preserved.add(pair)
            }
        }

        return if (foundAmp || preserved.size != pairs.size) {
            preserved.joinToString("&")
        } else {
            rawQuery
        }
    }

    private fun extractQueryParam(query: String, paramName: String): String? {
        for (pair in query.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx != -1) {
                val key = pair.substring(0, eqIdx)
                if (key.equals(paramName, ignoreCase = true)) {
                    return pair.substring(eqIdx + 1)
                }
            }
        }
        return null
    }

    private fun buildResult(original: SanitizedLink, candidateUrl: String, source: AmpSource): DeAmpResult? {
        if (candidateUrl == original.rawUrl) return null

        val sanitized = IntentSanitizer.sanitizeUrl(candidateUrl)
        if (sanitized !is SanitizationResult.Success) return null

        val deAmped = sanitized.link
        if (deAmped.rawUrl == original.rawUrl) return null

        return DeAmpResult(
            originalLink = original,
            deAmpedLink = deAmped,
            wasDeAmped = true,
            source = source
        )
    }
}
