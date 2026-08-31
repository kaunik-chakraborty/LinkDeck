package com.linkdeck.android.ui.testlink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.inspector.UrlRedactor
import com.linkdeck.android.core.intent.AppResolver
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.TargetCategory
import com.linkdeck.android.core.network.RedirectErrorType
import com.linkdeck.android.core.network.RedirectResolver
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.RoutingRuleMatcher
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.ui.base.BaseActivity
import com.linkdeck.android.ui.chooser.ChooserActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Diagnostic testing screen allowing users to input a URL and preview
 * link sanitization, redirect resolution, tracking parameter stripping,
 * rule matching, and candidate app discovery without automatically launching.
 */
class TestLinkActivity : BaseActivity() {

    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(this) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(this) }
    private val redirectResolver = RedirectResolver()

    private var currentJob: Job? = null
    private var lastTestedLink: SanitizedLink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_link)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.testLinkRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupInspectAction()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.testLinkToolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupInspectAction() {
        val editInput: TextInputEditText = findViewById(R.id.editTestLinkInput)
        val btnRunTest: MaterialButton = findViewById(R.id.btnRunTest)
        val btnOpenInChooser: MaterialButton = findViewById(R.id.btnOpenInChooser)

        btnRunTest.setOnClickListener {
            val raw = editInput.text?.toString()?.trim()
            if (raw.isNullOrBlank()) {
                Toast.makeText(this, R.string.error_invalid_link_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runDiagnostic(raw)
        }

        btnOpenInChooser.setOnClickListener {
            val link = lastTestedLink
            if (link != null) {
                val intent = Intent(this, ChooserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(link.rawUrl)
                }
                startActivity(intent)
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
            val redirectResult = if (appSettingsStore.isRedirectCheckingEnabled) {
                redirectResolver.resolve(initialLink)
            } else {
                RedirectResult.NoRedirect(initialLink.rawUrl)
            }

            if (!isActive || isFinishing) return@launch

            val effectiveLink = when (redirectResult) {
                is RedirectResult.Success -> {
                    val sanitizedFinal = IntentSanitizer.sanitizeUrl(redirectResult.finalUrl)
                    if (sanitizedFinal is SanitizationResult.Success) sanitizedFinal.link else initialLink
                }
                is RedirectResult.NoRedirect -> initialLink
                is RedirectResult.Error -> initialLink
            }

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

            lastTestedLink = candidateLink

            // Routing checks
            val matchingRule = RoutingRuleMatcher.findBestMatch(candidateLink, ruleStore.getRules())
            val savedPref = preferenceStore.getPreference(candidateLink.host)

            val openResolver = AppResolver(packageManager, packageName)
            val openTargets = openResolver.resolve(candidateLink)

            val browserPackages = openTargets.filter {
                it.category == TargetCategory.BROWSER || it.isBrowser
            }.map { it.packageName }.toSet()

            val shareResolver = com.linkdeck.android.core.intent.ShareTargetResolver(packageManager, packageName)
            val shareTargets = shareResolver.resolve(candidateLink, excludedPackageNames = browserPackages)

            // Render Results
            val textOriginal: TextView = findViewById(R.id.textTestOriginal)
            val textRedirects: TextView = findViewById(R.id.textTestRedirects)
            val textCleaned: TextView = findViewById(R.id.textTestCleaned)
            val textRoutingRule: TextView = findViewById(R.id.textTestRoutingRule)
            val textTargetApps: TextView = findViewById(R.id.textTestTargetApps)

            textOriginal.text = UrlRedactor.redact(initialLink)

            textRedirects.text = when (redirectResult) {
                is RedirectResult.Success -> {
                    val hopsStr = redirectResult.hops.joinToString("\n") {
                        "• HTTP ${it.statusCode} → ${UrlRedactor.redactUrlString(it.targetUrl)}"
                    }
                    "${redirectResult.hops.size} redirect(s):\n$hopsStr"
                }
                is RedirectResult.NoRedirect -> getString(R.string.inspector_no_redirects)
                is RedirectResult.Error -> when (redirectResult.errorType) {
                    RedirectErrorType.BLOCKED_PRIVATE_ADDRESS -> getString(R.string.inspector_redirect_blocked)
                    RedirectErrorType.REDIRECT_LOOP -> getString(R.string.inspector_redirect_loop)
                    RedirectErrorType.TOO_MANY_REDIRECTS -> getString(R.string.inspector_redirect_too_many)
                    else -> getString(R.string.inspector_redirect_unreachable)
                }
            }

            textCleaned.text = if (wasCleaned) {
                UrlRedactor.redact(candidateLink) + "\n(" + getString(R.string.inspector_tracking_cleaned, removedParams.size) + ")"
            } else {
                UrlRedactor.redact(candidateLink)
            }

            textRoutingRule.text = when {
                matchingRule != null -> getString(R.string.inspector_rule_matched, matchingRule.displayCondition) + " → ${matchingRule.appLabel}"
                savedPref != null -> getString(R.string.inspector_pref_matched, savedPref.domain) + " → ${savedPref.appLabel}"
                else -> getString(R.string.inspector_manual_choice)
            }

            val openSummary = if (openTargets.isNotEmpty()) {
                openTargets.joinToString(", ") { it.appLabel }
            } else {
                "None"
            }
            val shareSummary = if (shareTargets.isNotEmpty()) {
                shareTargets.joinToString(", ") { it.appLabel }
            } else {
                "None"
            }
            textTargetApps.text = "Open with: $openSummary\nShare with: $shareSummary"

            loadingLayout.visibility = View.GONE
            resultsCard.visibility = View.VISIBLE
            btnRunTest.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
    }
}
