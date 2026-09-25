package com.sohva.tv.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.WatchedBadge
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * An episode card (spec 40 VOD-FR-84, layout §4): the 208 × 117 dp still in one rounded clip with a
 * bottom scrim, "S1 E2", the title, a 3 dp bar when partly watched, the watched badge, a 2 dp rule
 * under the still when selected, and the duration. Focusing it selects the episode; OK plays it.
 */
@Composable
internal fun EpisodeCardView(
    card: EpisodeCard,
    selected: Boolean,
    fallbackImage: String?,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val record = card.record
    val palette = Sohva.palette
    val ring = palette.textPrimary
    Column(
        modifier
            .width(208.dp)
            .testTag("series-episode-${record.key}")
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .focusable(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(117.dp)
                .drawWithContent {
                    drawContent()
                    if (focused) {
                        val stroke = 3.dp.toPx()
                        drawRoundRect(ring, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(12.dp.toPx()), style = Stroke(stroke))
                    }
                },
        ) {
            Still(record.thumbnailUrl ?: fallbackImage.takeIf { selected })
            val progress = card.progress
            if (progress != null && !progress.completed && progress.fraction > 0f && progress.fraction < 1f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.fraction).height(3.dp).roundFill(palette.focus, 0.dp))
            }
            if (progress?.completed == true) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).roundFill(if (selected) palette.focus else palette.divider.copy(alpha = 0f), 0.dp))
        Text(
            stringResource(R.string.series_episode_label, record.season, record.number),
            Modifier.padding(top = 6.dp),
            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = palette.focus,
        )
        Text(
            card.title ?: stringResource(R.string.addon_ui_episode, record.number),
            style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold),
            color = if (focused) palette.textPrimary else palette.textMuted,
            maxLines = 1,
        )
        record.durationSeconds?.takeIf { it > 0 }?.let {
            Text(runtime(it * 1_000L), style = Sohva.typography.caption, color = palette.textDim)
        }
    }
}

/** The still, decoded as RGB_565 at the card's size, under the shared bottom scrim; a plain tile without one. */
@Composable
private fun Still(url: String?) {
    val loader = LocalArtwork.current
    val palette = Sohva.palette
    val px = with(LocalDensity.current) { IntSize(208.dp.roundToPx(), 117.dp.roundToPx()) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) image = loader.load(url, px.width, px.height, opaque = true)
    }
    val scrim = remember(palette) { Brush.verticalGradient(0.55f to palette.background.copy(alpha = 0f), 1f to palette.background.copy(alpha = 0.72f)) }
    val ground = palette.surfaceSubtle
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Sohva.shapes.medium))
            .drawBehind {
                val bitmap = image
                if (bitmap == null) {
                    drawRect(ground)
                    return@drawBehind
                }
                val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
                val w = (size.width / scale).toInt().coerceAtMost(bitmap.width)
                val h = (size.height / scale).toInt().coerceAtMost(bitmap.height)
                drawImage(bitmap, IntOffset((bitmap.width - w) / 2, (bitmap.height - h) / 2), IntSize(w, h), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                drawRect(scrim)
            },
    )
}
