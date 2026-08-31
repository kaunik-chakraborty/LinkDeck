package com.linkdeck.android.core.rule

import com.linkdeck.android.core.model.SanitizedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRuleMatcherTest {

    @Test
    fun matches_exactHostOnly_matchesAllPathsOnHost() {
        val rule = RoutingRule(host = "youtube.com", packageName = "com.google.android.youtube", appLabel = "YouTube")

        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/watch", rule))
        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts/123", rule))
        assertTrue(RoutingRuleMatcher.matches("m.youtube.com", "/feed", rule))
    }

    @Test
    fun matches_exactPath_matchesOnlyTargetSegment() {
        val rule = RoutingRule(
            host = "youtube.com",
            pathPattern = "/shorts",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts", rule))
        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts/", rule))
        assertFalse(RoutingRuleMatcher.matches("youtube.com", "/shorts/123", rule))
        assertFalse(RoutingRuleMatcher.matches("youtube.com", "/shorts-abc", rule))
    }

    @Test
    fun matches_wildcardPath_matchesSubpaths() {
        val rule = RoutingRule(
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts/abc", rule))
        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts/123/edit", rule))
        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts/", rule))
        assertTrue(RoutingRuleMatcher.matches("youtube.com", "/shorts", rule))
    }

    @Test
    fun matches_wildcardPath_segmentBoundaryProtection() {
        val rule = RoutingRule(
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        // Suffix/word boundaries outside /shorts/ must not match
        assertFalse(RoutingRuleMatcher.matches("youtube.com", "/shortsfoo", rule))
        assertFalse(RoutingRuleMatcher.matches("youtube.com", "/shorts2", rule))
        assertFalse(RoutingRuleMatcher.matches("youtube.com", "/watch", rule))
    }

    @Test
    fun findBestMatch_precedence_exactPathBeatsWildcardBeatsHostOnly() {
        val hostOnlyRule = RoutingRule(
            id = "1",
            host = "youtube.com",
            pathPattern = null,
            packageName = "com.brave.browser",
            appLabel = "Brave"
        )
        val wildcardRule = RoutingRule(
            id = "2",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )
        val exactPathRule = RoutingRule(
            id = "3",
            host = "youtube.com",
            pathPattern = "/shorts/featured",
            packageName = "com.special.player",
            appLabel = "Special Player"
        )

        val rules = listOf(hostOnlyRule, wildcardRule, exactPathRule)

        // 1. Exact path matches exactPathRule
        val link1 = SanitizedLink(
            rawUrl = "https://youtube.com/shorts/featured",
            scheme = "https",
            host = "youtube.com",
            path = "/shorts/featured"
        )
        val match1 = RoutingRuleMatcher.findBestMatch(link1, rules)
        assertNotNull(match1)
        assertEquals("3", match1?.id)

        // 2. Wildcard path matches wildcardRule
        val link2 = SanitizedLink(
            rawUrl = "https://youtube.com/shorts/12345",
            scheme = "https",
            host = "youtube.com",
            path = "/shorts/12345"
        )
        val match2 = RoutingRuleMatcher.findBestMatch(link2, rules)
        assertNotNull(match2)
        assertEquals("2", match2?.id)

        // 3. Other path matches hostOnlyRule
        val link3 = SanitizedLink(
            rawUrl = "https://youtube.com/watch?v=123",
            scheme = "https",
            host = "youtube.com",
            path = "/watch"
        )
        val match3 = RoutingRuleMatcher.findBestMatch(link3, rules)
        assertNotNull(match3)
        assertEquals("1", match3?.id)
    }

    @Test
    fun findBestMatch_subdomainPrecedence_exactSubdomainMatchBeatsApexMatch() {
        val apexRule = RoutingRule(
            id = "apex",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.android.chrome",
            appLabel = "Chrome"
        )
        val subdomainRule = RoutingRule(
            id = "sub",
            host = "m.youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        val rules = listOf(apexRule, subdomainRule)

        val link = SanitizedLink(
            rawUrl = "https://m.youtube.com/shorts/123",
            scheme = "https",
            host = "m.youtube.com",
            path = "/shorts/123"
        )

        val match = RoutingRuleMatcher.findBestMatch(link, rules)
        assertNotNull(match)
        assertEquals("sub", match?.id)
    }

    @Test
    fun findBestMatch_disabledRules_ignored() {
        val disabledRule = RoutingRule(
            id = "1",
            host = "youtube.com",
            pathPattern = null,
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            isEnabled = false
        )

        val link = SanitizedLink(
            rawUrl = "https://youtube.com/watch?v=123",
            scheme = "https",
            host = "youtube.com",
            path = "/watch"
        )
        val match = RoutingRuleMatcher.findBestMatch(link, listOf(disabledRule))
        assertNull(match)
    }

    @Test
    fun findBestMatch_gymscaleup_matchesBraveRule() {
        val rule = RoutingRule(
            id = "gym",
            host = "gymscaleup.com",
            pathPattern = null,
            packageName = "com.brave.browser",
            appLabel = "Brave"
        )
        val link = SanitizedLink(
            rawUrl = "https://gymscaleup.com/",
            scheme = "https",
            host = "gymscaleup.com",
            path = "/"
        )
        val match = RoutingRuleMatcher.findBestMatch(link, listOf(rule))
        assertNotNull(match)
        assertEquals("gym", match?.id)
        assertEquals("com.brave.browser", match?.packageName)
    }
}
