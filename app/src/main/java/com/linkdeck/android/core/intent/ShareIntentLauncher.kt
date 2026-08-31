package com.linkdeck.android.core.intent

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget

/**
 * Safely dispatches a validated sanitized URL to the chosen [ShareTarget] via ACTION_SEND.
 *
 * Security & Reliability guarantees:
 * 1. Constructs a fresh [Intent.ACTION_SEND] with mimeType "text/plain".
 * 2. Explicitly targets the selected [ComponentName] to prevent intent hijacking.
 * 3. Passes ONLY the validated, sanitized raw URL (never redacted display strings).
 * 4. Adds [Intent.FLAG_ACTIVITY_NEW_TASK] to ensure the target runs in its own task.
 * 5. Catches and gracefully handles [ActivityNotFoundException] and [SecurityException].
 */
object ShareIntentLauncher {

    /**
     * Constructs the explicit share intent for the specified [target].
     */
    fun createShareIntent(
        target: ShareTarget,
        sanitizedLink: SanitizedLink
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sanitizedLink.rawUrl)
            component = ComponentName(target.packageName, target.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Dispatches the sanitized URL to the specified [target].
     *
     * @return [Result.success] if started, [Result.failure] otherwise.
     */
    fun launch(
        context: Context,
        target: ShareTarget,
        sanitizedLink: SanitizedLink
    ): Result<Unit> {
        return try {
            val shareIntent = createShareIntent(target, sanitizedLink)
            context.startActivity(shareIntent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
