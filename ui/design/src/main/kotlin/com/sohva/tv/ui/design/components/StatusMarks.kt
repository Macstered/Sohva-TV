package com.sohva.tv.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The one tone that should pull the eye across a room: 8 dp (hero, Home), 7 dp in the badge. */
@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 8.dp, alpha: () -> Float = { 1f }) {
    val color = Sohva.palette.danger
    Box(modifier.size(size).drawBehind { drawCircle(color.copy(alpha = alpha())) })
}

/**
 * The match card's live badge: dot, minute and "LIVE" on `danger` α0.16. The dot pulses twice
 * (240 ms legs) when the minute changes: finite on purpose, static in reduced motion.
 */
@Composable
fun LiveBadge(minute: String, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    val reduced = Sohva.reducedMotion
    LaunchedEffect(minute, reduced) {
        if (reduced) return@LaunchedEffect
        repeat(2) {
            pulse.animateTo(0.25f, tween(240))
            pulse.animateTo(1f, tween(240))
        }
    }
    val p = Sohva.palette
    Row(
        modifier.roundFill(p.danger.copy(alpha = 0.16f), 6.dp).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(size = 7.dp, alpha = { pulse.value })
        Spacer(Modifier.width(5.dp))
        Text(minute, style = Sohva.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Black), color = p.textPrimary)
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.guide_live),
            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black),
            color = p.danger,
        )
    }
}

/** A `focus` circle with a check in `background`: 26 dp on the catalogue, 22 dp on Discover and Trakt. */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val p = Sohva.palette
    Box(modifier.size(size).drawBehind { drawCircle(p.focus) }, contentAlignment = Alignment.Center) {
        Icon(TvIcons.Check, size = size * 15 / 26, tint = p.background)
    }
}

/**
 * A Trakt card whose title the viewer's sources have (spec 02 HOME-FR-98): the rail's Movies or
 * Series icon in `focus` on a small `background` α0.82 square, so it reads over any poster without
 * covering it (the owner found the worded tag too large). Screen readers hear "In library". Static.
 */
@Composable
fun LibraryBadge(series: Boolean, modifier: Modifier = Modifier) {
    val p = Sohva.palette
    Box(modifier.size(24.dp).roundFill(p.background.copy(alpha = 0.82f), 6.dp), contentAlignment = Alignment.Center) {
        Icon(
            if (series) NavIcons.Series else NavIcons.Movies,
            size = 16.dp,
            tint = p.focus,
            contentDescription = stringResource(R.string.home_trakt_in_library),
        )
    }
}

/**
 * Shown whenever the player is buffering: without it a stalled stream and a dead one look alike.
 * The arc is the only infinite animation; it steps every 150 ms in reduced motion (design/02 §14).
 */
@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) {
    val p = Sohva.palette
    val angle = Motion.bufferingAngle()
    Row(
        modifier.roundFill(p.background.copy(alpha = 0.72f), Sohva.shapes.large).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).drawBehind {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawCircle(p.textPrimary.copy(alpha = 0.18f), radius = size.minDimension / 2 - inset, style = Stroke(stroke))
                drawArc(p.focus, angle.value, 90f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            },
        )
        Text(
            stringResource(R.string.player_buffering),
            Modifier.padding(start = 14.dp),
            style = Sohva.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = p.textPrimary,
        )
    }
}
