package com.linkdeck.android.ui.chooser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.inspector.LinkInspectionData
import com.linkdeck.android.core.inspector.RoutingExplanation
import com.linkdeck.android.core.intent.AppResolver
import com.linkdeck.android.core.intent.IntentLauncher
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.intent.ShareIntentLauncher
import com.linkdeck.android.core.intent.ShareTargetResolver
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget
import com.linkdeck.android.core.model.TargetCategory
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectResolver
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.preference.PreferenceTargetValidator
import com.linkdeck.android.core.preference.RoutingPreference
import com.linkdeck.android.core.preference.SavePreferenceResult
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.RoutingRuleMatcher
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.ui.base.BaseActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Translucent entry point Activity for intercepting incoming HTTP/HTTPS links,
 * safely resolving web redirects on-device, stripping tracking parameters,
 * checking advanced routing rules and saved preferences, and presenting the chooser.
 */
class ChooserActivity : BaseActivity() {

    private var chooserSheet: ChooserBottomSheet? = null
    private var isLaunchingTarget = false
    private var currentResolutionJob: Job? = null
    private val redirectResolver = RedirectResolver()
    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(this) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingFragment = supportFragmentManager.findFragmentByTag(ChooserBottomSheet.TAG) as? ChooserBottomSheet
        if (existingFragment != null) {
            chooserSheet = existingFragment
            bindSheetCallbacks(existingFragment)
        } else {
            handleIncomingIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val sanitizationResult = IntentSanitizer.sanitizeIntent(intent)

        when (sanitizationResult) {
            is SanitizationResult.Success -> {
                processValidLink(sanitizationResult.link)
            }
            is SanitizationResult.Error -> {
                showError(sanitizationResult.message)
            }
        }
    }

    private fun processValidLink(link: SanitizedLink) {
        currentResolutionJob?.cancel()
        chooserSheet?.dismissAllowingStateLoss()

        val sheet = ChooserBottomSheet.newInstance().apply {
            setLoadingData(link)
        }
        bindSheetCallbacks(sheet)
        chooserSheet = sheet
        sheet.show(supportFragmentManager, ChooserBottomSheet.TAG)

        currentResolutionJob = lifecycleScope.launch {
            var processedLink = link
            var wasDeAmped = false
            var deAmpSource: String? = null

            if (appSettingsStore.isDeAmpingEnabled) {
                val deAmpResult = com.linkdeck.android.core.deamp.DeAmpEngine.deAmp(link)
                if (deAmpResult.wasDeAmped) {
                    processedLink = deAmpResult.deAmpedLink
                    wasDeAmped = true
                    deAmpSource = deAmpResult.source?.displayName
                }
            }

            val redirectResult = if (appSettingsStore.isRedirectCheckingEnabled) {
                redirectResolver.resolve(processedLink)
            } else {
                RedirectResult.NoRedirect(processedLink.rawUrl)
            }

            if (!isActive || isFinishing) return@launch

            val effectiveLink = when (redirectResult) {
                is RedirectResult.Success -> {
                    (IntentSanitizer.sanitizeUrl(redirectResult.finalUrl) as? SanitizationResult.Success)?.link ?: processedLink
                }
                is RedirectResult.NoRedirect -> processedLink
                is RedirectResult.Error -> {
                    if (redirectResult.errorType == RedirectErrorType.BLOCKED_PRIVATE_ADDRESS) {
                        Toast.makeText(this@ChooserActivity, R.string.blocked_private_network_notice, Toast.LENGTH_LONG).show()
                    }
                    processedLink
                }
            }

            // Step 3: Optional tracking parameter cleaner
            var candidateLink = effectiveLink
            var wasCleaned = false
            var removedParams = emptyList<String>()

            if (appSettingsStore.isTrackingCleanerEnabled) {
                val cleanResult = TrackingParameterCleaner.clean(effectiveLink)
                if (cleanResult.hasRemovedParams) {
                    val reSanitized = IntentSanitizer.sanitizeUrl(cleanResult.cleanedLink.rawUrl)
                    if (reSanitized is SanitizationResult.Success) {
                        candidateLink = reSanitized.link
                        wasCleaned = true
                        removedParams = cleanResult.removedParams
                    }
                }
            }

            val isAutoRouting = appSettingsStore.isAutomaticRoutingEnabled

            // Step 4: Evaluate explicit Phase 4 Routing Rules (highest priority)
            val matchingRule = RoutingRuleMatcher.findBestMatch(candidateLink, ruleStore.getRules())
            if (matchingRule != null) {
                val validTarget = PreferenceTargetValidator.validate(
                    packageManager = packageManager,
                    preference = RoutingPreference(matchingRule.host, matchingRule.packageName, matchingRule.appLabel),
                    sanitizedLink = candidateLink,
                    selfPackageName = packageName
                )
                if (validTarget != null && isAutoRouting) {
                    sheet.dismissAllowingStateLoss()
                    executeLaunch(validTarget, candidateLink, isAlways = false)
                    return@launch
                }
            }

            // Step 5: Evaluate Phase 3 Saved Preferences
            val savedPref = preferenceStore.getPreference(candidateLink.host)
            if (savedPref != null) {
                val validTarget = PreferenceTargetValidator.validate(
                    packageManager = packageManager,
                    preference = savedPref,
                    sanitizedLink = candidateLink,
                    selfPackageName = packageName
                )
                if (validTarget != null && isAutoRouting) {
                    sheet.dismissAllowingStateLoss()
                    executeLaunch(validTarget, candidateLink, isAlways = false)
                    return@launch
                }
            }

            // Step 6: Fall through to interactive Chooser
            val openResolver = AppResolver(packageManager, packageName)
            val openTargets = openResolver.resolve(candidateLink)
            val browserPackages = openTargets.filter { it.category == TargetCategory.BROWSER || it.isBrowser }
                .map { it.packageName }.toSet()

            val shareResolver = ShareTargetResolver(packageManager, packageName)
            val shareTargets = shareResolver.resolve(candidateLink, excludedPackageNames = browserPackages)
            val original = if (candidateLink.rawUrl != link.rawUrl) link else null

            val explanation = when {
                matchingRule != null -> RoutingExplanation.MatchedRule(matchingRule)
                savedPref != null -> RoutingExplanation.SavedPreference(savedPref)
                else -> RoutingExplanation.ManualChoice
            }

            val inspectionData = LinkInspectionData(
                originalLink = link,
                effectiveDestination = candidateLink,
                redirectResult = redirectResult,
                wasCleaned = wasCleaned,
                removedTrackingParams = removedParams,
                wasDeAmped = wasDeAmped,
                deAmpSource = deAmpSource,
                routingExplanation = explanation,
                targetApp = null
            )

            sheet.setLinkData(
                link = candidateLink,
                openTargets = openTargets,
                shareTargets = shareTargets,
                originalLink = original,
                wasCleaned = wasCleaned,
                wasDeAmped = wasDeAmped,
                inspectionData = inspectionData,
                allowRememberChoices = appSettingsStore.isRememberChoicesEnabled
            )
        }
    }

