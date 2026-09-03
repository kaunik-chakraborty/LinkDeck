package com.linkdeck.android.ui.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.rules.CustomParameterRule
import com.linkdeck.android.core.cleaner.rules.ParameterRuleAction

/**
 * RecyclerView adapter presenting user-defined custom query parameter rules.
 * Handles instant toggling and rule deletion via callback lambdas.
 */
class CustomRuleAdapter(
    private val onToggle: (CustomParameterRule, Boolean) -> Unit,
    private val onDelete: (CustomParameterRule) -> Unit
) : ListAdapter<CustomParameterRule, CustomRuleAdapter.RuleViewHolder>(RuleDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_rule, parent, false)
        return RuleViewHolder(view, onToggle, onDelete)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RuleViewHolder(
        itemView: View,
        private val onToggle: (CustomParameterRule, Boolean) -> Unit,
        private val onDelete: (CustomParameterRule) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textParameterPattern: TextView = itemView.findViewById(R.id.textParameterPattern)
        private val switchRuleEnabled: MaterialSwitch = itemView.findViewById(R.id.switchRuleEnabled)
        private val chipRuleAction: TextView = itemView.findViewById(R.id.chipRuleAction)
        private val chipDomainScope: TextView = itemView.findViewById(R.id.chipDomainScope)
        private val btnDeleteRule: ImageButton = itemView.findViewById(R.id.btnDeleteRule)

        fun bind(rule: CustomParameterRule) {
            textParameterPattern.text = rule.parameterPattern

            // Switch toggle without re-triggering listener on rebinding
            switchRuleEnabled.setOnCheckedChangeListener(null)
            switchRuleEnabled.isChecked = rule.isEnabled
            switchRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }

            // Action chip (Block vs Allow)
            val context = itemView.context
            if (rule.action == ParameterRuleAction.BLOCK) {
                chipRuleAction.text = context.getString(R.string.custom_rule_action_block)
                chipRuleAction.setTextColor(ContextCompat.getColor(context, R.color.status_inactive))
            } else {
                chipRuleAction.text = context.getString(R.string.custom_rule_action_allow)
                chipRuleAction.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            }

            // Domain scope chip
            if (rule.domainPattern.isNullOrBlank()) {
                chipDomainScope.text = context.getString(R.string.custom_rule_scope_global)
            } else {
                chipDomainScope.text = rule.domainPattern
            }

            // Delete click
            btnDeleteRule.setOnClickListener {
                onDelete(rule)
            }
        }
    }

    private object RuleDiffCallback : DiffUtil.ItemCallback<CustomParameterRule>() {
        override fun areItemsTheSame(oldItem: CustomParameterRule, newItem: CustomParameterRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CustomParameterRule, newItem: CustomParameterRule): Boolean {
            return oldItem == newItem
        }
    }
}
