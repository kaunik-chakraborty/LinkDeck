package com.linkdeck.android.core.security

import com.linkdeck.android.core.model.SanitizedLink
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * High-performance on-demand TLS/SSL certificate inspector.
 * Connects directly via cryptographic socket handshake without transmitting HTTP request data,
 * extracting transport cipher suites, certificate authorities, public key specifications, and fingerprints.
 */
object TlsCertificateInspector {

    private const val CONNECT_TIMEOUT_MS = 3500
    private const val READ_TIMEOUT_MS = 3500
    private const val DEFAULT_HTTPS_PORT = 443

    /**
     * Inspects the server TLS certificate for the given link.
     */
    fun inspect(link: SanitizedLink): TlsInspectionResult {
        if (link.scheme.equals("http", ignoreCase = true)) {
            return TlsInspectionResult.InsecureHttp
        }

        val host = link.host.substringBefore(':')
        if (host.isBlank()) {
            return TlsInspectionResult.Error("Invalid host destination")
        }

        val targetPort = try {
            val uri = java.net.URI(link.rawUrl)
            if (uri.port > 0) uri.port else DEFAULT_HTTPS_PORT
        } catch (_: Exception) {
            DEFAULT_HTTPS_PORT
        }

        // 1. First Attempt: Standard platform-validated TLS handshake
        try {
            val socketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            (socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.soTimeout = READ_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, targetPort), CONNECT_TIMEOUT_MS)
                socket.startHandshake()

                val session = socket.session
                val peerCerts = session.peerCertificates
                val leafCert = peerCerts.firstOrNull() as? X509Certificate
                    ?: return TlsInspectionResult.Error("No server certificate presented")

                val certInfo = parseX509Certificate(leafCert, session.protocol, session.cipherSuite)
                return TlsInspectionResult.Success(certInfo)
            }
        } catch (sslEx: SSLException) {
            // Handshake failed (e.g. untrusted CA, expired cert, or hostname mismatch).
            // Attempt diagnostic non-validating probe to extract certificate details so user can inspect WHY it failed.
            val diagnosticCert = probeDiagnosticCertificate(host, targetPort)
            val errorMessage = sslEx.localizedMessage ?: "TLS verification failed"
            return TlsInspectionResult.HandshakeFailed(
                errorMessage = errorMessage,
                partialCertInfo = diagnosticCert
            )
        } catch (e: Exception) {
            return TlsInspectionResult.Error(e.localizedMessage ?: "Failed to connect to host")
        }
    }

    /**
     * Diagnostic probe to extract certificate metadata even if the certificate is untrusted or expired.
     */
    private fun probeDiagnosticCertificate(host: String, port: Int = DEFAULT_HTTPS_PORT): TlsCertificateInfo? {
        return try {
            val unverifiedCerts = arrayOf<X509Certificate>()
            val permissiveTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    // Collect certificates without throwing validation errors
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = unverifiedCerts
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(permissiveTrustManager), null)

            (sslContext.socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.soTimeout = READ_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.startHandshake()

                val session = socket.session
                val peerCerts = session.peerCertificates
                val leaf = peerCerts.firstOrNull() as? X509Certificate ?: return null
                parseX509Certificate(leaf, session.protocol, session.cipherSuite)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses standard X.509 certificate attributes into our domain model.
     */
    fun parseX509Certificate(
        cert: X509Certificate,
        protocol: String,
        cipherSuite: String
    ): TlsCertificateInfo {
        val now = System.currentTimeMillis()
        val validFrom = cert.notBefore
        val validTo = cert.notAfter
        val isExpired = validTo.time < now
        val daysRemaining = if (isExpired) 0L else (validTo.time - now) / (1000 * 60 * 60 * 24)
        val isExpiringSoon = !isExpired && daysRemaining <= 14

        val subjectCn = extractAttribute(cert.subjectX500Principal.name, "CN") ?: cert.subjectX500Principal.name
        val subjectOrg = extractAttribute(cert.subjectX500Principal.name, "O")
        val issuerCn = extractAttribute(cert.issuerX500Principal.name, "CN") ?: cert.issuerX500Principal.name
        val issuerOrg = extractAttribute(cert.issuerX500Principal.name, "O")

        val (keyAlgo, keyBits) = extractPublicKeySpecs(cert)
        val sha256Fingerprint = computeSha256Fingerprint(cert.encoded)
        val serialHex = cert.serialNumber.toString(16).uppercase().chunked(2).joinToString(":")

        val sans = mutableListOf<String>()
        try {
            cert.subjectAlternativeNames?.forEach { item ->
                if (item.size >= 2) {
                    val sanValue = item[1]?.toString()
                    if (!sanValue.isNullOrBlank()) sans.add(sanValue)
                }
            }
        } catch (_: CertificateException) {}

        return TlsCertificateInfo(
            protocol = protocol,
            cipherSuite = cipherSuite,
            subjectCn = subjectCn,
            subjectOrg = subjectOrg,
            issuerCn = issuerCn,
            issuerOrg = issuerOrg,
            validFrom = validFrom,
            validTo = validTo,
            daysRemaining = daysRemaining,
            isExpired = isExpired,
            isExpiringSoon = isExpiringSoon,
            serialNumberHex = serialHex,
            publicKeyAlgorithm = keyAlgo,
            publicKeySizeBits = keyBits,
            signatureAlgorithm = cert.sigAlgName,
            sha256Fingerprint = sha256Fingerprint,
            subjectAlternativeNames = sans
        )
    }

    private fun extractPublicKeySpecs(cert: X509Certificate): Pair<String, Int> {
        val publicKey = cert.publicKey
        val algo = publicKey.algorithm
        val bits = when (publicKey) {
            is RSAPublicKey -> publicKey.modulus.bitLength()
            is ECPublicKey -> publicKey.params.order.bitLength()
            else -> 0
        }
        return Pair(algo, bits)
    }

    private fun extractAttribute(distinguishedName: String, attributeKey: String): String? {
        val pattern = "(?:^|,\\s*)$attributeKey=([^,]*)"
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val match = regex.find(distinguishedName)
        return match?.groupValues?.getOrNull(1)?.trim()?.removeSurrounding("\"")
    }

    private fun computeSha256Fingerprint(derBytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "N/A"
        }
    }
}
