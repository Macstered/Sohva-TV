package com.sohva.tv.feature.sport.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.reminder.ReminderIds
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.feature.sport.today.Crest
import com.sohva.tv.feature.sport.today.StatusBadge
import com.sohva.tv.feature.sport.today.TodayModel
import com.sohva.tv.feature.sport.today.accent
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The match hub (spec 60 §4.6, D§8): an overlay in Today's window, not a dialog — a remote has no
 * "click outside". D-pad moves stay inside it; Back and Close go through the screen, which places
 * focus on the list before the hub hides. Enter and exit: fade and a slide by a fifth of the width.
 * [covering] turns true once the hub is fully in, so the screen stops drawing the list behind it
 * (spec 60 §9 "Drawing").
 */
@Composable
internal fun MatchHubOverlay(model: TodayModel, labels: TimeLabels, watchable: (String) -> Int, covering: MutableState<Boolean>, close: () -> Unit) {
    val event by model.hubEvent.collectAsStateWithLifecycle()
    // The last game shown stays drawn while the hub slides out.
    val kept = remember { arrayOfNulls<SportEvent>(1) }
    event?.let { kept[0] = it }
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = event != null
    val covers = shown.isIdle && shown.currentState
    SideEffect { covering.value = covers }
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn() + slideInHorizontally { it / 5 },
        exit = fadeOut() + slideOutHorizontally { it / 5 },
    ) {
        kept[0]?.let { Hub(it, model, labels, watchable(it.id), close) }
    }
}

@Composable
private fun Hub(event: SportEvent, model: TodayModel, labels: TimeLabels, watchable: Int, close: () -> Unit) {
    val closeButton = remember { FocusRequester() }
    // One opaque panel with flat fills: the old radial gradient over a 94 % scrim cost the low-end GPU (D§10).
    val panel = Sohva.palette.surfaceSubtle.compositeOver(Sohva.palette.background)
    Box(Modifier.fillMaxSize().background(Sohva.palette.background.copy(alpha = 0.94f)).testTag("match-hub"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).background(panel, RoundedCornerShape(20.dp)).padding(22.dp)
                .focusProperties { onExit = { if (requestedFocusDirection in KEY_MOVES) cancelFocusChange() } }.focusGroup(),
        ) {
            Header(event, model, labels, watchable, closeButton, close)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MatchEventsPanel(event, model, Modifier.weight(1.12f))
                StreamsPanel(watchable, Modifier.weight(0.88f))
            }
        }
    }
    LaunchedEffect(event.id) { model.loadMatchEvents(event) }
    // SPORT-FR-79: the first stream's lead control, else Close, retried while the hub animates in.
    LaunchedEffect(event.id) { closeButton.requestFocusWhenAttached(FOCUS_ATTEMPTS) }
}

/** D§8 header: competition column 205 dp, the two teams around the score, then Remind me and Close (150 dp). */
@Composable
private fun Header(event: SportEvent, model: TodayModel, labels: TimeLabels, watchable: Int, closeButton: FocusRequester, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(205.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(58.dp)) { event.competitionLogo?.let { Crest(it, 58.dp, Modifier.size(58.dp)) } }
            Text(
                event.competition.uppercase(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Black), color = Sohva.palette.focus,
            )
            StatusBadge(event)
            Text(
                stringResource(
                    R.string.today_start_and_streams, labels.guideTime(event.startMillis),
                    pluralStringResource(R.plurals.today_available_streams, watchable, watchable),
                ),
                Modifier.testTag("hub-start"), style = Sohva.typography.caption.copy(fontSize = 12.sp), color = Sohva.palette.textMuted,
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            HubTeam(event.home, event, Modifier.weight(1f))
            Score(event, Modifier.padding(horizontal = 18.dp))
            HubTeam(event.away, event, Modifier.weight(1f))
        }
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemindButton(event, model)
            TvActionButton(
                stringResource(R.string.action_close), close, Modifier.fillMaxWidth().focusRequester(closeButton).testTag("hub-close"),
                TvIcons.Close, compact = true,
            )
        }
    }
}

/** The score, 36 sp Black, "–" before one exists; `focus` while live, as on the card (the two differed, D§10). */
@Composable
private fun Score(event: SportEvent, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            event.score ?: "–", Modifier.testTag("hub-score"), maxLines = 1,
            style = Sohva.typography.headline.copy(fontSize = 36.sp, fontWeight = FontWeight.Black),
            color = if (event.status == EventStatus.LIVE) Sohva.palette.focus else Sohva.palette.textPrimary,
        )
        event.scoreDetail?.let { Text(it, maxLines = 1, style = Sohva.typography.label.copy(fontSize = 14.sp), color = Sohva.palette.textMuted) }
    }
}

/** A 56 dp circle with a thin outline, the initials in the sport's accent under the 48 dp crest, and the name. */
@Composable
private fun HubTeam(side: Side, event: SportEvent, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).border(1.dp, Sohva.palette.outline.copy(alpha = 0.55f), CircleShape).background(Sohva.palette.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(side.initials, style = Sohva.typography.label.copy(fontWeight = FontWeight.Black), color = accent(event.sport))
            side.logo?.let { Crest(it, 48.dp, Modifier.size(48.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            side.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = Sohva.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
            color = Sohva.palette.textPrimary,
        )
    }
}

/** SPORT-FR-95: only for a scheduled game still ahead; drawn selected once set; OK toggles. */
@Composable
private fun RemindButton(event: SportEvent, model: TodayModel) {
    val now by model.now.collectAsStateWithLifecycle()
    if (event.status != EventStatus.SCHEDULED || event.startMillis <= now) return
    val ids by model.reminders.collectAsStateWithLifecycle()
    val set = ReminderIds.event(event.id) in ids
    TvActionButton(
        stringResource(if (set) R.string.match_reminder_set else R.string.match_remind), { model.toggleReminder(event) },
        Modifier.fillMaxWidth().testTag("hub-remind"), if (set) TvIcons.Check else TvIcons.Target,
        state = SurfaceState(selected = set, keepsFocus = true), compact = true,
    )
}

/** D-pad moves stay inside the hub; a placement asked for in code (on close) may leave it. */
private val KEY_MOVES = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down, FocusDirection.Next, FocusDirection.Previous)

/** SPORT-FR-79: 90 frames while the hub animates in. */
private const val FOCUS_ATTEMPTS = 90
