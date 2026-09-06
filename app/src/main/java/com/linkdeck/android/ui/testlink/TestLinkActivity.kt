package com.linkdeck.android.ui.testlink

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.cleaner.rules.CustomParameterRulesStore
import com.linkdeck.android.core.deamp.DeAmpEngine
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.intent.AppResolver
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.intent.ShareTargetResolver
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget
import com.linkdeck.android.core.model.TargetCategory
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectResolver
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.preference.RoutingPreference
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.RoutingRule
import com.linkdeck.android.core.rule.RoutingRuleMatcher
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.ui.base.BaseActivity
import com.linkdeck.android.ui.chooser.ChooserActivity
import com.linkdeck.android.ui.qr.QrGeneratorBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Diagnostic testing screen allowing users to input a URL and preview
 * link sanitization, redirect resolution, tracking parameter stripping,
 * rule matching, and candidate app discovery without launching.
 */
class TestLinkActivity : BaseActivity() {

    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(this) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(this) }
    private val redirectResolver by lazy {
        RedirectResolver(
            hopInterceptor = { url ->
                if (appSettingsStore.isDeAmpingEnabled) DeAmpEngine.deAmpUrl(url) else null
            }
        )
    }

    private var currentJob: Job? = null
    private var lastTestedLink: SanitizedLink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_link)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.testLinkRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<MaterialToolbar>(R.id.testLinkToolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        setupInspectAction()

        val initialUrl = intent.getStringExtra(EXTRA_INITIAL_URL)
        if (!initialUrl.isNullOrBlank()) {
            findViewById<TextInputEditText>(R.id.editTestLinkInput).setText(initialUrl)
            runDiagnostic(initialUrl)
        }
    }

    private fun setupInspectAction() {
        val editInput: TextInputEditText = findViewById(R.id.editTestLinkInput)
        val btnRunTest: MaterialButton = findViewById(R.id.btnRunTest)
        val btnPaste: View? = findViewById(R.id.btnTestPaste)
        val btnOpenInChooser: MaterialButton = findViewById(R.id.btnOpenInChooser)
        val btnShowQrCode: MaterialButton = findViewById(R.id.btnShowQrCode)

        btnPaste?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!text.isNullOrBlank()) editInput.setText(text)
        }

        btnRunTest.setOnClickListener {
            val raw = editInput.text?.toString()?.trim()
            if (raw.isNullOrBlank()) {
                Toast.makeText(this, R.string.error_invalid_link_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runDiagnostic(raw)
        }

        btnOpenInChooser.setOnClickListener {
            val link = lastTestedLink ?: return@setOnClickListener
            val intent = Intent(this, ChooserActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(link.rawUrl)
            }
            startActivity(intent)
        }

        btnShowQrCode.setOnClickListener {
            val target = lastTestedLink?.rawUrl ?: editInput.text?.toString()?.trim().orEmpty()
            if (target.isNotBlank()) {
                val sheet = QrGeneratorBottomSheet.newInstance(target)
                sheet.show(supportFragmentManager, QrGeneratorBottomSheet.TAG)
            }
        }
    }

    private fun runDiagnostic(rawUrl: String) {
        val sanitization = IntentSanitizer.sanitizeUrl(rawUrl)
        if (sanitization !is SanitizationResult.Success) {
            val msg = (sanitization as? SanitizationResult.Error)?.message ?: getString(R.string.error_invalid_link_title)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }

        val initialLink = sanitization.link
        lastTestedLink = initialLink

        val loadingLayout: View = findViewById(R.id.testLoadingLayout)
        val resultsCard: View = findViewById(R.id.testResultsCard)
        val btnRunTest: MaterialButton = findViewById(R.id.btnRunTest)

        loadingLayout.visibility = View.VISIBLE
        resultsCard.visibility = View.GONE
        btnRunTest.isEnabled = false

        currentJob?.cancel()
        currentJob = lifecycleScope.launch {
            var processedLink = initialLink
            var wasDeAmped = false
            if (appSettingsStore.isDeAmpingEnabled) {
                val res = DeAmpEngine.deAmp(initialLink)
                if (res.wasDeAmped) { processedLink = res.deAmpedLink; wasDeAmped = true }
            }

            val redirectResult = if (appSettingsStore.isRedirectCheckingEnabled) {
                redirectResolver.resolve(processedLink)
            } else RedirectResult.NoRedirect(processedLink.rawUrl)

            if (!isActive || isFinishing) return@launch

            val effectiveLink = when (redirectResult) {
                is RedirectResult.Success -> {
                    val sFinal = IntentSanitizer.sanitizeUrl(redirectResult.finalUrl)
                    if (sFinal is SanitizationResult.Success) sFinal.link else processedLink
                }
                else -> processedLink
            }

            var candidateLink = effectiveLink
            if (appSettingsStore.isDeAmpingEnabled) {
                val postRes = DeAmpEngine.deAmp(effectiveLink)
                if (postRes.wasDeAmped) { candidateLink = postRes.deAmpedLink; wasDeAmped = true }
            }

            var wasCleaned = false
            var removedParams = emptyList<String>()
            if (appSettingsStore.isTrackingCleanerEnabled) {
                val customRules = CustomParameterRulesStore(this@TestLinkActivity).getEnabledRules()
                val cleanResult = TrackingParameterCleaner.clean(candidateLink, customRules)
                if (cleanResult.hasRemovedParams) {
                    val reSanitized = IntentSanitizer.sanitizeUrl(cleanResult.cleanedLink.rawUrl)
                    if (reSanitized is SanitizationResult.Success) {
                        candidateLink = reSanitized.link
                        wasCleaned = true
                        removedParams = cleanResult.removedParams
                    }
                }
            }

            lastTestedLink = candidateLink
            val matchingRule = RoutingRuleMatcher.findBestMatch(candidateLink, ruleStore.getRules())
            val savedPref = preferenceStore.getPreference(candidateLink.host)
            val openTargets = AppResolver(packageManager, packageName).resolve(candidateLink)
            val browserPackages = openTargets.filter { it.category == TargetCategory.BROWSER || it.isBrowser }.map { it.packageName }.toSet()
            val shareTargets = ShareTargetResolver(packageManager, packageName).resolve(candidateLink, excludedPackageNames = browserPackages)

            renderResults(initialLink, redirectResult, candidateLink, wasDeAmped, wasCleaned, removedParams, matchingRule, savedPref, openTargets, shareTargets)

            loadingLayout.visibility = View.GONE
            resultsCard.visibility = View.VISIBLE
            btnRunTest.isEnabled = true
        }
    }

    private fun renderResults(
        initialLink: SanitizedLink,
        redirectResult: RedirectResult,
        candidateLink: SanitizedLink,
        wasDeAmped: Boolean,
        wasCleaned: Boolean,
        removedParams: List<String>,
        matchingRule: RoutingRule?,
        savedPref: RoutingPreference?,
        openTargets: List<AppTarget>,
        shareTargets: List<ShareTarget>
    ) {
        val chipDeAmped: Chip = findViewById(R.id.chipTestDeAmped)
        val chipCleaned: Chip = findViewById(R.id.chipTestCleaned)
        val chipRedirects: Chip = findViewById(R.id.chipTestRedirects)

        chipDeAmped.visibility = if (wasDeAmped) View.VISIBLE else View.GONE
        chipCleaned.visibility = if (wasCleaned) View.VISIBLE else View.GONE
        if (wasCleaned) chipCleaned.text = getString(R.string.inspector_tracking_cleaned, removedParams.size)
        chipRedirects.text = when (redirectResult) {
            is RedirectResult.Success -> "${redirectResult.hops.size} Redirect(s)"
            is RedirectResult.NoRedirect -> "Direct Link"
            is RedirectResult.Error -> "Redirect Error"
        }

        findViewById<TextView>(R.id.textTestOriginal).text = UrlRedactor.redact(initialLink)
        findViewById<TextView>(R.id.textTestRedirects).text = when (redirectResult) {
            is RedirectResult.Success -> redirectResult.hops.joinToString("\n") { "• HTTP ${it.statusCode} → ${UrlRedactor.redactUrlString(it.targetUrl)}" }
            is RedirectResult.NoRedirect -> getString(R.string.inspector_no_redirects)
            is RedirectResult.Error -> when (redirectResult.errorType) {
                RedirectErrorType.BLOCKED_PRIVATE_ADDRESS -> getString(R.string.inspector_redirect_blocked)
                RedirectErrorType.REDIRECT_LOOP -> getString(R.string.inspector_redirect_loop)
                RedirectErrorType.TOO_MANY_REDIRECTS -> getString(R.string.inspector_redirect_too_many)
                else -> getString(R.string.inspector_redirect_unreachable)
            }
        }
        findViewById<TextView>(R.id.textTestCleaned).text = UrlRedactor.redact(candidateLink)
        findViewById<TextView>(R.id.textTestRoutingRule).text = when {
            matchingRule != null -> getString(R.string.inspector_rule_matched, matchingRule.displayCondition) + " → ${matchingRule.appLabel}"
            savedPref != null -> getString(R.string.inspector_pref_matched, savedPref.domain) + " → ${savedPref.appLabel}"
            else -> getString(R.string.inspector_manual_choice)
        }

        populateOpenChips(findViewById(R.id.chipGroupOpenApps), openTargets)
        populateShareChips(findViewById(R.id.chipGroupShareApps), shareTargets, maxDisplay = 12)
    }

    private fun populateOpenChips(group: ChipGroup, targets: List<AppTarget>) {
        group.removeAllViews()
        if (targets.isEmpty()) {
            group.addView(Chip(this).apply { text = getString(R.string.empty_targets_title); isClickable = false })
            return
        }
        targets.forEach { target ->
            group.addView(Chip(this).apply {
                text = target.appLabel
                chipIcon = runCatching { packageManager.getApplicationIcon(target.packageName) }.getOrNull()
                isClickable = false
                isCheckable = false
            })
        }
    }

    private fun populateShareChips(group: ChipGroup, targets: List<ShareTarget>, maxDisplay: Int = 12) {
        group.removeAllViews()
        if (targets.isEmpty()) {
            group.addView(Chip(this).apply { text = getString(R.string.empty_targets_title); isClickable = false })
            return
        }
        targets.take(maxDisplay).forEach { target ->
            group.addView(Chip(this).apply {
                text = target.appLabel
                chipIcon = runCatching { packageManager.getApplicationIcon(target.packageName) }.getOrNull()
                isClickable = false
                isCheckable = false
            })
        }
        if (targets.size > maxDisplay) {
            group.addView(Chip(this).apply { text = "+${targets.size - maxDisplay} more"; isClickable = false })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
    }

    companion object {
        const val EXTRA_INITIAL_URL = "extra_initial_url"
    }
}
