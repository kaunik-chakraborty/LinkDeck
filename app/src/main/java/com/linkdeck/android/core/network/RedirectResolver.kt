package com.linkdeck.android.core.network

import com.linkdeck.android.core.model.SanitizedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Safely resolves HTTP/HTTPS redirects on-device to discover the final destination URL.
 *
 * Enforces:
 * 1. Bounded redirect depth (maximum 8 hops).
 * 2. Hard total and per-hop execution timeouts.
 * 3. Exhaustive SSRF and private IP blocking before socket creation.
 * 4. Redirect cycle detection.
 * 5. Strict HTTP/HTTPS scheme restrictions and userinfo credential rejection.
 * 6. Isolated network requests with zero body buffering, zero cookies, and zero telemetry.
 */
class RedirectResolver(
    private val transport: HttpTransport = DefaultHttpTransport(),
    private val maxHops: Int = MAX_HOPS,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS,
    private val hopInterceptor: ((String) -> String?)? = null
) {

    suspend fun resolve(sanitizedLink: SanitizedLink): RedirectResult = withContext(Dispatchers.IO) {
        val originalUrl = sanitizedLink.rawUrl
        var currentUrl = originalUrl
        val hops = mutableListOf<RedirectHop>()
        val visitedUrls = mutableSetOf<String>()
        val deadlineMs = System.currentTimeMillis() + totalTimeoutMs

        for (hopIndex in 0 until maxHops) {
            if (!isActive) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.CANCELLED,
                    message = "Redirect resolution cancelled",
                    hops = hops
                )
            }

            val remainingTimeMs = deadlineMs - System.currentTimeMillis()
            if (remainingTimeMs <= 0) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.TIMEOUT,
                    message = "Total resolution timeout exceeded",
                    hops = hops
                )
            }

            // Normalization strips terminal slashes and empty fragments to catch loops
            val normalizedForCycle = normalizeForCycle(currentUrl)
            if (!visitedUrls.add(normalizedForCycle)) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.REDIRECT_LOOP,
                    message = "Redirect cycle detected at $currentUrl",
                    hops = hops
                )
            }

            val uri = try {
                URI(currentUrl)
            } catch (e: Exception) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.MALFORMED_URL,
                    message = "Invalid URL format: ${e.message}",
                    hops = hops
                )
            }

            if (NetworkSafetyChecker.hasUserInfo(uri)) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.REJECTED_CREDENTIALS,
                    message = "URL contains embedded credentials",
                    hops = hops
                )
            }

            if (!NetworkSafetyChecker.isPortAllowed(uri)) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.BLOCKED_UNSAFE_PORT,
                    message = "Port ${uri.port} is not permitted for web resolution",
                    hops = hops
                )
            }

            val host = uri.host
            if (host.isNullOrBlank()) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.MALFORMED_URL,
                    message = "Missing host authority",
                    hops = hops
                )
            }

            val resolvedAddresses = try {
                transport.resolveDns(host)
            } catch (e: UnknownHostException) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.DNS_FAILURE,
                    message = "Could not resolve host: $host",
                    hops = hops
                )
            } catch (e: Exception) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.DNS_FAILURE,
                    message = "DNS error: ${e.message}",
                    hops = hops
                )
            }

            if (!NetworkSafetyChecker.areAddressesSafe(resolvedAddresses)) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.BLOCKED_PRIVATE_ADDRESS,
                    message = "Destination resolves to a private or reserved network address",
                    hops = hops
                )
            }

            val hopConnectTimeout = minOf(connectTimeoutMs, remainingTimeMs.toInt())
            val hopReadTimeout = minOf(readTimeoutMs, remainingTimeMs.toInt())

            val response = try {
                // Direct GET with immediate stream closure avoids HEAD 200 false-negatives
                transport.requestHop(currentUrl, "GET", hopConnectTimeout, hopReadTimeout)
            } catch (e: SocketTimeoutException) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.TIMEOUT,
                    message = "Connection or read timed out",
                    hops = hops
                )
            } catch (e: SSLException) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.TLS_FAILURE,
                    message = "TLS handshake failed: ${e.message}",
                    hops = hops
                )
            } catch (e: IOException) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.CONNECTION_FAILURE,
                    message = "Network connection failed: ${e.message}",
                    hops = hops
                )
            } catch (e: Exception) {
                return@withContext RedirectResult.Error(
                    originalUrl = originalUrl,
                    lastUrl = currentUrl,
                    errorType = RedirectErrorType.UNKNOWN_FAILURE,
                    message = "Unexpected failure: ${e.message}",
                    hops = hops
                )
            }

            if (isRedirectStatus(response.statusCode)) {
                val rawLocation = response.locationHeader
                if (rawLocation.isNullOrBlank()) {
                    return@withContext RedirectResult.Error(
                        originalUrl = originalUrl,
                        lastUrl = currentUrl,
                        errorType = RedirectErrorType.INVALID_LOCATION,
                        message = "Redirect status ${response.statusCode} missing Location header",
                        hops = hops
                    )
                }

                if (rawLocation.length > MAX_URL_LENGTH) {
                    return@withContext RedirectResult.Error(
                        originalUrl = originalUrl,
                        lastUrl = currentUrl,
                        errorType = RedirectErrorType.EXCEEDS_MAX_LENGTH,
                        message = "Redirect location exceeds maximum URL length",
                        hops = hops
                    )
                }

                // Resolves absolute, root-relative (/path), and relative (../item) headers safely
                val resolvedUri = try {
                    uri.resolve(rawLocation.trim())
                } catch (e: Exception) {
                    return@withContext RedirectResult.Error(
                        originalUrl = originalUrl,
                        lastUrl = currentUrl,
                        errorType = RedirectErrorType.INVALID_LOCATION,
                        message = "Malformed Location header: $rawLocation",
                        hops = hops
                    )
                }

                val nextScheme = resolvedUri.scheme?.lowercase(Locale.ROOT)
                if (nextScheme != "http" && nextScheme != "https") {
                    return@withContext RedirectResult.Error(
                        originalUrl = originalUrl,
                        lastUrl = currentUrl,
                        errorType = RedirectErrorType.UNSUPPORTED_SCHEME,
                        message = "Unsupported redirect scheme: ${resolvedUri.scheme}",
                        hops = hops
                    )
                }

                val nextUrl = resolvedUri.toASCIIString()
                val interceptedUrl = hopInterceptor?.invoke(nextUrl)
                if (interceptedUrl != null) {
                    hops.add(
                        RedirectHop(
                            sourceUrl = currentUrl,
                            statusCode = response.statusCode,
                            location = rawLocation,
                            targetUrl = interceptedUrl,
                            resolvedIp = resolvedAddresses.firstOrNull()?.hostAddress
                        )
                    )
                    return@withContext RedirectResult.Success(originalUrl, interceptedUrl, hops)
                }

                hops.add(
                    RedirectHop(
                        sourceUrl = currentUrl,
                        statusCode = response.statusCode,
                        location = rawLocation,
                        targetUrl = nextUrl,
                        resolvedIp = resolvedAddresses.firstOrNull()?.hostAddress
                    )
                )
                currentUrl = nextUrl
            } else {
                return@withContext if (hops.isEmpty()) {
                    RedirectResult.NoRedirect(currentUrl)
                } else {
                    RedirectResult.Success(originalUrl, currentUrl, hops)
                }
            }
        }

        RedirectResult.Error(
            originalUrl = originalUrl,
            lastUrl = currentUrl,
            errorType = RedirectErrorType.TOO_MANY_REDIRECTS,
            message = "Exceeded maximum limit of $maxHops redirects",
            hops = hops
        )
    }

    private fun isRedirectStatus(code: Int): Boolean {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308
    }

    private fun normalizeForCycle(url: String): String {
        return url.trim().removeSuffix("#").removeSuffix("/")
    }

    companion object {
        const val MAX_HOPS = 8
        const val DEFAULT_CONNECT_TIMEOUT_MS = 2500
        const val DEFAULT_READ_TIMEOUT_MS = 2500
        const val DEFAULT_TOTAL_TIMEOUT_MS = 5000L
        const val MAX_URL_LENGTH = 4096
    }
}
