package com.linkdeck.android.core.intent

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget

/**
 * Discovers and queries installed applications capable of receiving a link via ACTION_SEND (text/plain).
 *
 * Enforces architectural and UX guarantees:
 * 1. Excludes LinkDeck itself ([selfPackageName]) to prevent self-sharing loops.
 * 2. Excludes unexported or disabled components that cannot be safely launched from outside.
 * 3. Excludes applications already classified as open/browser targets ([excludedPackageNames])
 *    so browsers like Chrome and Brave are not duplicated under "Share with".
 * 4. Deduplicates targets by component signature.
 * 5. Produces a stable, alphabetically sorted list of [ShareTarget] destinations.
 */
class ShareTargetResolver(
    private val packageManager: PackageManager? = null,
    private val selfPackageName: String
) {

    /**
     * Resolves all compatible installed share targets for the given [sanitizedLink],
     * excluding any packages in [excludedPackageNames] (such as discovered browsers).
     */
    fun resolve(
        @Suppress("UNUSED_PARAMETER") sanitizedLink: SanitizedLink? = null,
        excludedPackageNames: Set<String> = emptySet()
    ): List<ShareTarget> {
        val pm = checkNotNull(packageManager) { "PackageManager must not be null for share queries" }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }

        val resolves = queryIntentActivitiesCompat(pm, shareIntent)
        return filterAndProcessTargets(
            resolves = resolves,
            excludedPackageNames = excludedPackageNames,
            labelLoader = { resolveInfo ->
                try {
                    resolveInfo.loadLabel(pm).toString().trim()
                } catch (e: Exception) {
                    resolveInfo.activityInfo?.packageName ?: ""
                }
            }
        )
    }

    /**
     * Filters, deduplicates, and sorts candidate share targets.
     * Exposed internally for pure unit testing without Android runtime dependencies.
     */
    internal fun filterAndProcessTargets(
        resolves: List<ResolveInfo>,
        excludedPackageNames: Set<String> = emptySet(),
        labelLoader: (ResolveInfo) -> String
    ): List<ShareTarget> {
        val targets = mutableListOf<ShareTarget>()
        val seenSignatures = mutableSetOf<String>()

        for (resolveInfo in resolves) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkgName = activityInfo.packageName ?: continue
            val actName = activityInfo.name ?: continue

            // Self-loop prevention: Never include LinkDeck itself
            if (pkgName == selfPackageName) {
                continue
            }

            // Exclude packages already classified as browsers or open targets
            if (excludedPackageNames.contains(pkgName)) {
                continue
            }

            // Exclude unexported components (which cannot be launched by external intents)
            if (!activityInfo.exported) {
                continue
            }

            val signature = "$pkgName/$actName"
            if (!seenSignatures.add(signature)) {
                continue
            }

            val rawLabel = labelLoader(resolveInfo)
            val appLabel = rawLabel.ifEmpty { pkgName }

            targets.add(
                ShareTarget(
                    packageName = pkgName,
                    activityName = actName,
                    appLabel = appLabel
                )
            )
        }

        // Deterministic alphabetical sorting by human-readable app label
        return targets.sortedWith(
            compareBy { it.appLabel.lowercase() }
        )
    }

    private fun queryIntentActivitiesCompat(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        val defaultFlags = PackageManager.MATCH_DEFAULT_ONLY
        val results = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(defaultFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, defaultFlags)
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (results.isNotEmpty()) {
            return results
        }

        return try {
            val fallbackFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(fallbackFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, fallbackFlags)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
