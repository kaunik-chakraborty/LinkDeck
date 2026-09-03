package com.linkdeck.android.core.deamp

/**
 * Categorizes the detected source or wrapper type of an Accelerated Mobile Pages (AMP) link.
 */
enum class AmpSource(val displayName: String) {
    GOOGLE_VIEWER("Google AMP Viewer"),
    GOOGLE_SEARCH_REDIRECT("Google Search Redirect"),
    AMPROJECT_CDN("AMP Cache CDN"),
    CLOUDFLARE_CACHE("Cloudflare AMP Cache"),
    BING_CACHE("Bing AMP Cache"),
    PUBLISHER_SUBDOMAIN("Publisher AMP Subdomain"),
    PATH_SUFFIX("Publisher Path Suffix"),
    QUERY_PARAM("AMP Query Parameter")
}
