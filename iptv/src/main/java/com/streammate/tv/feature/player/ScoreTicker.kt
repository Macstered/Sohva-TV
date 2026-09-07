package com.streammate.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.iptv.R

/**
 * What the ticker shows: the followed matches that are on now, then the ones
 * starting soon, a few at most. It reads the sport screen's own state, so it
 * costs no request the sport screen would not have made.
 */
fun scoreTickerEvents(
    events: List<TodayEvent>,
    nowEpochMillis: Long,
    max: Int = SCORE_TICKER_MAX_ROWS,
    upcomingWindowMillis: Long = SCORE_TICKER_UPCOMING_MILLIS,
): List<TodayEvent> {
    val live = events.filter { it.status == TodayEventStatus.LIVE }.sortedBy { it.startEpochMillis }
    val soon = events.filter {
        it.status == TodayEventStatus.SCHEDULED && it.startEpochMillis in nowEpochMillis..(nowEpochMillis + upcomingWindowMillis)
    }.sortedBy { it.startEpochMillis }
    return (live + soon).take(max)
}

/** A small panel over the picture: one line per match, score or start time on the right. */
@Composable
internal fun ScoreTickerOverlay(events: List<TodayEvent>, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    Column(
        modifier = modifier
            .widthIn(min = 300.dp, max = 420.dp)
            .clip(StreamMateThemeTokens.shapes.medium)
            .background(palette.panel.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("player-score-ticker"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.player_score_ticker_empty),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
        events.forEach { event ->
            val live = event.status == TodayEventStatus.LIVE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${event.home} – ${event.away}",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (live) event.score ?: event.statusLabel else event.startLabel,
                    color = if (live) palette.danger else palette.textMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                if (live && event.score != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = event.statusLabel,
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

const val SCORE_TICKER_MAX_ROWS = 4
const val SCORE_TICKER_UPCOMING_MILLIS = 3L * 60 * 60_000L