    private fun bindSheetCallbacks(sheet: ChooserBottomSheet) {
        sheet.onTargetLaunchRequested = { selectedTarget, link, isAlways ->
            handleTargetSelection(selectedTarget, link, isAlways)
        }
        sheet.onShareRequested = { selectedShareTarget, link ->
            handleShareSelection(selectedShareTarget, link)
        }
        sheet.onDismissed = {
            if (!isFinishing && !isLaunchingTarget) {
                finish()
            }
        }
    }

    private fun handleTargetSelection(target: AppTarget, link: SanitizedLink, isAlways: Boolean) {
        if (isLaunchingTarget) return
        executeLaunch(target, link, isAlways)
    }

    private fun handleShareSelection(target: ShareTarget, link: SanitizedLink) {
        if (isLaunchingTarget) return
        isLaunchingTarget = true
        val result = ShareIntentLauncher.launch(this, target, link)
        if (result.isSuccess) {
            finish()
        } else {
            isLaunchingTarget = false
            Toast.makeText(this, getString(R.string.share_failed_toast, target.appLabel), Toast.LENGTH_LONG).show()
        }
    }

    private fun executeLaunch(target: AppTarget, link: SanitizedLink, isAlways: Boolean) {
        isLaunchingTarget = true
        val result = IntentLauncher.launch(this, target, link)
        if (result.isSuccess) {
            if (isAlways && appSettingsStore.isRememberChoicesEnabled) {
                savePreference(link.host, target)
            }
            finish()
        } else {
            isLaunchingTarget = false
            Toast.makeText(this, getString(R.string.launch_failed_toast, target.appLabel), Toast.LENGTH_LONG).show()
        }
    }

    private fun savePreference(domain: String, target: AppTarget) {
        val pref = RoutingPreference(domain, target.packageName, target.appLabel)
        when (preferenceStore.savePreference(pref)) {
            is SavePreferenceResult.Success -> Toast.makeText(this, getString(R.string.preference_saved_toast, domain, target.appLabel), Toast.LENGTH_SHORT).show()
            is SavePreferenceResult.LimitReached -> Toast.makeText(this, R.string.preference_limit_reached, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    private fun showError(message: String) {
        currentResolutionJob?.cancel()
        chooserSheet?.dismissAllowingStateLoss()
        val sheet = ChooserBottomSheet.newInstance().apply {
            setErrorData(message)
            onDismissed = {
                if (!isFinishing) {
                    finish()
                }
            }
        }
        chooserSheet = sheet
        sheet.show(supportFragmentManager, ChooserBottomSheet.TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentResolutionJob?.cancel()
    }
}
