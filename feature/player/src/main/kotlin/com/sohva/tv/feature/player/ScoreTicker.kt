package com.sohva.tv.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.text.rememberTimeStyle
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The score ticker (spec 30 §4.20, §5.14): top-right, below the clock when the info line is on;
 * live games with their score, then games starting within three hours; at most four. While it is
 * drawn over a resumed player the games poll as if Sohva Sport were on screen (SPORT-FR-27).
 */
@Composable
internal fun ScoreTicker(source: ScoreTickerSource, infoLine: Boolean, modifier: Modifier) {
    val shown by source.shown.collectAsStateWithLifecycle()
    if (!shown) return
    LifecycleResumeEffect(source) {
        source.setVisible(true)
        onPauseOrDispose { source.setVisible(false) }
    }
    val games by source.games.collectAsStateWithLifecycle(emptyList())
    val zone by source.zoneId.collectAsStateWithLifecycle("")
    val style = rememberTimeStyle()
    val labels = remember(zone, style) { TimeLabels(TimeLabels.zoneOf(zone), style) }
    Column(
        modifier.padding(end = 40.dp, top = if (infoLine) 72.dp else 24.dp).widthIn(300.dp, 420.dp)
            .roundFill(Sohva.palette.panel.copy(alpha = 0.92f), Sohva.shapes.medium).padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("player-ticker"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (games.isEmpty()) {
            Text(stringResource(R.string.player_score_ticker_empty), style = Sohva.typography.caption.copy(fontSize = 13.sp), color = Sohva.palette.textMuted)
        }
        games.forEach { TickerRow(it, labels) }
    }
}

@Composable
private fun TickerRow(game: SportEvent, labels: TimeLabels) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            game.title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = Sohva.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary,
        )
        Spacer(Modifier.width(12.dp))
        val status = game.minute ?: stringResource(R.string.status_live)
        if (game.status == EventStatus.LIVE) {
            Text(game.score ?: status, maxLines = 1, style = Sohva.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Black), color = Sohva.palette.danger)
            if (game.score != null) {
                Spacer(Modifier.width(8.dp))
                Text(status, maxLines = 1, style = Sohva.typography.caption.copy(fontSize = 12.sp), color = Sohva.palette.textMuted)
            }
        } else {
            Text(labels.guideTime(game.startMillis), maxLines = 1, style = Sohva.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Black), color = Sohva.palette.textMuted)
        }
    }
}
