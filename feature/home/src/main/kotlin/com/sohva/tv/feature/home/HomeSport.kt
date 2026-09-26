package com.sohva.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LiveDot
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.text.rememberTimeStyle
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A Today's sport card (spec 02 §5 table, spec 60 SPORT-FR-97): 244 × 160 dp; the status or kick-off,
 * the two teams with 38 dp crests around the score ("–" before one), the competition.
 */
@Composable
internal fun SportCardView(card: SportCard, zoneId: String?, modifier: Modifier, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surfaceSubtle, focusScale = 1f, padding = PaddingValues(12.dp))
    val event = card.event
    val live = event.status == EventStatus.LIVE
    TvSurface(onClick = onClick, modifier = modifier.size(244.dp, 160.dp), style = style) { colors ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    LiveDot(size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    statusOrKickOff(event, zoneId), maxLines = 1, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (live && !colors.focused) Sohva.palette.danger else colors.secondaryContent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Team(event.home, colors.content, Modifier.weight(1f))
                Text(
                    event.score ?: "–", Modifier.padding(horizontal = 8.dp), maxLines = 1,
                    style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                    color = if (live && !colors.focused) Sohva.palette.focus else colors.content,
                )
                Team(event.away, colors.content, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Text(event.competition, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = colors.secondaryContent)
        }
    }
}

@Composable
private fun Team(side: Side, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(38.dp).roundFill(content.copy(alpha = 0.10f), 8.dp), contentAlignment = Alignment.Center) {
            Text(side.initials, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black), color = content)
            side.logo?.let { Crest(it, 32.dp, Modifier.size(38.dp).padding(3.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            side.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), color = content,
        )
    }
}

/** The minute or "LIVE" while live, the kick-off in the app zone while scheduled, else the status in words. */
@Composable
private fun statusOrKickOff(event: SportEvent, zoneId: String? = null): String {
    val style = rememberTimeStyle()
    val labels = remember(zoneId, style) { TimeLabels(TimeLabels.zoneOf(zoneId), style) }
    return when (event.status) {
        EventStatus.LIVE -> event.minute ?: stringResource(R.string.status_live)
        EventStatus.SCHEDULED -> labels.guideTime(event.startMillis)
        EventStatus.FINISHED -> stringResource(R.string.status_finished)
        EventStatus.POSTPONED -> stringResource(R.string.status_postponed)
        EventStatus.CANCELLED -> stringResource(R.string.status_cancelled)
        EventStatus.INTERRUPTED -> stringResource(R.string.status_interrupted)
        EventStatus.UNKNOWN -> stringResource(R.string.status_unknown)
    }
}

/**
 * The hero for a focused sport card (SPORT-FR-97): "Live now" or "Today's sport", "home – away",
 * then the competition, the minute or kick-off, and the score.
 */
@Composable
internal fun sportText(event: SportEvent, zoneId: String?): HeroText {
    val live = event.status == EventStatus.LIVE
    val facts = listOfNotNull(event.competition, statusOrKickOff(event, zoneId), event.score).joinToString("  ·  ")
    return HeroText(stringResource(if (live) R.string.home_hero_live else R.string.home_sports_today), live, event.title, facts, null, null)
}

/**
 * The sport hero's picture (SPORT-FR-97): the two crests at 150 dp, 56 dp apart, at α 0.55 over a
 * 60 % `background` wash in the art box; each crest decoded at 150 dp (spec 60 §9 "Images").
 */
@Composable
internal fun SportBackdrop(event: SportEvent) {
    val wash = Sohva.palette.background.copy(alpha = 0.6f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = maxWidth * 0.66f
        val boxH = maxHeight * 0.56f
        Box(Modifier.fillMaxSize().drawBehind { drawRect(wash, Offset(size.width * 0.34f, 0f), Size(size.width * 0.66f, size.height * 0.56f)) })
        Row(
            Modifier.padding(start = maxWidth - boxW).width(boxW).height(boxH),
            horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(event.home, event.away).forEach { side ->
                Box(Modifier.size(150.dp)) { side.logo?.let { Crest(it, 150.dp, Modifier.size(150.dp), alpha = 0.55f) } }
            }
        }
    }
}

/** A crest decoded at the size it is drawn, fitted, nothing until it arrives. */
@Composable
internal fun Crest(url: String, size: Dp, modifier: Modifier, alpha: Float = 1f) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, px) { image = loader.load(url, px, px, opaque = false) }
    Box(
        modifier.drawBehind {
            val bitmap = image ?: return@drawBehind
            // Fitted and centred, its alpha on the image itself: no layer (AGENTS.md §4 rule 6).
            val scale = minOf(this.size.width / bitmap.width, this.size.height / bitmap.height)
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            drawImage(
                bitmap, dstOffset = androidx.compose.ui.unit.IntOffset(((this.size.width - w) / 2).toInt(), ((this.size.height - h) / 2).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w, h), alpha = alpha,
            )
        },
    )
}
