package com.linkdeck.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.linkdeck.android.R
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.core.typography.AppFont
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Settings fragment hosting appearance, typography, dynamic colors, link handling options,
 * tracking protection toggles, redirect resolution, and data reset tools.
 */
class SettingsFragment : Fragment() {

    private val settingsStore by lazy { AppSettingsStore(requireContext()) }
    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(requireContext()) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(requireContext()) }

    private lateinit var switchAutoRouting: MaterialSwitch
    private lateinit var switchRememberChoices: MaterialSwitch
    private lateinit var switchTrackingCleaner: MaterialSwitch
    private lateinit var switchRedirectChecking: MaterialSwitch
    private lateinit var switchThreatWarnings: MaterialSwitch
    private lateinit var switchTlsInspection: MaterialSwitch
    private lateinit var switchShareCleaning: MaterialSwitch
    private lateinit var switchCnameDetection: MaterialSwitch
    private lateinit var switchDeAmping: MaterialSwitch
    private lateinit var switchDynamicColor: MaterialSwitch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindAppearanceActions(view)
        bindSwitches(view)
        bindDataActions(view)
        bindDiagnosticsAction(view)
        bindAboutActions(view)
    }

    override fun onResume() {
        super.onResume()
        refreshSwitchStates()
    }

    private fun bindAppearanceActions(root: View) {
        val rowTheme = root.findViewById<View>(R.id.rowThemeSelection) ?: return
        val textTheme = root.findViewById<TextView>(R.id.textCurrentTheme) ?: return
        val rowFont = root.findViewById<View>(R.id.rowFontSelection) ?: return
        val textFont = root.findViewById<TextView>(R.id.textCurrentFont) ?: return
        val rowDynamicColor = root.findViewById<View>(R.id.rowDynamicColor) ?: return
        switchDynamicColor = root.findViewById(R.id.switchDynamicColor)

        fun updateThemeLabel() {
            textTheme.text = when (settingsStore.appThemeMode) {
                AppSettingsStore.THEME_LIGHT -> "Light theme"
                AppSettingsStore.THEME_DARK -> "Dark theme"
                else -> "System default"
            }
        }

        fun updateFontLabel() {
            val currentFont = AppFont.fromKey(settingsStore.appFontKey)
            textFont.text = "${currentFont.displayName} (${currentFont.description})"
        }

        updateThemeLabel()
        updateFontLabel()

        // Theme mode dialog
        rowTheme.setOnClickListener {
            val options = arrayOf("System default", "Light theme", "Dark theme")
            val currentSelected = when (settingsStore.appThemeMode) {
                AppSettingsStore.THEME_LIGHT -> 1
                AppSettingsStore.THEME_DARK -> 2
                else -> 0
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose app theme")
                .setSingleChoiceItems(options, currentSelected) { dialog, which ->
                    val newMode = when (which) {
                        1 -> AppSettingsStore.THEME_LIGHT
                        2 -> AppSettingsStore.THEME_DARK
                        else -> AppSettingsStore.THEME_SYSTEM
                    }
                    settingsStore.appThemeMode = newMode
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    updateThemeLabel()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        // Font selection dialog
        rowFont.setOnClickListener {
            val allFonts = AppFont.entries.toTypedArray()
            val fontLabels = allFonts.map { "${it.displayName}\n${it.description}" }.toTypedArray()
            val currentFont = AppFont.fromKey(settingsStore.appFontKey)
            val currentIdx = allFonts.indexOf(currentFont).coerceAtLeast(0)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose app font")
                .setSingleChoiceItems(fontLabels, currentIdx) { dialog, which ->
                    val selectedFont = allFonts[which]
                    if (selectedFont.key != settingsStore.appFontKey) {
                        settingsStore.appFontKey = selectedFont.key
                        updateFontLabel()
                        dialog.dismiss()
                        requireActivity().recreate()
                    } else {
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        val dividerDynamicColor = root.findViewById<View>(R.id.dividerDynamicColor)

        // Dynamic colors setup: Only visible on supported Android 12+ (API 31+) devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()) {
            rowDynamicColor.visibility = View.VISIBLE
            dividerDynamicColor?.visibility = View.VISIBLE
            switchDynamicColor.isChecked = settingsStore.isDynamicColorEnabled
            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                settingsStore.isDynamicColorEnabled = isChecked
                requireActivity().recreate()
            }
        } else {
            rowDynamicColor.visibility = View.GONE
            dividerDynamicColor?.visibility = View.GONE
        }
    }

    private fun bindSwitches(root: View) {
        switchAutoRouting = root.findViewById(R.id.switchAutoRouting)
        switchRememberChoices = root.findViewById(R.id.switchRememberChoices)
        switchTrackingCleaner = root.findViewById(R.id.switchTrackingCleaner)
        switchRedirectChecking = root.findViewById(R.id.switchRedirectChecking)
        switchThreatWarnings = root.findViewById(R.id.switchThreatWarnings)
        switchTlsInspection = root.findViewById(R.id.switchTlsInspection)
        switchShareCleaning = root.findViewById(R.id.switchShareCleaning)
        switchCnameDetection = root.findViewById(R.id.switchCnameDetection)
        switchDeAmping = root.findViewById(R.id.switchDeAmping)

        refreshSwitchStates()

        switchAutoRouting.setOnCheckedChangeListener { _, isChecked -> settingsStore.isAutomaticRoutingEnabled = isChecked }
        switchRememberChoices.setOnCheckedChangeListener { _, isChecked -> settingsStore.isRememberChoicesEnabled = isChecked }
        switchTrackingCleaner.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.isTrackingCleanerEnabled = isChecked
            com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
        }
        switchRedirectChecking.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.isRedirectCheckingEnabled = isChecked
            com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
        }
        switchThreatWarnings.setOnCheckedChangeListener { _, isChecked -> settingsStore.isThreatWarningsEnabled = isChecked }
        switchTlsInspection.setOnCheckedChangeListener { _, isChecked -> settingsStore.isTlsInspectionEnabled = isChecked }
        switchShareCleaning.setOnCheckedChangeListener { _, isChecked -> settingsStore.isShareCleaningEnabled = isChecked }
        switchCnameDetection.setOnCheckedChangeListener { _, isChecked -> settingsStore.isCnameDetectionEnabled = isChecked }
        switchDeAmping.setOnCheckedChangeListener { _, isChecked -> settingsStore.isDeAmpingEnabled = isChecked }

        root.findViewById<View>(R.id.rowCustomParameterRules).setOnClickListener {
            startActivity(Intent(requireContext(), com.linkdeck.android.ui.rules.CustomParameterRulesActivity::class.java))
        }
    }

    fun refreshSwitchStates() {
        if (!::switchAutoRouting.isInitialized) return
        switchAutoRouting.isChecked = settingsStore.isAutomaticRoutingEnabled
        switchRememberChoices.isChecked = settingsStore.isRememberChoicesEnabled
        switchTrackingCleaner.isChecked = settingsStore.isTrackingCleanerEnabled
        switchRedirectChecking.isChecked = settingsStore.isRedirectCheckingEnabled
        switchThreatWarnings.isChecked = settingsStore.isThreatWarningsEnabled
        switchTlsInspection.isChecked = settingsStore.isTlsInspectionEnabled
        switchShareCleaning.isChecked = settingsStore.isShareCleaningEnabled
        switchCnameDetection.isChecked = settingsStore.isCnameDetectionEnabled
        switchDeAmping.isChecked = settingsStore.isDeAmpingEnabled
        if (::switchDynamicColor.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switchDynamicColor.isChecked = settingsStore.isDynamicColorEnabled
        }
    }

    private fun bindDataActions(root: View) {
        root.findViewById<View>(R.id.btnRowClearPreferences).setOnClickListener {
            showConfirmDialog(R.string.setting_clear_preferences_title, R.string.dialog_clear_preferences_msg, R.string.btn_clear) {
                preferenceStore.clearAll()
                Toast.makeText(requireContext(), R.string.toast_preferences_cleared, Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<View>(R.id.btnRowClearRules).setOnClickListener {
            showConfirmDialog(R.string.setting_clear_rules_title, R.string.dialog_clear_rules_msg, R.string.btn_clear) {
                ruleStore.clearAll()
                Toast.makeText(requireContext(), R.string.toast_rules_cleared, Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<View>(R.id.btnRowResetSettings).setOnClickListener {
            showConfirmDialog(R.string.setting_reset_settings_title, R.string.dialog_reset_settings_msg, R.string.btn_reset) {
                settingsStore.resetSettings()
                refreshSwitchStates()
                Toast.makeText(requireContext(), R.string.toast_settings_reset, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindDiagnosticsAction(root: View) {
        root.findViewById<View>(R.id.btnRowTestLink).setOnClickListener {
            startActivity(Intent(requireContext(), TestLinkActivity::class.java))
        }
    }

    private fun bindAboutActions(root: View) {
        root.findViewById<View>(R.id.btnRowFeaturesGuide)?.setOnClickListener {
            startActivity(Intent(requireContext(), com.linkdeck.android.ui.guide.FeaturesGuideActivity::class.java))
        }
        root.findViewById<View>(R.id.btnRowWalkthrough)?.setOnClickListener {
            val intent = com.linkdeck.android.ui.onboarding.OnboardingActivity.createIntent(requireContext(), isReplay = true)
            startActivity(intent)
        }
        root.findViewById<View>(R.id.btnRowGithub)?.setOnClickListener {
            val url = getString(R.string.github_repo_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), url, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmDialog(titleRes: Int, messageRes: Int, positiveBtnRes: Int, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(positiveBtnRes) { _, _ -> onConfirm() }
            .show()
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
