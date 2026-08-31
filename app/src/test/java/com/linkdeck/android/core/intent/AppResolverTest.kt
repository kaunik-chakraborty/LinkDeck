package com.linkdeck.android.core.intent

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import com.linkdeck.android.core.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResolverTest {

    private val selfPkg = "com.linkdeck.android"

    @Test
    fun filterAndClassifyCandidates_excludesSelfPackage() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val selfResolve = createResolveInfo(selfPkg, "com.linkdeck.android.ChooserActivity", exported = true)
        val browserResolve = createResolveInfo("org.mozilla.firefox", "org.mozilla.firefox.App", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(selfResolve, browserResolve),
            targetHost = "example.com",
            genericBrowserPackages = setOf("org.mozilla.firefox"),
            labelLoader = { if (it.activityInfo.packageName == selfPkg) "LinkDeck" else "Firefox" }
        )

        assertEquals(1, candidates.size)
        assertEquals("org.mozilla.firefox", candidates[0].packageName)
        assertFalse(candidates.any { it.packageName == selfPkg })
    }

    @Test
    fun filterAndClassifyCandidates_excludesUnexportedActivities() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val unexportedResolve = createResolveInfo("com.private.app", "com.private.app.HiddenActivity", exported = false)
        val exportedResolve = createResolveInfo("com.public.app", "com.public.app.PublicActivity", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(unexportedResolve, exportedResolve),
            targetHost = "example.com",
            genericBrowserPackages = emptySet(),
            labelLoader = { "App Label" }
        )

        assertEquals(1, candidates.size)
        assertEquals("com.public.app", candidates[0].packageName)
    }

    @Test
    fun filterAndClassifyCandidates_deduplicatesIdenticalTargets() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val resolve1 = createResolveInfo("com.browser.app", "com.browser.app.MainActivity", exported = true)
        val resolve2 = createResolveInfo("com.browser.app", "com.browser.app.MainActivity", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(resolve1, resolve2),
            targetHost = "example.com",
            genericBrowserPackages = setOf("com.browser.app"),
            labelLoader = { "Browser" }
        )

        assertEquals(1, candidates.size)
    }

    @Test
    fun filterAndClassifyCandidates_arbitraryDomainWithNoDedicatedApp_discoversGenericBrowsers() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        // gymscaleup.com has NO specific dedicated app, but Chrome & Firefox exist in generic candidates
        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)
        val firefoxResolve = createResolveInfo("org.mozilla.firefox", "org.mozilla.firefox.App", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(chromeResolve, firefoxResolve),
            targetHost = "gymscaleup.com",
            genericBrowserPackages = setOf("com.android.chrome", "org.mozilla.firefox"),
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.android.chrome" -> "Chrome"
                    else -> "Firefox"
                }
            }
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.category == TargetCategory.BROWSER })
        assertTrue(candidates.all { it.isBrowser })
        assertFalse(candidates.any { it.isNativeMatch })
        assertEquals("Chrome", candidates[0].appLabel)
        assertEquals("Firefox", candidates[1].appLabel)
    }

    @Test
    fun filterAndClassifyCandidates_dedicatedAppAndGenericBrowsers_correctlyCategorized() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val flipkartResolve = createResolveInfo("com.flipkart.android", "com.flipkart.android.SplashActivity", exported = true)
        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(flipkartResolve, chromeResolve),
            targetHost = "www.flipkart.com",
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

        assertEquals(2, candidates.size)
        assertEquals(TargetCategory.RECOMMENDED, candidates[0].category)
        assertTrue(candidates[0].isNativeMatch)
        assertEquals("Flipkart", candidates[0].appLabel)

        assertEquals(TargetCategory.BROWSER, candidates[1].category)
        assertTrue(candidates[1].isBrowser)
        assertEquals("Chrome", candidates[1].appLabel)
    }

    @Test
    fun filterAndClassifyCandidates_defaultBrowserPrioritizedFirstAmongBrowsers() {
        val resolver = AppResolver(packageManager = null, selfPackageName = selfPkg)

        val braveResolve = createResolveInfo("com.brave.browser", "com.brave.browser.Main", exported = true)
        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.Main", exported = true)
        val firefoxResolve = createResolveInfo("org.mozilla.firefox", "org.mozilla.firefox.App", exported = true)

        val candidates = resolver.filterAndClassifyCandidates(
            candidates = listOf(braveResolve, chromeResolve, firefoxResolve),
            targetHost = "example.com",
            genericBrowserPackages = setOf("com.brave.browser", "com.android.chrome", "org.mozilla.firefox"),
            defaultBrowserPackage = "org.mozilla.firefox",
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.brave.browser" -> "Brave"
                    "com.android.chrome" -> "Chrome"
                    else -> "Firefox"
                }
            }
        )

        assertEquals(3, candidates.size)
        // Firefox is default, so it appears first, followed by Brave and Chrome alphabetically
        assertEquals("Firefox", candidates[0].appLabel)
        assertEquals("Brave", candidates[1].appLabel)
        assertEquals("Chrome", candidates[2].appLabel)
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
