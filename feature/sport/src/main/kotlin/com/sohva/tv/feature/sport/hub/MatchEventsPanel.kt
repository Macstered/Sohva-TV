package com.sohva.tv.feature.sport.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.IncidentKind
import com.sohva.tv.core.model.sport.IncidentTimeline
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.TimelineSide
import com.sohva.tv.feature.sport.today.TodayModel
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.launch

/** The match events panel's state for the game the hub shows (SPORT-FR-90, -91). */
data class MatchEventsState(
    val eventId: String? = null,
    /** Null until the first answer; an earlier list stays when a later request fails. */
    val incidents: List<Incident>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** The answer was the saved copy after the network failed (the stale fallback). */
    val cached: Boolean = false,
    val loadedAt: Long = 0,
)

/** D§8 "Match events": title, subtitle and Refresh, then the state, the timeline band and the list. */
@Composable
internal fun MatchEventsPanel(event: SportEvent, model: TodayModel, modifier: Modifier) {
    val all by model.matchEvents.collectAsStateWithLifecycle()
    val state = if (all.eventId == event.id) all else MatchEventsState(event.id, loading = true)
    Column(modifier.fillMaxHeight().background(Sohva.palette.surface, RoundedCornerShape(14.dp)).padding(16.dp).testTag("hub-events")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(TvIcons.Target), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Sohva.palette.focus))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.incidents_title), style = PANEL_TITLE, color = Sohva.palette.focus)
                Text(
                    stringResource(if (event.detailsAvailable) R.string.incidents_football_subtitle else R.string.incidents_other_subtitle),
                    style = Sohva.typography.caption, color = Sohva.palette.textMuted,
                )
            }
            if (event.detailsAvailable) {
                TvActionButton(
                    stringResource(R.string.action_refresh), { model.loadMatchEvents(event, force = true) }, Modifier.testTag("hub-events-refresh"),
                    TvIcons.Refresh, state = SurfaceState(keepsFocus = true), compact = true,
                )
            }
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Sohva.palette.divider))
        val incidents = state.incidents
        when {
            !event.detailsAvailable -> Message(stringResource(R.string.incidents_unavailable))
            incidents == null && state.failed -> {
                Message(stringResource(R.string.error_details_unavailable))
                Spacer(Modifier.height(10.dp))
                TvActionButton(
                    stringResource(R.string.action_retry), { model.loadMatchEvents(event, force = true) }, Modifier.testTag("hub-events-retry"),
                    TvIcons.Refresh, compact = true,
                )
            }
            incidents == null -> Message(stringResource(R.string.incidents_loading))
            else -> {
                if (state.failed || state.cached) {
                    Text(
                        stringResource(R.string.showing_cached_data, stringResource(R.string.error_details_unavailable)),
                        Modifier.padding(bottom = 8.dp).testTag("hub-events-cached"), style = Sohva.typography.caption, color = Sohva.palette.accent,
                    )
                }
                if (incidents.isEmpty()) {
                    Message(stringResource(R.string.incidents_empty))
                } else {
                    val timeline = remember(incidents, event.home.name, event.away.name) { IncidentTimeline.of(incidents, event.home.name, event.away.name) }
                    TimelineBand(timeline)
                    Spacer(Modifier.height(10.dp))
                    IncidentList(incidents, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.testTag("hub-events-message"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
}

/**
 * The 62 dp band (SPORT-FR-93): the axis, the half-time tick, home markers above and away below,
 * a team that is neither on the axis. Drawn once per list; nothing animates.
 */
@Composable
private fun TimelineBand(timeline: IncidentTimeline) {
    val axis = Sohva.palette.divider
    val tick = Sohva.palette.textDim
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(BAND).testTag("hub-timeline").drawBehind {
            val y = size.height / 2
            drawLine(axis, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            val x = size.width * timeline.halfTime
            drawLine(tick, Offset(x, y - 6.dp.toPx()), Offset(x, y + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
        },
    ) {
        val width = maxWidth
        timeline.markers.forEach { marker ->
            val colour = kindColour(marker.incident.kind)
            val x = (width * marker.position - MARKER / 2).coerceIn(0.dp, width - MARKER)
            val dot = @Composable { Box(Modifier.size(9.dp).background(colour, CircleShape)) }
            val time = @Composable {
                Text(marker.incident.timeLabel, maxLines = 1, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black, textAlign = TextAlign.Center), color = colour)
            }
            val column = Modifier.width(MARKER).offset(x = x)
            when (marker.side) {
                TimelineSide.HOME -> Column(column.align(Alignment.TopStart).height(BAND / 2 + 4.5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    time()
                    dot()
                }
                TimelineSide.AWAY -> Column(column.align(Alignment.BottomStart).height(BAND / 2 + 4.5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    dot()
                    time()
                }
                TimelineSide.NEUTRAL -> Box(column.align(Alignment.CenterStart), contentAlignment = Alignment.Center) { dot() }
            }
        }
    }
}

/**
 * The incident list (SPORT-FR-94) takes focus as a whole (a 3 dp ring): Up and Down scroll it by
 * 120 dp while there is room and pass on at either end, so focus can leave it.
 */
@Composable
private fun IncidentList(incidents: List<Incident>, modifier: Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 120.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) Modifier.border(3.dp, Sohva.palette.focus, RoundedCornerShape(10.dp)) else Modifier
    Column(
        modifier.fillMaxWidth().then(ring).padding(4.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val by = when {
                    e.key == Key.DirectionDown && scroll.value < scroll.maxValue -> step
                    e.key == Key.DirectionUp && scroll.value > 0 -> -step
                    else -> return@onPreviewKeyEvent false
                }
                scope.launch { scroll.animateScrollBy(by) }
                true
            }
            .focusable().testTag("hub-incidents")
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // In the provider's order, as beta 23 (chronological); a match has a few dozen at most.
        incidents.forEach { IncidentRow(it) }
    }
}

@Composable
private fun IncidentRow(incident: Incident) {
    Row(
        Modifier.fillMaxWidth().background(Sohva.palette.surfaceSubtle, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            incident.timeLabel, Modifier.width(56.dp), maxLines = 1,
            style = Sohva.typography.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = kindColour(incident.kind),
        )
        Column(Modifier.weight(1f)) {
            Text(mainLine(incident), maxLines = 1, style = Sohva.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Sohva.palette.textPrimary)
            Text(kindLine(incident), maxLines = 1, style = Sohva.typography.caption.copy(fontSize = 12.sp), color = Sohva.palette.textMuted)
        }
    }
}

/** SPORT-FR-94: "actor → related" for a substitution, "scorer · assist X" for a goal, else the actor or the detail. */
@Composable
private fun mainLine(incident: Incident): String {
    val actor = incident.actor
    val related = incident.related
    return when (incident.kind) {
        IncidentKind.SUBSTITUTION -> if (actor != null && related != null) "$actor → $related" else actor ?: kindLabel(incident.kind)
        IncidentKind.GOAL -> when {
            actor != null && related != null -> stringResource(R.string.incident_assist, actor, related)
            else -> actor ?: kindLabel(incident.kind)
        }
        else -> actor ?: incident.detail ?: kindLabel(incident.kind)
    }
}

/** "Kind · detail · team", the parts that exist. */
@Composable
private fun kindLine(incident: Incident): String = listOfNotNull(kindLabel(incident.kind), incident.detail, incident.team).joinToString(" · ")

@Composable
private fun kindLabel(kind: IncidentKind): String = stringResource(
    when (kind) {
        IncidentKind.GOAL -> R.string.incident_goal
        IncidentKind.CARD -> R.string.incident_card
        IncidentKind.SUBSTITUTION -> R.string.incident_substitution
        IncidentKind.VAR -> R.string.incident_var
        IncidentKind.OTHER -> R.string.incident_other
    },
)

/** D§8 incident colours: goal `focus`, card `accent`, substitution and VAR their own, other muted. */
@Composable
private fun kindColour(kind: IncidentKind): Color = when (kind) {
    IncidentKind.GOAL -> Sohva.palette.focus
    IncidentKind.CARD -> Sohva.palette.accent
    IncidentKind.SUBSTITUTION -> ContentColors.substitution
    IncidentKind.VAR -> ContentColors.videoReview
    IncidentKind.OTHER -> Sohva.palette.textMuted
}

private val BAND = 62.dp
private val MARKER = 38.dp

internal val PANEL_TITLE @Composable get() = Sohva.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Black)
