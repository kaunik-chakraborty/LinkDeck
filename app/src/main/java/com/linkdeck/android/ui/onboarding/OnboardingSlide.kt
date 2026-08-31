package com.linkdeck.android.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.linkdeck.android.R

/**
 * Data model defining content and visuals for an onboarding walkthrough page.
 */
data class OnboardingSlide(
    @DrawableRes val iconRes: Int,
    @StringRes val badgeRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val featureResList: List<Int>,
    val isFinalSlide: Boolean = false
) {
    companion object {
        fun getSlides(): List<OnboardingSlide> {
            return listOf(
                OnboardingSlide(
                    iconRes = R.drawable.link,
                    badgeRes = R.string.onboarding_badge_1,
                    titleRes = R.string.onboarding_title_1,
                    descRes = R.string.onboarding_desc_1,
                    featureResList = listOf(
                        R.string.onboarding_feat_1_1,
                        R.string.onboarding_feat_1_2,
                        R.string.onboarding_feat_1_3
                    ),
                    isFinalSlide = false
                ),
                OnboardingSlide(
                    iconRes = R.drawable.shieldcheck,
                    badgeRes = R.string.onboarding_badge_2,
                    titleRes = R.string.onboarding_title_2,
                    descRes = R.string.onboarding_desc_2,
                    featureResList = listOf(
                        R.string.onboarding_feat_2_1,
                        R.string.onboarding_feat_2_2,
                        R.string.onboarding_feat_2_3
                    ),
                    isFinalSlide = false
                ),
                OnboardingSlide(
                    iconRes = R.drawable.edit,
                    badgeRes = R.string.onboarding_badge_3,
                    titleRes = R.string.onboarding_title_3,
                    descRes = R.string.onboarding_desc_3,
                    featureResList = listOf(
                        R.string.onboarding_feat_3_1,
                        R.string.onboarding_feat_3_2,
                        R.string.onboarding_feat_3_3
                    ),
                    isFinalSlide = false
                ),
                OnboardingSlide(
                    iconRes = R.drawable.redirect,
                    badgeRes = R.string.onboarding_badge_4,
                    titleRes = R.string.onboarding_title_4,
                    descRes = R.string.onboarding_desc_4,
                    featureResList = listOf(
                        R.string.onboarding_feat_4_1,
                        R.string.onboarding_feat_4_2,
                        R.string.onboarding_feat_4_3
                    ),
                    isFinalSlide = true
                )
            )
        }
    }
}
