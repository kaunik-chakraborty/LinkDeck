package com.linkdeck.android.core.network

/**
 * Metadata recorded for a single HTTP redirect transition.
 */
data class RedirectHop(
    val sourceUrl: String,
    val statusCode: Int,
    val location: String?,
    val targetUrl: String,
    val resolvedIp: String? = null
)

/**
 * Categorization of potential redirect resolution failures.
 */
enum class RedirectErrorType {
    TOO_MANY_REDIRECTS,
    REDIRECT_LOOP,
    INVALID_LOCATION,
    UNSUPPORTED_SCHEME,
    BLOCKED_PRIVATE_ADDRESS,
    BLOCKED_UNSAFE_PORT,
    REJECTED_CREDENTIALS,
    EXCEEDS_MAX_LENGTH,
    MALFORMED_URL,
    TIMEOUT,
    DNS_FAILURE,
    TLS_FAILURE,
    CONNECTION_FAILURE,
    CANCELLED,
    UNKNOWN_FAILURE
}

/**
 * Result outcome of an on-device bounded redirect resolution pipeline.
 */
sealed class RedirectResult {

    /**
     * Successfully resolved through one or more redirect hops to a final destination.
     */
    data class Success(
        val originalUrl: String,
        val finalUrl: String,
        val hops: List<RedirectHop>
    ) : RedirectResult()

    /**
     * The URL responded with a non-redirect HTTP status (e.g. 200 OK) on the initial hop.
     */
    data class NoRedirect(
        val url: String
    ) : RedirectResult()

    /**
     * Resolution stopped due to a bounded limit, security check, network error, or cancellation.
     */
    data class Error(
        val originalUrl: String,
        val lastUrl: String,
        val errorType: RedirectErrorType,
        val message: String,
        val hops: List<RedirectHop> = emptyList()
    ) : RedirectResult()
}
