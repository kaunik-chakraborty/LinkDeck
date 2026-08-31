package com.linkdeck.android.core.intent

import android.content.IntentFilter
import android.content.pm.ResolveInfo
import com.linkdeck.android.core.model.TargetCategory
import java.util.Locale

/**
 * Classifies resolved application targets into distinct categories based on package
 * and intent filter metadata, without relying on fragile hardcoded package lists.
 */
object BrowserClassifier {

    /**
     * Evaluates a candidate handler against the destination host and general browser metadata.
     *
     * @param resolveInfo Android package manager resolution info.
     * @param targetHost The domain host of the link being resolved (e.g. "youtube.com").
     * @param isGenericBrowserCandidate True if this app resolves generic web links (like https://example.com).
     * @param authoritiesExtractor Optional provider for host authorities (defaults to reading from IntentFilter).
     * @return A classification triple containing (isNativeMatch, isBrowser, TargetCategory).
     */
    fun classify(
        resolveInfo: ResolveInfo,
        targetHost: String,
        isGenericBrowserCandidate: Boolean,
        authoritiesExtractor: (ResolveInfo) -> List<String> = { extractAuthorities(it.filter) }
    ): ClassificationResult {
        val declaredHosts = authoritiesExtractor(resolveInfo)
        val isNativeMatch = isHostMatch(declaredHosts, targetHost)
        val isBrowser = !isNativeMatch && (isGenericBrowserCandidate || declaredHosts.isEmpty() || declaredHosts.all { it == "*" || it == "*.*" || it.isBlank() })

        val category = when {
            isNativeMatch -> TargetCategory.RECOMMENDED
            isBrowser -> TargetCategory.BROWSER
            else -> TargetCategory.OTHER
        }

        return ClassificationResult(
            isNativeMatch = isNativeMatch,
            isBrowser = isBrowser,
            category = category
        )
    }

    /**
     * Extracts declared data authority hosts from an Android [IntentFilter].
     */
    fun extractAuthorities(filter: IntentFilter?): List<String> {
        if (filter == null) return emptyList()
        val authorityCount = try {
            filter.countDataAuthorities()
        } catch (e: Exception) {
            0
        }
        if (authorityCount == 0) return emptyList()

        val declaredHosts = mutableListOf<String>()
        for (i in 0 until authorityCount) {
            val auth = try { filter.getDataAuthority(i) } catch (e: Exception) { null } ?: continue
            val host = auth.host ?: continue
            declaredHosts.add(host)
        }
        return declaredHosts
    }

    /**
     * Checks if the [IntentFilter] contains explicit host authorities matching [targetHost].
     */
    fun isExplicitDomainMatch(filter: IntentFilter?, targetHost: String): Boolean {
        val declaredHosts = extractAuthorities(filter)
        return isHostMatch(declaredHosts, targetHost)
    }

    /**
     * Pure testable logic determining whether any of the [declaredHosts] matches [targetHost].
     *
     * Security & Correctness rules:
     * 1. Generic catch-all wildcards ("*", "*.*", "") are NEVER treated as specific domain matches.
     * 2. Wildcard subdomains (e.g. "*.example.com") match "example.com" and "sub.example.com",
     *    but strictly reject suffix spoofing (e.g. "fake-example.com").
     * 3. Exact hosts match case-insensitively and normalize trailing dots.
     */
    fun isHostMatch(declaredHosts: List<String>, targetHost: String): Boolean {
        if (declaredHosts.isEmpty() || targetHost.isBlank()) return false
        val normalizedTarget = targetHost.trim().removeSuffix(".").lowercase(Locale.ROOT)
        if (normalizedTarget.isEmpty()) return false

        for (declaredHost in declaredHosts) {
            val rawAuth = declaredHost.trim().removeSuffix(".").lowercase(Locale.ROOT)
            
            // Reject catch-all wildcards or empty authorities from being native domain matches
            if (rawAuth.isEmpty() || rawAuth == "*" || rawAuth == "*.*") {
                continue
            }

            // Direct exact match (e.g. "youtube.com" == "youtube.com")
            if (rawAuth == normalizedTarget) {
                return true
            }

            // Wildcard prefix match (e.g. "*.youtube.com")
            if (rawAuth.startsWith("*.")) {
                val baseDomain = rawAuth.removePrefix("*.")
                if (baseDomain.isNotEmpty() && baseDomain.contains(".")) {
                    if (normalizedTarget == baseDomain || normalizedTarget.endsWith(".$baseDomain")) {
                        return true
                    }
                }
            } else if (rawAuth.contains(".")) {
                // If declared host is an apex domain (e.g. "youtube.com"), also match subdomains ("m.youtube.com")
                if (normalizedTarget.endsWith(".$rawAuth")) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Determines whether an application behaves like a general-purpose web browser.
     *
     * An application is classified as a browser if:
     * 1. It is capable of handling generic web traffic without domain-specific restrictions, AND
     * 2. It does not declare specific host authorities (or only declares generic wildcards).
     */
    fun isBrowserLike(
        isGenericBrowserCandidate: Boolean,
        declaredHosts: List<String>
    ): Boolean {
        if (isGenericBrowserCandidate) {
            if (declaredHosts.isEmpty()) {
                return true
            }
            if (declaredHosts.size == 1) {
                val first = declaredHosts[0]
                if (first == "*" || first == "*.*" || first.isBlank()) {
                    return true
                }
            }
        }
        return false
    }

    data class ClassificationResult(
        val isNativeMatch: Boolean,
        val isBrowser: Boolean,
        val category: TargetCategory
    )
}
