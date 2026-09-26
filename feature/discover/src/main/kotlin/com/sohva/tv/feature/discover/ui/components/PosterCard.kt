package com.sohva.tv.feature.discover.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.ProgressBar
import com.sohva.tv.ui.design.components.WatchedBadge
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** What a poster card shows (spec 50 §5.2). */
data class PosterContent(
    val title: String,
    val poster: String?,
    val progress: Float? = null,
    val watched: Boolean = false,
    val caption: String? = null,
)

/**
 * The 2:3 poster card of every Discover grid and row (§5.2): the poster decoded at the drawn size
 * as RGB_565 (§9 "Poster decode"), the title only when there is no poster or it failed, nothing
 * drawn while it loads. Focus is a 3 dp border drawn inside the card, never a scale (lazy rows
 * would clip it). Captions under the card in grids, Search and Library.
 */
@Composable
fun PosterCard(content: PosterContent, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val p = Sohva.palette
    val shape = RoundedCornerShape(Sohva.shapes.medium)
    Column(
        modifier.width(width)
            .semantics { contentDescription = content.title }
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Brush.verticalGradient(listOf(p.surface, p.background)), shape)) {
            Poster(content, width)
            content.progress?.let { f -> ProgressBar({ f }, Modifier.align(Alignment.BottomStart), height = 4.dp, track = p.background.copy(alpha = 0f)) }
            if (content.watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp), size = 22.dp)
            if (focused) {
                val corner = Sohva.shapes.medium
                Box(Modifier.fillMaxSize().drawBehind {
                    val w = 3.dp.toPx()
                    drawRoundRect(p.textPrimary, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(corner.toPx()), style = Stroke(w))
                })
            }
        }
        content.caption?.let { caption ->
            Text(
                content.title, Modifier.padding(top = 7.dp).height(20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.label, color = if (focused) p.textPrimary else p.textMuted,
            )
            if (caption.isNotEmpty()) Text(caption, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = p.textDim)
        }
    }
}

@Composable
private fun Poster(content: PosterContent, width: Dp) {
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = w * 3 / 2
    var image by remember(content.poster) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(content.poster) { mutableStateOf(content.poster == null) }
    LaunchedEffect(content.poster, w) {
        val url = content.poster ?: return@LaunchedEffect
        image = loader.load(url, w, h, opaque = true)
        failed = image == null
    }
    val radius = with(density) { Sohva.shapes.medium.toPx() }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            // The rounded clip is built once per size, not per frame.
            val clip = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }
            onDrawWithContent {
                val bitmap = image
                if (bitmap != null) {
                    // Cropped to fill, clipped to the card's corners.
                    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
                    val sw = (size.width / scale).toInt()
                    val sh = (size.height / scale).toInt()
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(clip)
                    drawImage(
                        bitmap, srcOffset = IntOffset((bitmap.width - sw) / 2, (bitmap.height - sh) / 2), srcSize = IntSize(sw, sh),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                    drawContext.canvas.restore()
                }
                drawContent()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Text(
                content.title, Modifier.padding(16.dp), maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.label.copy(textAlign = TextAlign.Center), color = Sohva.palette.textMuted,
            )
        }
    }
}

/** A loading placeholder card (FR-61): surface fill, poster-shaped; the first may take focus. */
@Composable
fun PosterPlaceholder(width: Dp, focusable: Boolean, description: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val p = Sohva.palette
    val base = modifier.width(width).aspectRatio(2f / 3f).background(p.surface, RoundedCornerShape(Sohva.shapes.medium))
    Box(
        if (focusable) {
            base.semantics { contentDescription = description }.focusable(interactionSource = interaction)
                .then(if (focused) Modifier.border(3.dp, p.textPrimary, RoundedCornerShape(Sohva.shapes.medium)) else Modifier)
        } else {
            base
        },
    )
}
