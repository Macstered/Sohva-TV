package com.sohva.tv.app.reminder

import com.sohva.tv.app.profile.ChannelStarter
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderSchedule
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.navigation.BackStack
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** What a notification or a bring-forward asks the app to open (spec 01 SHELL-FR-40/41). */
sealed interface OpenRequest {
    data class Channel(val key: String) : OpenRequest

    data class Event(val id: String) : OpenRequest

    companion object {
        fun of(intent: Intent?): OpenRequest? {
            intent ?: return null
            intent.getStringExtra(ReminderCenter.EXTRA_OPEN_CHANNEL)?.takeIf { it.isNotBlank() }?.let { return Channel(it) }
            intent.getStringExtra(ReminderCenter.EXTRA_OPEN_EVENT)?.takeIf { it.isNotBlank() }?.let { return Event(it) }
            return null
        }
    }
}

/**
 * The shell's reminder layer, over every destination once the start route is applied (spec 22
 * REM-FR-21): the due reminder's alert, the one-time overlay prompt with the notification
 * permission before it, and the requests a notification tap brings. Composed only while shown.
 */
@Composable
internal fun ReminderLayer(graph: AppGraph, stack: BackStack<AppRoute>) {
    if (!graph.flags.reminders) return
    val reminder by graph.reminders.current.collectAsState()
    val request by graph.openRequest.collectAsState()
    val prompt by graph.reminders.prompt.collectAsState()
    reminder?.let { r ->
        ReminderAlert(
            r,
            onWatch = {
                graph.reminders.dismiss(r.id)
                openFor(r, stack, graph)
            },
            onLater = { graph.reminders.dismiss(r.id) },
            clock = { graph.clock.wallMillis() },
        )
    }
    if (prompt == PromptStep.OVERLAY) {
        OverlayPrompt(
            onOpen = {
                graph.reminders.promptDone()
                graph.reminders.openOverlaySettings()
            },
            onLater = graph.reminders::promptDone,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { graph.reminders.afterPermission() }
    LaunchedEffect(prompt) {
        if (prompt == PromptStep.PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(request) {
        when (val r = request ?: return@LaunchedEffect) {
            // Playback a notification started is not a recent channel (CHAN-FR-61); Back returns under it.
            is OpenRequest.Channel -> ChannelStarter(graph, stack).play(r.key, forGuide = false)
            // Today with the hub open on the game once the list holds it (SPORT-NAV-04).
            is OpenRequest.Event -> {
                graph.sport.openGame(r.id)
                stack.push(AppRoute.Today)
            }
        }
        graph.openRequest.value = null
    }
}

/** Watch plays the channel over the current screen (REM-FR-22); a match without a channel opens Sohva Sport. */
private fun openFor(reminder: Reminder, stack: BackStack<AppRoute>, graph: AppGraph) {
    val key = reminder.channelKey
    when {
        key != null -> ChannelStarter(graph, stack).play(key, forGuide = false)
        else -> {
            reminder.eventId?.let(graph.sport::openGame)
            stack.push(AppRoute.Today)
        }
    }
}

/** The steps after the first reminder is set (REM-FR-05). */
enum class PromptStep { NONE, PERMISSION, OVERLAY }

/** §5 "Reminder alert": 460 dp, `surface`, radius medium, Watch first. Back dismisses the dialog: Not now (REM-FR-23). */
@Composable
private fun ReminderAlert(reminder: Reminder, onWatch: () -> Unit, onLater: () -> Unit, clock: () -> Long) {
    val watch = remember { FocusRequester() }
    val shownAt = remember(reminder.id) { clock() }
    val soon = ReminderSchedule.startsSoon(reminder, shownAt)
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(460.dp).roundFill(Sohva.palette.surface, Sohva.shapes.medium).padding(18.dp).testTag("reminder-alert"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(if (soon) R.string.reminder_alert_title_soon else R.string.reminder_alert_title_now, reminder.title),
                Modifier.padding(start = 6.dp),
                style = Sohva.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = Sohva.palette.textPrimary,
                maxLines = 2,
            )
            reminder.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.padding(start = 6.dp, bottom = 8.dp), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted, maxLines = 1)
            }
            TvListRow(
                stringResource(if (reminder.channelKey != null) R.string.reminder_alert_watch else R.string.reminder_alert_open_card),
                onWatch,
                Modifier.focusRequester(watch).testTag("reminder-alert-watch"),
                icon = TvIcons.Play,
                layout = ListRowLayout(dense = true),
            )
            TvListRow(stringResource(R.string.reminder_alert_later), onLater, Modifier.testTag("reminder-alert-later"), layout = ListRowLayout(dense = true))
        }
        LaunchedEffect(reminder.id) { watch.requestFocusWhenAttached() }
    }
}

/** §5 "Overlay prompt": 520 dp; "Open TV settings" first (REM-FR-33). */
@Composable
private fun OverlayPrompt(onOpen: () -> Unit, onLater: () -> Unit) {
    val open = remember { FocusRequester() }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(520.dp).roundFill(Sohva.palette.surface, Sohva.shapes.medium).padding(18.dp).testTag("reminder-overlay-prompt"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.reminder_overlay_title),
                Modifier.padding(start = 6.dp),
                style = Sohva.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = Sohva.palette.textPrimary,
                maxLines = 2,
            )
            Text(
                stringResource(R.string.reminder_overlay_body),
                Modifier.padding(start = 6.dp, bottom = 8.dp),
                style = Sohva.typography.label.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
                color = Sohva.palette.textMuted,
            )
            TvListRow(
                stringResource(R.string.reminder_overlay_open),
                onOpen,
                Modifier.focusRequester(open).testTag("reminder-overlay-open"),
                icon = TvIcons.Settings,
                layout = ListRowLayout(dense = true),
            )
            TvListRow(stringResource(R.string.reminder_alert_later), onLater, Modifier.testTag("reminder-overlay-later"), layout = ListRowLayout(dense = true))
        }
        LaunchedEffect(Unit) { open.requestFocusWhenAttached() }
    }
}
