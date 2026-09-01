package com.linkdeck.android.ui.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.preference.SharedPreferencesRoutingPreferenceStore
import com.linkdeck.android.core.rule.RoutingRule
import com.linkdeck.android.core.rule.SaveRuleResult
import com.linkdeck.android.core.rule.SharedPreferencesRoutingRuleStore
import com.linkdeck.android.ui.chooser.ChooserActivity
import com.linkdeck.android.ui.preference.PreferenceAdapter
import com.linkdeck.android.ui.rule.RuleAdapter
import com.linkdeck.android.ui.rule.RuleEditorBottomSheet

/**
 * Home dashboard fragment hosting default browser management,
 * custom routing rules, saved preferences, and quick link testing.
 */
class HomeFragment : Fragment() {

    private val ruleStore by lazy { SharedPreferencesRoutingRuleStore(requireContext()) }
    private val preferenceStore by lazy { SharedPreferencesRoutingPreferenceStore(requireContext()) }

    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var preferenceAdapter: PreferenceAdapter

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateBrowserStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons(view)
        setupRulesList(view)
        setupPreferencesList(view)
    }

    override fun onResume() {
        super.onResume()
        updateBrowserStatus()
        updateRulesList()
        updatePreferencesList()
    }

    private fun setupRulesList(root: View) {
        val recyclerView: RecyclerView = root.findViewById(R.id.rulesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        ruleAdapter = RuleAdapter(
            onToggleEnabled = { rule, isEnabled ->
                ruleStore.toggleRuleEnabled(rule.id, isEnabled)
                com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
                updateRulesList()
            },
            onEditClicked = { rule ->
                showRuleEditor(rule)
            },
            onDeleteClicked = { rule ->
                ruleStore.deleteRule(rule.id)
                com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
                Toast.makeText(
                    requireContext(),
                    getString(R.string.rule_deleted_toast, rule.displayCondition),
                    Toast.LENGTH_SHORT
                ).show()
                updateRulesList()
            }
        )
        recyclerView.adapter = ruleAdapter

        val btnAddRule: MaterialButton = root.findViewById(R.id.btnAddRule)
        btnAddRule.setOnClickListener {
            showRuleEditor(null)
        }
    }

    private fun showRuleEditor(rule: RoutingRule?) {
        val dialog = RuleEditorBottomSheet.newInstance(rule)
        dialog.onRuleSaved = { savedRule ->
            when (val res = ruleStore.saveRule(savedRule)) {
                is SaveRuleResult.Success -> {
                    com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.rule_saved_toast, savedRule.displayCondition),
                        Toast.LENGTH_SHORT
                    ).show()
                    updateRulesList()
                }
                is SaveRuleResult.LimitReached -> {
                    Toast.makeText(requireContext(), R.string.rule_limit_reached, Toast.LENGTH_LONG).show()
                }
                is SaveRuleResult.Conflict -> {
                    Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
                }
                is SaveRuleResult.Invalid -> {
                    Toast.makeText(requireContext(), res.reason, Toast.LENGTH_LONG).show()
                }
                is SaveRuleResult.Error -> {
                    Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show(childFragmentManager, RuleEditorBottomSheet.TAG)
    }

    fun updateRulesList() {
        val root = view ?: return
        val rules = ruleStore.getRules()
        val emptyView: View = root.findViewById(R.id.rulesEmptyState)
        val recyclerView: RecyclerView = root.findViewById(R.id.rulesRecyclerView)

        if (rules.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            ruleAdapter.submitList(rules)
        }
    }

    private fun setupPreferencesList(root: View) {
        val recyclerView: RecyclerView = root.findViewById(R.id.preferencesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        preferenceAdapter = PreferenceAdapter { preference ->
            preferenceStore.removePreference(preference.domain)
            com.linkdeck.android.widget.WidgetUpdateHelper.updateAllWidgets(requireContext())
            Toast.makeText(
                requireContext(),
                getString(R.string.preference_removed_toast, preference.domain),
                Toast.LENGTH_SHORT
            ).show()
            updatePreferencesList()
        }
        recyclerView.adapter = preferenceAdapter
    }

    fun updatePreferencesList() {
        val root = view ?: return
        val preferences = preferenceStore.getAllPreferences()
        val emptyView: View = root.findViewById(R.id.preferencesEmptyState)
        val recyclerView: RecyclerView = root.findViewById(R.id.preferencesRecyclerView)

        if (preferences.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            preferenceAdapter.submitList(preferences)
        }
    }

    private fun setupButtons(root: View) {
        val btnSetDefault: MaterialButton = root.findViewById(R.id.btnSetDefaultBrowser)
        val btnTestLink: MaterialButton = root.findViewById(R.id.btnTestLink)
        val editUrl: TextInputEditText = root.findViewById(R.id.editTestUrl)

        btnSetDefault.setOnClickListener {
            requestDefaultBrowserRole()
        }

        val homeScroll: androidx.core.widget.NestedScrollView? = root.findViewById(R.id.homeScroll)
        editUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editUrl.postDelayed({
                    homeScroll?.fullScroll(View.FOCUS_DOWN)
                }, 250)
            }
        }

        btnTestLink.setOnClickListener {
            val inputUrl = editUrl.text?.toString()?.trim()
            val sanitizationResult = IntentSanitizer.sanitizeUrl(inputUrl)

            when (sanitizationResult) {
                is SanitizationResult.Success -> {
                    val testIntent = Intent(requireContext(), ChooserActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(sanitizationResult.link.rawUrl)
                    }
                    startActivity(testIntent)
                }
                is SanitizationResult.Error -> {
                    Toast.makeText(requireContext(), sanitizationResult.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateBrowserStatus() {
        val root = view ?: return
        val isDefault = isDefaultBrowser()
        val textTitle: TextView = root.findViewById(R.id.textStatusTitle)
        val textDesc: TextView = root.findViewById(R.id.textStatusDescription)
        val statusDot: View = root.findViewById(R.id.viewStatusDot)
        val btnSetDefault: MaterialButton = root.findViewById(R.id.btnSetDefaultBrowser)

        if (isDefault) {
            textTitle.text = getString(R.string.status_active_title)
            textDesc.text = getString(R.string.status_active_desc)
            statusDot.setBackgroundResource(R.drawable.bg_status_active)
            btnSetDefault.visibility = View.GONE
        } else {
            textTitle.text = getString(R.string.status_inactive_title)
            textDesc.text = getString(R.string.status_inactive_desc)
            statusDot.setBackgroundResource(R.drawable.bg_status_inactive)
            btnSetDefault.visibility = View.VISIBLE
        }
    }

    private fun isDefaultBrowser(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            false
        }
    }

    private fun requestDefaultBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                roleRequestLauncher.launch(intent)
                return
            }
        }

        try {
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(fallbackIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.status_inactive_desc, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
