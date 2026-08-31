package com.linkdeck.android.core.intent

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration-level tests validating the complete candidate discovery and routing pipeline.
 */
class CandidateDiscoveryIntegrationTest {

    private val selfPkg = "com.linkdeck.android"

    @Test
    fun gymscaleup_noDedicatedApp_returnsBrowsersAndShareTargets_neverEmpty() {
        val appResolver = AppResolver(packageManager = null, selfPackageName = selfPkg)
        val shareResolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val link = SanitizedLink(
            rawUrl = "https://gymscaleup.com",
            scheme = "https",
            host = "gymscaleup.com",
            path = "/"
        )

        // Mock installed applications: Chrome (browser) and WhatsApp (share target)
        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)
        val whatsappResolve = createResolveInfo("com.whatsapp", "com.whatsapp.ContactPicker", exported = true)

        val openTargets = appResolver.filterAndClassifyCandidates(
            candidates = listOf(chromeResolve),
            targetHost = link.host,
            genericBrowserPackages = setOf("com.android.chrome"),
            labelLoader = { "Chrome" }
        )

        val shareTargets = shareResolver.filterAndProcessTargets(
            resolves = listOf(whatsappResolve),
            labelLoader = { "WhatsApp" }
        )

        // Verifications:
        // 1. Browsers must be found (never "No apps found")
        assertEquals(1, openTargets.size)
        assertEquals(TargetCategory.BROWSER, openTargets[0].category)
        assertTrue(openTargets[0].isBrowser)
        assertFalse(openTargets[0].isNativeMatch)
        assertEquals("Chrome", openTargets[0].appLabel)

