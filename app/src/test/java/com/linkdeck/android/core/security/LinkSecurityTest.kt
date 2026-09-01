package com.linkdeck.android.core.security

import com.linkdeck.android.core.model.SanitizedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSecurityTest {

    private fun createLink(rawUrl: String, scheme: String, host: String, path: String = "/"): SanitizedLink {
        return SanitizedLink(
            rawUrl = rawUrl,
            scheme = scheme,
            host = host,
            path = path,
            query = null
        )
    }

    // Threat Heuristic Tests
    @Test
    fun punycodePhishing_detected() {
        val link = createLink("https://xn--pypal-4ve.com/login", "https", "xn--pypal-4ve.com", "/login")
        val threats = LinkThreatAnalyzer.analyze(link)

        assertTrue(threats.any { it is LinkThreatWarning.PunycodePhishing })
        val punycode = threats.filterIsInstance<LinkThreatWarning.PunycodePhishing>().first()
        assertEquals("xn--pypal-4ve.com", punycode.asciiHost)
    }

    @Test
    fun userInfoDeception_detected() {
        val link = createLink("https://google.com@attacker-site.com/auth", "https", "attacker-site.com", "/auth")
        val threats = LinkThreatAnalyzer.analyze(link)

        assertTrue(threats.any { it is LinkThreatWarning.UserInfoDeception })
        val userInfo = threats.filterIsInstance<LinkThreatWarning.UserInfoDeception>().first()
        assertEquals("google.com", userInfo.deceptivePrefix)
        assertEquals("attacker-site.com", userInfo.actualHost)
    }

    @Test
    fun rawIpHost_detected() {
        val ipv4Link = createLink("https://198.51.100.25/download", "https", "198.51.100.25", "/download")
        val threats = LinkThreatAnalyzer.analyze(ipv4Link)

        assertTrue(threats.any { it is LinkThreatWarning.RawIpHost })
        val ipThreat = threats.filterIsInstance<LinkThreatWarning.RawIpHost>().first()
        assertEquals("198.51.100.25", ipThreat.ipAddress)
    }

    @Test
    fun cleartextHttp_detected() {
        val httpLink = createLink("http://example.com/item", "http", "example.com", "/item")
        val threats = LinkThreatAnalyzer.analyze(httpLink)

        assertTrue(threats.any { it is LinkThreatWarning.CleartextHttp })
    }

    @Test
    fun excessiveRedirects_detected() {
        val link = createLink("https://example.com/final", "https", "example.com", "/final")
        val threats = LinkThreatAnalyzer.analyze(link, redirectHopCount = 5)

        assertTrue(threats.any { it is LinkThreatWarning.ExcessiveRedirects })
        val redirectThreat = threats.filterIsInstance<LinkThreatWarning.ExcessiveRedirects>().first()
        assertEquals(5, redirectThreat.hopCount)
    }

    @Test
    fun benignHttpsUrl_zeroThreats() {
        val safeLink = createLink("https://github.com/kaunik-chakraborty/LinkDeck", "https", "github.com", "/kaunik-chakraborty/LinkDeck")
        val threats = LinkThreatAnalyzer.analyze(safeLink, redirectHopCount = 0)

        assertTrue(threats.isEmpty())
    }

    // TLS Inspector Tests
    @Test
    fun tlsInspect_httpLink_returnsInsecureHttp() {
        val httpLink = createLink("http://example.com", "http", "example.com")
        val result = TlsCertificateInspector.inspect(httpLink)

        assertEquals(TlsInspectionResult.InsecureHttp, result)
    }

    @Test
    fun tlsInspect_emptyHost_returnsError() {
        val emptyHostLink = createLink("https://", "https", "")
        val result = TlsCertificateInspector.inspect(emptyHostLink)

        assertTrue(result is TlsInspectionResult.Error)
    }
}
