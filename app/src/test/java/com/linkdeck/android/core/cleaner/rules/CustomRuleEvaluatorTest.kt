package com.linkdeck.android.core.cleaner.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomRuleEvaluatorTest {

    @Test
    fun evaluate_emptyRules_returnsNeutral() {
        val decision = CustomRuleEvaluator.evaluate("utm_source", "example.com", emptyList())
        assertEquals(RuleDecision.NEUTRAL, decision)
    }

    @Test
    fun evaluate_exactMatchBlockRule_returnsBlock() {
        val rule = CustomParameterRule(
            parameterPattern = "ref_src",
            isPrefix = false,
            domainPattern = null,
            action = ParameterRuleAction.BLOCK
        )
        val decision = CustomRuleEvaluator.evaluate("ref_src", "example.com", listOf(rule))
        assertEquals(RuleDecision.BLOCK, decision)
    }

    @Test
    fun evaluate_prefixMatchBlockRule_returnsBlock() {
        val rule = CustomParameterRule(
            parameterPattern = "mkt_*",
            isPrefix = true,
            domainPattern = null,
            action = ParameterRuleAction.BLOCK
        )
        val decision = CustomRuleEvaluator.evaluate("mkt_tok", "example.com", listOf(rule))
        assertEquals(RuleDecision.BLOCK, decision)
    }

    @Test
    fun evaluate_domainScopedRule_matchesOnlySpecifiedHost() {
        val rule = CustomParameterRule(
            parameterPattern = "spm",
            isPrefix = false,
            domainPattern = "aliexpress.com",
            action = ParameterRuleAction.BLOCK
        )
        val matched = CustomRuleEvaluator.evaluate("spm", "www.aliexpress.com", listOf(rule))
        assertEquals(RuleDecision.BLOCK, matched)

        val notMatched = CustomRuleEvaluator.evaluate("spm", "amazon.com", listOf(rule))
        assertEquals(RuleDecision.NEUTRAL, notMatched)
    }

    @Test
    fun evaluate_allowRule_overridesBlockRule() {
        val blockRule = CustomParameterRule(
            parameterPattern = "token",
            action = ParameterRuleAction.BLOCK
        )
        val allowRule = CustomParameterRule(
            parameterPattern = "token",
            domainPattern = "internal.company.com",
            action = ParameterRuleAction.ALLOW
        )
        val rules = listOf(blockRule, allowRule)

        val decisionOnAllowedHost = CustomRuleEvaluator.evaluate("token", "internal.company.com", rules)
        assertEquals(RuleDecision.ALLOW, decisionOnAllowedHost)

        val decisionOnOtherHost = CustomRuleEvaluator.evaluate("token", "other.com", rules)
        assertEquals(RuleDecision.BLOCK, decisionOnOtherHost)
    }

    @Test
    fun evaluate_disabledRule_isIgnored() {
        val rule = CustomParameterRule(
            parameterPattern = "test_tracker",
            isEnabled = false,
            action = ParameterRuleAction.BLOCK
        )
        val decision = CustomRuleEvaluator.evaluate("test_tracker", "example.com", listOf(rule))
        assertEquals(RuleDecision.NEUTRAL, decision)
    }

    @Test
    fun evaluate_caseInsensitiveMatching_matchesCorrectly() {
        val rule = CustomParameterRule(
            parameterPattern = "REF_SRC",
            action = ParameterRuleAction.BLOCK
        )
        val decision = CustomRuleEvaluator.evaluate("ref_src", "EXAMPLE.COM", listOf(rule))
        assertEquals(RuleDecision.BLOCK, decision)
    }

    @Test
    fun evaluate_emptyPatternRule_doesNotMatch() {
        val rule = CustomParameterRule(
            parameterPattern = "*",
            action = ParameterRuleAction.BLOCK
        )
        val decision = CustomRuleEvaluator.evaluate("token", "example.com", listOf(rule))
        assertEquals(RuleDecision.NEUTRAL, decision)
    }

    @Test
    fun evaluate_domainScopeWithWwwPrefix_matchesBothWays() {
        val ruleWithWww = CustomParameterRule(
            parameterPattern = "spm",
            domainPattern = "www.aliexpress.com",
            action = ParameterRuleAction.BLOCK
        )
        // Rule has www.aliexpress.com, host is aliexpress.com
        val match1 = CustomRuleEvaluator.evaluate("spm", "aliexpress.com", listOf(ruleWithWww))
        assertEquals(RuleDecision.BLOCK, match1)

        val ruleWithoutWww = CustomParameterRule(
            parameterPattern = "tag",
            domainPattern = "amazon.com",
            action = ParameterRuleAction.BLOCK
        )
        // Rule has amazon.com, host is www.amazon.com
        val match2 = CustomRuleEvaluator.evaluate("tag", "www.amazon.com", listOf(ruleWithoutWww))
        assertEquals(RuleDecision.BLOCK, match2)
    }
}
