package com.streammate.tv.feature.settings

import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import com.streammate.tv.app.RemoteAction
import com.streammate.tv.app.RemoteActionGroup
import com.streammate.tv.app.RemoteActionScope
import com.streammate.tv.app.RemoteButton
import com.streammate.tv.app.RemoteGesture
import com.streammate.tv.app.RemoteMappings
import com.streammate.tv.app.RemoteSlot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.iptv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton

/** The Remote buttons page: the press/hold grid and its action picker. */

/**
 * The remote grid: one row per button, a press cell and a hold cell. Selecting
 * a cell swaps the grid for the action list; Back, or a choice, brings the
 * grid back with focus on the cell that was edited.
 */
@Composable
internal fun RemoteMappingSection(
    mappings: RemoteMappings,
    firstCellFocusRequester: FocusRequester,
    onAssign: (RemoteSlot, RemoteAction) -> Unit,
    onReset: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    var editingSlot by remember { mutableStateOf<RemoteSlot?>(null) }
    var focusedSlot by remember { mutableStateOf<RemoteSlot?>(null) }
    var returnFocusTo by remember { mutableStateOf<RemoteSlot?>(null) }
    var resetArmed by remember { mutableStateOf(false) }
    val cellFocusRequesters = remember { RemoteSlot.MAPPABLE.associateWith { FocusRequester() } }
    val firstSlot = RemoteSlot.MAPPABLE.first()

    BackHandler(enabled = editingSlot != null) {
        returnFocusTo = editingSlot
        editingSlot = null
    }
    LaunchedEffect(editingSlot, returnFocusTo) {
        if (editingSlot == null) {
            returnFocusTo?.let { slot -> cellFocusRequesters.getValue(slot).requestFocus() }
            returnFocusTo = null
        }
    }

    val slot = editingSlot
    if (slot != null) {
        RemoteActionPicker(
            slot = slot,
            current = mappings[slot],
            onPick = { action ->
                onAssign(slot, action)
                returnFocusTo = slot
                editingSlot = null
            },
        )
        return
    }

    SettingsGroup {
        SettingsGroupHeading(stringResource(R.string.settings_section_remote))
        Text(
            text = stringResource(R.string.remote_mapping_help),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        RemoteGridHeader()
        RemoteButton.entries.forEach { button ->
            if (button == RemoteButton.entries.first { it.optional }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.remote_optional_heading),
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = button.localizedLabel(),
                    modifier = Modifier.width(REMOTE_BUTTON_COLUMN_WIDTH),
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                RemoteGesture.entries.forEach { gesture ->
                    val cell = RemoteSlot(button, gesture)
                    if (cell.fixed) {
                        Text(
                            text = stringResource(R.string.remote_back_fixed),
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            color = palette.textMuted,
                        )
                    } else {
                        TvActionButton(
                            label = mappings[cell].localizedLabel(),
                            onClick = { editingSlot = cell },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { state -> if (state.isFocused) focusedSlot = cell },
                            focusRequester = if (cell == firstSlot) {
                                firstCellFocusRequester
                            } else {
                                cellFocusRequesters.getValue(cell)
                            },
                            compact = true,
                            testTag = "settings-remote-slot-" +
                                "${button.name.lowercase()}-${gesture.name.lowercase()}",
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = focusedSlot?.let { focused ->
                stringResource(
                    R.string.remote_slot_preview,
                    focused.button.localizedLabel(),
                    focused.gesture.localizedLabel(),
                    mappings[focused].localizedLabel(),
                )
            } ?: stringResource(R.string.remote_back_hold_note),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (resetArmed) {
                Text(
                    text = stringResource(R.string.remote_reset_confirm),
                    color = palette.textPrimary,
                )
                TvActionButton(
                    label = stringResource(R.string.remote_reset_confirm_action),
                    onClick = {
                        onReset()
                        resetArmed = false
                    },
                    compact = true,
                    danger = true,
                    testTag = "settings-remote-reset-confirm",
                )
                TvActionButton(
                    label = stringResource(R.string.remote_reset_cancel),
                    onClick = { resetArmed = false },
                    compact = true,
                    testTag = "settings-remote-reset-cancel",
                )
            } else {
                TvActionButton(
                    label = stringResource(R.string.remote_reset),
                    onClick = { resetArmed = true },
                    compact = true,
                    testTag = "settings-remote-reset",
                )
            }
        }
    }
}

@Composable
internal fun RemoteGridHeader() {
    val palette = StreamMateThemeTokens.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.width(REMOTE_BUTTON_COLUMN_WIDTH))
        RemoteGesture.entries.forEach { gesture ->
            Text(
                text = gesture.localizedLabel(),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = palette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun RemoteActionPicker(
    slot: RemoteSlot,
    current: RemoteAction,
    onPick: (RemoteAction) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val currentFocusRequester = remember { FocusRequester() }
    LaunchedEffect(slot) { currentFocusRequester.requestFocus() }
    SettingsGroup {
        SettingsGroupHeading(
            stringResource(R.string.remote_slot_title, slot.button.localizedLabel(), slot.gesture.localizedLabel()),
        )
        RemoteActionGroup.entries.forEach { group ->
            val actions = RemoteAction.entries.filter { it.group == group }
            if (group != RemoteActionGroup.NOTHING) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = group.localizedLabel(),
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            actions.forEach { action ->
                val scopeHint = when (action.scope) {
                    RemoteActionScope.LIVE -> stringResource(R.string.remote_scope_live)
                    RemoteActionScope.TIMESHIFT -> stringResource(R.string.remote_scope_timeshift)
                    RemoteActionScope.ANY -> null
                }
                TvActionButton(
                    label = scopeHint?.let { hint -> "${action.localizedLabel()} · $hint" }
                        ?: action.localizedLabel(),
                    onClick = { onPick(action) },
                    selected = action == current,
                    focusRequester = if (action == current) currentFocusRequester else null,
                    compact = true,
                    testTag = "settings-remote-action-${action.name.lowercase()}",
                )
            }
        }
    }
}

internal val REMOTE_BUTTON_COLUMN_WIDTH = 110.dp

@Composable
internal fun RemoteButton.localizedLabel(): String = stringResource(
    when (this) {
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
    },
)

@Composable
internal fun RemoteGesture.localizedLabel(): String = stringResource(
    when (this) {
        RemoteGesture.PRESS -> R.string.remote_column_press
        RemoteGesture.HOLD -> R.string.remote_column_hold
    },
)

@Composable
internal fun RemoteActionGroup.localizedLabel(): String = stringResource(
    when (this) {
        RemoteActionGroup.CHANNELS -> R.string.remote_group_channels
        RemoteActionGroup.INFORMATION -> R.string.remote_group_information
        RemoteActionGroup.PLAYBACK -> R.string.remote_group_playback
        RemoteActionGroup.SOUND_AND_PICTURE -> R.string.remote_group_sound_and_picture
        RemoteActionGroup.LEAVE -> R.string.remote_group_leave
        RemoteActionGroup.NOTHING -> R.string.remote_group_nothing
    },
)

@Composable
internal fun RemoteAction.localizedLabel(): String = stringResource(
    when (this) {
        RemoteAction.NOTHING -> R.string.remote_action_nothing
        RemoteAction.NEXT_CHANNEL -> R.string.remote_action_next_channel
        RemoteAction.PREVIOUS_CHANNEL -> R.string.remote_action_previous_channel
        RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL -> R.string.remote_action_switch_to_previous_channel
        RemoteAction.OPEN_CHANNEL_BROWSER -> R.string.remote_action_open_channel_browser
        RemoteAction.OPEN_GROUP_BROWSER -> R.string.remote_action_open_group_browser
        RemoteAction.PROGRAMME_INFO -> R.string.remote_action_programme_info
        RemoteAction.TOGGLE_STATS -> R.string.remote_action_toggle_stats
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
    },
)
