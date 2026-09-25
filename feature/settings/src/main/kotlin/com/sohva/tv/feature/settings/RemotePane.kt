package com.sohva.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.player.ActionScope
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.player.RemoteSlots
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Settings › Remote buttons (spec 31 §4.8–4.9): the grid of Press and Hold cells, or, while a
 * cell is edited, the grouped action list in its place. Every cell has its own focus requester in
 * one map (REMOTE-FR-47), so focus returns to the edited cell after a choice or Back.
 */
@Composable
internal fun RemotePane(state: SettingsState, model: SettingsModel, start: FocusRequester) {
    var editing by remember { mutableStateOf<Pair<RemoteButton, Gesture>?>(null) }
    var readBack by remember { mutableStateOf<Pair<RemoteButton, Gesture>?>(null) }
    // The edited cell takes focus back when the action list closes (REMOTE-FR-46).
    var focusCell by remember { mutableStateOf<Int?>(null) }
    var focusSerial by remember { mutableIntStateOf(0) }
    val cells = remember { RemoteSlots.MAPPABLE.associate { (b, g) -> RemoteSlots.index(b, g) to FocusRequester() } }
    fun focus(slot: Pair<RemoteButton, Gesture>) {
        focusCell = RemoteSlots.index(slot.first, slot.second)
        focusSerial++
    }

    BackHandler(enabled = editing != null) {
        val slot = editing ?: return@BackHandler
        editing = null
        focus(slot)
    }
    SettingsGroup {
        val slot = editing
        if (slot == null) {
            RemoteGrid(state.remote, cells, start, onFocus = { readBack = it }, onEdit = { editing = it })
            ReadBack(readBack, state.remote)
            ResetRow(onReset = model::resetRemoteMapping)
        } else {
            ActionList(slot, state.remote.action(slot.first, slot.second)) { action ->
                model.setRemoteAction(slot.first, slot.second, action)
                editing = null
                focus(slot)
            }
        }
    }
    LaunchedEffect(focusSerial) {
        focusCell?.let { cells[it]?.requestFocusWhenAttached() }
    }
}

@Composable
private fun ColumnScope.RemoteGrid(
    mapping: RemoteMapping,
    cells: Map<Int, FocusRequester>,
    start: FocusRequester,
    onFocus: (Pair<RemoteButton, Gesture>) -> Unit,
    onEdit: (Pair<RemoteButton, Gesture>) -> Unit,
) {
    Text(stringResource(R.string.settings_section_remote).uppercase(), style = Sohva.typography.overline, color = Sohva.palette.textDim)
    Text(stringResource(R.string.remote_mapping_help), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.width(NAME_WIDTH))
        for (column in listOf(R.string.remote_column_press, R.string.remote_column_hold)) {
            Text(
                stringResource(column),
                Modifier.weight(1f).padding(horizontal = 12.dp),
                style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = Sohva.palette.textMuted,
            )
        }
    }
    for (button in RemoteButton.entries) {
        if (button == RemoteButton.CHANNEL_UP) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.remote_optional_heading),
                style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = Sohva.palette.textMuted,
            )
        }
        ButtonRow(button, mapping, cells, start, onFocus, onEdit)
    }
}

@Composable
private fun ButtonRow(
    button: RemoteButton,
    mapping: RemoteMapping,
    cells: Map<Int, FocusRequester>,
    start: FocusRequester,
    onFocus: (Pair<RemoteButton, Gesture>) -> Unit,
    onEdit: (Pair<RemoteButton, Gesture>) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(button.label()),
            Modifier.width(NAME_WIDTH),
            style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold),
            color = Sohva.palette.textPrimary,
        )
        for (gesture in Gesture.entries) {
            val slot = button to gesture
            if (RemoteSlots.isFixed(button, gesture)) {
                // Back / Press always dismisses, then leaves: plain text, never focusable (REMOTE-FR-41).
                Text(
                    stringResource(R.string.remote_back_fixed),
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                    color = Sohva.palette.textMuted,
                )
                continue
            }
            val index = RemoteSlots.index(button, gesture)
            val requester = cells.getValue(index)
            val first = button == RemoteButton.UP && gesture == Gesture.PRESS
            Row(Modifier.weight(1f)) {
                TvActionButton(
                    stringResource(mapping.action(button, gesture).label()),
                    { onEdit(slot) },
                    Modifier
                        .focusRequester(requester)
                        .then(if (first) Modifier.focusRequester(start) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocus(slot) }
                        .testTag("settings-remote-slot-${button.name.lowercase()}-${gesture.name.lowercase()}"),
                    compact = true,
                )
            }
        }
    }
}

