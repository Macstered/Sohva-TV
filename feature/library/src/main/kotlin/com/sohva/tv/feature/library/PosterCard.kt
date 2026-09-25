package com.sohva.tv.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.QualityChips
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.InitialsTile
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.components.WatchedBadge
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A poster card (spec 40 VOD-FR-33…39, layout §1 "Poster card"): the 2:3 poster in one rounded
 * clip, the chips, a one-line title and "year · rating". Focus is the 3 dp ring, drawn in the
 * draw phase, and the title's colour; no scale, shadow, animation or click indication (§9.6).
 * Cards of a stale wall cannot take focus (VOD-FR-14).
 */
@Composable
internal fun PosterCard(
    item: WallItem,
    enabled: Boolean,
    watched: Boolean,
    posterPx: IntSize,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onMove: (Key) -> Boolean,
    register: WallFocus,
    index: Int,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    DisposableEffect(index, requester) {
        register.register(index, requester)
        onDispose { register.unregister(index, requester) }
    }
    val ring = Sohva.palette.textPrimary
    val row = item.row
    Column(
        modifier
            .testTag("library-card-${row.key}")
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRequester(requester)
            .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && onMove(e.key) }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .focusable(enabled),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .drawWithContent {
                    drawContent()
                    if (focused) drawRing(ring)
                },
        ) {
            Poster(row.name, row.posterUrl, posterPx)
            if (watched) WatchedBadge(Modifier.align(Alignment.TopStart).padding(6.dp).testTag("library-tick"))
            Chips(row.qualityMask, Modifier.align(Alignment.TopEnd))
        }
        Text(
            row.name,
            Modifier.padding(top = 9.dp),
            style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (focused) Sohva.palette.textPrimary else Sohva.palette.textMuted,
            maxLines = 1,
        )
        val facts = remember(row.year, row.rating) { listOfNotNull(row.year?.toString(), row.rating?.takeIf { it.isNotBlank() }).joinToString(" · ") }
        Text(facts, style = Sohva.typography.caption, color = Sohva.palette.textDim, maxLines = 1)
    }
}

/**
 * The poster in one rounded clip over a solid `surfaceSubtle` fill: the initials when there is no
 * address or it fails (beta 23 left an empty tile), the image cropped once it has loaded. Decoded
 * as RGB_565 at the drawn size, no cross-fade; the request is cancelled when the card leaves.
 */
@Composable
internal fun Poster(title: String, url: String?, px: IntSize) {
    val loader = LocalArtwork.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    LaunchedEffect(url, px) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        image = loader.load(url, px.width, px.height, opaque = true)
        failed = image == null
    }
    val shape = RoundedCornerShape(Sohva.shapes.medium)
    val ground = Sohva.palette.surfaceSubtle
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .drawBehind {
                val bitmap = image
                if (bitmap == null) drawRect(ground) else drawCropped(bitmap)
            },
    ) {
        if (failed) InitialsTile(title, Modifier.fillMaxSize(), corner = Sohva.shapes.medium, fontSize = 22.sp)
    }
}

/** "×N" (films folded from several copies, M4b) and the picture-quality chips, 6 dp in, 4 dp apart. */
@Composable
private fun Chips(qualityMask: Int, modifier: Modifier) {
    if (qualityMask == 0) return
    val labels = remember(qualityMask) { QualityChips.labels(qualityMask) }
    Row(modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { TvTagChip(it, tone = TagTone.PRIMARY) }
    }
}

internal fun DrawScope.drawRing(color: Color) {
    val stroke = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(stroke),
    )
}

/** Draws [bitmap] scaled to fill the box, cropped to its centre. */
internal fun DrawScope.drawCropped(bitmap: ImageBitmap) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val srcW = (size.width / scale).toInt().coerceAtMost(bitmap.width)
    val srcH = (size.height / scale).toInt().coerceAtMost(bitmap.height)
    drawImage(
        bitmap,
        srcOffset = IntOffset((bitmap.width - srcW) / 2, (bitmap.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

@StringRes
internal fun genreLabel(genre: Genre): Int = when (genre) {
    Genre.ACTION -> R.string.genre_action
    Genre.ADVENTURE -> R.string.genre_adventure
    Genre.ANIMATION -> R.string.genre_animation
    Genre.COMEDY -> R.string.genre_comedy
    Genre.CRIME -> R.string.genre_crime
    Genre.DOCUMENTARY -> R.string.genre_documentary
    Genre.DRAMA -> R.string.genre_drama
    Genre.FAMILY -> R.string.genre_family
    Genre.FANTASY -> R.string.genre_fantasy
    Genre.HISTORY -> R.string.genre_history
    Genre.HORROR -> R.string.genre_horror
    Genre.MUSIC -> R.string.genre_music
    Genre.MYSTERY -> R.string.genre_mystery
    Genre.NEWS -> R.string.genre_news
    Genre.REALITY -> R.string.genre_reality
    Genre.ROMANCE -> R.string.genre_romance
    Genre.SCIENCE_FICTION -> R.string.genre_science_fiction
    Genre.SOAP -> R.string.genre_soap
    Genre.TALK -> R.string.genre_talk
    Genre.THRILLER -> R.string.genre_thriller
    Genre.WAR -> R.string.genre_war
    Genre.WESTERN -> R.string.genre_western
}
