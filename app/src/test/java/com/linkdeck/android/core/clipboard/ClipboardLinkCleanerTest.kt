package com.linkdeck.android.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardLinkCleanerTest {

    @Test
    fun clean_nullOrBlank_returnsEmpty() {
        assertTrue(ClipboardLinkCleaner.clean(null) is ClipboardCleanResult.Empty)
        assertTrue(ClipboardLinkCleaner.clean("") is ClipboardCleanResult.Empty)
        assertTrue(ClipboardLinkCleaner.clean("   \n\t  ") is ClipboardCleanResult.Empty)
    }

    @Test
    fun clean_plainTextWithoutUrl_returnsNoLinkFound() {
        val result = ClipboardLinkCleaner.clean("Hello team, please review the document tomorrow morning.")
        assertTrue(result is ClipboardCleanResult.NoLinkFound)
        assertEquals("Hello team, please review the document tomorrow morning.", (result as ClipboardCleanResult.NoLinkFound).rawText)
    }

    @Test
    fun clean_unsupportedScheme_returnsNoLinkFoundOrInvalid() {
        val result = ClipboardLinkCleaner.clean("javascript:alert('pwned')")
        assertTrue(result is ClipboardCleanResult.NoLinkFound)
    }

    @Test
    fun clean_alreadyCleanUrl_returnsAlreadyClean() {
        val cleanUrl = "https://example.com/blog/getting-started?id=42"
        val result = ClipboardLinkCleaner.clean(cleanUrl)

        assertTrue(result is ClipboardCleanResult.AlreadyClean)
        assertEquals(cleanUrl, (result as ClipboardCleanResult.AlreadyClean).link.rawUrl)
    }

    @Test
    fun clean_urlWithTrackingTokens_stripsParamsAndReturnsCleaned() {
        val dirtyUrl = "https://example.com/product?utm_source=newsletter&id=99&fbclid=IwAR0123"
        val result = ClipboardLinkCleaner.clean(dirtyUrl)

        assertTrue(result is ClipboardCleanResult.Cleaned)
        val cleaned = result as ClipboardCleanResult.Cleaned
        assertEquals("https://example.com/product?id=99", cleaned.cleanedLink.rawUrl)
        assertEquals(2, cleaned.removedParams.size)
        assertTrue(cleaned.removedParams.contains("utm_source"))
        assertTrue(cleaned.removedParams.contains("fbclid"))
        assertFalse(cleaned.wasDeAmped)
    }

    @Test
    fun clean_googleAmpViewer_unwrapsCanonicalAndReturnsCleaned() {
        val ampUrl = "https://www.google.com/amp/s/www.theverge.com/2026/01/tech-news"
        val result = ClipboardLinkCleaner.clean(ampUrl)

        assertTrue(result is ClipboardCleanResult.Cleaned)
        val cleaned = result as ClipboardCleanResult.Cleaned
        assertEquals("https://www.theverge.com/2026/01/tech-news", cleaned.cleanedLink.rawUrl)
        assertTrue(cleaned.wasDeAmped)
    }

    @Test
    fun clean_ampLinkWithTrackingTokens_unwrapsAndStripsAll() {
        val dirtyAmp = "https://www.google.com/amp/s/example.com/article?utm_medium=social&amp_js_v=0.1&ref=friend"
        val result = ClipboardLinkCleaner.clean(dirtyAmp)

        assertTrue(result is ClipboardCleanResult.Cleaned)
        val cleaned = result as ClipboardCleanResult.Cleaned
        assertEquals("https://example.com/article?ref=friend", cleaned.cleanedLink.rawUrl)
        assertTrue(cleaned.wasDeAmped)
        assertTrue(cleaned.removedParams.contains("utm_medium"))
    }

    @Test
    fun clean_textWithEnclosingMessage_extractsAndReturnsCleaned() {
        val text = "Check out this deal https://example.com/sale?utm_source=twitter&product=123 from yesterday!"
        val result = ClipboardLinkCleaner.clean(text)

        assertTrue(result is ClipboardCleanResult.Cleaned)
        val cleaned = result as ClipboardCleanResult.Cleaned
        assertEquals("https://example.com/sale?product=123", cleaned.cleanedLink.rawUrl)
    }

    @Test
    fun clean_disabledDeAmping_preservesAmpContainer() {
        val ampUrl = "https://www.google.com/amp/s/example.com/news?utm_source=test"
        val result = ClipboardLinkCleaner.clean(ampUrl, isDeAmpingEnabled = false)

        assertTrue(result is ClipboardCleanResult.Cleaned)
        val cleaned = result as ClipboardCleanResult.Cleaned
        assertFalse(cleaned.wasDeAmped)
        assertTrue(cleaned.cleanedLink.rawUrl.startsWith("https://www.google.com/amp/s/example.com/news"))
    }

    @Test
    fun clean_disabledTrackingCleaner_preservesTrackingTokens() {
        val dirtyUrl = "https://example.com/page?utm_source=twitter"
        val result = ClipboardLinkCleaner.clean(dirtyUrl, isTrackingCleanerEnabled = false)

        assertTrue(result is ClipboardCleanResult.AlreadyClean)
        assertEquals(dirtyUrl, (result as ClipboardCleanResult.AlreadyClean).link.rawUrl)
    }
}
