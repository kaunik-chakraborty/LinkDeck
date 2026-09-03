package com.linkdeck.android.core.cleaner.rules

/**
 * Result of evaluating a query parameter against user-defined custom rules.
 */
enum class RuleDecision {
    ALLOW,   // Parameter must be preserved, bypassing default tracking blocklists
    BLOCK,   // Parameter must be stripped
    NEUTRAL  // No custom rule matched; defer to standard built-in rules
}

/**
 * Evaluates individual query parameters against a collection of custom rules.
 * Pure functional evaluation ensures fast, thread-safe, and deterministic results.
 */
object CustomRuleEvaluator {

    /**
     * Evaluates [key] against [rules] for a given [host].
     *
     * Precedence:
     * 1. ALLOW rules take precedence over BLOCK rules, enabling users to unbreak specific sites.
     * 2. BLOCK rules trigger parameter stripping.
     * 3. If no rules match, returns NEUTRAL.
     */
    fun evaluate(key: String, host: String?, rules: List<CustomParameterRule>): RuleDecision {
        if (rules.isEmpty()) return RuleDecision.NEUTRAL

        var hasBlockMatch = false

        for (rule in rules) {
            if (rule.matches(key, host)) {
                if (rule.action == ParameterRuleAction.ALLOW) {
                    return RuleDecision.ALLOW
                } else if (rule.action == ParameterRuleAction.BLOCK) {
                    hasBlockMatch = true
                }
            }
        }

        return if (hasBlockMatch) RuleDecision.BLOCK else RuleDecision.NEUTRAL
    }
}
