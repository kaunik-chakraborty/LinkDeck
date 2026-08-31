package com.linkdeck.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkExecutionContextTest {

    @Test
    fun urlFidelity_preservesExactParametersForLaunchingWhileRedactingForDisplay() {
        val originalUrl = "https://arena.ai/nextjs-api/callback/email?token=SUPER_SECRET_12345&type=email&redirect_to=%2Fleaderboard"
        val originalSanitized = SanitizedLink(
            rawUrl = originalUrl,
            scheme = "https",
            host = "arena.ai",
            path = "/nextjs-api/callback/email",
            query = "token=SUPER_SECRET_12345&type=email&redirect_to=%2Fleaderboard"
        )

        val context = LinkExecutionContext(
            originalSanitized = originalSanitized,
            resolvedDestination = originalSanitized,
            cleanedDestination = originalSanitized,
            wasRedirected = false,
            wasCleaned = false
        )

        // 1. Effective launch link retains full unredacted security token and parameters
        assertEquals(originalUrl, context.effectiveLaunchLink.rawUrl)
        assertEquals("SUPER_SECRET_12345&type=email&redirect_to=%2Fleaderboard", context.effectiveLaunchLink.query?.substringAfter("token="))
        assertFalse(context.effectiveLaunchLink.rawUrl.contains("[redacted]"))

        // 2. Presentation strings redact the token for on-screen shoulder-surfing protection
        assertTrue(context.displayRedactedEffectiveUrl.contains("token=[redacted]"))
        assertTrue(context.displayRedactedOriginalUrl.contains("token=[redacted]"))
        assertFalse(context.displayRedactedEffectiveUrl.contains("SUPER_SECRET_12345"))
    }

    @Test
    fun urlTransformations_distinguishesOriginalFromRedirectedAndCleaned() {
        val shortUrl = "https://fkrt.cc/hMGtAKJ"
        val originalSanitized = SanitizedLink(
            rawUrl = shortUrl,
            scheme = "https",
            host = "fkrt.cc",
            path = "/hMGtAKJ"
        )

        val resolvedUrl = "https://www.flipkart.com/item/123?utm_source=sms&utm_medium=affiliate&pid=ABC"
        val resolvedSanitized = SanitizedLink(
            rawUrl = resolvedUrl,
            scheme = "https",
            host = "www.flipkart.com",
            path = "/item/123",
            query = "utm_source=sms&utm_medium=affiliate&pid=ABC"
        )

        val cleanedUrl = "https://www.flipkart.com/item/123?pid=ABC"
        val cleanedSanitized = SanitizedLink(
            rawUrl = cleanedUrl,
            scheme = "https",
            host = "www.flipkart.com",
            path = "/item/123",
            query = "pid=ABC"
        )

        val context = LinkExecutionContext(
            originalSanitized = originalSanitized,
            resolvedDestination = resolvedSanitized,
            cleanedDestination = cleanedSanitized,
            wasRedirected = true,
            wasCleaned = true,
            removedTrackingParams = listOf("utm_source", "utm_medium")
        )

        assertTrue(context.hasTransformations)
        assertEquals(shortUrl, context.originalSanitized.rawUrl)
        assertEquals(cleanedUrl, context.effectiveLaunchLink.rawUrl)
        assertEquals(listOf("utm_source", "utm_medium"), context.removedTrackingParams)
    }
}
