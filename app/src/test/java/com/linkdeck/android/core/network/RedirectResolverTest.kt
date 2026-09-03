package com.linkdeck.android.core.network

import com.linkdeck.android.core.model.SanitizedLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class RedirectResolverTest {

    private class FakeHttpTransport : HttpTransport {
        val routes = mutableMapOf<String, HttpResponse>()
        val dnsMap = mutableMapOf<String, List<InetAddress>>()

        fun registerRoute(url: String, statusCode: Int, location: String? = null) {
            routes[url] = HttpResponse(
                statusCode = statusCode,
                headers = location?.let { mapOf("Location" to listOf(it)) } ?: emptyMap(),
                locationHeader = location
            )
        }

        fun registerDns(host: String, addresses: List<InetAddress>) {
            dnsMap[host] = addresses
        }

        override fun resolveDns(host: String): List<InetAddress> {
            if (host == "dns-error.com") {
                throw UnknownHostException("Host not found: $host")
            }
            return dnsMap[host] ?: listOf(InetAddress.getByName("93.184.216.34"))
        }

        override fun requestHop(
            url: String,
            method: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int
        ): HttpResponse {
            return routes[url] ?: HttpResponse(200, emptyMap(), null)
        }
    }

    @Test
    fun resolve_singleHop301_returnsSuccessWithDestination() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://bit.ly/test", 301, "https://youtube.com/watch?v=123")
            registerRoute("https://youtube.com/watch?v=123", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://bit.ly/test", "bit.ly")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        val success = result as RedirectResult.Success
        assertEquals("https://bit.ly/test", success.originalUrl)
        assertEquals("https://youtube.com/watch?v=123", success.finalUrl)
        assertEquals(1, success.hops.size)
        assertEquals(301, success.hops[0].statusCode)
    }

    @Test
    fun resolve_multiHop302To307_returnsSuccess() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://t.co/abc", 302, "https://link.tracking.com/hop1")
            registerRoute("https://link.tracking.com/hop1", 307, "https://amazon.in/dp/B001")
            registerRoute("https://amazon.in/dp/B001", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://t.co/abc", "t.co")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        val success = result as RedirectResult.Success
        assertEquals("https://amazon.in/dp/B001", success.finalUrl)
        assertEquals(2, success.hops.size)
    }

    @Test
    fun resolve_relativeLocationHeader_resolvesAgainstCurrentUrl() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/short/link", 302, "/watch?v=abc")
            registerRoute("https://example.com/watch?v=abc", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/short/link", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        assertEquals("https://example.com/watch?v=abc", (result as RedirectResult.Success).finalUrl)
    }

    @Test
    fun resolve_schemeRelativeLocationHeader_resolvesWithScheme() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/start", 302, "//target.example.org/destination")
            registerRoute("https://target.example.org/destination", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/start", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        assertEquals("https://target.example.org/destination", (result as RedirectResult.Success).finalUrl)
    }

    @Test
    fun resolve_queryOnlyLocationHeader_resolvesAgainstCurrentUrl() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/items/", 302, "?page=2&sort=asc")
            registerRoute("https://example.com/items/?page=2&sort=asc", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/items/", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        assertEquals("https://example.com/items/?page=2&sort=asc", (result as RedirectResult.Success).finalUrl)
    }

    @Test
    fun resolve_httpsToHttp_followsSafelyWhenTargetIsPublic() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/secure-start", 302, "http://legacy.example.org/page")
            registerRoute("http://legacy.example.org/page", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/secure-start", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Success)
        assertEquals("http://legacy.example.org/page", (result as RedirectResult.Success).finalUrl)
    }

    @Test
    fun resolve_initial200Ok_returnsNoRedirect() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/direct", 200)
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/direct", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.NoRedirect)
        assertEquals("https://example.com/direct", (result as RedirectResult.NoRedirect).url)
    }

    @Test
    fun resolve_redirectCycle_returnsRedirectLoopError() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/a", 302, "https://example.com/b")
            registerRoute("https://example.com/b", 302, "https://example.com/a")
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/a", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.REDIRECT_LOOP, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_exceedsMaxHops_returnsTooManyRedirectsError() = runTest {
        val fake = FakeHttpTransport().apply {
            for (i in 1..10) {
                registerRoute("https://example.com/hop$i", 302, "https://example.com/hop${i + 1}")
            }
        }
        val resolver = RedirectResolver(transport = fake, maxHops = 8)
        val link = createSanitizedLink("https://example.com/hop1", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.TOO_MANY_REDIRECTS, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_unsupportedSchemeRedirect_returnsUnsupportedSchemeError() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/applink", 302, "intent://launch#Intent;scheme=app;end")
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/applink", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.UNSUPPORTED_SCHEME, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_ssrfPrivateIp_returnsBlockedPrivateAddressError() = runTest {
        val fake = FakeHttpTransport().apply {
            registerDns("private.internal", listOf(InetAddress.getByName("192.168.1.1")))
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://private.internal/secret", "private.internal")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.BLOCKED_PRIVATE_ADDRESS, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_multiHomedMixedDns_failsClosedWithBlockedPrivateAddressError() = runTest {
        val fake = FakeHttpTransport().apply {
            registerDns(
                "mixed.example.com",
                listOf(
                    InetAddress.getByName("93.184.216.34"),
                    InetAddress.getByName("10.0.0.1")
                )
            )
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://mixed.example.com/data", "mixed.example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.BLOCKED_PRIVATE_ADDRESS, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_credentialsInRedirectLocation_returnsRejectedCredentialsError() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://example.com/login-redirect", 302, "https://admin:secret@target.com/dashboard")
        }
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://example.com/login-redirect", "example.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.REJECTED_CREDENTIALS, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_dnsFailure_returnsDnsFailureError() = runTest {
        val fake = FakeHttpTransport()
        val resolver = RedirectResolver(transport = fake)
        val link = createSanitizedLink("https://dns-error.com/path", "dns-error.com")

        val result = resolver.resolve(link)
        assertTrue(result is RedirectResult.Error)
        assertEquals(RedirectErrorType.DNS_FAILURE, (result as RedirectResult.Error).errorType)
    }

    @Test
    fun resolve_hopInterceptor_terminatesEarlyWhenIntercepted() = runTest {
        val fake = FakeHttpTransport().apply {
            registerRoute("https://tinyurl.com/amp-test", 301, "https://www.google.com/amp/s/en.wikipedia.org/wiki/Kotlin?utm_source=google")
        }
        val resolver = RedirectResolver(
            transport = fake,
            hopInterceptor = { url ->
                if (url.contains("/amp/s/")) "https://en.wikipedia.org/wiki/Kotlin?utm_source=google" else null
            }
        )
        val link = createSanitizedLink("https://tinyurl.com/amp-test", "tinyurl.com")
        val result = resolver.resolve(link)

        assertTrue(result is RedirectResult.Success)
        val success = result as RedirectResult.Success
        assertEquals("https://en.wikipedia.org/wiki/Kotlin?utm_source=google", success.finalUrl)
        assertEquals(1, success.hops.size)
    }

    private fun createSanitizedLink(url: String, host: String): SanitizedLink {
        return SanitizedLink(
            rawUrl = url,
            scheme = if (url.startsWith("https")) "https" else "http",
            host = host,
            path = "",
            query = null
        )
    }
}
