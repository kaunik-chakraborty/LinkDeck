package com.linkdeck.android.core.intent

import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.model.TargetCategory
import org.junit.Assert.assertNotNull
import org.junit.Test

class IntentLauncherTest {

    @Test
    fun createLaunchIntent_constructsNonNullIntent() {
        val target = AppTarget(
            packageName = "org.mozilla.firefox",
            activityName = "org.mozilla.firefox.App",
            appLabel = "Firefox",
            category = TargetCategory.BROWSER
        )
        val link = SanitizedLink(
            rawUrl = "https://example.com/item/123",
            scheme = "https",
            host = "example.com",
            path = "/item/123"
        )

        val intent = IntentLauncher.createLaunchIntent(target, link)
        assertNotNull(intent)
    }
}
