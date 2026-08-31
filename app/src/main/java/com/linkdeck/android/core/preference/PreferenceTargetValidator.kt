package com.linkdeck.android.core.preference

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.linkdeck.android.core.intent.BrowserClassifier
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink

/**
 * Validates whether a stored [RoutingPreference] points to an installed, enabled,
 * and currently capable handler application for the target destination link.
 */
object PreferenceTargetValidator {

    /**
     * Inspects the stored preference against [PackageManager] to ensure the target app
     * exists, is enabled, is exported, and can handle the specific destination URL.
     * Returns a valid [AppTarget] if safe to launch, or null if stale/unsupported.
     */
    fun validate(
        packageManager: PackageManager,
        preference: RoutingPreference,
        sanitizedLink: SanitizedLink,
        selfPackageName: String
    ): AppTarget? {
        if (preference.packageName == selfPackageName) return null

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        } else {
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        }

        val uri = Uri.parse(sanitizedLink.rawUrl)

        // 1. Query ACTION_VIEW with browsable category and explicit package
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            `package` = preference.packageName
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        var resolved: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, flags)
            }
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Fallback: Query ACTION_VIEW without CATEGORY_BROWSABLE
        if (resolved.isEmpty()) {
            val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                `package` = preference.packageName
            }
            resolved = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(genericIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(genericIntent, flags)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 3. Fallback: If still empty (e.g. for generic browsers), query generic web url with target package
        if (resolved.isEmpty()) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                `package` = preference.packageName
            }
            resolved = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(browserIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(browserIntent, flags)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        val resolveInfo = resolved.firstOrNull { it.activityInfo?.packageName == preference.packageName }
            ?: return null

        val loadedLabel = try {
            resolveInfo.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            ""
        }

        return validateResolveInfo(
            resolveInfo = resolveInfo,
            targetHost = sanitizedLink.host,
            appLabel = if (loadedLabel.isNotBlank()) loadedLabel else preference.appLabel,
            selfPackageName = selfPackageName
        )
    }

    /**
     * Pure testable validation ensuring the target is exported, not LinkDeck itself,
     * and correctly classified.
     */
    fun validateResolveInfo(
        resolveInfo: ResolveInfo?,
        targetHost: String,
        appLabel: String,
        selfPackageName: String
    ): AppTarget? {
        if (resolveInfo == null) return null
        val activityInfo = resolveInfo.activityInfo ?: return null

        if (activityInfo.packageName == selfPackageName) return null
        if (!activityInfo.exported && activityInfo.packageName != selfPackageName) return null

        val classification = BrowserClassifier.classify(
            resolveInfo = resolveInfo,
            targetHost = targetHost,
            isGenericBrowserCandidate = true
        )

        return AppTarget(
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            appLabel = appLabel,
            matchedHost = if (classification.isNativeMatch) targetHost else null,
            isBrowser = classification.isBrowser,
            isNativeMatch = classification.isNativeMatch,
            category = classification.category
        )
    }
}
