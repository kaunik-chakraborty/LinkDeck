package com.linkdeck.android.ui.rules

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.rules.CustomParameterRule
import com.linkdeck.android.core.cleaner.rules.CustomParameterRulesStore
import com.linkdeck.android.ui.base.BaseActivity

/**
 * Management activity allowing power users to view, add, toggle, and delete
 * custom tracking parameter stripping and allowlist rules.
 * Inherits BaseActivity for Material You dynamic colors and font overlay support.
 */
class CustomParameterRulesActivity : BaseActivity() {

    private val rulesStore by lazy { CustomParameterRulesStore(this) }
    private lateinit var adapter: CustomRuleAdapter
    private lateinit var recyclerCustomRules: RecyclerView
    private lateinit var viewEmptyState: View
    private lateinit var fabAddRule: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_rules)

        val appBar = findViewById<View>(R.id.rulesAppBar)
        val toolbar = findViewById<MaterialToolbar>(R.id.rulesToolbar)
        recyclerCustomRules = findViewById(R.id.recyclerCustomRules)
        viewEmptyState = findViewById(R.id.viewEmptyState)
        fabAddRule = findViewById(R.id.fabAddRule)
        val btnLoadPresets = findViewById<MaterialButton>(R.id.btnLoadPresets)
        val btnEmptyAddRule = findViewById<MaterialButton>(R.id.btnEmptyAddRule)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rulesCoordinator)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = systemBars.top)
            recyclerCustomRules.updatePadding(bottom = systemBars.bottom + 96)
            fabAddRule.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (20 * resources.displayMetrics.density).toInt()
                rightMargin = systemBars.right + (20 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        adapter = CustomRuleAdapter(
            onToggle = { rule, isChecked ->
                rulesStore.toggleRule(rule.id, isChecked)
            },
            onDelete = { rule ->
                showDeleteConfirmationDialog(rule)
            }
        )

        recyclerCustomRules.layoutManager = LinearLayoutManager(this)
        recyclerCustomRules.adapter = adapter

        fabAddRule.setOnClickListener {
            showAddRuleSheet()
        }

        btnEmptyAddRule.setOnClickListener {
            showAddRuleSheet()
        }

        btnLoadPresets.setOnClickListener {
            val count = rulesStore.loadPresets()
            if (count > 0) {
                Toast.makeText(this, getString(R.string.custom_rules_presets_loaded, count), Toast.LENGTH_SHORT).show()
                refreshRulesList()
            } else {
                Toast.makeText(this, getString(R.string.custom_rules_presets_already_exist), Toast.LENGTH_SHORT).show()
            }
        }

        refreshRulesList()
    }

    private fun refreshRulesList() {
        val rules = rulesStore.getRules()
        adapter.submitList(rules)

        if (rules.isEmpty()) {
            viewEmptyState.visibility = View.VISIBLE
            recyclerCustomRules.visibility = View.GONE
            fabAddRule.visibility = View.GONE
        } else {
            viewEmptyState.visibility = View.GONE
            recyclerCustomRules.visibility = View.VISIBLE
            fabAddRule.visibility = View.VISIBLE
        }
    }

    private fun showAddRuleSheet() {
        val sheet = AddParameterRuleBottomSheet.newInstance()
        sheet.onRuleCreated = { newRule ->
            rulesStore.saveRule(newRule)
            refreshRulesList()
        }
        sheet.show(supportFragmentManager, AddParameterRuleBottomSheet.TAG)
    }

    private fun showDeleteConfirmationDialog(rule: CustomParameterRule) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_rules_dialog_delete_title)
            .setMessage(getString(R.string.custom_rules_dialog_delete_message, rule.parameterPattern))
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                rulesStore.deleteRule(rule.id)
                refreshRulesList()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
