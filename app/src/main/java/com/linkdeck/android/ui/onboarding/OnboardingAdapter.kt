package com.linkdeck.android.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.linkdeck.android.R

/**
 * Adapter for presenting the interactive onboarding walkthrough slides in ViewPager2.
 */
class OnboardingAdapter(
    private val slides: List<OnboardingSlide>
) : RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_slide, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    inner class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.imageSlideIcon)
        private val textBadge: TextView = itemView.findViewById(R.id.textSlideBadge)
        private val textTitle: TextView = itemView.findViewById(R.id.textSlideTitle)
        private val textDesc: TextView = itemView.findViewById(R.id.textSlideDesc)
        private val textFeature1: TextView = itemView.findViewById(R.id.textFeature1)
        private val textFeature2: TextView = itemView.findViewById(R.id.textFeature2)
        private val textFeature3: TextView = itemView.findViewById(R.id.textFeature3)

        fun bind(slide: OnboardingSlide) {
            iconView.setImageResource(slide.iconRes)
            textBadge.setText(slide.badgeRes)
            textTitle.setText(slide.titleRes)
            textDesc.setText(slide.descRes)

            if (slide.featureResList.size >= 3) {
                textFeature1.setText(slide.featureResList[0])
                textFeature2.setText(slide.featureResList[1])
                textFeature3.setText(slide.featureResList[2])
            }
        }
    }
}
