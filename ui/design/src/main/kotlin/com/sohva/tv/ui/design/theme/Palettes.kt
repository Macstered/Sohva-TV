package com.sohva.tv.ui.design.theme

import androidx.compose.ui.graphics.Color
import com.sohva.tv.core.model.settings.ColorThemeId

/**
 * The seven palettes with the resolved values of design/01 §5.1. The six derived themes were
 * built at run time in beta 23 with Oklab `lerp`; hard-coding the results costs nothing at start
 * and keeps their surfaces opaque, as they are today. Original's surface ladder stays translucent.
 */
object Palettes {
    // Shared by every theme: sports, ratings, live/error colours and black video scrims keep their
    // meaning across themes.
    private val accent = Color(0xFFFF8A4C)
    private val danger = Color(0xFFFF3B5C)
    private val rating = Color(0xFFFFC857)
    private val scrim = Color(0xFF000000)
    private val onScrim = Color(0xFFFFFFFF)
    private val dangerSurface = Color(0xFF7A1624)
    private val onDangerSurface = Color(0xFFFFFFFF)

    val original: SohvaPalette = SohvaPalette(
        background = Color(0xFF05070D),
        backgroundTop = Color(0xFF080C15),
        backgroundBottom = Color(0xFF04060A),
        panel = Color(0xFF0A0F1A),
        surfaceSubtle = Color(0x09FFFFFF),
        surface = Color(0x0FFFFFFF),
        surfaceRaised = Color(0x1AFFFFFF),
        surfaceFocused = Color(0x24FFFFFF),
        divider = Color(0x12FFFFFF),
        outline = Color(0x12FFFFFF),
        focus = Color(0xFF2DE2E6),
        secondaryGlow = Color(0xFF2276D2),
        accent = accent,
        danger = danger,
        rating = rating,
        textPrimary = Color(0xFFF2F5F9),
        textMuted = Color(0xFF93A1B5),
        textDim = Color(0xFF5B6981),
        textDisabled = Color(0xFF5B6981),
        scrim = scrim,
        onScrim = onScrim,
        dangerSurface = dangerSurface,
        onDangerSurface = onDangerSurface,
        playerInfoSurface = Color(0xFF252A31),
    )

    /** Columns of design/01 §5.1, in the table's order, for one derived theme. */
    private fun derived(
        background: Long,
        backgroundTop: Long,
        backgroundBottom: Long,
        surface: Long,
        surfaceRaised: Long,
        surfaceFocused: Long,
        text: Triple<Long, Long, Long>,
        focus: Long,
        secondaryGlow: Long,
    ): SohvaPalette {
        val (primary, muted, dim) = text
        val textPrimary = Color(primary)
        return original.copy(
            background = Color(background),
            backgroundTop = Color(backgroundTop),
            backgroundBottom = Color(backgroundBottom),
            panel = Color(surface),
            surfaceSubtle = Color(backgroundTop),
            surface = Color(surface),
            surfaceRaised = Color(surfaceRaised),
            surfaceFocused = Color(surfaceFocused),
            divider = textPrimary.copy(alpha = 0x12 / 255f),
            outline = textPrimary.copy(alpha = 0x1A / 255f),
            focus = Color(focus),
            secondaryGlow = Color(secondaryGlow),
            textPrimary = textPrimary,
            textMuted = Color(muted),
            textDim = Color(dim),
            textDisabled = Color(dim).copy(alpha = 0xA6 / 255f),
            playerInfoSurface = Color(surface),
        )
    }

    val nordicSlate: SohvaPalette = derived(
        0xFF12151A, 0xFF161A23, 0xFF07080C, 0xFF1A202C, 0xFF1E2430, 0xFF232935,
        Triple(0xFFF8FAFC, 0xFF94A3B8, 0xFF8998AE), 0xFF4E95D9, 0xFF3C5C8C,
    )
    val cozyHearth: SohvaPalette = derived(
        0xFF16120E, 0xFF1C1712, 0xFF090705, 0xFF231C16, 0xFF28201A, 0xFF2C251F,
        Triple(0xFFFFFBEB, 0xFFA8A29E, 0xFF9C9690), 0xFFF59E0B, 0xFF8B542B,
    )
    val cyberPlum: SohvaPalette = derived(
        0xFF130E1C, 0xFF191225, 0xFF07050D, 0xFF1F162E, 0xFF231B33, 0xFF281F37,
        Triple(0xFFFAF5FF, 0xFFA899B8, 0xFF9C8DAD), 0xFFC084FC, 0xFF653498,
    )
    val nord: SohvaPalette = derived(
        0xFF151A21, 0xFF1B222B, 0xFF080C10, 0xFF222A35, 0xFF262E39, 0xFF2A323D,
        Triple(0xFFECEFF4, 0xFFA5B1C2, 0xFF98A5B8), 0xFF88C0D0, 0xFF5E81AC,
    )
    val everforest: SohvaPalette = derived(
        0xFF151B18, 0xFF1A231E, 0xFF080C0A, 0xFF202B25, 0xFF242F29, 0xFF28332C,
        Triple(0xFFE8E3D5, 0xFFA6B3A5, 0xFF98A794), 0xFFA7C080, 0xFF536D59,
    )
    val kanagawa: SohvaPalette = derived(
        0xFF16161D, 0xFF1C1C26, 0xFF09090E, 0xFF232330, 0xFF272733, 0xFF2B2B36,
        Triple(0xFFDCD7BA, 0xFFAAA6BD, 0xFF9C99AF), 0xFF7E9CD8, 0xFF4C628A,
    )

    fun of(theme: ColorThemeId): SohvaPalette = when (theme) {
        ColorThemeId.ORIGINAL -> original
        ColorThemeId.NORDIC_SLATE -> nordicSlate
        ColorThemeId.COZY_HEARTH -> cozyHearth
        ColorThemeId.CYBER_PLUM -> cyberPlum
        ColorThemeId.NORD -> nord
        ColorThemeId.EVERFOREST -> everforest
        ColorThemeId.KANAGAWA -> kanagawa
    }
}
