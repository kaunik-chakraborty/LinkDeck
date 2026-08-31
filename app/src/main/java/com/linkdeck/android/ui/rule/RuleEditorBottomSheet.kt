package com.linkdeck.android.ui.rule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.intent.AppResolver
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.rule.RoutingRule
import com.linkdeck.android.core.rule.RuleValidationResult
import java.util.UUID

/**
 * BottomSheet dialog for creating and editing structured routing rules.
 */
class RuleEditorBottomSheet : BottomSheetDialogFragment() {

    private var editingRule: RoutingRule? = null
    private var availableTargets: List<AppTarget> = emptyList()
    private var selectedTarget: AppTarget? = null

    var onRuleSaved: ((RoutingRule) -> Unit)? = null

    fun setEditingRule(rule: RoutingRule?) {
        this.editingRule = rule
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_rule_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 16)
            insets
        }

        val textTitle: TextView = view.findViewById(R.id.textEditorTitle)
        val editHost: TextInputEditText = view.findViewById(R.id.editRuleHost)
        val editPath: TextInputEditText = view.findViewById(R.id.editRulePath)
        val autoCompleteTarget: AutoCompleteTextView = view.findViewById(R.id.autoCompleteRuleTarget)
        val textError: TextView = view.findViewById(R.id.textRuleEditorError)
        val btnSave: MaterialButton = view.findViewById(R.id.btnSaveRule)
        val btnClose: View? = view.findViewById(R.id.btnRuleEditorClose)

        btnClose?.setOnClickListener {
            dismiss()
        }

        // Query available web handlers
        val pm = requireContext().packageManager
        val selfPkg = requireContext().packageName
        val resolver = AppResolver(pm, selfPkg)
        availableTargets = resolver.resolve(SanitizedLink("https://example.com", "example.com", "/"))

        val targetNames = availableTargets.map { it.appLabel }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, targetNames)
        autoCompleteTarget.setAdapter(adapter)

        autoCompleteTarget.setOnItemClickListener { _, _, position, _ ->
            selectedTarget = availableTargets.getOrNull(position)
            textError.visibility = View.GONE
        }

        val rule = editingRule
        if (rule != null) {
            textTitle.text = getString(R.string.edit_rule_title)
            editHost.setText(rule.host)
            editPath.setText(rule.pathPattern ?: "")

            val currentTarget = availableTargets.firstOrNull { it.packageName == rule.packageName }
            if (currentTarget != null) {
                selectedTarget = currentTarget
                autoCompleteTarget.setText(currentTarget.appLabel, false)
            } else {
                selectedTarget = AppTarget(rule.packageName, "", rule.appLabel)
                autoCompleteTarget.setText(rule.appLabel, false)
            }
        } else {
            textTitle.text = getString(R.string.add_rule_title)
        }

        btnSave.setOnClickListener {
            val rawHost = editHost.text?.toString() ?: ""
            val rawPath = editPath.text?.toString()
            val target = selectedTarget

            if (target == null) {
                textError.text = "Please select a target application"
                textError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val validation = RoutingRule.validate(
                rawHost = rawHost,
                rawPathPattern = rawPath,
                packageName = target.packageName,
                appLabel = target.appLabel,
                isEnabled = rule?.isEnabled ?: true,
                selfPackageName = selfPkg,
                id = rule?.id ?: UUID.randomUUID().toString(),
                createdAt = rule?.createdAt ?: System.currentTimeMillis()
            )

            when (validation) {
                is RuleValidationResult.Valid -> {
                    textError.visibility = View.GONE
                    onRuleSaved?.invoke(validation.rule)
                    dismiss()
                }
                is RuleValidationResult.Invalid -> {
                    textError.text = validation.reason
                    textError.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        const val TAG = "RuleEditorBottomSheet"

        fun newInstance(rule: RoutingRule? = null): RuleEditorBottomSheet {
            return RuleEditorBottomSheet().apply {
                setEditingRule(rule)
            }
        }
    }
}
