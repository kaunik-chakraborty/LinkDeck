package com.linkdeck.android.core.security

/**
 * Represents on-device security, phishing, and transport threats identified in a link.
 */
sealed interface LinkThreatWarning {
    val severity: Severity

    enum class Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Domain uses Internationalized Domain Name (Punycode / xn--) notation,
     * frequently utilized in homoglyph spoofing attacks (e.g. Cyrillic characters resembling Latin).
     */
    data class PunycodePhishing(
        val asciiHost: String,
        val unicodeHost: String
    ) : LinkThreatWarning {
        override val severity: Severity get() = Severity.CRITICAL
    }

    /**
     * URL contains deceptive userinfo credentials preceding '@' (e.g. https://google.com@attacker.com),
     * designed to visually mislead users regarding the actual destination host.
     */
    data class UserInfoDeception(
        val deceptivePrefix: String,
        val actualHost: String
    ) : LinkThreatWarning {
        override val severity: Severity get() = Severity.HIGH
    }

    /**
     * Link targets a raw IPv4 or IPv6 address rather than a registered domain name.
     */
    data class RawIpHost(
        val ipAddress: String
    ) : LinkThreatWarning {
        override val severity: Severity get() = Severity.MEDIUM
    }

    /**
     * Link uses unencrypted cleartext HTTP, exposing transport traffic and session credentials to eavesdropping.
     */
    data object CleartextHttp : LinkThreatWarning {
        override val severity: Severity get() = Severity.MEDIUM
    }

    /**
     * Redirect chain exceeds standard length, indicating potential ad affiliate chaining or cloaking.
     */
    data class ExcessiveRedirects(
        val hopCount: Int
    ) : LinkThreatWarning {
        override val severity: Severity get() = Severity.LOW
    }
}
