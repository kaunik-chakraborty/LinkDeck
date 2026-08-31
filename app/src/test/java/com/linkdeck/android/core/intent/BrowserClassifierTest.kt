package com.linkdeck.android.core.intent

import com.linkdeck.android.core.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserClassifierTest {

    @Test
    fun isHostMatch_exactHost_returnsTrue() {
        val declared = listOf("youtube.com")
        assertTrue(BrowserClassifier.isHostMatch(declared, "youtube.com"))
        assertTrue(BrowserClassifier.isHostMatch(declared, "YOUTUBE.COM"))
    }

    @Test
    fun isHostMatch_trailingDot_normalizesAndMatches() {
        val declared = listOf("youtube.com.")
        assertTrue(BrowserClassifier.isHostMatch(declared, "youtube.com"))
        assertTrue(BrowserClassifier.isHostMatch(listOf("youtube.com"), "youtube.com."))
    }

    @Test
    fun isHostMatch_subdomain_returnsTrue() {
        val declared = listOf("youtube.com")
        assertTrue(BrowserClassifier.isHostMatch(declared, "m.youtube.com"))
        assertTrue(BrowserClassifier.isHostMatch(declared, "music.youtube.com"))
    }

    @Test
    fun isHostMatch_wildcardAuthority_matchesBaseAndSubdomains() {
        val declared = listOf("*.example.com")
        assertTrue(BrowserClassifier.isHostMatch(declared, "example.com"))
        assertTrue(BrowserClassifier.isHostMatch(declared, "sub.example.com"))
        assertTrue(BrowserClassifier.isHostMatch(declared, "api.service.example.com"))
    }

    @Test
    fun isHostMatch_suffixSpoofing_strictlyRejected() {
        val declared = listOf("example.com")
        assertFalse(BrowserClassifier.isHostMatch(declared, "fake-example.com"))
        assertFalse(BrowserClassifier.isHostMatch(declared, "notexample.com"))
        assertFalse(BrowserClassifier.isHostMatch(declared, "example.company"))
    }

    @Test
    fun isHostMatch_catchAllWildcard_rejectedFromNativeMatch() {
        val declared = listOf("*", "*.*")
        assertFalse(BrowserClassifier.isHostMatch(declared, "youtube.com"))
        assertFalse(BrowserClassifier.isHostMatch(declared, "google.com"))
    }

    @Test
    fun isHostMatch_differentHost_returnsFalse() {
        val declared = listOf("amazon.in")
        assertFalse(BrowserClassifier.isHostMatch(declared, "flipkart.com"))
        assertFalse(BrowserClassifier.isHostMatch(declared, "fakeamazon.in"))
    }

    @Test
    fun isHostMatch_emptyOrBlank_returnsFalse() {
        assertFalse(BrowserClassifier.isHostMatch(emptyList(), "youtube.com"))
        assertFalse(BrowserClassifier.isHostMatch(listOf("youtube.com"), ""))
        assertFalse(BrowserClassifier.isHostMatch(listOf("   "), "youtube.com"))
    }

    @Test
    fun classificationResult_categoriesMappedCorrectly() {
        val nativeRes = BrowserClassifier.ClassificationResult(
            isNativeMatch = true,
            isBrowser = false,
            category = TargetCategory.RECOMMENDED
        )
        assertEquals(TargetCategory.RECOMMENDED, nativeRes.category)

        val browserRes = BrowserClassifier.ClassificationResult(
            isNativeMatch = false,
            isBrowser = true,
            category = TargetCategory.BROWSER
        )
        assertEquals(TargetCategory.BROWSER, browserRes.category)

        val otherRes = BrowserClassifier.ClassificationResult(
            isNativeMatch = false,
            isBrowser = false,
            category = TargetCategory.OTHER
        )
        assertEquals(TargetCategory.OTHER, otherRes.category)
    }
}
