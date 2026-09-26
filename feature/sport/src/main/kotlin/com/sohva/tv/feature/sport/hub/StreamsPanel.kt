package com.sohva.tv.feature.sport.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.text.StreamTags
import com.sohva.tv.feature.sport.today.TodayModel
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.focus.KeepVisibleBringIntoViewSpec
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlin.math.abs

/**
 * D§8 "Streams" (SPORT-FR-73…78): the header with the watchable count, then one row per stream in
 * the order it had when the hub opened. [firstLead] is the first row's lead control, which takes
 * focus on open; after a decision [refocus] names the row whose new lead control takes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamsPanel(model: TodayModel, firstLead: FocusRequester, refocus: MutableState<String?>, modifier: Modifier) {
    val streams by model.hubStreams.collectAsStateWithLifecycle()
    val failed by model.decisionFailed.collectAsStateWithLifecycle()
    val watchable = streams.count { it.confidence == Confidence.AVAILABLE }
    Column(modifier.fillMaxHeight().background(Sohva.palette.surface, RoundedCornerShape(14.dp)).padding(16.dp).testTag("hub-streams")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(TvIcons.Play), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Sohva.palette.accent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.streams_title), style = PANEL_TITLE, color = Sohva.palette.accent)
                Text(stringResource(R.string.streams_subtitle), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            }
            Text(
                stringResource(R.string.streams_available_count, watchable), Modifier.testTag("hub-streams-count"),
                style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = if (watchable > 0) Sohva.palette.focus else Sohva.palette.textMuted,
            )
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Sohva.palette.divider))
        if (failed) {
            Text(stringResource(R.string.error_match_decision), Modifier.padding(bottom = 8.dp).testTag("hub-decision-failed"), style = Sohva.typography.caption, color = Sohva.palette.danger)
        }
        if (streams.isEmpty()) {
            Text(stringResource(R.string.streams_empty), Modifier.testTag("hub-streams-empty"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
            return@Column
        }
        // A focused row scrolls only as far as needed to show it whole, never to the centre (SPORT-FR-78).
        CompositionLocalProvider(LocalBringIntoViewSpec provides KeepVisibleBringIntoViewSpec) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("hub-stream-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(streams, key = { it.channelKey }) { stream ->
                    StreamRow(stream, model, if (stream.channelKey == streams.first().channelKey) firstLead else null, refocus)
                }
            }
        }
    }
}

/** Which buttons a row has (SPORT-FR-75); the first is its lead control. */
private enum class RowAction { WATCH, CONFIRM, REJECT, RESTORE }

private fun actionsOf(s: StreamMatch): List<RowAction> = buildList {
    if (s.confidence == Confidence.AVAILABLE) add(RowAction.WATCH)
    if (s.decision == null) {
        if (s.confidence == Confidence.POSSIBLE) add(RowAction.CONFIRM)
        add(RowAction.REJECT)
    } else {
        add(RowAction.RESTORE)
    }
}

/**
 * One stream (SPORT-FR-74): status, source, channel, what is on, tags, the start explanation and
 * the buttons. Flat fill with a coloured left edge for the confidence, not beta 23's gradient (D§10).
 */
