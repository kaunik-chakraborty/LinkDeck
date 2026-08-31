package com.linkdeck.android.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRuleValidationTest {

    @Test
    fun validate_validHostAndPath_returnsValid() {
        val res = RoutingRule.validate(
            rawHost = "YouTube.com.",
            rawPathPattern = " /shorts/* ",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )

        assertTrue(res is RuleValidationResult.Valid)
        val rule = (res as RuleValidationResult.Valid).rule
        assertEquals("youtube.com", rule.host)
        assertEquals("/shorts/*", rule.pathPattern)
        assertEquals("com.google.android.youtube", rule.packageName)
    }

    @Test
    fun validate_ipLiterals_rejected() {
        val res1 = RoutingRule.validate(
            rawHost = "127.0.0.1",
            rawPathPattern = null,
            packageName = "com.android.chrome",
            appLabel = "Chrome"
        )
        assertTrue(res1 is RuleValidationResult.Invalid)

        val res2 = RoutingRule.validate(
            rawHost = "[::1]",
            rawPathPattern = null,
            packageName = "com.android.chrome",
            appLabel = "Chrome"
        )
        assertTrue(res2 is RuleValidationResult.Invalid)
    }

    @Test
    fun validate_selfPackage_rejected() {
        val res = RoutingRule.validate(
            rawHost = "example.com",
            rawPathPattern = null,
            packageName = "com.linkdeck.android",
            appLabel = "LinkDeck",
            selfPackageName = "com.linkdeck.android"
        )
        assertTrue(res is RuleValidationResult.Invalid)
    }

    @Test
    fun validate_invalidWildcardPatterns_rejected() {
        val invalidPatterns = listOf(
            "*/shorts",
            "/sh*orts",
            "/shorts/*/view",
            "*",
            "/*/*",
            "/shorts*",
            "/path/to/file*x"
        )

        for (pattern in invalidPatterns) {
            val res = RoutingRule.validate(
                rawHost = "example.com",
                rawPathPattern = pattern,
                packageName = "com.android.chrome",
                appLabel = "Chrome"
            )
            assertTrue("Expected pattern '$pattern' to be rejected", res is RuleValidationResult.Invalid)
        }
    }

    @Test
    fun validate_validWildcardPatterns_accepted() {
        val validPatterns = listOf(
            "/shorts/*",
            "/*",
            "/a/b/c/*"
        )

        for (pattern in validPatterns) {
            val res = RoutingRule.validate(
                rawHost = "example.com",
                rawPathPattern = pattern,
                packageName = "com.android.chrome",
                appLabel = "Chrome"
            )
            assertTrue("Expected pattern '$pattern' to be accepted", res is RuleValidationResult.Valid)
        }
    }
}
