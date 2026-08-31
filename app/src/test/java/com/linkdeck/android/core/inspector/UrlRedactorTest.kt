package com.linkdeck.android.core.inspector

import com.linkdeck.android.core.model.SanitizedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRedactorTest {

    @Test
    fun redact_sensitiveQueryParameters_redacted() {
        val sensitiveKeys = listOf(
            "token",
            "code",
            "password",
            "pass",
            "session",
            "auth",
            "key",
            "secret",
            "access_token",
            "refresh_token",
            "id_token",
            "api_key",
            "apikey",
            "jwt",
            "state",
            "sig",
            "signature",
            "client_secret",
            "nonce"
        )

        for (key in sensitiveKeys) {
            val link = SanitizedLink(
                rawUrl = "https://example.com/oauth?$key=SUPER_SECRET_VALUE_123&id=42",
                scheme = "https",
                host = "example.com",
                path = "/oauth"
            )
            val redacted = UrlRedactor.redact(link)
            assertTrue("Expected key $key to be redacted in: $redacted", redacted.contains("$key=[redacted]"))
            assertTrue("Expected non-sensitive param 'id' to be preserved in: $redacted", redacted.contains("id=42"))
        }
    }

    @Test
    fun redact_caseInsensitiveSensitiveKey_redactedPreservingCase() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/login?TOKEN=Secret123&SESSION=abc456&user_id=99",
            scheme = "https",
            host = "example.com",
            path = "/login"
        )
        val redacted = UrlRedactor.redact(link)
        assertTrue(redacted.contains("TOKEN=[redacted]"))
        assertTrue(redacted.contains("SESSION=[redacted]"))
        assertTrue(redacted.contains("user_id=99"))
    }

    @Test
    fun redact_substringMatchesNotRedacted() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/search?my_token=123&tokenizer=abc&not_secret=456&id=99",
            scheme = "https",
            host = "example.com",
            path = "/search"
        )
        val redacted = UrlRedactor.redact(link)
        assertTrue(redacted.contains("my_token=123"))
        assertTrue(redacted.contains("tokenizer=abc"))
        assertTrue(redacted.contains("not_secret=456"))
        assertTrue(redacted.contains("id=99"))
        assertFalse(redacted.contains("[redacted]"))
    }

    @Test
    fun redact_duplicateSensitiveParameters_allRedactedInOrder() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/auth?token=first_secret&id=1&token=second_secret&id=2",
            scheme = "https",
            host = "example.com",
            path = "/auth"
        )
        val redacted = UrlRedactor.redact(link)
        assertEquals("https://example.com/auth?token=[redacted]&id=1&token=[redacted]&id=2", redacted)
    }

    @Test
    fun redact_sensitiveParametersWithEncoding_redactedSafely() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/view?token=abc%2F123%3D%3D&id=42",
            scheme = "https",
            host = "example.com",
            path = "/view"
        )
        val redacted = UrlRedactor.redact(link)
        assertEquals("https://example.com/view?token=[redacted]&id=42", redacted)
    }

    @Test
    fun redact_fragmentWithSensitiveOAuthTokens_redacted() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/oauth/callback#access_token=SECRET_JWT_TOKEN&token_type=bearer&state=STATE_123",
            scheme = "https",
            host = "example.com",
            path = "/oauth/callback"
        )
        val redacted = UrlRedactor.redact(link)
        assertTrue(redacted.contains("access_token=[redacted]"))
        assertTrue(redacted.contains("token_type=bearer"))
        assertTrue(redacted.contains("state=[redacted]"))
    }

    @Test
    fun redact_fragmentStandardAnchor_preserved() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/docs#section-installation",
            scheme = "https",
            host = "example.com",
            path = "/docs"
        )
        val redacted = UrlRedactor.redact(link)
        assertEquals("https://example.com/docs#section-installation", redacted)
    }

    @Test
    fun redact_preservesNonSensitiveParameters() {
        val link = SanitizedLink(
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ&t=42s&ref=share",
            scheme = "https",
            host = "youtube.com",
            path = "/watch"
        )
        val redacted = UrlRedactor.redact(link)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ&t=42s&ref=share", redacted)
    }

    @Test
    fun redact_stripsUserInfo() {
        val rawUrl = "https://admin:password123@example.com/dashboard?id=1"
        val redacted = UrlRedactor.redactUrlString(rawUrl)
        assertEquals("https://example.com/dashboard?id=1", redacted)
    }

    @Test
    fun truncateForDisplay_shortText_returnedUnmodified() {
        val text = "https://example.com/shorts"
        assertEquals(text, UrlRedactor.truncateForDisplay(text, maxLength = 50))
    }

    @Test
    fun truncateForDisplay_longText_truncatedWithEllipsis() {
        val text = "https://example.com/very/long/path/that/exceeds/the/maximum/allowed/display/length/for/a/clean/mobile/user/interface"
        val truncated = UrlRedactor.truncateForDisplay(text, maxLength = 40)
        assertEquals(40, truncated.length)
        assertTrue(truncated.contains("…"))
    }
}
