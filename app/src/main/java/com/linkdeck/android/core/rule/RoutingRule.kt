package com.linkdeck.android.core.rule

import com.linkdeck.android.core.preference.RoutingPreferenceMatcher
import java.util.UUID

/**
 * Validation outcome for a candidate routing rule.
 */
sealed class RuleValidationResult {
    data class Valid(val rule: RoutingRule) : RuleValidationResult()
    data class Invalid(val reason: String) : RuleValidationResult()
}

/**
 * Persisted structured routing rule defining where links matching a host and optional
 * path pattern should open.
 */
data class RoutingRule(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val pathPattern: String? = null,
    val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasPathCondition: Boolean get() = !pathPattern.isNullOrBlank()

    val displayCondition: String
        get() = if (!pathPattern.isNullOrBlank()) "$host$pathPattern" else host

    companion object {
        private const val MAX_PATH_LENGTH = 255
        private const val WILDCARD_SUFFIX = "/" + "*"
        private const val WILDCARD_ALL = "/" + "*"

        fun validate(
            rawHost: String,
            rawPathPattern: String?,
            packageName: String,
            appLabel: String,
            isEnabled: Boolean = true,
            selfPackageName: String? = null,
            id: String = UUID.randomUUID().toString(),
            createdAt: Long = System.currentTimeMillis()
        ): RuleValidationResult {
            val canonicalHost = RoutingPreferenceMatcher.canonicalizeHost(rawHost)
                ?: return RuleValidationResult.Invalid("Please enter a valid website domain (e.g. youtube.com)")

            if (packageName.isBlank()) {
                return RuleValidationResult.Invalid("Please select a target application")
            }

            if (selfPackageName != null && packageName == selfPackageName) {
                return RuleValidationResult.Invalid("Cannot route links to LinkDeck itself")
            }

            var cleanPath: String? = null
            if (!rawPathPattern.isNullOrBlank()) {
                val trimmedPath = rawPathPattern.trim()
                if (!trimmedPath.startsWith("/")) {
                    return RuleValidationResult.Invalid("Path pattern must start with '/' (e.g. /shorts/...)")
                }
                if (trimmedPath.length > MAX_PATH_LENGTH) {
                    return RuleValidationResult.Invalid("Path pattern is too long (maximum $MAX_PATH_LENGTH characters)")
                }

                val wildcardCount = trimmedPath.count { it == '*' }
                if (wildcardCount > 1) {
                    return RuleValidationResult.Invalid("Only a single trailing wildcard is permitted at the end of the path")
                }
                if (wildcardCount == 1) {
                    if (!trimmedPath.endsWith(WILDCARD_SUFFIX) && trimmedPath != WILDCARD_ALL && !trimmedPath.endsWith("*")) {
                        return RuleValidationResult.Invalid("Wildcard is only allowed at the very end of the path pattern")
                    }
                    if (trimmedPath.contains("*" + "/") || trimmedPath.contains("*.") || (trimmedPath.endsWith("*") && !trimmedPath.endsWith(WILDCARD_SUFFIX) && trimmedPath != WILDCARD_ALL)) {
                        return RuleValidationResult.Invalid("Wildcard pattern must follow a path segment boundary")
                    }
                }

                cleanPath = trimmedPath
            }

            val cleanLabel = if (appLabel.isNotBlank()) appLabel.trim() else packageName

            return RuleValidationResult.Valid(
                RoutingRule(
                    id = id,
                    host = canonicalHost,
                    pathPattern = cleanPath,
                    packageName = packageName.trim(),
                    appLabel = cleanLabel,
                    isEnabled = isEnabled,
                    createdAt = createdAt
                )
            )
        }
    }
}
