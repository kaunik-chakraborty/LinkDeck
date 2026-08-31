package com.linkdeck.android.core.rule

import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.preference.RoutingPreferenceMatcher

/**
 * Evaluates incoming URLs against active [RoutingRule]s using parsed URI components
 * and deterministic specificity scoring.
 */
object RoutingRuleMatcher {

    private const val WILDCARD_SUFFIX = "/" + "*"
    private const val WILDCARD_ALL = "/" + "*"

    fun findBestMatch(link: SanitizedLink, rules: List<RoutingRule>): RoutingRule? {
        val candidateHost = RoutingPreferenceMatcher.canonicalizeHost(link.host) ?: return null
        val candidatePath = normalizeCandidatePath(link.path)

        val enabledRules = rules.filter { it.isEnabled }
        val matchingRules = enabledRules.filter { rule ->
            matches(candidateHost, candidatePath, rule)
        }

        if (matchingRules.isEmpty()) return null

        return matchingRules.maxWithOrNull(
            compareBy<RoutingRule> { rule -> calculateSpecificity(candidateHost, rule) }
                .thenByDescending { rule -> rule.createdAt }
        )
    }

    fun matches(candidateHost: String, candidatePath: String, rule: RoutingRule): Boolean {
        if (!RoutingPreferenceMatcher.matches(candidateHost, rule.host)) {
            return false
        }

        val pattern = rule.pathPattern
        if (pattern.isNullOrBlank()) {
            return true
        }

        if (pattern.endsWith(WILDCARD_SUFFIX)) {
            val prefix = pattern.removeSuffix("*")
            val prefixWithoutSlash = prefix.removeSuffix("/")

            return candidatePath.startsWith(prefix) || candidatePath == prefixWithoutSlash || (prefix == "/" && candidatePath.startsWith("/"))
        }

        if (pattern == WILDCARD_ALL) {
            return true
        }

        val normalizedPattern = normalizeCandidatePath(pattern)
        return candidatePath == normalizedPattern || candidatePath == "$normalizedPattern/" || "$candidatePath/" == normalizedPattern
    }

    private fun calculateSpecificity(candidateHost: String, rule: RoutingRule): Int {
        var score = 0

        if (candidateHost == rule.host) {
            score += 50
        }

        val pattern = rule.pathPattern
        if (pattern.isNullOrBlank()) {
            score += 100
        } else if (pattern.endsWith(WILDCARD_SUFFIX) || pattern == WILDCARD_ALL) {
            score += 500 + pattern.length
        } else {
            score += 1000 + pattern.length
        }

        return score
    }

    private fun normalizeCandidatePath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return "/"
        var path = rawPath.trim()
        if (!path.startsWith("/")) path = "/$path"
        return path
    }
}
