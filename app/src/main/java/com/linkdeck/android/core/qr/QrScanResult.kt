package com.linkdeck.android.core.qr

import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.security.LinkThreatWarning

/**
 * Encapsulates the processed result of a scanned QR code payload.
 */
sealed class QrScanResult {
    abstract val rawPayload: String

    /**
     * Represents a scanned web URL with full on-device sanitization,
     * de-AMPing, and security threat heuristic warnings.
     */
    data class WebLink(
        override val rawPayload: String,
        val sanitizedLink: SanitizedLink,
        val deAmpedUrl: String?,
        val threatWarnings: List<LinkThreatWarning>
    ) : QrScanResult() {
        val targetUrl: String
            get() = deAmpedUrl ?: sanitizedLink.rawUrl
    }

    /**
     * Represents a non-URL plaintext payload (e.g., text notes, structured formats).
     */
    data class PlainText(
        override val rawPayload: String
    ) : QrScanResult()
}
