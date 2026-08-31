package com.linkdeck.android.core.rule

import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.model.SanitizedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RulePipelineIntegrationTest {

    @Test
    fun pipeline_phase4RuleOverridesPhase3Preference() {
        val link = SanitizedLink(
            rawUrl = "https://youtube.com/shorts/123",
            scheme = "https",
            host = "youtube.com",
            path = "/shorts/123"
        )

        val phase4Rule = RoutingRule(
            id = "rule_shorts",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        val rules = listOf(phase4Rule)
        val matchedRule = RoutingRuleMatcher.findBestMatch(link, rules)

        assertNotNull(matchedRule)
        assertEquals("com.google.android.youtube", matchedRule?.packageName)
    }

    @Test
    fun pipeline_fallsThroughToPhase3WhenNoRuleMatches() {
        val link = SanitizedLink(
            rawUrl = "https://youtube.com/watch?v=123",
            scheme = "https",
            host = "youtube.com",
            path = "/watch"
        )

        val phase4Rule = RoutingRule(
            id = "rule_shorts",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        val rules = listOf(phase4Rule)
        val matchedRule = RoutingRuleMatcher.findBestMatch(link, rules)

        assertNull(matchedRule)
    }

    @Test
    fun pipeline_trackingCleanerRunsBeforeRuleMatching() {
        val dirtyLink = SanitizedLink(
            rawUrl = "https://youtube.com/shorts/abc?utm_source=twitter&fbclid=xyz",
            scheme = "https",
            host = "youtube.com",
            path = "/shorts/abc"
        )

        val cleanResult = TrackingParameterCleaner.clean(dirtyLink)
        assertEquals("https://youtube.com/shorts/abc", cleanResult.cleanedLink.rawUrl)

        val phase4Rule = RoutingRule(
            id = "rule_shorts",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        val matchedRule = RoutingRuleMatcher.findBestMatch(cleanResult.cleanedLink, listOf(phase4Rule))
        assertNotNull(matchedRule)
        assertEquals("com.google.android.youtube", matchedRule?.packageName)
    }

    @Test
    fun pipeline_cleanerDoesNotInspectOrRewriteNestedUrlParameters() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/login?next=https%3A%2F%2Fexample.com%2Fdashboard%3Futm_source%3Dignored&id=42",
            scheme = "https",
            host = "example.com",
            path = "/login"
        )

        val cleanResult = TrackingParameterCleaner.clean(link)
        // Top-level 'next' and 'id' are functional parameters, so nothing is stripped
        assertEquals(link.rawUrl, cleanResult.cleanedLink.rawUrl)
        assertEquals(false, cleanResult.hasRemovedParams)
    }

    @Test
    fun pipeline_cleanerPreservesFragmentsAndRemovesTrackingParameters() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/product/123?utm_source=newsletter&utm_medium=email#specifications",
            scheme = "https",
            host = "example.com",
            path = "/product/123"
        )

        val cleanResult = TrackingParameterCleaner.clean(link)
        assertEquals(true, cleanResult.hasRemovedParams)
        assertEquals("https://example.com/product/123#specifications", cleanResult.cleanedLink.rawUrl)
    }
}
