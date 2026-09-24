package com.sohva.tv.ui.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * "Sohva TV" as real text: the accent span ("TV", in `focus`) starts after the last space. Text,
 * not the wordmark image, keeps it crisp and readable by accessibility services (design/02 §3).
 * 34 sp by default; 22 sp on Home and details pages.
 */
@Composable
fun SohvaTvBrand(modifier: Modifier = Modifier, fontSize: TextUnit = 34.sp) {
    BrandText(stringResource(R.string.brand_sohva_tv), Sohva.palette.focus, fontSize, modifier)
}

/** "Sohva Sport", with "Sport" in the sport `accent`, 30 sp. */
@Composable
fun SohvaSportBrand(modifier: Modifier = Modifier) {
    BrandText(stringResource(R.string.brand_sohva_sport), Sohva.palette.accent, 30.sp, modifier)
}

@Composable
private fun BrandText(text: String, accent: Color, fontSize: TextUnit, modifier: Modifier) {
    val split = text.lastIndexOf(' ') + 1
    val styled = buildAnnotatedString {
        withStyle(SpanStyle(color = Sohva.palette.textPrimary)) { append(text.substring(0, split)) }
        withStyle(SpanStyle(color = accent)) { append(text.substring(split)) }
    }
    val style = Sohva.typography.headline.copy(
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeight = fontSize,
    )
    Text(styled, modifier, style = style, maxLines = 1)
}
