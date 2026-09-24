package com.sohva.tv.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colour roles of design/01 §4. Only colour changes between themes; type, spacing and shapes
 * are the same in all seven.
 */
@Immutable
data class SohvaPalette(
    /** The ground everything sits on, and the ink on a focused fill. */
    val background: Color,
    /** Top of the ground gradient; rails. */
    val backgroundTop: Color,
    /** Deepest ground; scrims that must bury artwork. */
    val backgroundBottom: Color,
    /** Opaque panel for overlays and every dialog card. */
    val panel: Color,
    val surfaceSubtle: Color,
    /** The default resting fill for a control. */
    val surface: Color,
    val surfaceRaised: Color,
    /** Selected-but-unfocused tint. Focus itself is the [textPrimary] fill. */
    val surfaceFocused: Color,
    val divider: Color,
    val outline: Color,
    /** Selection, progress and brand accent. Never the focus fill. */
    val focus: Color,
    /** Ambient counter-wash on the ground only. */
    val secondaryGlow: Color,
    /** The sport role. */
    val accent: Color,
    /** Live and destructive. */
    val danger: Color,
    val rating: Color,
    /** Primary text and the focus fill. */
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    /** Content of a control that cannot be focused. */
    val textDisabled: Color,
    val scrim: Color,
    val onScrim: Color,
    val dangerSurface: Color,
    val onDangerSurface: Color,
    val playerInfoSurface: Color,
)

/** Category identities, identical in every theme (design/01 §7). */
object ContentColors {
    val genreFilm: Color = Color(0xFF8E7BFF)
    val genreSport: Color = Color(0xFFFF8A4C)
    val genreNews: Color = Color(0xFF4CC2FF)
    val genreChildren: Color = Color(0xFF57D9A3)

    val australianFootball: Color = Color(0xFFE959FF)
    val basketball: Color = Color(0xFFFF9A3C)
    val baseball: Color = Color(0xFFFF647C)
    val handball: Color = Color(0xFFFFC857)
    val rugby: Color = Color(0xFF56D68B)
    val volleyball: Color = Color(0xFF6C9DFF)
    val americanFootball: Color = Color(0xFFB08CFF)
    val mma: Color = Color(0xFFFF7A59)
    val formulaOne: Color = Color(0xFFFF4D4D)
    val nba: Color = Color(0xFFFFB347)
    val substitution: Color = Color(0xFF8EA7FF)
    val videoReview: Color = Color(0xFFE959FF)

    /** Profile avatar fills; the order matches the colour indices stored in profiles. */
    private val profiles: List<Color> = listOf(
        Color(0xFF2EC4B6),
        Color(0xFFFF9F1C),
        Color(0xFFE71D36),
        Color(0xFF7B61FF),
        Color(0xFF4CAF50),
        Color(0xFFF06292),
    )
    val onAvatar: Color = Color(0xFFFFFFFF)

    fun profile(index: Int): Color = profiles[index.coerceIn(0, profiles.lastIndex)]
}
