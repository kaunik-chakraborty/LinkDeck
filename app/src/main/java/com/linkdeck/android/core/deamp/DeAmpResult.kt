package com.linkdeck.android.core.deamp

import com.linkdeck.android.core.model.SanitizedLink

/**
 * Encapsulates the output of a de-AMPing transformation.
 */
data class DeAmpResult(
    val originalLink: SanitizedLink,
    val deAmpedLink: SanitizedLink,
    val wasDeAmped: Boolean,
    val source: AmpSource? = null
)
