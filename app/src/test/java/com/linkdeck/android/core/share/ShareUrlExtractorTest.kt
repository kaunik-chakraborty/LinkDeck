package com.linkdeck.android.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareUrlExtractorTest {

    @Test
    fun extractFirstUrl_plainUrl_returnsUrl() {
        val input = "https://youtu.be/dQw4w9WgXcQ"
        val result = ShareUrlExtractor.extractFirstUrl(input)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", result)
    }

    @Test
    fun extractFirstUrl_urlWithTrailingDot_cleansPunctuation() {
        val input = "Check this link: https://example.com/article."
        val result = ShareUrlExtractor.extractFirstUrl(input)
        assertEquals("https://example.com/article", result)
    }

    @Test
    fun extractFirstUrl_multilineSharedMessage_extractsEmbeddedUrl() {
        val input = """
            Hey check out this video:
            https://www.youtube.com/watch?v=12345&si=abcde
            Let me know what you think!
        """.trimIndent()
        val result = ShareUrlExtractor.extractFirstUrl(input)
        assertEquals("https://www.youtube.com/watch?v=12345&si=abcde", result)
    }

    @Test
    fun extractFirstUrl_noUrlPresent_returnsNull() {
        val input = "Just some text without any links at all."
        val result = ShareUrlExtractor.extractFirstUrl(input)
        assertNull(result)
    }

    @Test
    fun extractFirstUrl_nullOrBlank_returnsNull() {
        assertNull(ShareUrlExtractor.extractFirstUrl(null))
        assertNull(ShareUrlExtractor.extractFirstUrl("   "))
    }

    @Test
    fun extractFirstUrl_plainWords_returnsNull() {
        assertNull(ShareUrlExtractor.extractFirstUrl("hello"))
        assertNull(ShareUrlExtractor.extractFirstUrl("Hello world! How are you doing?"))
        assertNull(ShareUrlExtractor.extractFirstUrl("user@example.com"))
    }

    @Test
    fun extractFirstUrl_schemelessDomains_returnsHttpsUrl() {
        assertEquals("https://www.google.com", ShareUrlExtractor.extractFirstUrl("www.google.com"))
        assertEquals("https://google.com", ShareUrlExtractor.extractFirstUrl("google.com"))
        assertEquals("https://github.com/repo", ShareUrlExtractor.extractFirstUrl("github.com/repo"))
    }
}
