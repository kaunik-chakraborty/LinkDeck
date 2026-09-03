package com.linkdeck.android.core.intent

import com.linkdeck.android.core.model.SanitizationError
import com.linkdeck.android.core.model.SanitizationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentSanitizerTest {

    @Test
    fun sanitizeUrl_validHttpsUrl_returnsSuccess() {
        val result = IntentSanitizer.sanitizeUrl("https://example.com/product/123?ref=test")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("https", success.link.scheme)
        assertEquals("example.com", success.link.host)
        assertEquals("/product/123", success.link.path)
        assertEquals("ref=test", success.link.query)
    }

    @Test
    fun sanitizeUrl_missingScheme_autoPrependsHttps() {
        val result = IntentSanitizer.sanitizeUrl("youtube.com/watch?v=123")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("https", success.link.scheme)
        assertEquals("youtube.com", success.link.host)
        assertEquals("/watch", success.link.path)
        assertEquals("v=123", success.link.query)
        assertEquals("https://youtube.com/watch?v=123", success.link.rawUrl)
    }

    @Test
    fun sanitizeUrl_domainOnly_autoPrependsHttps() {
        val result = IntentSanitizer.sanitizeUrl("gymscaleup.com")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("https", success.link.scheme)
        assertEquals("gymscaleup.com", success.link.host)
    }

    @Test
    fun sanitizeUrl_uppercaseSchemeAndHost_normalizesToLowercase() {
        val result = IntentSanitizer.sanitizeUrl("HTTPS://YOUTUBE.COM/WATCH?V=123")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("https", success.link.scheme)
        assertEquals("youtube.com", success.link.host)
    }

    @Test
    fun sanitizeUrl_trailingDotInHost_normalizesHost() {
        val result = IntentSanitizer.sanitizeUrl("https://example.com./path")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("example.com", success.link.host)
    }

    @Test
    fun sanitizeUrl_urlWithUserInfo_parsesHostCorrectly() {
        val result = IntentSanitizer.sanitizeUrl("https://user:pass@example.com:8080/dashboard")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("example.com", success.link.host)
    }

    @Test
    fun sanitizeUrl_unencodedQueryParams_handledGracefully() {
        val result = IntentSanitizer.sanitizeUrl("https://example.com/search?q=hello world&category=all")
        assertTrue(result is SanitizationResult.Success)
        val success = result as SanitizationResult.Success
        assertEquals("example.com", success.link.host)
    }

    @Test
    fun sanitizeUrl_ipAddresses_handledCorrectly() {
        val resultIpv4 = IntentSanitizer.sanitizeUrl("http://192.168.1.1:8080/setup")
        assertTrue(resultIpv4 is SanitizationResult.Success)
        assertEquals("192.168.1.1", (resultIpv4 as SanitizationResult.Success).link.host)
    }

    @Test
    fun sanitizeUrl_nullOrBlank_returnsMissingUriError() {
        val nullResult = IntentSanitizer.sanitizeUrl(null)
        assertTrue(nullResult is SanitizationResult.Error)
        assertEquals(SanitizationError.MISSING_URI, (nullResult as SanitizationResult.Error).error)

        val blankResult = IntentSanitizer.sanitizeUrl("   ")
        assertTrue(blankResult is SanitizationResult.Error)
        assertEquals(SanitizationError.MISSING_URI, (blankResult as SanitizationResult.Error).error)
    }

    @Test
    fun sanitizeUrl_unsupportedSchemes_rejected() {
        val schemes = listOf(
            "javascript:alert(1)",
            "file:///sdcard/test.txt",
            "content://com.example.provider/data",
            "intent://host#Intent;scheme=https;package=com.example;end",
            "market://details?id=com.example",
            "tel:+1234567890",
            "mailto:user@example.com",
            "data:text/html,<h1>Hello</h1>",
            "custom-app://open"
        )

        for (unsupported in schemes) {
            val result = IntentSanitizer.sanitizeUrl(unsupported)
            assertTrue("Expected failure for: $unsupported", result is SanitizationResult.Error)
            assertEquals(
                "Expected UNSUPPORTED_SCHEME for: $unsupported",
                SanitizationError.UNSUPPORTED_SCHEME,
                (result as SanitizationResult.Error).error
            )
        }
    }

    @Test
    fun sanitizeUrl_exceedsMaxLength_returnsExceedsMaxLengthError() {
        val longPath = "a".repeat(IntentSanitizer.MAX_URL_LENGTH + 10)
        val oversizedUrl = "https://example.com/$longPath"
        val result = IntentSanitizer.sanitizeUrl(oversizedUrl)

        assertTrue(result is SanitizationResult.Error)
        assertEquals(SanitizationError.EXCEEDS_MAX_LENGTH, (result as SanitizationResult.Error).error)
    }

    @Test
    fun sanitizeUrl_missingHost_returnsMissingHostError() {
        val result = IntentSanitizer.sanitizeUrl("https://")
        assertTrue(result is SanitizationResult.Error)
    }

    @Test
    fun extractUrlFromText_cleansTrailingPunctuation() {
        assertEquals(
            "https://flipkart.com/item/123",
            IntentSanitizer.extractUrlFromText("Check out this link: https://flipkart.com/item/123.")
        )
        assertEquals(
            "https://amazon.in/dp/123",
            IntentSanitizer.extractUrlFromText("(See product at https://amazon.in/dp/123)")
        )
        assertEquals(
            "https://github.com/torvalds/linux",
            IntentSanitizer.extractUrlFromText("Source: <https://github.com/torvalds/linux>, review now!")
        )
        assertNull(IntentSanitizer.extractUrlFromText("Just regular text without any link"))
    }

    @Test
    fun sanitizeUrl_plainWordWithoutDomain_returnsMissingHostError() {
        val result = IntentSanitizer.sanitizeUrl("hello")
        assertTrue("Expected error for 'hello'", result is SanitizationResult.Error)
        assertEquals(SanitizationError.MISSING_HOST, (result as SanitizationResult.Error).error)
    }
}
