package com.linkdeck.android

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.linkdeck.android.ui.base.BaseActivity
import com.linkdeck.android.ui.home.HomeFragment
import com.linkdeck.android.ui.settings.SettingsFragment

/**
 * Hosts the primary LinkDeck experience with a smooth animated ViewPager2
 * coordinating Home and Settings screens, large collapsing top app bar,
 * floating pill bottom navigation, and system-level predictive back gesture integration.
 */
class MainActivity : BaseActivity() {

    private lateinit var mainAppBar: AppBarLayout
    private lateinit var mainCollapsingToolbar: CollapsingToolbarLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabHome: View
    private lateinit var tabSettings: View
    private lateinit var iconHome: ImageView
    private lateinit var iconSettings: ImageView
    private lateinit var textHome: TextView
    private lateinit var textSettings: TextView

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (viewPager.currentItem != 0) {
                viewPager.setCurrentItem(0, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!appSettingsStore.isOnboardingCompleted) {
            startActivity(Intent(this, com.linkdeck.android.ui.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mainAppBar = findViewById(R.id.mainAppBar)
        mainCollapsingToolbar = findViewById(R.id.mainCollapsingToolbar)
        viewPager = findViewById(R.id.mainViewPager)
        tabHome = findViewById(R.id.tabHome)
        tabSettings = findViewById(R.id.tabSettings)
        iconHome = findViewById(R.id.iconHome)
        iconSettings = findViewById(R.id.iconSettings)
        textHome = findViewById(R.id.textHome)
        textSettings = findViewById(R.id.textSettings)

        val initialPage = savedInstanceState?.getInt(KEY_ACTIVE_PAGE, 0) ?: 0

        setupWindowInsets()
        setupViewPager(initialPage)
        setupNavigation()
        setupPredictiveBack()
        updateNavigationState(initialPage)
    }

    private val defaultBrowserLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        // Returned from system default browser selector
    }

    override fun onResume() {
        super.onResume()
        checkDefaultBrowserPrompt()
    }

    private fun checkDefaultBrowserPrompt() {
        if (!com.linkdeck.android.core.intent.DefaultBrowserHelper.isDefaultBrowser(this)) {
            val existing = supportFragmentManager.findFragmentByTag(com.linkdeck.android.ui.common.DefaultBrowserBottomSheet.TAG)
            if (existing == null) {
                val dialog = com.linkdeck.android.ui.common.DefaultBrowserBottomSheet.newInstance()
                dialog.onSetDefaultClicked = {
                    val intent = com.linkdeck.android.core.intent.DefaultBrowserHelper.createDefaultBrowserIntent(this)
                    try {
                        defaultBrowserLauncher.launch(intent)
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } catch (_: Exception) {}
                    }
                }
                dialog.show(supportFragmentManager, com.linkdeck.android.ui.common.DefaultBrowserBottomSheet.TAG)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_PAGE, viewPager.currentItem)
    }

    private fun setupWindowInsets() {
        val nav = findViewById<View>(R.id.cardFloatingNav)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val initialBottomMargin = resources.getDimensionPixelSize(R.dimen.floating_nav_margin_bottom)
        val initialStartMargin = resources.getDimensionPixelSize(R.dimen.floating_nav_margin_horizontal)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainCoordinator)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            view.setPadding(bars.left, bars.top, bars.right, if (imeVisible) ime.bottom else 0)
            nav.visibility = if (imeVisible) View.GONE else View.VISIBLE

            (nav.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
                if (isLandscape) {
                    params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    params.marginStart = initialStartMargin + bars.left
                    params.bottomMargin = 0
                } else {
                    params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    params.bottomMargin = initialBottomMargin + bars.bottom
                }
                nav.layoutParams = params
            }
            insets
        }
    }

    private fun setupViewPager(initialPage: Int) {
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        // Smooth subtle fade-scale page transformer
        viewPager.setPageTransformer { page, position ->
            page.apply {
                when {
                    position < -1 -> {
                        alpha = 0f
                    }
                    position <= 1 -> {
                        val factor = 1 - Math.abs(position)
                        alpha = 0.6f + (factor * 0.4f)
                        val scale = 0.97f + (factor * 0.03f)
                        scaleX = scale
                        scaleY = scale
                    }
                    else -> {
                        alpha = 0f
                    }
                }
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigationState(position)
                backPressedCallback.isEnabled = (position != 0)
                mainAppBar.setExpanded(true, true)
                mainCollapsingToolbar.title = if (position == 0) {
                    getString(R.string.app_name)
                } else {
                    getString(R.string.settings_title)
                }
            }
        })

        if (initialPage != 0) {
            viewPager.setCurrentItem(initialPage, false)
            mainCollapsingToolbar.title = getString(R.string.settings_title)
            backPressedCallback.isEnabled = true
        }
    }

    private fun setupNavigation() {
        tabHome.setOnClickListener {
            if (viewPager.currentItem != 0) {
                viewPager.setCurrentItem(0, true)
            } else {
                mainAppBar.setExpanded(true, true)
            }
        }

        tabSettings.setOnClickListener {
            if (viewPager.currentItem != 1) {
                viewPager.setCurrentItem(1, true)
            } else {
                mainAppBar.setExpanded(true, true)
            }
        }
    }

    private fun setupPredictiveBack() {
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        backPressedCallback.isEnabled = (viewPager.currentItem != 0)
    }

    private fun updateNavigationState(selectedPosition: Int) {
        val onPrimary = themeColor(com.google.android.material.R.attr.colorOnPrimary)
        val muted = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        if (selectedPosition == 0) {
            tabHome.setBackgroundResource(R.drawable.bg_nav_tab_active)
            iconHome.imageTintList = ColorStateList.valueOf(onPrimary)
            textHome.setTextColor(onPrimary)
            textHome.setTypeface(null, Typeface.BOLD)
            tabHome.isSelected = true

            tabSettings.setBackgroundResource(R.drawable.bg_nav_tab_inactive)
            iconSettings.imageTintList = ColorStateList.valueOf(muted)
            textSettings.setTextColor(muted)
            textSettings.setTypeface(null, Typeface.NORMAL)
            tabSettings.isSelected = false
        } else {
            tabSettings.setBackgroundResource(R.drawable.bg_nav_tab_active)
            iconSettings.imageTintList = ColorStateList.valueOf(onPrimary)
            textSettings.setTextColor(onPrimary)
            textSettings.setTypeface(null, Typeface.BOLD)
            tabSettings.isSelected = true

            tabHome.setBackgroundResource(R.drawable.bg_nav_tab_inactive)
            iconHome.imageTintList = ColorStateList.valueOf(muted)
            textHome.setTextColor(muted)
            textHome.setTypeface(null, Typeface.NORMAL)
            tabHome.isSelected = false
        }
    }

    private fun themeColor(@AttrRes attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) HomeFragment.newInstance() else SettingsFragment.newInstance()
        }
    }

    companion object {
        private const val KEY_ACTIVE_PAGE = "key_active_page"
    }
}
