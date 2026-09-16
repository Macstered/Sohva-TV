package com.streammate.tv.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Stable category identities; a theme can adjust these without changing the mappings. */
@Immutable
data class StreamMateGenreColors(
    val film: Color = Color(0xFF8E7BFF),
    val sport: Color = Color(0xFFFF8A4C),
    val news: Color = Color(0xFF4CC2FF),
    val children: Color = Color(0xFF57D9A3),
)

@Immutable
data class StreamMateSportColors(
    val australianFootball: Color = Color(0xFFE959FF),
    val basketball: Color = Color(0xFFFF9A3C),
    val baseball: Color = Color(0xFFFF647C),
    val handball: Color = Color(0xFFFFC857),
    val rugby: Color = Color(0xFF56D68B),
    val volleyball: Color = Color(0xFF6C9DFF),
    val americanFootball: Color = Color(0xFFB08CFF),
    val mma: Color = Color(0xFFFF7A59),
    val formulaOne: Color = Color(0xFFFF4D4D),
    val nba: Color = Color(0xFFFFB347),
    val substitution: Color = Color(0xFF8EA7FF),
    val videoReview: Color = Color(0xFFE959FF),
)

/** The order matches the colour indices already stored in household profiles. */
@Immutable
data class StreamMateProfileColors(
    val teal: Color = Color(0xFF2EC4B6),
    val amber: Color = Color(0xFFFF9F1C),
    val red: Color = Color(0xFFE71D36),
    val violet: Color = Color(0xFF7B61FF),
    val green: Color = Color(0xFF4CAF50),
    val pink: Color = Color(0xFFF06292),
    val onAvatar: Color = Color.White,
) {
    fun atIndex(index: Int): Color = when (index.coerceIn(0, 5)) {
        0 -> teal
        1 -> amber
        2 -> red
        3 -> violet
        4 -> green
        else -> pink
    }
}
