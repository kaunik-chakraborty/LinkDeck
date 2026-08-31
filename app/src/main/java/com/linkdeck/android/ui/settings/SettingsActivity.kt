package com.linkdeck.android.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.linkdeck.android.R
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.core.typography.AppFont
import com.linkdeck.android.ui.base.BaseActivity
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Dedicated settings screen for configuring typography, app appearance, dynamic colors,
 * link handling, tracking protection, redirect inspection, and managing local data.
 */
class SettingsActivity : BaseActivity() {

    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(this) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(this) }

    private lateinit var switchAutoRouting: MaterialSwitch
    private lateinit var switchRememberChoices: MaterialSwitch
    private lateinit var switchTrackingCleaner: MaterialSwitch
    private lateinit var switchRedirectChecking: MaterialSwitch
    private lateinit var switchDynamicColor: MaterialSwitch

    private lateinit var tabHome: View
    private lateinit var tabSettings: View
    private lateinit var iconHome: ImageView
    private lateinit var iconSettings: ImageView
    private lateinit var textHome: TextView
    private lateinit var textSettings: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            val nav = findViewById<View>(R.id.cardFloatingNav)
            val params = nav.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.floating_nav_margin_bottom) + systemBars.bottom
                nav.layoutParams = params
            }
            insets
        }

        setupToolbar()
        setupNavigation()
        bindAppearanceActions()
        bindSwitches()
        bindDataActions()
        bindDiagnosticsAction()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupNavigation() {
        tabHome = findViewById(R.id.tabHome)
        tabSettings = findViewById(R.id.tabSettings)
        iconHome = findViewById(R.id.iconHome)
        iconSettings = findViewById(R.id.iconSettings)
        textHome = findViewById(R.id.textHome)
        textSettings = findViewById(R.id.textSettings)

        tabHome.setOnClickListener { finish() }
        tabSettings.setOnClickListener { updateNavigationState() }
        updateNavigationState()
    }

    private fun updateNavigationState() {
        val onPrimary = themeColor(com.google.android.material.R.attr.colorOnPrimary)
        val muted = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        tabHome.setBackgroundResource(R.drawable.bg_nav_tab_inactive)
        iconHome.imageTintList = ColorStateList.valueOf(muted)
        textHome.setTextColor(muted)
        textHome.setTypeface(null, Typeface.NORMAL)

        tabSettings.setBackgroundResource(R.drawable.bg_nav_tab_active)
        iconSettings.imageTintList = ColorStateList.valueOf(onPrimary)
        textSettings.setTextColor(onPrimary)
        textSettings.setTypeface(null, Typeface.BOLD)
        tabHome.isSelected = false
        tabSettings.isSelected = true
    }

    private fun bindAppearanceActions() {
        val rowTheme = findViewById<View>(R.id.rowThemeSelection) ?: return
        val textTheme = findViewById<TextView>(R.id.textCurrentTheme) ?: return
        val rowFont = findViewById<View>(R.id.rowFontSelection) ?: return
        val textFont = findViewById<TextView>(R.id.textCurrentFont) ?: return
        val rowDynamicColor = findViewById<View>(R.id.rowDynamicColor) ?: return
        switchDynamicColor = findViewById(R.id.switchDynamicColor)

        fun updateThemeLabel() {
            textTheme.text = when (appSettingsStore.appThemeMode) {
                AppSettingsStore.THEME_LIGHT -> "Light theme"
                AppSettingsStore.THEME_DARK -> "Dark theme"
                else -> "System default"
            }
        }

        fun updateFontLabel() {
            val currentFont = AppFont.fromKey(appSettingsStore.appFontKey)
            textFont.text = "${currentFont.displayName} (${currentFont.description})"
        }

        updateThemeLabel()
        updateFontLabel()

        rowTheme.setOnClickListener {
            val options = arrayOf("System default", "Light theme", "Dark theme")
            val currentSelected = when (appSettingsStore.appThemeMode) {
                AppSettingsStore.THEME_LIGHT -> 1
                AppSettingsStore.THEME_DARK -> 2
                else -> 0
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Choose app theme")
                .setSingleChoiceItems(options, currentSelected) { dialog, which ->
                    val newMode = when (which) {
                        1 -> AppSettingsStore.THEME_LIGHT
                        2 -> AppSettingsStore.THEME_DARK
                        else -> AppSettingsStore.THEME_SYSTEM
                    }
                    appSettingsStore.appThemeMode = newMode
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    updateThemeLabel()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        rowFont.setOnClickListener {
            val allFonts = AppFont.entries.toTypedArray()
            val fontLabels = allFonts.map { "${it.displayName}\n${it.description}" }.toTypedArray()
            val currentFont = AppFont.fromKey(appSettingsStore.appFontKey)
            val currentIdx = allFonts.indexOf(currentFont).coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle("Choose app font")
                .setSingleChoiceItems(fontLabels, currentIdx) { dialog, which ->
                    val selectedFont = allFonts[which]
                    if (selectedFont.key != appSettingsStore.appFontKey) {
                        appSettingsStore.appFontKey = selectedFont.key
                        updateFontLabel()
                        dialog.dismiss()
                        recreate()
                    } else {
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        val dividerDynamicColor = findViewById<View>(R.id.dividerDynamicColor)

        // Dynamic colors setup: Only visible on supported Android 12+ (API 31+) devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()) {
            rowDynamicColor.visibility = View.VISIBLE
            dividerDynamicColor?.visibility = View.VISIBLE
            switchDynamicColor.isChecked = appSettingsStore.isDynamicColorEnabled
            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                appSettingsStore.isDynamicColorEnabled = isChecked
                recreate()
            }
        } else {
            rowDynamicColor.visibility = View.GONE
            dividerDynamicColor?.visibility = View.GONE
        }
    }

    private fun bindSwitches() {
        switchAutoRouting = findViewById(R.id.switchAutoRouting)
        switchRememberChoices = findViewById(R.id.switchRememberChoices)
        switchTrackingCleaner = findViewById(R.id.switchTrackingCleaner)
        switchRedirectChecking = findViewById(R.id.switchRedirectChecking)

        refreshSwitchStates()

        switchAutoRouting.setOnCheckedChangeListener { _, isChecked ->
            appSettingsStore.isAutomaticRoutingEnabled = isChecked
        }

        switchRememberChoices.setOnCheckedChangeListener { _, isChecked ->
            appSettingsStore.isRememberChoicesEnabled = isChecked
        }

        switchTrackingCleaner.setOnCheckedChangeListener { _, isChecked ->
            appSettingsStore.isTrackingCleanerEnabled = isChecked
        }

        switchRedirectChecking.setOnCheckedChangeListener { _, isChecked ->
            appSettingsStore.isRedirectCheckingEnabled = isChecked
        }
    }

    private fun refreshSwitchStates() {
        switchAutoRouting.isChecked = appSettingsStore.isAutomaticRoutingEnabled
        switchRememberChoices.isChecked = appSettingsStore.isRememberChoicesEnabled
        switchTrackingCleaner.isChecked = appSettingsStore.isTrackingCleanerEnabled
        switchRedirectChecking.isChecked = appSettingsStore.isRedirectCheckingEnabled
        if (::switchDynamicColor.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switchDynamicColor.isChecked = appSettingsStore.isDynamicColorEnabled
        }
    }

    private fun bindDataActions() {
        findViewById<android.view.View>(R.id.btnRowClearPreferences).setOnClickListener {
            showClearPreferencesDialog()
        }

        findViewById<android.view.View>(R.id.btnRowClearRules).setOnClickListener {
            showClearRulesDialog()
        }

        findViewById<android.view.View>(R.id.btnRowResetSettings).setOnClickListener {
            showResetSettingsDialog()
        }
    }

    private fun bindDiagnosticsAction() {
        findViewById<android.view.View>(R.id.btnRowTestLink).setOnClickListener {
            startActivity(Intent(this, TestLinkActivity::class.java))
        }
    }

    private fun showClearPreferencesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_clear_preferences_title)
            .setMessage(R.string.dialog_clear_preferences_msg)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                preferenceStore.clearAll()
                Toast.makeText(this, R.string.toast_preferences_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showClearRulesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_clear_rules_title)
            .setMessage(R.string.dialog_clear_rules_msg)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                ruleStore.clearAll()
                Toast.makeText(this, R.string.toast_rules_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_reset_settings_title)
            .setMessage(R.string.dialog_reset_settings_msg)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_reset) { _, _ ->
                appSettingsStore.resetSettings()
                refreshSwitchStates()
                Toast.makeText(this, R.string.toast_settings_reset, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun themeColor(@AttrRes attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }
}