        // 2. Share targets must be found separately
        assertEquals(1, shareTargets.size)
        assertEquals("WhatsApp", shareTargets[0].appLabel)
    }

    @Test
    fun flipkart_dedicatedAppPresent_returnsDedicatedAppAndBrowsersAndShareTargets() {
        val appResolver = AppResolver(packageManager = null, selfPackageName = selfPkg)
        val shareResolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val link = SanitizedLink(
            rawUrl = "https://www.flipkart.com/item/123",
            scheme = "https",
            host = "www.flipkart.com",
            path = "/item/123"
        )

        val flipkartResolve = createResolveInfo("com.flipkart.android", "com.flipkart.android.SplashActivity", exported = true)
        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)
        val telegramResolve = createResolveInfo("org.telegram.messenger", "org.telegram.ui.LaunchActivity", exported = true)

        val openTargets = appResolver.filterAndClassifyCandidates(
            candidates = listOf(flipkartResolve, chromeResolve),
            targetHost = link.host,
            genericBrowserPackages = setOf("com.android.chrome"),
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.flipkart.android" -> "Flipkart"
                    else -> "Chrome"
                }
            },
            authoritiesExtractor = {
                if (it.activityInfo.packageName == "com.flipkart.android") {
                    listOf("flipkart.com", "www.flipkart.com")
                } else {
                    emptyList()
                }
            }
        )

        val shareTargets = shareResolver.filterAndProcessTargets(
            resolves = listOf(telegramResolve),
            labelLoader = { "Telegram" }
        )

        // Group 1: Dedicated app
        val dedicatedApps = openTargets.filter { it.category == TargetCategory.RECOMMENDED }
        assertEquals(1, dedicatedApps.size)
        assertEquals("Flipkart", dedicatedApps[0].appLabel)
        assertTrue(dedicatedApps[0].isNativeMatch)

        // Group 2: Generic Browsers
        val browsers = openTargets.filter { it.category == TargetCategory.BROWSER }
        assertEquals(1, browsers.size)
        assertEquals("Chrome", browsers[0].appLabel)
        assertTrue(browsers[0].isBrowser)

        // Group 3: Share Targets
        assertEquals(1, shareTargets.size)
        assertEquals("Telegram", shareTargets[0].appLabel)
    }

    @Test
    fun maliciousHostname_doesNotMatchDedicatedApp() {
        val appResolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val youtubeResolve = createResolveInfo("com.google.android.youtube", "com.google.android.youtube.UrlHandler", exported = true)

        // Phishing domain: evil-youtube.com
        val phishingTargets = appResolver.filterAndClassifyCandidates(
            candidates = listOf(youtubeResolve),
            targetHost = "evil-youtube.com",
            genericBrowserPackages = emptySet(),
            labelLoader = { "YouTube" },
            authoritiesExtractor = { listOf("youtube.com") }
        )

        assertEquals(1, phishingTargets.size)
        assertFalse(phishingTargets[0].isNativeMatch)
        assertEquals(TargetCategory.OTHER, phishingTargets[0].category)

        // Subdomain hijack: youtube.com.attacker.com
        val hijackTargets = appResolver.filterAndClassifyCandidates(
            candidates = listOf(youtubeResolve),
            targetHost = "youtube.com.attacker.com",
            genericBrowserPackages = emptySet(),
            labelLoader = { "YouTube" },
            authoritiesExtractor = { listOf("youtube.com") }
        )

        assertEquals(1, hijackTargets.size)
        assertFalse(hijackTargets[0].isNativeMatch)
        assertEquals(TargetCategory.OTHER, hijackTargets[0].category)
    }

    @Test
    fun sanitizerSecurity_rejectsMaliciousSchemesBeforeResolver() {
        val dangerousUrls = listOf(
            "javascript:alert(1)",
            "intent://example.com#Intent;scheme=https;package=com.victim;end",
            "file:///data/data/com.linkdeck.android/databases/prefs.db",
            "content://telephony/carriers",
            "data:text/html,<script>alert(1)</script>",
            "market://details?id=com.evil.app"
        )

        for (url in dangerousUrls) {
            val result = IntentSanitizer.sanitizeUrl(url)
            assertTrue("Expected $url to be rejected", result is SanitizationResult.Error)
        }
    }

    @Test
    fun browserDeduplication_browsersNeverAppearUnderShareWith() {
        val appResolver = AppResolver(packageManager = null, selfPackageName = selfPkg)
        val shareResolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val link = SanitizedLink(
            rawUrl = "https://gymscaleup.com",
            scheme = "https",
            host = "gymscaleup.com",
            path = "/"
        )

        // Mock installed applications: Chrome (supports VIEW + SEND), Brave (supports VIEW + SEND), WhatsApp (SEND only)
        val chromeViewResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)
        val braveViewResolve = createResolveInfo("com.brave.browser", "com.brave.browser.Main", exported = true)

        val chromeSendResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.ShareActivity", exported = true)
        val braveSendResolve = createResolveInfo("com.brave.browser", "com.brave.browser.ShareActivity", exported = true)
        val whatsAppSendResolve = createResolveInfo("com.whatsapp", "com.whatsapp.ContactPicker", exported = true)

        // 1. Resolve open targets
        val openTargets = appResolver.filterAndClassifyCandidates(
            candidates = listOf(chromeViewResolve, braveViewResolve),
            targetHost = link.host,
            genericBrowserPackages = setOf("com.android.chrome", "com.brave.browser"),
            labelLoader = { if (it.activityInfo.packageName == "com.android.chrome") "Chrome" else "Brave" }
        )

        assertEquals(2, openTargets.size)
        assertTrue(openTargets.all { it.category == TargetCategory.BROWSER })

        // 2. Extract browser packages to exclude from share targets
        val browserPackages = openTargets.filter {
            it.category == TargetCategory.BROWSER || it.isBrowser
        }.map { it.packageName }.toSet()

        assertEquals(setOf("com.android.chrome", "com.brave.browser"), browserPackages)

        // 3. Resolve share targets with excluded browser packages
        val shareTargets = shareResolver.filterAndProcessTargets(
            resolves = listOf(chromeSendResolve, braveSendResolve, whatsAppSendResolve),
            excludedPackageNames = browserPackages,
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.android.chrome" -> "Chrome"
                    "com.brave.browser" -> "Brave"
                    else -> "WhatsApp"
                }
            }
        )

        // Verifications:
        // WhatsApp is the ONLY share target; Chrome and Brave are strictly omitted from Share With
        assertEquals(1, shareTargets.size)
        assertEquals("WhatsApp", shareTargets[0].appLabel)
        assertEquals("com.whatsapp", shareTargets[0].packageName)
        assertFalse(shareTargets.any { it.packageName == "com.android.chrome" })
        assertFalse(shareTargets.any { it.packageName == "com.brave.browser" })
    }

    private fun createResolveInfo(pkg: String, act: String, exported: Boolean): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = act
                this.exported = exported
            }
        }
    }
}
