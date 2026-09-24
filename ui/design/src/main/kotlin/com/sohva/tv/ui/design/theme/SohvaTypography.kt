package com.sohva.tv.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The type scale of design/01 §8. The floor is 12 sp: anything smaller is unreadable from a sofa.
 * System sans-serif only; no bundled fonts.
 */
@Immutable
data class SohvaTypography(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
) {
    companion object {
        /**
         * The inherited style that shaped beta 23's real look (tv-material's default text style):
         * 16/24 sp, Regular, 0.5 sp letter spacing, no font padding, proportional line height
         * trimmed at both ends, so a single line is as tall as the font. Set on purpose here,
         * because the rebuild does not use tv-material (design/01 §8, §17).
         */
        val Inherited: TextStyle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        private fun token(size: Int, line: Int, weight: FontWeight, spacing: TextUnit = Inherited.letterSpacing) =
            Inherited.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing)

        val Default: SohvaTypography = SohvaTypography(
            display = token(40, 44, FontWeight.Black, (-0.5).sp),
            title = token(28, 32, FontWeight.Bold, (-0.3).sp),
            headline = token(22, 27, FontWeight.Bold),
            bodyLarge = token(18, 25, FontWeight.Normal),
            body = token(16, 23, FontWeight.Normal),
            label = token(14, 19, FontWeight.SemiBold),
            caption = token(12, 16, FontWeight.Medium),
            overline = token(12, 16, FontWeight.Bold, 1.4.sp),
        )
    }
}
