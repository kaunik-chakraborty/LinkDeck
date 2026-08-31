package com.linkdeck.android.core.preference

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import com.linkdeck.android.core.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PreferenceTargetValidatorTest {

    private val selfPkg = "com.linkdeck.android"

    @Test
    fun validateResolveInfo_selfPackage_returnsNull() {
        val resolveInfo = createResolveInfo(selfPkg, "com.linkdeck.android.ChooserActivity", exported = true)
        val result = PreferenceTargetValidator.validateResolveInfo(
            resolveInfo = resolveInfo,
            targetHost = "youtube.com",
            appLabel = "LinkDeck",
            selfPackageName = selfPkg
        )
        assertNull(result)
    }

    @Test
    fun validateResolveInfo_nullResolveInfo_returnsNull() {
        val result = PreferenceTargetValidator.validateResolveInfo(
            resolveInfo = null,
            targetHost = "youtube.com",
            appLabel = "YouTube",
            selfPackageName = selfPkg
        )
        assertNull(result)
    }

    @Test
    fun validateResolveInfo_packageInstalledAndExported_returnsAppTarget() {
        val resolveInfo = createResolveInfo(
            pkg = "com.google.android.youtube",
            act = "com.google.android.youtube.UrlHandlerActivity",
            exported = true
        )

        val result = PreferenceTargetValidator.validateResolveInfo(
            resolveInfo = resolveInfo,
            targetHost = "youtube.com",
            appLabel = "YouTube",
            selfPackageName = selfPkg
        )

        assertNotNull(result)
        assertEquals("com.google.android.youtube", result?.packageName)
        assertEquals("com.google.android.youtube.UrlHandlerActivity", result?.activityName)
        assertEquals("YouTube", result?.appLabel)
    }

    @Test
    fun validateResolveInfo_packageNotExported_returnsNull() {
        val resolveInfo = createResolveInfo(
            pkg = "com.example.privateapp",
            act = "com.example.privateapp.InternalActivity",
            exported = false
        )

        val result = PreferenceTargetValidator.validateResolveInfo(
            resolveInfo = resolveInfo,
            targetHost = "example.com",
            appLabel = "Private App",
            selfPackageName = selfPkg
        )

        assertNull(result)
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
