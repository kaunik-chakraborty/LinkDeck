package com.linkdeck.android.core.cleaner.rules

import java.util.Locale
import java.util.UUID

/**
 * Action taken when a custom query parameter matches a link.
 */
enum class ParameterRuleAction {
    BLOCK, // Strip the query parameter
    ALLOW  // Preserve/allow the query parameter (overrides default tracking rules)
}

/**
 * Encapsulates a user-defined query parameter rule.
 * Supports exact key matching, prefix wildcards, and domain-scoped filtering.
 */
data class CustomParameterRule(
    val id: String = UUID.randomUUID().toString(),
    val parameterPattern: String,
    val isPrefix: Boolean = parameterPattern.endsWith("*"),
    val domainPattern: String? = null,
    val action: ParameterRuleAction = ParameterRuleAction.BLOCK,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {

    /**
     * Normalized parameter key for case-insensitive matching.
     */
    val normalizedKey: String = if (isPrefix) {
        parameterPattern.removeSuffix("*").trim().lowercase(Locale.ROOT)
    } else {
        parameterPattern.trim().lowercase(Locale.ROOT)
    }

    /**
     * Normalized domain for scoping rules, or null if applied globally.
     */
    val normalizedDomain: String? = domainPattern?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    /**
     * Checks if this rule applies to the specified [candidateKey] and [targetHost].
     */
    fun matches(candidateKey: String, targetHost: String?): Boolean {
        if (!isEnabled) return false

        // 1. Check parameter key match
        if (normalizedKey.isEmpty()) return false
        val keyLower = candidateKey.trim().lowercase(Locale.ROOT)
        val keyMatches = if (isPrefix) {
            keyLower.startsWith(normalizedKey)
        } else {
            keyLower == normalizedKey
        }
        if (!keyMatches) return false

        // 2. Check domain scope match
        val ruleDomain = normalizedDomain ?: return true // Global rule matches any host
        if (targetHost.isNullOrBlank()) return false

        val hostLower = targetHost.trim().lowercase(Locale.ROOT)
        val cleanRuleDomain = ruleDomain.removePrefix("www.").removePrefix("*.")
        val cleanHost = hostLower.removePrefix("www.")
        return cleanHost == cleanRuleDomain || cleanHost.endsWith(".$cleanRuleDomain")
    }
}
