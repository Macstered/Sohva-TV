package com.sohva.tv.feature.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.ProgressBar
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlin.math.ceil

/**
 * The details pages' backdrop (layout §2 "Backdrop", spec 40 §9.7): the image at full strength,
 * decoded as RGB_565 at no more than the screen size, under one horizontal and one vertical scrim;
 * the ground gradient only when there is no image. It is its own layer beneath the scrolling
 * column, so scrolling and focus moves never repaint it.
 */
@Composable
internal fun DetailsBackdrop(url: String?) {
    val palette = Sohva.palette
    val loader = LocalArtwork.current
    // The window's size in pixels: the backdrop is never decoded larger than the screen (§9.5).
    val size = LocalWindowInfo.current.containerSize
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, size) {
        if (!url.isNullOrBlank()) image = loader.load(url, size.width, size.height, opaque = true)
    }
    val ground = remember(palette) { Brush.linearGradient(listOf(palette.backgroundTop, palette.background, palette.backgroundBottom)) }
    val across = remember(palette) {
        Brush.horizontalGradient(
            0f to palette.background.copy(alpha = 0.97f),
            0.28f to palette.background.copy(alpha = 0.84f),
            0.60f to palette.background.copy(alpha = 0.04f),
            1f to palette.background.copy(alpha = 0.22f),
        )
    }
    val down = remember(palette) {
        Brush.verticalGradient(
            0f to palette.background.copy(alpha = 0.42f),
            0.20f to palette.background.copy(alpha = 0f),
            0.86f to palette.background.copy(alpha = 0.99f),
            1f to palette.background,
        )
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val bitmap = image
            if (bitmap == null) {
                drawRect(ground)
            } else {
                val scale = maxOf(this.size.width / bitmap.width, this.size.height / bitmap.height)
                val w = (this.size.width / scale).toInt().coerceAtMost(bitmap.width)
                val h = (this.size.height / scale).toInt().coerceAtMost(bitmap.height)
                drawImage(
                    bitmap,
                    srcOffset = IntOffset((bitmap.width - w) / 2, (bitmap.height - h) / 2),
                    srcSize = IntSize(w, h),
                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                )
            }
            drawRect(across)
            drawRect(down, topLeft = Offset.Zero)
        },
    )
}

/** "MOVIES › GROUP › TITLE" (layout §2 "Breadcrumb"): upper-cased, the last crumb brighter. */
@Composable
internal fun Breadcrumb(parts: List<String>, modifier: Modifier = Modifier) {
    val style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, part ->
            if (i > 0) Text("›", Modifier.padding(horizontal = 8.dp), style = style, color = Sohva.palette.textDisabled)
            Text(
                part.uppercase(),
                style = style,
                color = if (i == parts.lastIndex) Sohva.palette.textMuted else Sohva.palette.textDim,
                maxLines = 1,
            )
        }
    }
}

/** Score with a star, then the facts joined "  ·  ", then the quality chips (layout §2 "Facts"). */
@Composable
internal fun FactsRow(score: String?, facts: List<String>, chips: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!score.isNullOrBlank()) {
            Icon(TvIcons.Star, size = 16.dp, tint = Sohva.palette.rating)
            Text(score, style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.rating)
        }
        if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted)
        chips.forEach { TvTagChip(it, tone = TagTone.PRIMARY) }
    }
}

/**
 * "Watched X of Y", the 220 × 4 dp bar and "Z left" (VOD-FR-63): only for a started, unfinished
 * title with a position and a duration; minutes round up.
 */
@Composable
internal fun ProgressLine(progress: Progress?, modifier: Modifier = Modifier) {
    val p = progress ?: return
    if (p.completed || p.positionMs <= 0 || p.durationMs <= 0) return
    Row(modifier.testTag("details-progress"), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.details_watched_of, runtime(p.positionMs), runtime(p.durationMs)),
            style = Sohva.typography.label,
            color = Sohva.palette.textMuted,
        )
        ProgressBar({ p.fraction }, Modifier.padding(horizontal = 14.dp).width(220.dp), height = 4.dp)
        Text(stringResource(R.string.details_time_left, runtime(p.durationMs - p.positionMs)), style = Sohva.typography.label, color = Sohva.palette.textMuted)
    }
}

/** "%1$d h %2$d min" from an hour ("2 h 0 min"), else "%1$d min"; minutes round up (VOD-FR-61, -63). */
@Composable
internal fun runtime(ms: Long): String {
    val minutes = ceil(ms / 60_000.0).toInt()
    return if (minutes >= 60) {
        stringResource(R.string.details_runtime_hours, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.series_episode_duration_minutes, minutes)
    }
}

/** "N seasons" (VOD-FR-78). */
@Composable
internal fun seasonCount(count: Int): String = pluralStringResource(R.plurals.series_season_count, count, count)

/**
 * A details action (layout §2 "Action button"): 48 dp tall, medium corners, `surfaceRaised` for
 * the primary one and `surface` for the rest at rest; focus flips the fill, no scale.
 */
@Composable
internal fun DetailsButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = false,
) {
    val palette = Sohva.palette
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        resting = if (primary) palette.surfaceRaised else palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        padding = PaddingValues(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    )
    TvSurface(onClick = onClick, modifier = modifier.height(48.dp), state = SurfaceState(), style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, size = 18.dp, tint = colors.content)
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = colors.content, maxLines = 1)
        }
    }
}

/**
 * The action row (layout §3 "Actions"): 12 dp apart on one line, scrolling sideways when the
 * buttons are wider than the page (a series with a started episode has six).
 */
@Composable
internal fun ActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** A section heading: headline Bold, 30 dp above and 14 below (layout §3). */
@Composable
internal fun SectionHeading(text: String, top: Int = 30, bottom: Int = 14) {
    Text(
        text,
        Modifier.padding(top = top.dp, bottom = bottom.dp),
        style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold),
        color = Sohva.palette.textPrimary,
    )
}
