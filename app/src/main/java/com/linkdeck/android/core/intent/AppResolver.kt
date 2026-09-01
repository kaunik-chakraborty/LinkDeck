package com.linkdeck.android.core.intent

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.TargetCategory

/**
 * Discovers and queries installed applications capable of handling a sanitized link
 * via ACTION_VIEW using Android's [PackageManager].
 *
 * Enforces architectural guarantees:
 * 1. Queries both specific URL handlers AND generic HTTP/HTTPS handlers so that browsers
 *    are never missed for arbitrary websites (e.g., gymscaleup.com).
 * 2. Excludes LinkDeck itself ([selfPackageName]) to prevent infinite self-launching loops.
 * 3. Filters out unexported or disabled components that would fail to launch.
 * 4. Categorizes candidates cleanly into Dedicated Apps (RECOMMENDED), Browsers, and Other handlers.
 */
class AppResolver(
    private val packageManager: PackageManager? = null,
    private val selfPackageName: String
) {

    /**
     * Resolves all compatible installed application targets for a given [sanitizedLink].
     */
    fun resolve(sanitizedLink: SanitizedLink): List<AppTarget> {
        val pm = checkNotNull(packageManager) { "PackageManager must not be null for intent queries" }
        val targetUri = Uri.parse(sanitizedLink.rawUrl)
        val targetIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        // 1. Query handlers for the specific target URI (discovers dedicated/native app links)
        val specificResolves = queryIntentActivitiesCompat(pm, targetIntent)

        // 2. Discover installed browsers (cached in-memory to prevent redundant Binder IPC calls)
        val now = System.currentTimeMillis()
        val (genericBrowserPackages, defaultBrowserPackage, genericResolves) = synchronized(browserCacheLock) {
            val cached = cachedBrowserInfo
            if (cached != null && (now - lastBrowserCacheTime) < BROWSER_CACHE_TTL_MS) {
                cached
            } else {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                val resolves = queryIntentActivitiesCompat(pm, genericIntent)
                val packages = resolves.mapNotNull { it.activityInfo?.packageName }.toSet()
                val defaultPkg = try {
                    val defaultResolve = pm.resolveActivity(genericIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    val pkg = defaultResolve?.activityInfo?.packageName
                    if (pkg != null && pkg != selfPackageName) pkg else null
                } catch (_: Exception) {
                    null
                }
                val info = Triple(packages, defaultPkg, resolves)
                cachedBrowserInfo = info
                lastBrowserCacheTime = now
                info
            }
        }

        // Merge specific and generic candidates to ensure browsers are never omitted
        val allCandidates = specificResolves + genericResolves

        return filterAndClassifyCandidates(
            candidates = allCandidates,
            targetHost = sanitizedLink.host,
            genericBrowserPackages = genericBrowserPackages,
            defaultBrowserPackage = defaultBrowserPackage,
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
     * Filters, deduplicates, classifies, and deterministically sorts candidate handlers.
     * Exposed internally for pure unit testing and diagnostic inspection.
     */
    internal fun filterAndClassifyCandidates(
        candidates: List<ResolveInfo>,
        targetHost: String,
        genericBrowserPackages: Set<String>,
        defaultBrowserPackage: String? = null,
        labelLoader: (ResolveInfo) -> String,
        authoritiesExtractor: (ResolveInfo) -> List<String> = { BrowserClassifier.extractAuthorities(it.filter) }
    ): List<AppTarget> {
        val targets = mutableListOf<AppTarget>()
        val seenSignatures = mutableSetOf<String>()

        for (resolveInfo in candidates) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkgName = activityInfo.packageName ?: continue
            val actName = activityInfo.name ?: continue

            // Self-loop prevention: Never include LinkDeck itself
            if (pkgName == selfPackageName) {
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

            val isGenericBrowser = genericBrowserPackages.contains(pkgName)
            val classification = BrowserClassifier.classify(
                resolveInfo = resolveInfo,
                targetHost = targetHost,
                isGenericBrowserCandidate = isGenericBrowser,
                authoritiesExtractor = authoritiesExtractor
            )

            val matchedHost = if (classification.isNativeMatch) targetHost else null

            targets.add(
                AppTarget(
                    packageName = pkgName,
                    activityName = actName,
                    appLabel = appLabel,
                    matchedHost = matchedHost,
                    isBrowser = classification.isBrowser,
                    isNativeMatch = classification.isNativeMatch,
                    category = classification.category
                )
            )
        }

        // Deterministic sorting:
        // 1. Recommended (native app match) first, then Browsers, then Other apps
        // 2. For Browsers: Default browser first if discoverable, then alphabetical by label
        // 3. For other sections: Alphabetical by label
        return targets.sortedWith { a, b ->
            if (a.category != b.category) {
                a.category.ordinal.compareTo(b.category.ordinal)
            } else if (a.category == TargetCategory.BROWSER) {
                val aIsDefault = (defaultBrowserPackage != null && a.packageName == defaultBrowserPackage)
                val bIsDefault = (defaultBrowserPackage != null && b.packageName == defaultBrowserPackage)
                when {
                    aIsDefault && !bIsDefault -> -1
                    !aIsDefault && bIsDefault -> 1
                    else -> a.appLabel.lowercase().compareTo(b.appLabel.lowercase())
                }
            } else {
                a.appLabel.lowercase().compareTo(b.appLabel.lowercase())
            }
        }
    }

    private fun queryIntentActivitiesCompat(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        val primaryFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        } else {
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        }

        val results = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(primaryFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, primaryFlags)
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (results.isNotEmpty()) {
            return results
        }

        // Fallback with MATCH_DEFAULT_ONLY flags
        val fallbackFlags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        return try {
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

    companion object {
        private val browserCacheLock = Any()
        @Volatile
        private var cachedBrowserInfo: Triple<Set<String>, String?, List<ResolveInfo>>? = null
        @Volatile
        private var lastBrowserCacheTime: Long = 0L
        private const val BROWSER_CACHE_TTL_MS = 60_000L

        /**
         * Clears cached browser resolutions (useful during testing or when package changes occur).
         */
        fun clearCache() {
            synchronized(browserCacheLock) {
                cachedBrowserInfo = null
                lastBrowserCacheTime = 0L
            }
        }
    }
}
