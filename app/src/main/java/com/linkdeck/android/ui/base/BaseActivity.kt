package com.linkdeck.android.ui.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.core.typography.AppFont

/**
 * Base activity providing automatic font theme overlay and Material You dynamic colors injection.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val appSettingsStore by lazy { AppSettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply user-configured font overlay before inflating views
        AppFont.applyFontTheme(this, appSettingsStore.appFontKey)

        // Apply Material You Dynamic Colors if enabled by user and supported by device
        if (appSettingsStore.isDynamicColorEnabled && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }
}
