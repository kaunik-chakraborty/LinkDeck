package com.linkdeck.android.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.rules.CustomParameterRule
import com.linkdeck.android.core.cleaner.rules.ParameterRuleAction
import java.util.Locale

/**
 * Bottom sheet dialog for creating and validating a new custom query parameter rule.
 */
class AddParameterRuleBottomSheet : BottomSheetDialogFragment() {

    var onRuleCreated: ((CustomParameterRule) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_add_parameter_rule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutInputParameter = view.findViewById<TextInputLayout>(R.id.layoutInputParameter)
        val editInputParameter = view.findViewById<TextInputEditText>(R.id.editInputParameter)
        val editInputDomain = view.findViewById<TextInputEditText>(R.id.editInputDomain)
        val radioGroupAction = view.findViewById<RadioGroup>(R.id.radioGroupAction)
        val btnSaveRule = view.findViewById<MaterialButton>(R.id.btnSaveRule)

        btnSaveRule.setOnClickListener {
            val rawParam = editInputParameter.text?.toString()?.trim() ?: ""
            val cleanedParam = rawParam.replace(" ", "").lowercase(Locale.ROOT)
            if (cleanedParam.isEmpty() || cleanedParam == "*") {
                layoutInputParameter.error = getString(R.string.sheet_add_rule_err_empty_param)
                return@setOnClickListener
            }
            layoutInputParameter.error = null

            val isPrefix = cleanedParam.endsWith("*")

            // Clean domain input (strip protocols and paths if user entered a full URL)
            val rawDomain = editInputDomain.text?.toString()?.trim() ?: ""
            val cleanedDomain = if (rawDomain.isNotEmpty()) {
                rawDomain
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore("/")
                    .substringBefore("?")
                    .substringBefore(":")
                    .lowercase(Locale.ROOT)
            } else {
                null
            }

            val action = if (radioGroupAction.checkedRadioButtonId == R.id.radioActionAllow) {
                ParameterRuleAction.ALLOW
            } else {
                ParameterRuleAction.BLOCK
            }

            val newRule = CustomParameterRule(
                parameterPattern = cleanedParam,
                isPrefix = isPrefix,
                domainPattern = cleanedDomain,
                action = action
            )

            onRuleCreated?.invoke(newRule)
            dismiss()
        }
    }

    companion object {
        const val TAG = "AddParameterRuleBottomSheet"

        fun newInstance(): AddParameterRuleBottomSheet {
            return AddParameterRuleBottomSheet()
        }
    }
}
