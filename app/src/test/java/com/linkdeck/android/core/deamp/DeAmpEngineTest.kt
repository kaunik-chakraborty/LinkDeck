package com.linkdeck.android.core.deamp

import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeAmpEngineTest {

    private fun sanitize(url: String) = (IntentSanitizer.sanitizeUrl(url) as SanitizationResult.Success).link

    @Test
    fun deAmp_googleAmpViewerHttps_unwrapsSuccessfully() {
        val link = sanitize("https://www.google.com/amp/s/www.theverge.com/2023/1/1/article")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://www.theverge.com/2023/1/1/article", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.GOOGLE_VIEWER, result.source)
    }

    @Test
    fun deAmp_googleAmpViewerHttp_unwrapsWithHttpScheme() {
        val link = sanitize("https://google.com/amp/example.com/plain-article")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("http://example.com/plain-article", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.GOOGLE_VIEWER, result.source)
    }

    @Test
    fun deAmp_googleAmpViewerRegionalDomains_unwrapsSuccessfully() {
        val regionalUrls = listOf(
            "https://www.google.co.uk/amp/s/theguardian.com/world/news" to "https://theguardian.com/world/news",
            "https://www.google.co.in/amp/s/ndtv.com/india/post" to "https://ndtv.com/india/post",
            "https://google.de/amp/s/spiegel.de/politik/artikel" to "https://spiegel.de/politik/artikel"
        )

        for ((input, expected) in regionalUrls) {
            val result = DeAmpEngine.deAmp(sanitize(input))
            assertTrue("Failed for $input", result.wasDeAmped)
            assertEquals("Mismatch for $input", expected, result.deAmpedLink.rawUrl)
        }
    }

    @Test
    fun deAmp_googleAmpViewerEncodedPath_decodesAndUnwraps() {
        val link = sanitize("https://www.google.com/amp/s/https%3A%2F%2Fwww.theverge.com%2Fnews")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://www.theverge.com/news", result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_googleSearchRedirectWithNestedAmp_unwrapsEntireChain() {
        val link = sanitize("https://www.google.com/url?q=https%3A%2F%2Fwww.google.com%2Famp%2Fs%2Fexample.com%2Fpost&sa=U")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://example.com/post", result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_ampProjectCdnContentCache_unwrapsCanonical() {
        val link = sanitize("https://theverge-com.cdn.ampproject.org/c/s/www.theverge.com/tech/news")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://www.theverge.com/tech/news", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.AMPROJECT_CDN, result.source)
    }

    @Test
    fun deAmp_ampProjectCdnViewerCache_stripsAmpJsParam() {
        val link = sanitize("https://example-com.cdn.ampproject.org/v/s/example.com/post?amp_js_v=0.1&article_id=99")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://example.com/post?article_id=99", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.AMPROJECT_CDN, result.source)
    }

    @Test
    fun deAmp_cloudflareAmpCache_unwrapsSuccessfully() {
        val link = sanitize("https://example-com.amp.cloudflare.com/c/s/example.com/article")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://example.com/article", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.CLOUDFLARE_CACHE, result.source)
    }

    @Test
    fun deAmp_publisherSubdomain_stripsAmpPrefix() {
        val link = sanitize("https://amp.reddit.com/r/android/comments/12345/discussion")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://reddit.com/r/android/comments/12345/discussion", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.PUBLISHER_SUBDOMAIN, result.source)
    }

    @Test
    fun deAmp_publisherSubdomainWithQuery_preservesQueryParameters() {
        val link = sanitize("https://amp.theguardian.com/world/news?page=2")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://theguardian.com/world/news?page=2", result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_publisherPathSuffix_removesAmpSegment() {
        val linkWithSlash = sanitize("https://example.com/2026/01/article/amp/")
        val resultWithSlash = DeAmpEngine.deAmp(linkWithSlash)
        assertTrue(resultWithSlash.wasDeAmped)
        assertEquals("https://example.com/2026/01/article/", resultWithSlash.deAmpedLink.rawUrl)

        val linkNoSlash = sanitize("https://example.com/2026/01/article/amp")
        val resultNoSlash = DeAmpEngine.deAmp(linkNoSlash)
        assertTrue(resultNoSlash.wasDeAmped)
        assertEquals("https://example.com/2026/01/article", resultNoSlash.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_standaloneAmpQueryParams_strippedWhilePreservingFunctionalParams() {
        val link = sanitize("https://example.com/article?amp=1&id=42&outputType=amp")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://example.com/article?id=42", result.deAmpedLink.rawUrl)
        assertEquals(AmpSource.QUERY_PARAM, result.source)
    }

    @Test
    fun deAmp_preservesFragmentAnchor() {
        val link = sanitize("https://www.google.com/amp/s/example.com/doc#section-two")
        val result = DeAmpEngine.deAmp(link)

        assertTrue(result.wasDeAmped)
        assertEquals("https://example.com/doc#section-two", result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_standardNonAmpLink_isIdempotent() {
        val normalLink = sanitize("https://example.com/blog/my-favorite-tools?id=123")
        val result = DeAmpEngine.deAmp(normalLink)

        assertFalse(result.wasDeAmped)
        assertEquals(normalLink.rawUrl, result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_domainContainingAmpWord_isNotModified() {
        val link = sanitize("https://amplified.com/technology/audio")
        val result = DeAmpEngine.deAmp(link)

        assertFalse(result.wasDeAmped)
        assertEquals(link.rawUrl, result.deAmpedLink.rawUrl)
    }

    @Test
    fun deAmp_twoSegmentAmpHost_isNotTreatedAsSubdomain() {
        val link = sanitize("https://amp.dev/documentation")
        val result = DeAmpEngine.deAmp(link)

        assertFalse(result.wasDeAmped)
        assertEquals(link.rawUrl, result.deAmpedLink.rawUrl)
    }
}
