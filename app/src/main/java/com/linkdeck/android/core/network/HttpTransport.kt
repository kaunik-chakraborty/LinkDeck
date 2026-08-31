package com.linkdeck.android.core.network

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Encapsulates the response metadata from a single HTTP redirect probe.
 */
data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val locationHeader: String?,
    val resolvedAddress: String? = null
)

/**
 * Abstraction over raw network calls and DNS resolution to ensure isolated,
 * deterministic unit testing without external network flakiness.
 */
interface HttpTransport {
    fun resolveDns(host: String): List<InetAddress>
    fun requestHop(
        url: String,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpResponse
}

/**
 * Default network transport leveraging standard Android/JDK [HttpURLConnection]
 * with automatic redirect following strictly disabled and zero body buffering.
 */
class DefaultHttpTransport : HttpTransport {

    override fun resolveDns(host: String): List<InetAddress> {
        return InetAddress.getAllByName(host).toList()
    }

    override fun requestHop(
        url: String,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpResponse {
        val targetUrl = URL(url)
        val connection = targetUrl.openConnection() as HttpURLConnection

        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = method
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Connection", "close")

            val statusCode = connection.responseCode
            val headerFields = connection.headerFields ?: emptyMap()
            val location = connection.getHeaderField("Location")

            // Close streams immediately without downloading response bodies
            try {
                connection.inputStream?.close()
            } catch (ignored: Exception) {
                try {
                    connection.errorStream?.close()
                } catch (ignored2: Exception) {}
            }

            HttpResponse(
                statusCode = statusCode,
                headers = headerFields,
                locationHeader = location
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT = "LinkDeck/1.0 (Android; On-Device Resolver)"
    }
}