/** "Up, Press: Channel list" for the cell focused last; before any, the Back-hold note (REMOTE-FR-42). */
@Composable
private fun ReadBack(slot: Pair<RemoteButton, Gesture>?, mapping: RemoteMapping) {
    Spacer(Modifier.height(4.dp))
    val text = if (slot == null) {
        stringResource(R.string.remote_back_hold_note)
    } else {
        stringResource(
            R.string.remote_slot_preview,
            stringResource(slot.first.label()),
            stringResource(slot.second.label()),
            stringResource(mapping.action(slot.first, slot.second).label()),
        )
    }
    Text(text, Modifier.testTag("settings-remote-readback"), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted)
    Spacer(Modifier.height(8.dp))
}

/**
 * Reset with a confirmation in place (REMOTE-FR-43). The focused button is about to go away on
 * every step, so the new one is composed next to it for a frame, takes focus, and only then does
 * the old one leave: removing a focused control first hands focus to the section's first cell
 * (AGENTS 5.2).
 */
@Composable
private fun ResetRow(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(true) }
    var showConfirm by remember { mutableStateOf(false) }
    val reset = remember { FocusRequester() }
    val confirm = remember { FocusRequester() }
    fun disarm() {
        armed = false
        showReset = true
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showReset) {
            TvActionButton(
                stringResource(R.string.remote_reset),
                {
                    armed = true
                    showConfirm = true
                },
                Modifier.focusRequester(reset).testTag("settings-remote-reset"),
                compact = true,
            )
        }
        if (showConfirm) {
            Text(stringResource(R.string.remote_reset_confirm), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
            TvActionButton(
                stringResource(R.string.remote_reset_confirm_action),
                {
                    onReset()
                    disarm()
                },
                Modifier.focusRequester(confirm).testTag("settings-remote-reset-confirm"),
                state = SurfaceState(danger = true),
                compact = true,
            )
            TvActionButton(stringResource(R.string.remote_reset_cancel), ::disarm, Modifier.testTag("settings-remote-reset-cancel"), compact = true)
        }
    }
    LaunchedEffect(armed) {
        if (armed && showReset) {
            confirm.requestFocusWhenAttached()
            showReset = false
        } else if (!armed && showConfirm) {
            reset.requestFocusWhenAttached()
            showConfirm = false
        }
    }
}

/** The grouped action list for one slot; the current action is marked and takes focus (REMOTE-FR-45). */
@Composable
private fun ColumnScope.ActionList(slot: Pair<RemoteButton, Gesture>, current: RemoteAction, onChoose: (RemoteAction) -> Unit) {
    val focus = remember { FocusRequester() }
    Text(
        stringResource(R.string.remote_slot_title, stringResource(slot.first.label()), stringResource(slot.second.label())).uppercase(),
        style = Sohva.typography.overline,
        color = Sohva.palette.textDim,
    )
    for (group in ActionGroup.entries) {
        if (group.heading != null) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(group.heading), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Sohva.palette.textMuted)
        }
        for (action in group.actions) {
            val selected = action == current
            Row {
                TvActionButton(
                    action.labelWithScope(),
                    { onChoose(action) },
                    Modifier
                        .then(if (selected) Modifier.focusRequester(focus) else Modifier)
                        .testTag("settings-remote-action-${action.name.lowercase()}"),
                    state = SurfaceState(selected = selected),
                    compact = true,
                )
            }
        }
    }
    LaunchedEffect(slot) { focus.requestFocusWhenAttached() }
}

/** The picker's groups in order (REMOTE-FR-10); Nothing comes last without a heading. */
private enum class ActionGroup(@StringRes val heading: Int?, val actions: List<RemoteAction>) {
    CHANNELS(
        R.string.remote_group_channels,
        listOf(
            RemoteAction.NEXT_CHANNEL, RemoteAction.PREVIOUS_CHANNEL, RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL,
            RemoteAction.OPEN_CHANNEL_BROWSER, RemoteAction.OPEN_GROUP_BROWSER,
        ),
    ),
    INFORMATION(
        R.string.remote_group_information,
        listOf(RemoteAction.PROGRAMME_INFO, RemoteAction.TOGGLE_STATS, RemoteAction.SCORE_TICKER, RemoteAction.GUIDE_AT_CHANNEL, RemoteAction.QUICK_ACTIONS),
    ),
    PLAYBACK(
        R.string.remote_group_playback,
        listOf(RemoteAction.PLAY_PAUSE, RemoteAction.SEEK_BACK, RemoteAction.SEEK_FORWARD, RemoteAction.RESTART, RemoteAction.SHOW_CONTROLS),
    ),
    SOUND_AND_PICTURE(
        R.string.remote_group_sound_and_picture,
        listOf(
            RemoteAction.AUDIO_PICKER, RemoteAction.NEXT_AUDIO_TRACK, RemoteAction.SUBTITLE_PICKER,
            RemoteAction.TOGGLE_SUBTITLES, RemoteAction.CYCLE_PICTURE_SHAPE,
        ),
    ),
    LEAVE(R.string.remote_group_leave, listOf(RemoteAction.LEAVE_PLAYER, RemoteAction.GO_HOME, RemoteAction.GO_GUIDE, RemoteAction.GO_SPORT)),
    NOTHING(null, listOf(RemoteAction.NOTHING)),
}

