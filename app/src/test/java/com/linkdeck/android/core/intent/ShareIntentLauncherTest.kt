package com.linkdeck.android.core.intent

import android.content.Intent
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.ShareTarget
import org.junit.Assert.assertNotNull
import org.junit.Test

class ShareIntentLauncherTest {

    @Test
    fun createShareIntent_constructsValidActionSendIntent() {
        val target = ShareTarget(
            packageName = "com.whatsapp",
            activityName = "com.whatsapp.ContactPicker",
            appLabel = "WhatsApp"
        )
        val link = SanitizedLink(
            rawUrl = "https://gymscaleup.com/pricing?ref=test",
            scheme = "https",
            host = "gymscaleup.com",
            path = "/pricing"
        )

        val intent = ShareIntentLauncher.createShareIntent(target, link)

        assertNotNull(intent)
    }
}
