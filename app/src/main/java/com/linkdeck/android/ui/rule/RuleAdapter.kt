package com.linkdeck.android.ui.rule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.linkdeck.android.R
import com.linkdeck.android.core.rule.RoutingRule

/**
 * Adapter for displaying, enabling/disabling, editing, and deleting routing rules.
 */
class RuleAdapter(
    private val onToggleEnabled: (RoutingRule, Boolean) -> Unit,
    private val onEditClicked: (RoutingRule) -> Unit,
    private val onDeleteClicked: (RoutingRule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.RuleViewHolder>() {

    private val items = mutableListOf<RoutingRule>()

    fun submitList(newItems: List<RoutingRule>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return RuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textCondition: TextView = itemView.findViewById(R.id.textRuleCondition)
        private val textTarget: TextView = itemView.findViewById(R.id.textRuleTarget)
        private val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switchRuleEnabled)
        private val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEditRule)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteRule)

        fun bind(rule: RoutingRule) {
            textCondition.text = rule.displayCondition
            textTarget.text = "Opens in ${rule.appLabel}"

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = rule.isEnabled
            switchEnabled.contentDescription = "Toggle rule for ${rule.displayCondition}"

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(rule, isChecked)
            }

            btnEdit.contentDescription = "Edit rule for ${rule.displayCondition}"
            btnEdit.setOnClickListener {
                onEditClicked(rule)
            }

            btnDelete.contentDescription = "Delete rule for ${rule.displayCondition}"
            btnDelete.setOnClickListener {
                onDeleteClicked(rule)
            }

            itemView.alpha = if (rule.isEnabled) 1.0f else 0.5f
        }
    }
}
