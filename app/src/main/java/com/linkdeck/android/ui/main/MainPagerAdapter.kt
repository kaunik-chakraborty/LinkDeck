package com.linkdeck.android.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.linkdeck.android.ui.home.HomeFragment
import com.linkdeck.android.ui.settings.SettingsFragment

/**
 * Pager adapter managing the Home dashboard and Settings fragments.
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment.newInstance()
            1 -> SettingsFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid page position: $position")
        }
    }
}