@Composable
private fun StreamRow(stream: StreamMatch, model: TodayModel, firstLead: FocusRequester?, refocus: MutableState<String?>) {
    val p = Sohva.palette
    val (label, edge) = when {
        stream.decision == Decision.CONFIRMED -> stringResource(R.string.match_confirmed_by_you).uppercase() to p.focus
        stream.confidence == Confidence.AVAILABLE -> stringResource(R.string.match_available).uppercase() to p.focus
        stream.confidence == Confidence.POSSIBLE -> stringResource(R.string.match_possible).uppercase() to p.accent
        else -> stringResource(R.string.match_rejected).uppercase() to p.textDim
    }
    val fill = if (stream.confidence == Confidence.REJECTED) p.background else p.surfaceSubtle
    Column(
        Modifier.fillMaxWidth().background(fill, RoundedCornerShape(10.dp))
            .drawBehind { drawRect(edge, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 15.dp, end = 12.dp, top = 9.dp, bottom = 9.dp).testTag("hub-stream-${stream.channelKey}"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(edge, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, Modifier.weight(1f).testTag("hub-stream-status-${stream.channelKey}"), maxLines = 1, style = Sohva.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Black), color = edge)
            Text(
                stringResource(if (stream.source == MatchSource.GUIDE) R.string.match_source_epg else R.string.match_source_m3u),
                style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = p.textMuted,
            )
        }
        Text(stream.channelName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = p.textPrimary)
        Text(
            if (stream.source == MatchSource.GUIDE) stream.programmeTitle else stringResource(R.string.match_m3u_detail),
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = p.textMuted,
        )
        val tags = remember(stream.channelName) { StreamTags.parts(stream.channelName) }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { (kind, text) ->
                    TvTagChip(
                        text,
                        tone = when (kind) {
                            StreamTags.Kind.RESOLUTION -> TagTone.PRIMARY
                            StreamTags.Kind.DYNAMIC_RANGE -> TagTone.ACCENT
                            else -> TagTone.MUTED
                        },
                    )
                }
            }
        }
        Text(offsetText(stream), maxLines = 1, style = Sohva.typography.caption, color = p.textDim)
        Actions(stream, model, firstLead, refocus)
    }
}

@Composable
private fun Actions(stream: StreamMatch, model: TodayModel, firstLead: FocusRequester?, refocus: MutableState<String?>) {
    val actions = actionsOf(stream)
    val lead = remember { FocusRequester() }
    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { i, action ->
            // The lead control answers both to the row (after a decision) and, on the first row, to the hub's opening focus.
            val focus = if (i == 0) Modifier.focusRequester(lead).then(firstLead?.let { Modifier.focusRequester(it) } ?: Modifier) else Modifier
            val (text, icon, run) = when (action) {
                RowAction.WATCH -> Triple(R.string.action_watch, TvIcons.Play) { model.play(stream.channelKey) }
                RowAction.CONFIRM -> Triple(R.string.action_confirm, TvIcons.Check) { decide(model, stream, Decision.CONFIRMED, refocus) }
                RowAction.REJECT -> Triple(R.string.action_reject, TvIcons.Close) { decide(model, stream, Decision.REJECTED, refocus) }
                RowAction.RESTORE -> Triple(R.string.action_restore, TvIcons.Replay) { decide(model, stream, null, refocus) }
            }
            TvActionButton(
                stringResource(text), run, focus.testTag("hub-stream-${action.name.lowercase()}-${stream.channelKey}"), icon,
                state = SurfaceState(keepsFocus = true), compact = true,
            )
        }
    }
    // SPORT-FR-77: once the decision's state arrives, the same row's new lead control takes focus.
    val leadAction = actions.first()
    LaunchedEffect(leadAction, refocus.value) {
        if (refocus.value == stream.channelKey) {
            lead.requestFocusWhenAttached(FOCUS_ATTEMPTS)
            refocus.value = null
        }
    }
}

private fun decide(model: TodayModel, stream: StreamMatch, decision: Decision?, refocus: MutableState<String?>) {
    refocus.value = stream.channelKey
    model.decide(stream, decision)
}

/** SPORT-FR-74 offset line: how the programme's or the name's start relates to kick-off. */
@Composable
private fun offsetText(s: StreamMatch): String {
    val minutes = abs(s.offsetMinutes).toInt()
    return when {
        s.source == MatchSource.GUIDE && s.offsetMinutes == 0L -> stringResource(R.string.offset_epg_exact)
        s.source == MatchSource.GUIDE && s.offsetMinutes < 0 -> stringResource(R.string.offset_epg_before, minutes)
        s.source == MatchSource.GUIDE -> stringResource(R.string.offset_epg_after, minutes)
        !s.explicitStart -> stringResource(R.string.offset_m3u_teams)
        s.offsetMinutes == 0L -> stringResource(R.string.offset_m3u_exact)
        s.offsetMinutes < 0 -> stringResource(R.string.offset_m3u_before, minutes)
        else -> stringResource(R.string.offset_m3u_after, minutes)
    }
}

/** SPORT-FR-77: up to 90 frames for the new state to arrive and draw. */
private const val FOCUS_ATTEMPTS = 90
