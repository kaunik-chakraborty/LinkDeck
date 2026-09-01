package com.linkdeck.android.core.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * On-demand DNS CNAME chain analyzer that detects third-party tracking cloaking.
 * CNAME cloaking occurs when a website maps a first-party subdomain (e.g. metrics.example.com)
 * to a third-party tracking provider to circumvent browser cookie restrictions and adblockers.
 * Signatures are loaded dynamically from assets/cname_trackers.json with an in-memory fallback.
 */
object CnameCloakingDetector {

    private const val DOH_ENDPOINT = "https://dns.quad9.net/dns-query"
    private const val DOH_TIMEOUT_MS = 2500

    data class CloakedTrackerSignature(val domainSuffix: String, val trackerName: String, val category: String = "Tracking")

    private val dynamicSignatures = mutableListOf<CloakedTrackerSignature>()
    private val loadLock = Any()
    @Volatile
    private var isLoadedFromAssets = false

    // Comprehensive baseline fallback in case assets cannot be accessed (e.g. in pure JVM unit tests)
    private val BASELINE_TRACKERS = listOf(
        CloakedTrackerSignature("criteo.net", "Criteo Advertising", "Advertising"),
        CloakedTrackerSignature("criteo.com", "Criteo Advertising", "Advertising"),
        CloakedTrackerSignature("branch.io", "Branch Attribution", "Attribution"),
        CloakedTrackerSignature("app.link", "Branch Attribution", "Attribution"),
        CloakedTrackerSignature("omtrdc.net", "Adobe Audience Manager", "Analytics"),
        CloakedTrackerSignature("demdex.net", "Adobe Audience Manager", "Analytics"),
        CloakedTrackerSignature("eulerian.net", "Eulerian Analytics", "Analytics"),
        CloakedTrackerSignature("keywee.co", "Keywee Content Analytics", "Analytics"),
        CloakedTrackerSignature("wizaly.com", "Wizaly Attribution", "Attribution"),
        CloakedTrackerSignature("at-o.net", "AT Internet (Piano)", "Analytics"),
        CloakedTrackerSignature("wt-eu02.net", "Webtrekk Analytics", "Analytics"),
        CloakedTrackerSignature("segment.io", "Twilio Segment CDP", "Analytics"),
        CloakedTrackerSignature("pardot.com", "Salesforce Pardot", "Marketing"),
        CloakedTrackerSignature("adjust.com", "Adjust Mobile Attribution", "Attribution"),
        CloakedTrackerSignature("appsflyer.com", "AppsFlyer Attribution", "Attribution"),
        CloakedTrackerSignature("affise.com", "Affise Performance Marketing", "Affiliate"),
        CloakedTrackerSignature("kochava.com", "Kochava Attribution", "Attribution"),
        CloakedTrackerSignature("singular.net", "Singular Attribution", "Attribution"),
        CloakedTrackerSignature("tiqcdn.com", "Tealium iQ", "Tag Management"),
        CloakedTrackerSignature("eloqua.com", "Oracle Eloqua", "Marketing"),
        CloakedTrackerSignature("adsrvr.org", "The Trade Desk", "Advertising"),
        CloakedTrackerSignature("mathtag.com", "MediaMath", "Advertising"),
        CloakedTrackerSignature("chartbeat.net", "Chartbeat Analytics", "Analytics"),
        CloakedTrackerSignature("hubspot.com", "HubSpot Platform", "Marketing"),
        CloakedTrackerSignature("sjv.io", "Impact Partnership Tracking", "Affiliate")
    )

    sealed interface CnameResult {
        data class CloakingDetected(val cnameHost: String, val trackerName: String) : CnameResult
        data class Clean(val cnameHost: String) : CnameResult
        data object DirectResolution : CnameResult
        data class Error(val message: String) : CnameResult
    }

    /**
     * Initializes signature rules from application asset file (cname_trackers.json).
     */
    fun loadSignatures(context: Context) {
        if (isLoadedFromAssets) return
        synchronized(loadLock) {
            if (isLoadedFromAssets) return
            try {
                context.assets.open("cname_trackers.json").use { stream ->
                    val text = BufferedReader(InputStreamReader(stream)).readText()
                    val array = JSONArray(text)
                    val parsed = mutableListOf<CloakedTrackerSignature>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        parsed.add(
                            CloakedTrackerSignature(
                                domainSuffix = obj.getString("suffix").lowercase(),
                                trackerName = obj.getString("name"),
                                category = obj.optString("category", "Tracking")
                            )
                        )
                    }
                    dynamicSignatures.clear()
                    dynamicSignatures.addAll(parsed)
                    isLoadedFromAssets = true
                }
            } catch (_: Exception) {
                // Keep baseline fallback active
            }
        }
    }

    /**
     * Resolves the DNS CNAME record for the given domain and verifies it against known cloaking networks.
     */
    fun checkCname(host: String, context: Context? = null): CnameResult {
        context?.let { loadSignatures(it) }

        val cleanHost = host.trim().lowercase().substringBefore(':')
        if (cleanHost.isBlank() || cleanHost == "localhost") {
            return CnameResult.DirectResolution
        }

        try {
            val encodedHost = URLEncoder.encode(cleanHost, "UTF-8")
            val queryUrl = URL("$DOH_ENDPOINT?name=$encodedHost&type=CNAME")
            val connection = queryUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = DOH_TIMEOUT_MS
            connection.readTimeout = DOH_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/dns-json")
            connection.setRequestProperty("User-Agent", "LinkDeck/1.1 (Android Security Companion)")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return CnameResult.Error("DNS query HTTP ${connection.responseCode}")
            }

            val responseText = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }

            val json = JSONObject(responseText)
            val answers = json.optJSONArray("Answer")

            if (answers == null || answers.length() == 0) {
                return CnameResult.DirectResolution
            }

            var resolvedCname: String? = null
            for (i in 0 until answers.length()) {
                val answer = answers.getJSONObject(i)
                val type = answer.optInt("type", 0)
                // DNS type 5 corresponds to CNAME
                if (type == 5) {
                    val rawCname = answer.optString("data", "").trimEnd('.')
                    if (rawCname.isNotBlank()) {
                        resolvedCname = rawCname
                        break
                    }
                }
            }

            if (resolvedCname == null) {
                return CnameResult.DirectResolution
            }

            val matchedTracker = findCloakedTracker(resolvedCname)
            return if (matchedTracker != null) {
                CnameResult.CloakingDetected(cnameHost = resolvedCname, trackerName = matchedTracker.trackerName)
            } else {
                CnameResult.Clean(cnameHost = resolvedCname)
            }

        } catch (e: Exception) {
            return CnameResult.Error(e.localizedMessage ?: "DNS resolution timed out")
        }
    }

    /**
     * Determines whether a given canonical hostname belongs to a known cloaked tracker signature.
     */
    fun findCloakedTracker(cnameHost: String): CloakedTrackerSignature? {
        val lowerCname = cnameHost.lowercase()
        val activeSignatures = if (dynamicSignatures.isNotEmpty()) dynamicSignatures else BASELINE_TRACKERS
        return activeSignatures.firstOrNull { signature ->
            lowerCname == signature.domainSuffix || lowerCname.endsWith(".${signature.domainSuffix}")
        }
    }
}
