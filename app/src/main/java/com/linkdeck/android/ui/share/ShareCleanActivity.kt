package com.linkdeck.android.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.linkdeck.android.R
import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.core.share.ShareUrlExtractor

/**
 * Transparent entrypoint Activity that intercepts ACTION_SEND intents containing URLs or text,
 * cleans tracking parameters, and displays the ShareCleanBottomSheet.
 */
class ShareCleanActivity : AppCompatActivity() {

    private val settingsStore by lazy { AppSettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND || intent?.type?.startsWith("text/") != true) {
            finish()
            return
        }

        if (!settingsStore.isShareCleaningEnabled) {
            // If share cleaning is disabled in settings, forward raw text to standard system chooser
            forwardRawIntent()
            finish()
            return
        }

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString()
            ?: intent.dataString
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val extractedUrl = ShareUrlExtractor.extractFirstUrl(rawText)

        if (extractedUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.share_clean_no_link_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Sanitize and clean link tracking tokens
        val sanitization = IntentSanitizer.sanitizeUrl(extractedUrl)
        if (sanitization !is SanitizationResult.Success) {
            Toast.makeText(this, R.string.share_clean_invalid_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cleanResult = TrackingParameterCleaner.clean(sanitization.link)
        val finalUrl = cleanResult.cleanedLink.rawUrl
        val removedParams = cleanResult.removedParams

        val sheet = ShareCleanBottomSheet.newInstance(
            cleanedUrl = finalUrl,
            originalUrl = extractedUrl,
            removedParams = removedParams
        )
        sheet.onDismissedListener = {
            finish()
        }
        sheet.show(supportFragmentManager, ShareCleanBottomSheet.TAG)
    }

    private fun forwardRawIntent() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = intent.type
            putExtra(Intent.EXTRA_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }
}
