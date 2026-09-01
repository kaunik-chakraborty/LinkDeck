package com.linkdeck.android.core.cleaner

import com.linkdeck.android.core.model.SanitizedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingParameterCleanerTest {

    @Test
    fun clean_allKnownTrackingParameters_removed() {
        val params = listOf(
            "utm_source=twitter",
            "utm_medium=social",
            "utm_campaign=spring_sale",
            "utm_term=android",
            "utm_content=logolink",
            "utm_id=12345",
            "fbclid=IwAR0abc",
            "gclid=CjwKCAiA",
            "dclid=CPT123",
            "msclkid=98765",
            "twclid=12345",
            "mc_eid=abcde",
            "igshid=xyz123",
            "_hsenc=p2ANqtz",
            "_hsmi=123456"
        )

        for (param in params) {
            val link = SanitizedLink(
                rawUrl = "https://example.com/product?$param&id=99",
                scheme = "https",
                host = "example.com",
                path = "/product"
            )
            val result = TrackingParameterCleaner.clean(link)
            assertTrue("Expected param $param to be removed", result.hasRemovedParams)
            assertEquals("https://example.com/product?id=99", result.cleanedLink.rawUrl)
        }
    }

    @Test
    fun clean_caseInsensitiveKey_removedWithoutModifyingValues() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/view?UTM_SOURCE=Twitter&ID=AbCdE",
            scheme = "https",
            host = "example.com",
            path = "/view"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://example.com/view?ID=AbCdE", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_preservesFunctionalParameters() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/search?q=kotlin&page=2&id=10&v=dQw4w9WgXcQ&ref=affiliate_1",
            scheme = "https",
            host = "example.com",
            path = "/search"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertFalse(result.hasRemovedParams)
        assertEquals(link.rawUrl, result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_preservesParameterOrdering() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/?first=1&utm_source=twitter&second=2&fbclid=123&third=3",
            scheme = "https",
            host = "example.com",
            path = "/"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://example.com/?first=1&second=2&third=3", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_allTrackingParameters_removesTrailingQuestionMark() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/path?utm_source=newsletter&utm_medium=email",
            scheme = "https",
            host = "example.com",
            path = "/path"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://example.com/path", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_duplicateTrackingParameters_removesAllOccurrences() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/?utm_source=a&utm_source=b&id=42",
            scheme = "https",
            host = "example.com",
            path = "/"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals(2, result.removedParams.size)
        assertEquals("https://example.com/?id=42", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_duplicateLegitimateParameters_preservesAll() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/?id=1&id=2&utm_source=test",
            scheme = "https",
            host = "example.com",
            path = "/"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://example.com/?id=1&id=2", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_preservesFragment() {
        val link = SanitizedLink(
            rawUrl = "https://youtube.com/watch?v=123&utm_source=share#t=1m30s",
            scheme = "https",
            host = "youtube.com",
            path = "/watch"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://youtube.com/watch?v=123#t=1m30s", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_preservesPercentEncodingWithoutDoubleEncoding() {
        val link = SanitizedLink(
            rawUrl = "https://example.com/search?q=a%20b&filter=%2F&utm_source=twitter",
            scheme = "https",
            host = "example.com",
            path = "/search"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://example.com/search?q=a%20b&filter=%2F", result.cleanedLink.rawUrl)
    }

    @Test
    fun clean_complexEcommercePpcUrl_completelyStripped() {
        val raw = "https://store.example.com/c/nutrition/best-sellers/?affil=sampleppc&kwds=123456&thg_ppc_campaign=11111111111&adtype=&product_id=&cq_src=google_ads&cq_cmp=11111111111&cq_con=222222222222&cq_term=sample%20coupon%20code&cq_med=&cq_plac=&cq_net=g&cq_plt=gp&gclsrc=aw.ds&gad_source=1&gad_campaignid=11111111111&gbraid=OAAAAADsamplemocktoken"
        val link = SanitizedLink(
            rawUrl = raw,
            scheme = "https",
            host = "store.example.com",
            path = "/c/nutrition/best-sellers/"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://store.example.com/c/nutrition/best-sellers/", result.cleanedLink.rawUrl)
        assertTrue(result.removedParams.contains("gbraid"))
        assertTrue(result.removedParams.contains("gad_source"))
        assertTrue(result.removedParams.contains("thg_ppc_campaign"))
        assertTrue(result.removedParams.contains("cq_src"))
    }

    @Test
    fun clean_instagramReelWithIgsi_completelyStripped() {
        val raw = "https://www.instagram.com/reel/C0000000000/?igsi=mock_share_token_12345"
        val link = SanitizedLink(
            rawUrl = raw,
            scheme = "https",
            host = "www.instagram.com",
            path = "/reel/C0000000000/"
        )
        val result = TrackingParameterCleaner.clean(link)

        assertTrue(result.hasRemovedParams)
        assertEquals("https://www.instagram.com/reel/C0000000000/", result.cleanedLink.rawUrl)
        assertTrue(result.removedParams.contains("igsi"))
    }
}
