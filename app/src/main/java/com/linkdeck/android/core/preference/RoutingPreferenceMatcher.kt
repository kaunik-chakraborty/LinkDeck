package com.linkdeck.android.core.preference

import java.util.Locale

/**
 * Normalizes hostnames and evaluates whether an incoming destination host
 * matches a saved routing preference using strict label boundaries.
 */
object RoutingPreferenceMatcher {

    private const val MAX_HOST_LENGTH = 253

    /**
     * Normalizes a hostname by stripping schemes (http://, https://), paths, query strings,
     * default port suffixes, converting to lowercase, and verifying length bounds.
     */
    fun canonicalizeHost(rawHost: String?): String? {
        if (rawHost.isNullOrBlank()) return null
        var host = rawHost.trim().lowercase(Locale.ROOT)

        // Strip schemes if user pasted full URL
        if (host.startsWith("http://")) host = host.removePrefix("http://")
        if (host.startsWith("https://")) host = host.removePrefix("https://")
        if (host.startsWith("//")) host = host.removePrefix("//")

        // Strip path, query, fragment if present
        if (host.contains("/")) host = host.substringBefore("/")
        if (host.contains("?")) host = host.substringBefore("?")
        if (host.contains("#")) host = host.substringBefore("#")

        // Strip leading dots
        while (host.startsWith(".")) {
            host = host.removePrefix(".")
        }

        // Strip trailing dots
        while (host.endsWith(".")) {
            host = host.removeSuffix(".")
        }
        if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return null

        // Strip default web port suffix if present
        if (host.endsWith(":80")) host = host.removeSuffix(":80")
        if (host.endsWith(":443")) host = host.removeSuffix(":443")

        // Reject IP literals from persistent domain preferences
        if (isIpAddress(host)) return null

        // Ensure host contains only valid hostname characters (alphanumeric, hyphens, dots, underscores)
        if (host.any { ch -> !ch.isLetterOrDigit() && ch != '-' && ch != '.' && ch != '_' }) {
            return null
        }

        return host
    }

    /**
     * Identifies whether a host string is an IPv4 or IPv6 numeric literal.
     */
    fun isIpAddress(host: String): Boolean {
        if (host.startsWith("[") || host.contains(":")) return true
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { it.all { ch -> ch.isDigit() } && it.isNotEmpty() }) {
            return true
        }
        return false
    }

    /**
     * Evaluates whether a candidate host matches a preferred domain rule.
     * Matches if candidate host is identical to domain, or is a valid subdomain
     * with a dot label boundary.
     */
    fun matches(candidateHost: String, preferredDomain: String): Boolean {
        val c = canonicalizeHost(candidateHost) ?: return false
        val p = canonicalizeHost(preferredDomain) ?: return false

        return c == p || c.endsWith(".$p")
    }
}
