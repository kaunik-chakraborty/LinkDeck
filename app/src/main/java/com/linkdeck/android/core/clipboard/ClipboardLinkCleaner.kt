package com.linkdeck.android.core.clipboard

import com.linkdeck.android.core.cleaner.TrackingParameterCleaner
import com.linkdeck.android.core.deamp.DeAmpEngine
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.core.share.ShareUrlExtractor

/**
 * Pure on-device engine that inspects raw clipboard strings, extracts links,
 * unrolls AMP containers, and strips tracking parameters.
 */
object ClipboardLinkCleaner {

    /**
     * Evaluates and sanitizes [rawClipboardText].
     *
     * @param rawClipboardText Text extracted from system clipboard.
     * @param isDeAmpingEnabled Whether AMP unwrapping is active.
     * @param isTrackingCleanerEnabled Whether tracking token stripping is active.
     * @return [ClipboardCleanResult] detailing the exact transformation or state.
     */
    fun clean(
        rawClipboardText: String?,
        isDeAmpingEnabled: Boolean = true,
        isTrackingCleanerEnabled: Boolean = true
    ): ClipboardCleanResult {
        if (rawClipboardText.isNullOrBlank()) {
            return ClipboardCleanResult.Empty
        }

        val rawText = rawClipboardText.trim()
        val extractedUrl = ShareUrlExtractor.extractFirstUrl(rawText)
            ?: return ClipboardCleanResult.NoLinkFound(rawText)

        val sanitization = IntentSanitizer.sanitizeUrl(extractedUrl)
        if (sanitization !is SanitizationResult.Success) {
            val error = (sanitization as SanitizationResult.Error)
            return ClipboardCleanResult.InvalidLink(error.error, error.message)
        }

        var candidate = sanitization.link
        var wasDeAmped = false

        if (isDeAmpingEnabled) {
            val deAmpResult = DeAmpEngine.deAmp(candidate)
            if (deAmpResult.wasDeAmped) {
                candidate = deAmpResult.deAmpedLink
                wasDeAmped = true
            }
        }

        var removedParams = emptyList<String>()
        if (isTrackingCleanerEnabled) {
            val cleanResult = TrackingParameterCleaner.clean(candidate)
            if (cleanResult.hasRemovedParams) {
                candidate = cleanResult.cleanedLink
                removedParams = cleanResult.removedParams
            }
        }

        val wasModified = wasDeAmped || removedParams.isNotEmpty() || candidate.rawUrl != rawText

        return if (!wasModified) {
            ClipboardCleanResult.AlreadyClean(candidate)
        } else {
            ClipboardCleanResult.Cleaned(
                originalText = rawText,
                cleanedLink = candidate,
                wasDeAmped = wasDeAmped,
                removedParams = removedParams
            )
        }
    }
}
