package com.linkdeck.android.ui.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.MainActivity
import com.linkdeck.android.R
import com.linkdeck.android.ui.base.BaseActivity

/**
 * Modern, interactive onboarding walkthrough introducing first-time users to LinkDeck:
 * - Smart link routing & chooser capabilities
 * - On-device safe redirect inspection & tracking parameter cleaning
 * - Custom domain & path rules
 * - Quick default browser role setup & finish action
 */
class OnboardingActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var btnSetDefault: MaterialButton
    private lateinit var indicators: List<View>

    private var isReplay: Boolean = false
    private val slides = OnboardingSlide.getSlides()

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Toast.makeText(this, R.string.status_active_desc, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        isReplay = intent.getBooleanExtra(EXTRA_REPLAY, false)

        setupWindowInsets()
        setupViews()
        setupViewPager()
        updateSlideState(0)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onboardingRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupViews() {
        viewPager = findViewById(R.id.viewPagerOnboarding)
        btnSkip = findViewById(R.id.btnOnboardingSkip)
        btnNext = findViewById(R.id.btnOnboardingNext)
        btnBack = findViewById(R.id.btnOnboardingBack)
        btnSetDefault = findViewById(R.id.btnOnboardingSetDefault)

        indicators = listOf(
            findViewById(R.id.indicator0),
            findViewById(R.id.indicator1),
            findViewById(R.id.indicator2),
            findViewById(R.id.indicator3)
        )

        btnSkip.setOnClickListener {
            completeOnboarding()
        }

        btnBack.setOnClickListener {
            val prev = viewPager.currentItem - 1
            if (prev >= 0) {
                viewPager.setCurrentItem(prev, true)
            }
        }

        btnNext.setOnClickListener {
            val next = viewPager.currentItem + 1
            if (next < slides.size) {
                viewPager.setCurrentItem(next, true)
            } else {
                completeOnboarding()
            }
        }

        btnSetDefault.setOnClickListener {
            requestDefaultBrowserRole()
        }
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(slides)
        viewPager.adapter = adapter

        // Smooth subtle scale/alpha page transition
        viewPager.setPageTransformer { page, position ->
            page.apply {
                when {
                    position < -1 -> {
                        alpha = 0f
                    }
                    position <= 1 -> {
                        val factor = 1 - Math.abs(position)
                        alpha = 0.5f + (factor * 0.5f)
                        val scale = 0.96f + (factor * 0.04f)
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
                updateSlideState(position)
            }
        })
    }

    private fun updateSlideState(position: Int) {
        val isFinal = (position == slides.size - 1)

        // 1. Back button visibility
        btnBack.visibility = if (position > 0) View.VISIBLE else View.GONE

        // 2. Next / Finish button text
        if (isFinal) {
            btnNext.setText(if (isReplay) R.string.onboarding_done else R.string.onboarding_get_started)
            btnSetDefault.visibility = View.VISIBLE
            btnSkip.visibility = View.INVISIBLE
        } else {
            btnNext.setText(R.string.onboarding_next)
            btnSetDefault.visibility = View.GONE
            btnSkip.visibility = View.VISIBLE
        }

        // 3. Update Indicator Dots & Pills
        val density = resources.displayMetrics.density
        val pillWidth = (24 * density).toInt()
        val dotWidth = (8 * density).toInt()
        val dotHeight = (8 * density).toInt()

        indicators.forEachIndexed { index, view ->
            val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
            if (index == position) {
                layoutParams.width = pillWidth
                layoutParams.height = dotHeight
                view.setBackgroundResource(R.drawable.bg_onboarding_pill)
            } else {
                layoutParams.width = dotWidth
                layoutParams.height = dotHeight
                view.setBackgroundResource(R.drawable.bg_onboarding_dot)
            }
            view.layoutParams = layoutParams
        }
    }

    private fun requestDefaultBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    roleRequestLauncher.launch(intent)
                    return
                } else {
                    Toast.makeText(this, R.string.status_active_title, Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Fallback: Open standard default apps / application details settings
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Please set LinkDeck as default browser in Android Settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completeOnboarding() {
        appSettingsStore.isOnboardingCompleted = true

        if (!isReplay) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
        finish()
    }

    companion object {
        const val EXTRA_REPLAY = "extra_replay"

        fun createIntent(context: Context, isReplay: Boolean = false): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_REPLAY, isReplay)
            }
        }
    }
}
