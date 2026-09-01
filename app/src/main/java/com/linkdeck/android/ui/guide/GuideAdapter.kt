package com.linkdeck.android.ui.guide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.linkdeck.android.R

/**
 * High-performance ListAdapter for rendering data-driven Guide cards.
 */
class GuideAdapter : ListAdapter<GuideItem, GuideAdapter.GuideViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_guide_card, parent, false)
        return GuideViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GuideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textIndex: TextView = itemView.findViewById(R.id.guideItemIndex)
        private val textTitle: TextView = itemView.findViewById(R.id.guideItemTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.guideItemDescription)
        private val layoutExample: View = itemView.findViewById(R.id.guideItemExampleLayout)
        private val textExample: TextView = itemView.findViewById(R.id.guideItemExampleText)

        fun bind(item: GuideItem) {
            textIndex.text = item.index.toString()
            textTitle.text = item.title
            textDescription.text = item.description

            if (item.example != null) {
                layoutExample.visibility = View.VISIBLE
                textExample.text = item.example
            } else {
                layoutExample.visibility = View.GONE
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<GuideItem>() {
        override fun areItemsTheSame(oldItem: GuideItem, newItem: GuideItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GuideItem, newItem: GuideItem): Boolean = oldItem == newItem
    }
}
