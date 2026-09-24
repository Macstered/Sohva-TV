package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.TvSurfaceColors
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.text.DrawColorText
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.SohvaPalette

/**
 * Borderless on the ladder at rest; on focus it flips to the off-white fill with near-black
 * content and lifts 1.03 (design/02 §7). `selected` is a state, not focus. Callers stretch it
 * with `fillMaxWidth`; content is centred. Beta 23's 10 dp focus shadow is dropped: it is
 * invisible on the dark ground (design/01 §16.1 rule 6).
 */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    state: SurfaceState = SurfaceState(),
    compact: Boolean = false,
) {
    val palette = Sohva.palette
    val style = SurfaceStyle(
        corner = Sohva.shapes.small,
        resting = if (state.enabled) palette.surface else palette.surfaceSubtle,
        restingContent = palette.textPrimary,
        focusScale = 1.03f,
        padding = if (compact) PaddingValues(12.dp, 7.dp) else PaddingValues(18.dp, 11.dp),
        contentAlignment = Alignment.Center,
    )
    TvSurface(onClick = onClick, modifier = modifier, state = state, style = style) { colors ->
        val content by animateColorAsState(buttonContent(colors, state, palette), Motion.focus(), label = "button-content")
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                DrawColorIcon(icon, { content }, size = if (compact) 16.dp else 18.dp)
                Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
            }
            val text = if (compact) Sohva.typography.caption else Sohva.typography.label
            DrawColorText(label, { content }, style = text.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Selected shows its content in `focus`, danger in `danger`, until focus flips the fill. */
private fun buttonContent(colors: TvSurfaceColors, state: SurfaceState, palette: SohvaPalette): Color = when {
    colors.focused || !state.enabled -> colors.content
    state.danger -> palette.danger
    state.selected -> palette.focus
    else -> colors.content
}
