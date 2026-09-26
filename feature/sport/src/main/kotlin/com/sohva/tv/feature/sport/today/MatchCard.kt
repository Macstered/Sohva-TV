package com.sohva.tv.feature.sport.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LiveBadge
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.drawFitted
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.TvSurfaceColors
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.Sohva

private val CARD_STYLE: SurfaceStyle
    @Composable get() = SurfaceStyle(corner = Sohva.shapes.large, resting = Sohva.palette.surfaceSubtle, restingContent = Sohva.palette.textPrimary, focusScale = 1.03f, padding = androidx.compose.foundation.layout.PaddingValues(16.dp))

/**
 * A game on Today (design D§5, SPORT-FR-50): competition and status, the two teams around the
 * score or kick-off, the watch call to action and the favourite star. 238 × 224 dp; the fill flips
 * on focus, no shadow (AGENTS.md §4 rule 6).
 */
@Composable
internal fun MatchCard(event: SportEvent, kickOff: String, watch: WatchSummary?, favourite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvSurface(onClick, modifier.size(238.dp, 224.dp), style = CARD_STYLE) { colors ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                event.competitionLogo?.let {
                    Crest(it, 18.dp, Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    event.competition.uppercase(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp), color = colors.secondaryContent,
                )
                Spacer(Modifier.width(10.dp))
                StatusBadge(event, colors.secondaryContent.takeIf { colors.focused })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TeamMark(event.home, event.sport, colors, Modifier.weight(1f))
                Centre(event, kickOff, colors, Modifier.width(62.dp))
                TeamMark(event.away, event.sport, colors, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallToAction(watch, colors, Modifier.weight(1f))
                if (favourite) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.Image(
                        painterResource(TvIcons.Star), null, Modifier.size(18.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Sohva.palette.accent),
                    )
                }
            }
        }
    }
}

/** Score or kick-off, the score detail (AFL goals and behinds), and the status label such as the minute. */
@Composable
private fun Centre(event: SportEvent, kickOff: String, colors: TvSurfaceColors, modifier: Modifier) {
    val live = event.status == EventStatus.LIVE
    Column(modifier.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            event.score ?: kickOff, maxLines = 1,
            style = Sohva.typography.headline.copy(fontWeight = FontWeight.Black),
            color = if (colors.focused) colors.content else if (live) Sohva.palette.focus else colors.content,
        )
        event.scoreDetail?.let { Text(it, maxLines = 1, style = Sohva.typography.caption, color = colors.secondaryContent) }
    }
}

/** The badge (SPORT-FR-52): live with its minute and a finite pulse, else the status in words. */
@Composable
internal fun StatusBadge(event: SportEvent, focusedColor: Color? = null) {
    if (event.status == EventStatus.LIVE) {
        LiveBadge(event.minute.orEmpty())
        return
    }
    val p = Sohva.palette
    val (text, color) = when (event.status) {
        EventStatus.SCHEDULED -> R.string.status_scheduled to p.textMuted
        EventStatus.FINISHED -> R.string.status_finished to p.textMuted
        EventStatus.POSTPONED -> R.string.status_postponed to p.accent
        EventStatus.INTERRUPTED -> R.string.status_interrupted to p.accent
        EventStatus.CANCELLED -> R.string.status_cancelled to p.danger
        else -> R.string.status_unknown to p.textMuted
    }
    Text(
        stringResource(text).uppercase(), maxLines = 1,
        style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
        color = focusedColor ?: color,
    )
}

/** A 48 dp circle with the initials in the sport's accent, the crest over them once loaded, then the name. */
@Composable
private fun TeamMark(side: Side, sport: SportType, colors: TvSurfaceColors, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).background(if (colors.focused) Sohva.palette.background.copy(alpha = 0.12f) else Sohva.palette.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(side.initials, style = Sohva.typography.label.copy(fontWeight = FontWeight.Black), color = accent(sport))
            side.logo?.let { Crest(it, 40.dp, Modifier.size(48.dp).padding(4.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            side.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold, lineHeight = 17.sp, textAlign = TextAlign.Center), color = colors.content,
        )
    }
}

/** A crest or logo decoded at the size it is drawn (spec 60 §9 "Images"); nothing drawn until it arrives. */
@Composable
internal fun Crest(url: String, size: Dp, modifier: Modifier) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, px) { image = loader.load(url, px, px, opaque = false) }
    Box(modifier.drawBehind { image?.let { drawFitted(it, this.size.width, this.size.height) } })
}

/** SPORT-FR-51: watch count, else possible matches, else no broadcast. */
@Composable
private fun CallToAction(watch: WatchSummary?, colors: TvSurfaceColors, modifier: Modifier) {
    val p = Sohva.palette
    val available = watch?.available ?: 0
    val possible = watch?.possible ?: 0
    val (text, color) = when {
        available > 0 -> pluralStringResource(R.plurals.today_watch_channels, available, available) to p.focus
        possible > 0 -> pluralStringResource(R.plurals.today_possible_channels, possible, possible) to p.textPrimary
        else -> stringResource(R.string.today_no_broadcast) to p.textDim
    }
    val fill = when {
        colors.focused -> p.background
        available > 0 || possible > 0 -> p.surface
        else -> Color.Transparent
    }
    Box(modifier.height(38.dp).roundFill(fill, Sohva.shapes.small).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = if (colors.focused && available == 0) p.textPrimary else color)
    }
}

/** Sport accents (D§5): football and ice hockey use `focus`, the others their own colour. */
@Composable
internal fun accent(sport: SportType): Color = when (sport) {
    SportType.FOOTBALL, SportType.ICE_HOCKEY -> Sohva.palette.focus
    SportType.AUSTRALIAN_FOOTBALL -> ContentColors.australianFootball
    SportType.BASKETBALL -> ContentColors.basketball
    SportType.BASEBALL -> ContentColors.baseball
    SportType.HANDBALL -> ContentColors.handball
    SportType.RUGBY -> ContentColors.rugby
    SportType.VOLLEYBALL -> ContentColors.volleyball
    SportType.AMERICAN_FOOTBALL -> ContentColors.americanFootball
    SportType.MMA -> ContentColors.mma
    SportType.FORMULA_1 -> ContentColors.formulaOne
    SportType.NBA -> ContentColors.nba
}

