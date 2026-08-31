package com.linkdeck.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.URI

class NetworkSafetyCheckerTest {

    @Test
    fun isAddressSafe_ipv4Loopback_returnsFalse() {
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("127.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("127.255.255.255")))
    }

    @Test
    fun isAddressSafe_ipv4PrivateRanges_returnsFalse() {
        // 10.0.0.0/8
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("10.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("10.255.255.255")))

        // 172.16.0.0/12
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("172.16.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("172.31.255.255")))

        // 192.168.0.0/16
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("192.168.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("192.168.1.100")))
    }

    @Test
    fun isAddressSafe_ipv4LinkLocalAndCgnat_returnsFalse() {
        // 169.254.0.0/16
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("169.254.1.1")))
        // 100.64.0.0/10
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("100.64.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("100.127.255.255")))
    }

    @Test
    fun isAddressSafe_ipv4MulticastAndReserved_returnsFalse() {
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("0.0.0.0")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("224.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("240.0.0.1")))
    }

    @Test
    fun isAddressSafe_ipv6LoopbackAndPrivate_returnsFalse() {
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("fc00::1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("fd00::1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("fe80::1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("ff02::1")))
    }

    @Test
    fun isAddressSafe_ipv4MappedAndCompatibleIPv6_returnsFalse() {
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::ffff:127.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::ffff:10.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::ffff:192.168.1.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::127.0.0.1")))
        assertFalse(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("::10.0.0.1")))
    }

    @Test
    fun isAddressSafe_publicAddresses_returnsTrue() {
        assertTrue(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("8.8.8.8")))
        assertTrue(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("1.1.1.1")))
        assertTrue(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("93.184.216.34")))
        assertTrue(NetworkSafetyChecker.isAddressSafe(InetAddress.getByName("2607:f8b0:4005:805::200e")))
    }

    @Test
    fun areAddressesSafe_multiHomedDns_failsClosedOnAnyPrivateAddress() {
        val safeOnly = listOf(
            InetAddress.getByName("93.184.216.34"),
            InetAddress.getByName("8.8.8.8")
        )
        assertTrue(NetworkSafetyChecker.areAddressesSafe(safeOnly))

        val mixedAddresses = listOf(
            InetAddress.getByName("93.184.216.34"),
            InetAddress.getByName("127.0.0.1")
        )
        assertFalse(NetworkSafetyChecker.areAddressesSafe(mixedAddresses))
    }

    @Test
    fun isPortAllowed_standardAndAllowedWebPorts_returnsTrue() {
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("https://example.com")))
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("http://example.com")))
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("http://example.com:80")))
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("https://example.com:443")))
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("http://example.com:8080")))
        assertTrue(NetworkSafetyChecker.isPortAllowed(URI("https://example.com:8443")))
    }

    @Test
    fun isPortAllowed_unsafePorts_returnsFalse() {
        val dangerousPorts = listOf(21, 22, 23, 25, 53, 445, 3306, 5432, 6379, 8081, 8444, 65535)
        for (port in dangerousPorts) {
            assertFalse(
                "Port $port should be blocked",
                NetworkSafetyChecker.isPortAllowed(URI("http://example.com:$port"))
            )
        }
    }

    @Test
    fun hasUserInfo_credentialsPresent_returnsTrue() {
        assertTrue(NetworkSafetyChecker.hasUserInfo(URI("https://admin:password@example.com")))
        assertTrue(NetworkSafetyChecker.hasUserInfo(URI("https://user@example.com/path")))
        assertFalse(NetworkSafetyChecker.hasUserInfo(URI("https://example.com/path?q=admin@email.com")))
    }
}
