package com.linkdeck.android.core.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPreferenceMatcherTest {

    @Test
    fun canonicalizeHost_standardDomain_lowercasedAndClean() {
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("youtube.com"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("YouTube.COM"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("  youtube.com.  "))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("...youtube.com..."))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost(".youtube.com"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("youtube.com:443"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("youtube.com:80"))
    }

    @Test
    fun canonicalizeHost_fullUrlWithSchemeAndPath_normalizesToHost() {
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("https://youtube.com/watch?v=123"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("http://youtube.com/"))
        assertEquals("youtube.com", RoutingPreferenceMatcher.canonicalizeHost("youtube.com/shorts/123"))
        assertEquals("gymscaleup.com", RoutingPreferenceMatcher.canonicalizeHost("https://gymscaleup.com"))
    }

    @Test
    fun canonicalizeHost_ipLiterals_returnsNull() {
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("127.0.0.1"))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("192.168.1.1"))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("[::1]"))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("::1"))
    }

    @Test
    fun canonicalizeHost_blankOrEmpty_returnsNull() {
        assertNull(RoutingPreferenceMatcher.canonicalizeHost(""))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("   "))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost(null))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("."))
        assertNull(RoutingPreferenceMatcher.canonicalizeHost("..."))
    }

    @Test
    fun canonicalizeHost_excessiveLength_returnsNull() {
        val longHost = "a".repeat(250) + ".com" // 254 chars
        assertNull(RoutingPreferenceMatcher.canonicalizeHost(longHost))
    }

    @Test
    fun matches_exactHost_returnsTrue() {
        assertTrue(RoutingPreferenceMatcher.matches("youtube.com", "youtube.com"))
        assertTrue(RoutingPreferenceMatcher.matches("YOUTUBE.COM.", "youtube.com"))
        assertTrue(RoutingPreferenceMatcher.matches("github.com", "GITHUB.COM"))
    }

    @Test
    fun matches_validSubdomain_returnsTrue() {
        assertTrue(RoutingPreferenceMatcher.matches("www.youtube.com", "youtube.com"))
        assertTrue(RoutingPreferenceMatcher.matches("m.youtube.com", "youtube.com"))
        assertTrue(RoutingPreferenceMatcher.matches("music.youtube.com", "youtube.com"))
        assertTrue(RoutingPreferenceMatcher.matches("raw.githubusercontent.com", "githubusercontent.com"))
    }

    @Test
    fun matches_substringAttacks_returnsFalse() {
        // Hyphenated prefix attack
        assertFalse(RoutingPreferenceMatcher.matches("evil-youtube.com", "youtube.com"))
        assertFalse(RoutingPreferenceMatcher.matches("notyoutube.com", "youtube.com"))

        // Subdomain suffix attack (target domain is a subdomain of an attacker root)
        assertFalse(RoutingPreferenceMatcher.matches("youtube.com.evil.com", "youtube.com"))
        assertFalse(RoutingPreferenceMatcher.matches("youtube.com.attacker.example", "youtube.com"))
        assertFalse(RoutingPreferenceMatcher.matches("evil.youtube.com.evil.com", "youtube.com"))

        // Unrelated domains
        assertFalse(RoutingPreferenceMatcher.matches("reddit.com", "youtube.com"))
    }

    @Test
    fun matches_ipLiterals_returnsFalse() {
        assertFalse(RoutingPreferenceMatcher.matches("127.0.0.1", "127.0.0.1"))
        assertFalse(RoutingPreferenceMatcher.matches("192.168.1.1", "youtube.com"))
    }
}
