package com.linkdeck.android.core.security

import com.linkdeck.android.core.model.SanitizedLink
import java.net.IDN
import java.net.URI
import java.util.regex.Pattern

/**
 * 100% on-device heuristic analyzer that scans web links for phishing, deception,
 * transport insecurity, and abusive redirect patterns with zero network overhead.
 */
object LinkThreatAnalyzer {

    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$"
    )

    private val IPV6_PATTERN = Pattern.compile(
        "^\\[?[0-9a-fA-F:]+\\]?$"
    )

    /**
     * Analyzes a sanitized link and redirect context for on-device security threats.
     */
    fun analyze(
        link: SanitizedLink,
        redirectHopCount: Int = 0
    ): List<LinkThreatWarning> {
        val warnings = mutableListOf<LinkThreatWarning>()

        // 1. Punycode / IDN Homoglyph Spoofing Detection
        val host = link.host.trim().lowercase()
        if (host.contains("xn--")) {
            try {
                val unicodeHost = IDN.toUnicode(host)
                warnings.add(LinkThreatWarning.PunycodePhishing(asciiHost = host, unicodeHost = unicodeHost))
            } catch (_: Exception) {
                warnings.add(LinkThreatWarning.PunycodePhishing(asciiHost = host, unicodeHost = host))
            }
        }

        // 2. Embedded UserInfo Deception Detection (e.g. https://google.com@attacker.com)
        try {
            val parsedUri = URI(link.rawUrl)
            val userInfo = parsedUri.rawUserInfo
            if (!userInfo.isNullOrBlank()) {
                warnings.add(
                    LinkThreatWarning.UserInfoDeception(
                        deceptivePrefix = userInfo,
                        actualHost = link.host
                    )
                )
            }
        } catch (_: Exception) {
            // If URI parsing fails, check manually for userinfo authority spoofing
            val authorityPart = link.rawUrl.substringAfter("://").substringBefore('/')
            if (authorityPart.contains('@')) {
                val userPart = authorityPart.substringBeforeLast('@')
                warnings.add(
                    LinkThreatWarning.UserInfoDeception(
                        deceptivePrefix = userPart,
                        actualHost = link.host
                    )
                )
            }
        }

        // 3. Raw IP Address Hostname Detection
        val cleanHost = host.removeSurrounding("[", "]")
        if (IPV4_PATTERN.matcher(cleanHost).matches() || (cleanHost.contains(':') && IPV6_PATTERN.matcher(cleanHost).matches())) {
            warnings.add(LinkThreatWarning.RawIpHost(ipAddress = host))
        }

        // 4. Cleartext HTTP Warning
        if (link.scheme.equals("http", ignoreCase = true)) {
            warnings.add(LinkThreatWarning.CleartextHttp)
        }

        // 5. Excessive Redirect Hops Warning
        if (redirectHopCount >= 4) {
            warnings.add(LinkThreatWarning.ExcessiveRedirects(hopCount = redirectHopCount))
        }

        return warnings
    }
}
