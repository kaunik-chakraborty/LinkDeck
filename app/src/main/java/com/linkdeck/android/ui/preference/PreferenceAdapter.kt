package com.linkdeck.android.ui.preference

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.R
import com.linkdeck.android.core.preference.RoutingPreference

/**
 * Adapter for presenting and managing saved user routing preferences in the dashboard.
 */
class PreferenceAdapter(
    private val onForgetClicked: (RoutingPreference) -> Unit
) : RecyclerView.Adapter<PreferenceAdapter.PreferenceViewHolder>() {

    private val items = mutableListOf<RoutingPreference>()

    fun submitList(newItems: List<RoutingPreference>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreferenceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preference, parent, false)
        return PreferenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PreferenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDomain: TextView = itemView.findViewById(R.id.textPrefDomain)
        private val textAppLabel: TextView = itemView.findViewById(R.id.textPrefAppLabel)
        private val btnForget: MaterialButton = itemView.findViewById(R.id.btnForgetPreference)

        fun bind(preference: RoutingPreference) {
            textDomain.text = preference.domain
            textAppLabel.text = itemView.context.getString(R.string.preference_saved_toast, preference.domain, preference.appLabel)
                .replace("Always opening ", "Opens in ")

            btnForget.contentDescription = "Forget preference for ${preference.domain}"
            btnForget.setOnClickListener {
                onForgetClicked(preference)
            }
        }
    }
}
