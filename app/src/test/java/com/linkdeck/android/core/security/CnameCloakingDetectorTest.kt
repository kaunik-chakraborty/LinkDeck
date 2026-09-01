package com.linkdeck.android.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CnameCloakingDetectorTest {

    @Test
    fun findCloakedTracker_criteoDomain_matches() {
        val match = CnameCloakingDetector.findCloakedTracker("cookie.criteo.net")
        assertNotNull(match)
        assertEquals("Criteo Advertising", match?.trackerName)
    }

    @Test
    fun findCloakedTracker_branchDomain_matches() {
        val match = CnameCloakingDetector.findCloakedTracker("links.branch.io")
        assertNotNull(match)
        assertEquals("Branch Attribution", match?.trackerName)
    }

    @Test
    fun findCloakedTracker_adobeOmtrdc_matches() {
        val match = CnameCloakingDetector.findCloakedTracker("metrics.omtrdc.net")
        assertNotNull(match)
        assertEquals("Adobe Audience Manager", match?.trackerName)
    }

    @Test
    fun findCloakedTracker_benignCdn_returnsNull() {
        assertNull(CnameCloakingDetector.findCloakedTracker("cdn.cloudflare.net"))
        assertNull(CnameCloakingDetector.findCloakedTracker("assets.github.com"))
        assertNull(CnameCloakingDetector.findCloakedTracker("d12345.cloudfront.net"))
    }

    @Test
    fun checkCname_localhost_returnsDirectResolution() {
        val result = CnameCloakingDetector.checkCname("localhost")
        assertEquals(CnameCloakingDetector.CnameResult.DirectResolution, result)
    }
}
