package com.streammate.tv.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Stable ids are shared by preferences and portable backups. */
enum class ColorTheme(val storedValue: String) {
    ORIGINAL("original"),
    NORDIC_SLATE("nordic_slate"),
    COZY_HEARTH("cozy_hearth"),
    CYBER_PLUM("cyber_plum"),
    ;

    val palette: StreamMatePalette
        get() = when (this) {
            ORIGINAL -> StreamMateDefaultPalette
            NORDIC_SLATE -> NordicSlatePalette
            COZY_HEARTH -> CozyHearthPalette
            CYBER_PLUM -> CyberPlumPalette
        }

    companion object {
        val DEFAULT = ORIGINAL

        fun fromStored(value: String?): ColorTheme =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

private val NordicSlatePalette = darkPalette(
    background = Color(0xFF12151A),
    surface = Color(0xFF1A202C),
    textPrimary = Color(0xFFF8FAFC),
    textMuted = Color(0xFF94A3B8),
    textDim = Color(0xFF8998AE),
    focus = Color(0xFF4E95D9),
    secondaryGlow = Color(0xFF3C5C8C),
)

private val CozyHearthPalette = darkPalette(
    background = Color(0xFF16120E),
    surface = Color(0xFF231C16),
    textPrimary = Color(0xFFFFFBEB),
    textMuted = Color(0xFFA8A29E),
    textDim = Color(0xFF9C9690),
    focus = Color(0xFFF59E0B),
    secondaryGlow = Color(0xFF8B542B),
)

private val CyberPlumPalette = darkPalette(
    background = Color(0xFF130E1C),
    surface = Color(0xFF1F162E),
    textPrimary = Color(0xFFFAF5FF),
    textMuted = Color(0xFFA899B8),
    textDim = Color(0xFF9C8DAD),
    // The brighter violet remains readable in small labels on raised surfaces.
    focus = Color(0xFFC084FC),
    secondaryGlow = Color(0xFF653498),
)

/**
 * Complete dark surfaces derived from each palette's base and panel colours.
 * Focus remains a light fill with dark text. Sports, ratings, live/error colours,
 * and black video scrims retain their existing meaning across themes.
 */
private fun darkPalette(
    background: Color,
    surface: Color,
    textPrimary: Color,
    textMuted: Color,
    textDim: Color,
    focus: Color,
    secondaryGlow: Color,
): StreamMatePalette = StreamMateDefaultPalette.copy(
    background = background,
    backgroundTop = lerp(background, surface, 0.5f),
    backgroundBottom = lerp(background, Color.Black, 0.3f),
    panel = surface,
    surfaceSubtle = lerp(background, surface, 0.5f),
    surface = surface,
    surfaceRaised = lerp(surface, textPrimary, 0.025f),
    surfaceFocused = lerp(surface, textPrimary, 0.05f),
    divider = textPrimary.copy(alpha = 0.07f),
    outline = textPrimary.copy(alpha = 0.10f),
    textPrimary = textPrimary,
    textMuted = textMuted,
    textDim = textDim,
    textDisabled = textDim.copy(alpha = 0.65f),
    focus = focus,
    secondaryGlow = secondaryGlow,
    playerInfoSurface = surface,
)
