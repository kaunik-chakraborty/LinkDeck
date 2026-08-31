package com.linkdeck.android.core.intent

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareTargetResolverTest {

    private val selfPkg = "com.linkdeck.android"

    @Test
    fun filterAndProcessTargets_discoversValidShareTargets() {
        val resolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val whatsappResolve = createResolveInfo("com.whatsapp", "com.whatsapp.ContactPicker", exported = true)
        val telegramResolve = createResolveInfo("org.telegram.messenger", "org.telegram.ui.LaunchActivity", exported = true)

        val targets = resolver.filterAndProcessTargets(
            resolves = listOf(whatsappResolve, telegramResolve),
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.whatsapp" -> "WhatsApp"
                    else -> "Telegram"
                }
            }
        )

        assertEquals(2, targets.size)
        assertEquals("Telegram", targets[0].appLabel)
        assertEquals("org.telegram.messenger", targets[0].packageName)
        assertEquals("WhatsApp", targets[1].appLabel)
        assertEquals("com.whatsapp", targets[1].packageName)
    }

    @Test
    fun filterAndProcessTargets_excludesSelfPackage() {
        val resolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val selfResolve = createResolveInfo(selfPkg, "com.linkdeck.android.ChooserActivity", exported = true)
        val messagesResolve = createResolveInfo("com.google.android.apps.messaging", "com.google.android.apps.messaging.ui.ConversationListActivity", exported = true)

        val targets = resolver.filterAndProcessTargets(
            resolves = listOf(selfResolve, messagesResolve),
            labelLoader = { if (it.activityInfo.packageName == selfPkg) "LinkDeck" else "Messages" }
        )

        assertEquals(1, targets.size)
        assertEquals("Messages", targets[0].appLabel)
        assertFalse(targets.any { it.packageName == selfPkg })
    }

    @Test
    fun filterAndProcessTargets_excludesUnexportedActivities() {
        val resolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val privateResolve = createResolveInfo("com.private.app", "com.private.app.InternalShare", exported = false)
        val publicResolve = createResolveInfo("com.public.chat", "com.public.chat.ShareActivity", exported = true)

        val targets = resolver.filterAndProcessTargets(
            resolves = listOf(privateResolve, publicResolve),
            labelLoader = { "Chat App" }
        )

        assertEquals(1, targets.size)
        assertEquals("com.public.chat", targets[0].packageName)
    }

    @Test
    fun filterAndProcessTargets_deduplicatesIdenticalTargets() {
        val resolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val resolve1 = createResolveInfo("com.chat.app", "com.chat.app.ShareActivity", exported = true)
        val resolve2 = createResolveInfo("com.chat.app", "com.chat.app.ShareActivity", exported = true)

        val targets = resolver.filterAndProcessTargets(
            resolves = listOf(resolve1, resolve2),
            labelLoader = { "Chat App" }
        )

        assertEquals(1, targets.size)
    }

    @Test
    fun filterAndProcessTargets_excludesBrowserPackagesFromShareWith() {
        val resolver = ShareTargetResolver(packageManager = null, selfPackageName = selfPkg)

        val chromeResolve = createResolveInfo("com.android.chrome", "com.google.android.apps.chrome.ShareActivity", exported = true)
        val braveResolve = createResolveInfo("com.brave.browser", "com.brave.browser.ShareActivity", exported = true)
        val whatsappResolve = createResolveInfo("com.whatsapp", "com.whatsapp.ContactPicker", exported = true)

        val targets = resolver.filterAndProcessTargets(
            resolves = listOf(chromeResolve, braveResolve, whatsappResolve),
            excludedPackageNames = setOf("com.android.chrome", "com.brave.browser"),
            labelLoader = {
                when (it.activityInfo.packageName) {
                    "com.android.chrome" -> "Chrome"
                    "com.brave.browser" -> "Brave"
                    else -> "WhatsApp"
                }
            }
        )

        assertEquals(1, targets.size)
        assertEquals("WhatsApp", targets[0].appLabel)
        assertEquals("com.whatsapp", targets[0].packageName)
        assertFalse(targets.any { it.packageName == "com.android.chrome" })
        assertFalse(targets.any { it.packageName == "com.brave.browser" })
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
