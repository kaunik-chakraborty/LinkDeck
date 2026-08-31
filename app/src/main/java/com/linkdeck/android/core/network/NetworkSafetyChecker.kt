package com.linkdeck.android.core.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Validates URLs and destination network addresses to defend against Server-Side Request
 * Forgery (SSRF), port scanning, credential leaks, and unsafe network destinations.
 */
object NetworkSafetyChecker {

    // Web traffic ports permitted for redirect resolution
    private val ALLOWED_PORTS = setOf(80, 443, 8080, 8443)

    /**
     * Inspects a parsed URI for embedded credentials (userinfo).
     */
    fun hasUserInfo(uri: URI): Boolean {
        if (!uri.userInfo.isNullOrBlank()) return true
        val rawAuthority = uri.rawAuthority ?: return false
        return rawAuthority.contains("@")
    }

    /**
     * Validates whether the URI's target port is an allowed web port.
     */
    fun isPortAllowed(uri: URI): Boolean {
        val port = uri.port
        if (port == -1) {
            // Default ports for HTTP (80) and HTTPS (443)
            return true
        }
        return ALLOWED_PORTS.contains(port)
    }

    /**
     * Evaluates a set of resolved [InetAddress] instances for a destination host.
     * All resolved addresses must be safe public destinations. If any address falls
     * within a private, loopback, link-local, multicast, or reserved range, fails closed.
     */
    fun areAddressesSafe(addresses: List<InetAddress>): Boolean {
        if (addresses.isEmpty()) return false
        return addresses.all { isAddressSafe(it) }
    }

    /**
     * Checks an individual IP address against loopback, private, link-local,
     * carrier-grade NAT, multicast, encapsulated IPv4, and reserved CIDR ranges.
     */
    fun isAddressSafe(address: InetAddress): Boolean {
        if (address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        val bytes = address.address

        return when (address) {
            is Inet4Address -> isIPv4Safe(bytes)
            is Inet6Address -> isIPv6Safe(bytes)
            else -> false
        }
    }

    private fun isIPv4Safe(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        // 0.0.0.0/8 (Current network / unspecified)
        if (b0 == 0) return false

        // 10.0.0.0/8 (Private-Use)
        if (b0 == 10) return false

        // 100.64.0.0/10 (Shared Address Space / CGNAT: 100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 in 64..127)) return false

        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) return false

        // 169.254.0.0/16 (Link-Local)
        if (b0 == 169 && b1 == 254) return false

        // 172.16.0.0/12 (Private-Use: 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 in 16..31)) return false

        // 192.0.0.0/24 (IETF Protocol Assignments)
        if (b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xFF == 0)) return false

        // 192.0.2.0/24 (TEST-NET-1)
        if (b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xFF == 2)) return false

        // 192.168.0.0/16 (Private-Use)
        if (b0 == 192 && b1 == 168) return false

        // 198.18.0.0/15 (Benchmarking: 198.18.0.0 - 198.19.255.255)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return false

        // 198.51.100.0/24 (TEST-NET-2)
        if (b0 == 198 && b1 == 51 && (bytes[2].toInt() and 0xFF == 100)) return false

        // 203.0.113.0/24 (TEST-NET-3)
        if (b0 == 203 && b1 == 0 && (bytes[2].toInt() and 0xFF == 113)) return false

        // 224.0.0.0/4 (Multicast: 224.0.0.0 - 239.255.255.255)
        if (b0 in 224..239) return false

        // 240.0.0.0/4 (Reserved / Future Use)
        if (b0 in 240..255) return false

        return true
    }

    private fun isIPv6Safe(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        // IPv4-mapped IPv6 (::ffff:w.x.y.z)
        val isIPv4Mapped = (0..9).all { bytes[it] == 0.toByte() } &&
                bytes[10] == 0xFF.toByte() &&
                bytes[11] == 0xFF.toByte()
        if (isIPv4Mapped) {
            val ipv4Bytes = ByteArray(4) { bytes[12 + it] }
            return isIPv4Safe(ipv4Bytes)
        }

        // IPv4-compatible IPv6 (::w.x.y.z)
        val isIPv4Compatible = (0..11).all { bytes[it] == 0.toByte() }
        if (isIPv4Compatible) {
            val ipv4Bytes = ByteArray(4) { bytes[12 + it] }
            return isIPv4Safe(ipv4Bytes)
        }

        // NAT64 Well-Known Prefix (64:ff9b::/96)
        val isNat64WellKnown = bytes[0] == 0x00.toByte() && bytes[1] == 0x64.toByte() &&
                bytes[2] == 0xFF.toByte() && bytes[3] == 0x9B.toByte() &&
                (4..11).all { bytes[it] == 0.toByte() }
        if (isNat64WellKnown) {
            val ipv4Bytes = ByteArray(4) { bytes[12 + it] }
            return isIPv4Safe(ipv4Bytes)
        }

        // 6to4 Prefix (2002::/16) - Embedded IPv4 at bytes 2..5
        if (b0 == 0x20 && b1 == 0x02) {
            val ipv4Bytes = ByteArray(4) { bytes[2 + it] }
            if (!isIPv4Safe(ipv4Bytes)) return false
        }

        // ::1 (Loopback) & :: (Unspecified)
        val isAllZeroExceptLast = (0..14).all { bytes[it] == 0.toByte() }
        if (isAllZeroExceptLast) {
            val last = bytes[15].toInt() and 0xFF
            if (last == 0 || last == 1) return false
        }

        // fc00::/7 (Unique Local Address: fc00::/8 and fd00::/8)
        if ((b0 and 0xFE) == 0xFC) return false

        // fe80::/10 (Link-Local Unicast)
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return false

        // ff00::/8 (Multicast)
        if (b0 == 0xFF) return false

        // 2001:db8::/32 (Documentation)
        if (b0 == 0x20 && b1 == 0x01 && (bytes[2].toInt() and 0xFF == 0x0D) && (bytes[3].toInt() and 0xFF == 0xB8)) {
            return false
        }

        return true
    }
}
