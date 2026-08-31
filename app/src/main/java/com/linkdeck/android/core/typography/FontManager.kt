package com.linkdeck.android.core.typography

import android.app.Activity
import androidx.annotation.FontRes
import androidx.annotation.StyleRes
import com.linkdeck.android.R

/**
 * Curated typography catalog for LinkDeck offering System Default and 6 distinct modern typefaces.
 */
enum class AppFont(
    val key: String,
    val displayName: String,
    val description: String,
    @FontRes val fontRes: Int?,
    @StyleRes val themeOverlayRes: Int
) {
    SYSTEM(
        key = "system",
        displayName = "System Default",
        description = "Standard device system typeface",
        fontRes = null,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_System
    ),
    SATOSHI(
        key = "satoshi",
        displayName = "Satoshi",
        description = "Contemporary geometric neo-grotesque",
        fontRes = R.font.font_satoshi,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_Satoshi
    ),
    OUTFIT(
        key = "outfit",
        displayName = "Outfit",
        description = "Modern circular geometric sans",
        fontRes = R.font.font_outfit,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_Outfit
    ),
    GENERAL_SANS(
        key = "general_sans",
        displayName = "General Sans",
        description = "Clean, sharp modernist grotesque",
        fontRes = R.font.font_general_sans,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_GeneralSans
    ),
    CABINET_GROTESK(
        key = "cabinet_grotesk",
        displayName = "Cabinet Grotesk",
        description = "Distinctive high-character design",
        fontRes = R.font.font_cabinet_grotesk,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_CabinetGrotesk
    ),
    SPACE_GROTESK(
        key = "space_grotesk",
        displayName = "Space Grotesk",
        description = "Tech-forward mono-hybrid grotesque",
        fontRes = R.font.font_space_grotesk,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_SpaceGrotesk
    ),
    PLUS_JAKARTA_SANS(
        key = "plus_jakarta_sans",
        displayName = "Plus Jakarta Sans",
        description = "Warm geometric sans with open curves",
        fontRes = R.font.font_plus_jakarta_sans,
        themeOverlayRes = R.style.ThemeOverlay_LinkDeck_Font_PlusJakartaSans
    );

    companion object {
        fun fromKey(key: String?): AppFont {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SATOSHI
        }

        fun applyFontTheme(activity: Activity, fontKey: String?) {
            val font = fromKey(fontKey)
            activity.theme.applyStyle(font.themeOverlayRes, true)
        }
    }
}
