package com.linkdeck.android.core.inspector

import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.SanitizedLink
import com.linkdeck.android.core.network.RedirectHop
import com.linkdeck.android.core.network.RedirectResult
import com.linkdeck.android.core.preference.RoutingPreference
import com.linkdeck.android.core.rule.RoutingRule

/**
 * Human-readable explanation of why a routing decision was made.
 */
sealed class RoutingExplanation {
    data class MatchedRule(val rule: RoutingRule) : RoutingExplanation()
    data class SavedPreference(val preference: RoutingPreference) : RoutingExplanation()
    object ManualChoice : RoutingExplanation()
    data class RecommendedApp(val appLabel: String) : RoutingExplanation()
}

/**
 * Ephemeral diagnostic data model for Link Inspector.
 * Kept purely in memory during the active inspection lifecycle and never persisted.
 */
data class LinkInspectionData(
    val originalLink: SanitizedLink,
    val effectiveDestination: SanitizedLink,
    val redirectResult: RedirectResult,
    val wasCleaned: Boolean = false,
    val removedTrackingParams: List<String> = emptyList(),
    val wasDeAmped: Boolean = false,
    val deAmpSource: String? = null,
    val routingExplanation: RoutingExplanation = RoutingExplanation.ManualChoice,
    val targetApp: AppTarget? = null
) {
    /**
     * List of redirect hops resolved during the redirect resolution phase.
     */
    val redirectHops: List<RedirectHop>
        get() = when (redirectResult) {
            is RedirectResult.Success -> redirectResult.hops
            is RedirectResult.Error -> redirectResult.hops
            is RedirectResult.NoRedirect -> emptyList()
        }

    /**
     * Total number of redirect transitions recorded.
     */
    val redirectCount: Int
        get() = redirectHops.size

    /**
     * True if the link underwent one or more redirects.
     */
    val hasRedirects: Boolean
        get() = redirectCount > 0
}
