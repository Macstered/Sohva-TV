package com.sohva.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.InitialsTile
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

internal val LANDSCAPE_WIDTH = 186.dp
private val ART_W = 178.dp
private val ART_H = 102.dp

/**
 * A Continue watching card (spec 02 §5): art over a progress bar, title and a subtitle line that is
 * always laid out so the row keeps one baseline. Artwork cards keep their fill and draw a ring when
 * focused; beta 23's 14 dp focus shadow is dropped (AGENTS.md §4 rule 6, as elsewhere in the app).
 */
@Composable
internal fun ResumeCardView(card: ResumeCard, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, focusRing = true, focusScale = 1f, padding = PaddingValues(4.dp))
    TvSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(LANDSCAPE_WIDTH), style = style) {
        Column {
            Box(Modifier.size(ART_W, ART_H)) {
                CroppedArt(card.title, card.image, ART_W, ART_H)
                if (card.fraction > 0f) {
                    ProgressLine({ card.fraction }, Sohva.palette.background.copy(alpha = 0.62f), Modifier.align(Alignment.BottomStart).fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(card.title, style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold), color = Sohva.palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(resumeSubtitle(card), style = Sohva.typography.caption, color = Sohva.palette.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "S1 E2 · Title" or the year, then "N min left" when the duration is known (HOME-FR-13, -63). */
@Composable
internal fun resumeSubtitle(card: ResumeCard): String {
    val item = card.item
    val label = if (item.season != null && item.episode != null) {
        val code = stringResource(R.string.series_episode_label, item.season!!, item.episode!!)
        item.episodeTitle?.takeIf { it.isNotBlank() }?.let { "$code · $it" } ?: code
    } else {
        item.year?.toString()
    }
    val left = card.minutesLeft?.let { pluralStringResource(R.plurals.home_minutes_left, it, it) }
    // Beta 23's facts separator, as on the hero (HOME-FR-63).
    return listOfNotNull(label, left).joinToString("  ·  ")
}

/** A recent channel card (spec 02 §5): logo, name, programme, and the programme's progress at the minute tick. */
@Composable
internal fun ChannelCardView(card: ChannelCard, now: () -> Long, modifier: Modifier, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surfaceSubtle, focusScale = 1f, padding = PaddingValues(12.dp))
    val channel = card.channel
    TvSurface(onClick = onClick, modifier = modifier.size(168.dp, 104.dp), style = style) { colors ->
        Column {
            LogoTile(channel.name, channel.logoUrl, 34.dp, fontSize = Sohva.typography.caption.fontSize)
            Spacer(Modifier.height(4.dp))
            Text(channel.name, style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.programme?.title.orEmpty(), style = Sohva.typography.caption, color = colors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val programme = channel.programme
            ProgressLine(
                {
                    if (programme == null || programme.stopAt <= programme.startAt) 0f
                    else ((now() - programme.startAt).toFloat() / (programme.stopAt - programme.startAt)).coerceIn(0f, 1f)
                },
                colors.content.copy(alpha = 0.20f),
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A 3 dp bar, `focus` fill (it stays cyan on a focused card); the fraction is read in the draw phase. */
@Composable
internal fun ProgressLine(fraction: () -> Float, track: Color, modifier: Modifier) {
    val fill = Sohva.palette.focus
    Box(
        modifier.height(3.dp).drawBehind {
            val radius = CornerRadius(1.5.dp.toPx())
            drawRoundRect(track, cornerRadius = radius)
            val f = fraction()
            if (f > 0f) drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = radius)
        },
    )
}

/**
 * Art for a card box: the initials under it, the image cropped to fill once loaded, decoded at the
 * box's size (spec 02 §9.2). Read in the draw phase, so its arrival recomposes nothing.
 */
@Composable
internal fun CroppedArt(name: String, url: String?, width: Dp, height: Dp) {
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, w, h) {
        if (!url.isNullOrBlank()) image = loader.load(url, w, h, opaque = true)
    }
    Box(Modifier.size(width, height)) {
        InitialsTile(name, Modifier.size(width, height))
        Box(Modifier.size(width, height).drawBehind { image?.let { drawCropped(it) } })
    }
}

/** Draws [bitmap] scaled to cover the whole draw area, centred. */
internal fun DrawScope.drawCropped(bitmap: ImageBitmap) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val srcW = (size.width / scale).toInt().coerceAtMost(bitmap.width)
    val srcH = (size.height / scale).toInt().coerceAtMost(bitmap.height)
    drawImage(
        bitmap,
        srcOffset = IntOffset((bitmap.width - srcW) / 2, (bitmap.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

/** Continue watching while it loads or after it failed (HOME-FR-45): the default focus surface. */
@Composable
internal fun StatusCard(failed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    TvSurface(onClick = onClick, modifier = modifier, style = SurfaceStyle(corner = Sohva.shapes.small, padding = PaddingValues(24.dp))) { colors ->
        Text(
            stringResource(if (failed) R.string.home_resume_unavailable else R.string.home_resume_loading),
            style = Sohva.typography.body, color = if (colors.focused) colors.content else Sohva.palette.textMuted,
        )
    }
}