@Composable
private fun RemoteAction.labelWithScope(): String {
    val name = stringResource(label())
    return when (scope) {
        ActionScope.ANY -> name
        ActionScope.LIVE -> "$name · ${stringResource(R.string.remote_scope_live)}"
        ActionScope.TIMESHIFT -> "$name · ${stringResource(R.string.remote_scope_timeshift)}"
    }
}

@StringRes
private fun RemoteButton.label(): Int = when (this) {
    RemoteButton.UP -> R.string.remote_button_up
    RemoteButton.DOWN -> R.string.remote_button_down
    RemoteButton.LEFT -> R.string.remote_button_left
    RemoteButton.RIGHT -> R.string.remote_button_right
    RemoteButton.OK -> R.string.remote_button_ok
    RemoteButton.BACK -> R.string.remote_button_back
    RemoteButton.CHANNEL_UP -> R.string.remote_button_channel_up
    RemoteButton.CHANNEL_DOWN -> R.string.remote_button_channel_down
    RemoteButton.INFO -> R.string.remote_button_info
    RemoteButton.AUDIO -> R.string.remote_button_audio
    RemoteButton.CAPTIONS -> R.string.remote_button_captions
    RemoteButton.MENU -> R.string.remote_button_menu
}

@StringRes
private fun Gesture.label(): Int = if (this == Gesture.PRESS) R.string.remote_column_press else R.string.remote_column_hold

@StringRes
private fun RemoteAction.label(): Int = when (this) {
    RemoteAction.NEXT_CHANNEL -> R.string.remote_action_next_channel
    RemoteAction.PREVIOUS_CHANNEL -> R.string.remote_action_previous_channel
    RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL -> R.string.remote_action_switch_to_previous_channel
    RemoteAction.OPEN_CHANNEL_BROWSER -> R.string.remote_action_open_channel_browser
    RemoteAction.OPEN_GROUP_BROWSER -> R.string.remote_action_open_group_browser
    RemoteAction.PROGRAMME_INFO -> R.string.remote_action_programme_info
    RemoteAction.TOGGLE_STATS -> R.string.remote_action_toggle_stats
    RemoteAction.SCORE_TICKER -> R.string.remote_action_score_ticker
    RemoteAction.GUIDE_AT_CHANNEL -> R.string.remote_action_guide_at_channel
    RemoteAction.QUICK_ACTIONS -> R.string.remote_action_quick_actions
    RemoteAction.PLAY_PAUSE -> R.string.remote_action_play_pause
    RemoteAction.SEEK_BACK -> R.string.remote_action_seek_back
    RemoteAction.SEEK_FORWARD -> R.string.remote_action_seek_forward
    RemoteAction.RESTART -> R.string.remote_action_restart
    RemoteAction.SHOW_CONTROLS -> R.string.remote_action_show_controls
    RemoteAction.AUDIO_PICKER -> R.string.remote_action_audio_picker
    RemoteAction.NEXT_AUDIO_TRACK -> R.string.remote_action_next_audio_track
    RemoteAction.SUBTITLE_PICKER -> R.string.remote_action_subtitle_picker
    RemoteAction.TOGGLE_SUBTITLES -> R.string.remote_action_toggle_subtitles
    RemoteAction.CYCLE_PICTURE_SHAPE -> R.string.remote_action_cycle_picture_shape
    RemoteAction.LEAVE_PLAYER -> R.string.remote_action_leave_player
    RemoteAction.GO_HOME -> R.string.remote_action_go_home
    RemoteAction.GO_GUIDE -> R.string.remote_action_go_guide
    RemoteAction.GO_SPORT -> R.string.remote_action_go_sport
    RemoteAction.NOTHING -> R.string.remote_action_nothing
}

private val NAME_WIDTH = 110.dp
