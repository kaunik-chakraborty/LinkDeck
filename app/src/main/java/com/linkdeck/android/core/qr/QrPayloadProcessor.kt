package com.linkdeck.android.core.qr

import com.linkdeck.android.core.deamp.DeAmpEngine
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.security.LinkThreatAnalyzer

/**
 * High-performance processor that parses, sanitizes, de-AMPs, and assesses
 * threat heuristics on raw QR code payloads.
 */
object QrPayloadProcessor {

    /**
     * Processes raw scanned QR content and evaluates whether it is a web URL
     * or non-URL plain text, applying LinkDeck's sanitization pipeline.
     */
    fun process(rawPayload: String?, deAmpEnabled: Boolean = true): QrScanResult {
        val trimmed = rawPayload?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return QrScanResult.PlainText("")
        }

        val sanitization = IntentSanitizer.sanitizeUrl(trimmed)
        return if (sanitization is SanitizationResult.Success) {
            val initialLink = sanitization.link
            val deAmpResult = if (deAmpEnabled) {
                DeAmpEngine.deAmp(initialLink)
            } else {
                null
            }

            val finalSanitized = if (deAmpResult?.wasDeAmped == true) {
                deAmpResult.deAmpedLink
            } else {
                initialLink
            }

            val deAmpedUrl = if (deAmpResult?.wasDeAmped == true) {
                deAmpResult.deAmpedLink.rawUrl
            } else {
                null
            }

            val warnings = LinkThreatAnalyzer.analyze(finalSanitized)
            QrScanResult.WebLink(
                rawPayload = trimmed,
                sanitizedLink = finalSanitized,
                deAmpedUrl = deAmpedUrl,
                threatWarnings = warnings
            )
        } else {
            QrScanResult.PlainText(trimmed)
        }
    }
}
