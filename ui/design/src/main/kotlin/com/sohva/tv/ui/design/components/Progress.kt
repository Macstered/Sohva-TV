package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A progress bar in one draw (design/02 §13): optional track, `focus` fill. [fraction] is read in
 * the draw phase, so moving the bar redraws it without recomposing anything.
 */
@Composable
fun ProgressBar(fraction: () -> Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Sohva.palette.surfaceRaised) {
    val fill = Sohva.palette.focus
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                if (track.alpha > 0f) drawRect(track)
                drawRect(fill, size = Size(size.width * fraction().coerceIn(0f, 1f), size.height))
            },
    )
}

/**
 * The player's seek bar: a 13 dp box with a 5 dp rounded track at `textPrimary` α0.20, the
 * `focus` fill, and a 13 dp thumb that moves by drawing, not by re-laying out a child.
 */
@Composable
fun PlayerProgressTrack(fraction: () -> Float, modifier: Modifier = Modifier) {
    val p = Sohva.palette
    val corner = Sohva.shapes.small
    Box(
        modifier
            .fillMaxWidth()
            .height(13.dp)
            .drawBehind {
                val trackHeight = 5.dp.toPx()
                val top = (size.height - trackHeight) / 2
                val radius = CornerRadius(corner.toPx().coerceAtMost(trackHeight / 2))
                val f = fraction().coerceIn(0f, 1f)
                drawRoundRect(p.textPrimary.copy(alpha = 0.20f), Offset(0f, top), Size(size.width, trackHeight), radius)
                drawRoundRect(p.focus, Offset(0f, top), Size(size.width * f, trackHeight), radius)
                val thumb = 13.dp.toPx()
                drawCircle(p.textPrimary, thumb / 2, Offset((size.width - thumb) * f + thumb / 2, size.height / 2))
            },
    )
}
