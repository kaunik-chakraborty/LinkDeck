package com.linkdeck.android.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.linkdeck.android.R
import com.linkdeck.android.core.typography.AppFont
import com.linkdeck.android.ui.base.BaseActivity

/**
 * Dedicated 2-tab Features & Architecture Guide activity.
 * Tab 1: Everyday user guide with clear examples for non-technical users.
 * Tab 2: Deep technical and security specifications for power users and engineers.
 */
class FeaturesGuideActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_features_guide)

        val toolbar: MaterialToolbar = findViewById(R.id.guideToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val collapsingToolbar: CollapsingToolbarLayout = findViewById(R.id.guideCollapsingToolbar)
        val font = AppFont.fromKey(appSettingsStore.appFontKey)
        font.fontRes?.let { resId ->
            val typeface = ResourcesCompat.getFont(this, resId)
            if (typeface != null) {
                collapsingToolbar.setExpandedTitleTypeface(typeface)
                collapsingToolbar.setCollapsedTitleTypeface(typeface)
            }
        }

        val tabLayout: TabLayout = findViewById(R.id.guideTabLayout)
        val viewPager: ViewPager2 = findViewById(R.id.guideViewPager)
        viewPager.adapter = GuidePagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.guide_tab_simple)
                1 -> getString(R.string.guide_tab_technical)
                else -> ""
            }
        }.attach()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.guideCoordinator)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
    }

    private class GuidePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> GuideSimpleFragment()
                1 -> GuideTechnicalFragment()
                else -> GuideSimpleFragment()
            }
        }
    }

    class GuideSimpleFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_guide_list, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val recyclerView: RecyclerView = view.findViewById(R.id.guideRecyclerView)
            val adapter = GuideAdapter()
            recyclerView.adapter = adapter
            adapter.submitList(GuideRepository.getSimpleGuideItems())
        }
    }

    class GuideTechnicalFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_guide_list, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val recyclerView: RecyclerView = view.findViewById(R.id.guideRecyclerView)
            val adapter = GuideAdapter()
            recyclerView.adapter = adapter
            adapter.submitList(GuideRepository.getTechnicalGuideItems())
        }
    }
}
