package com.linkdeck.android.core.inspector

import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectHop
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.preference.RoutingPreference
import com.linkdeck.android.core.rule.RoutingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkInspectionDataTest {

    @Test
    fun inspectionData_noRedirects_hasRedirectsFalse() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/direct",
            scheme = "https",
            host = "example.com",
            path = "/direct"
        )
        val data = LinkInspectionData(
            originalLink = link,
            effectiveDestination = link,
            redirectResult = RedirectResult.NoRedirect("https://example.com/direct")
        )

        assertFalse(data.hasRedirects)
        assertEquals(0, data.redirectCount)
        assertTrue(data.redirectHops.isEmpty())
    }

    @Test
    fun inspectionData_multipleRedirectHops_recordsAllHops() {
        val original = SanitizedLink("https://bit.ly/123", "https", "bit.ly", "/123")
        val destination = SanitizedLink("https://youtube.com/watch?v=abc", "https", "youtube.com", "/watch")

        val hop1 = RedirectHop("https://bit.ly/123", 301, "https://amzn.to/xyz", "https://amzn.to/xyz")
        val hop2 = RedirectHop("https://amzn.to/xyz", 302, "https://youtube.com/watch?v=abc", "https://youtube.com/watch?v=abc")

        val data = LinkInspectionData(
            originalLink = original,
            effectiveDestination = destination,
            redirectResult = RedirectResult.Success(
                originalUrl = original.rawUrl,
                finalUrl = destination.rawUrl,
                hops = listOf(hop1, hop2)
            ),
            wasCleaned = true,
            removedTrackingParams = listOf("utm_source", "fbclid"),
            routingExplanation = RoutingExplanation.MatchedRule(
                RoutingRule(
                    id = "rule_yt",
                    host = "youtube.com",
                    pathPattern = null,
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube"
                )
            )
        )

        assertTrue(data.hasRedirects)
        assertEquals(2, data.redirectCount)
        assertEquals("bit.ly", data.originalLink.host)
        assertEquals("youtube.com", data.effectiveDestination.host)
        assertTrue(data.wasCleaned)
        assertEquals(2, data.removedTrackingParams.size)
        assertTrue(data.routingExplanation is RoutingExplanation.MatchedRule)
    }

    @Test
    fun inspectionData_errorRedirect_preservesPartialHops() {
        val original = SanitizedLink("https://example.com/loop1", "https", "example.com", "/loop1")
        val hop1 = RedirectHop("https://example.com/loop1", 302, "https://example.com/loop2", "https://example.com/loop2")

        val data = LinkInspectionData(
            originalLink = original,
            effectiveDestination = original,
            redirectResult = RedirectResult.Error(
                originalUrl = original.rawUrl,
                lastUrl = "https://example.com/loop2",
                errorType = RedirectErrorType.REDIRECT_LOOP,
                message = "Redirect loop detected",
                hops = listOf(hop1)
            ),
            routingExplanation = RoutingExplanation.ManualChoice
        )

        assertTrue(data.hasRedirects)
        assertEquals(1, data.redirectCount)
        assertTrue(data.routingExplanation is RoutingExplanation.ManualChoice)
    }
}
