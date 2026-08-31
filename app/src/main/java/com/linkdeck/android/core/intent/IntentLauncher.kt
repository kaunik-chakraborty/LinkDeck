package com.linkdeck.android.core.intent

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink

/**
 * Safely launches a sanitized URL in the chosen [AppTarget].
 *
 * Security & Reliability guarantees:
 * 1. Creates a brand-new, clean [Intent.ACTION_VIEW] intent.
 * 2. Explicitly targets the selected [ComponentName] to prevent intent hijacking or redirection.
 * 3. Never forwards untrusted bundle extras from the incoming request.
 * 4. Adds [Intent.FLAG_ACTIVITY_NEW_TASK] to ensure the target app runs in its own task stack.
 * 5. Catches and gracefully reports [ActivityNotFoundException] and [SecurityException].
 */
object IntentLauncher {

    /**
     * Constructs the sanitized explicit launch intent for the given [target].
     */
    fun createLaunchIntent(
        target: AppTarget,
        sanitizedLink: SanitizedLink
    ): Intent {
        val destinationUri = Uri.parse(sanitizedLink.rawUrl)
        return Intent(Intent.ACTION_VIEW, destinationUri).apply {
            component = ComponentName(target.packageName, target.activityName)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Dispatches the sanitized URL to the specified [target].
     *
     * @return [Result.success] if the activity was launched successfully, [Result.failure] otherwise.
     */
    fun launch(
        context: Context,
        target: AppTarget,
        sanitizedLink: SanitizedLink
    ): Result<Unit> {
        return try {
            val launchIntent = createLaunchIntent(target, sanitizedLink)
            context.startActivity(launchIntent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sanitizedLink.rawUrl)).apply {
                    `package` = target.packageName
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e)
            }
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
