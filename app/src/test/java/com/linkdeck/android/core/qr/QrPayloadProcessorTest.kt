package com.linkdeck.android.core.qr

import com.linkdeck.android.core.security.LinkThreatWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadProcessorTest {

    @Test
    fun process_validCleanUrl_returnsWebLink() {
        val raw = "https://example.com/article?id=123"
        val result = QrPayloadProcessor.process(raw)

        assertTrue(result is QrScanResult.WebLink)
        val webLink = result as QrScanResult.WebLink
        assertEquals("https://example.com/article?id=123", webLink.targetUrl)
        assertTrue(webLink.threatWarnings.isEmpty())
    }

    @Test
    fun process_ampUrl_deAmpsUrlCorrectly() {
        val raw = "https://www.google.com/amp/s/en.wikipedia.org/wiki/Kotlin"
        val result = QrPayloadProcessor.process(raw, deAmpEnabled = true)

        assertTrue(result is QrScanResult.WebLink)
        val webLink = result as QrScanResult.WebLink
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", webLink.targetUrl)
        assertNotNull(webLink.deAmpedUrl)
    }

    @Test
    fun process_punycodeUrl_detectsThreat() {
        val raw = "https://xn--e1afmkfd.xn--p1ai/path"
        val result = QrPayloadProcessor.process(raw)

        assertTrue(result is QrScanResult.WebLink)
        val webLink = result as QrScanResult.WebLink
        assertTrue(webLink.threatWarnings.any { it is LinkThreatWarning.PunycodePhishing })
    }

    @Test
    fun process_plainTextNote_returnsPlainText() {
        val raw = "WIFI:S:MyNetwork;T:WPA;P:SecretPassword;;"
        val result = QrPayloadProcessor.process(raw)

        assertTrue(result is QrScanResult.PlainText)
        assertEquals(raw, result.rawPayload)
    }

    @Test
    fun process_emptyInput_returnsEmptyPlainText() {
        val result = QrPayloadProcessor.process("   ")
        assertTrue(result is QrScanResult.PlainText)
        assertEquals("", result.rawPayload)
    }
}
