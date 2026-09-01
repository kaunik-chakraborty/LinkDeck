package com.linkdeck.android.core.security

import java.util.Date

/**
 * Immutable cryptographic representation of an inspected X.509 server TLS certificate.
 */
data class TlsCertificateInfo(
    val protocol: String,
    val cipherSuite: String,
    val subjectCn: String,
    val subjectOrg: String?,
    val issuerCn: String,
    val issuerOrg: String?,
    val validFrom: Date,
    val validTo: Date,
    val daysRemaining: Long,
    val isExpired: Boolean,
    val isExpiringSoon: Boolean,
    val serialNumberHex: String,
    val publicKeyAlgorithm: String,
    val publicKeySizeBits: Int,
    val signatureAlgorithm: String,
    val sha256Fingerprint: String,
    val subjectAlternativeNames: List<String>
)

/**
 * Sealed outcome of an on-demand TLS certificate inspection.
 */
sealed interface TlsInspectionResult {
    /**
     * Successfully established TLS handshake and parsed server certificates.
     */
    data class Success(val certInfo: TlsCertificateInfo) : TlsInspectionResult

    /**
     * Target link uses unencrypted plain HTTP; no TLS certificate exists.
     */
    data object InsecureHttp : TlsInspectionResult

    /**
     * Handshake completed or failed with certificate errors (e.g. expired or untrusted CA).
     */
    data class HandshakeFailed(
        val errorMessage: String,
        val partialCertInfo: TlsCertificateInfo? = null
    ) : TlsInspectionResult

    /**
     * TLS inspection is disabled in user settings.
     */
    data object Disabled : TlsInspectionResult

    /**
     * General network, timeout, or DNS resolution error.
     */
    data class Error(val message: String) : TlsInspectionResult
}
