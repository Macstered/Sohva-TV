package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.data.profile.GroupChoice
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.profile.Profile
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.MultiPickerDialog
import com.sohva.tv.ui.design.components.PickerChoice
import com.sohva.tv.ui.design.components.SinglePickerDialog
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The Profiles group's pickers (spec 04 PROF-FR-15, -21, -22, -06); [onClose] gives focus back to the row. */
@Composable
internal fun ProfileDialogs(dialog: ProfileDialog, profiles: ProfileSettings, household: Household, editedName: String, onClose: () -> Unit) {
    val defaultName = stringResource(R.string.profile_default_name)
    fun choices(list: List<Profile>, prefix: String) = list.map { PickerChoice(it.id, it.displayName(defaultName), tag = "$prefix-${it.id}") }
    when (dialog) {
        ProfileDialog.ACTIVE -> SinglePickerDialog(
            title = stringResource(R.string.profile_active_title),
            choices = choices(household.shown, "settings-profile"),
            current = household.active.id,
            onChoose = {
                onClose()
                profiles.switchTo(it)
            },
            onDismiss = onClose,
        )
        ProfileDialog.CONTENT -> SinglePickerDialog(
            title = stringResource(R.string.profile_content_title),
            choices = choices(household.shown, "settings-profile-content"),
            current = profiles.edited(household),
            onChoose = {
                profiles.edit(it)
                onClose()
            },
            onDismiss = onClose,
        )
        ProfileDialog.LIVE, ProfileDialog.MOVIES, ProfileDialog.SERIES -> {
            val room = GROUP_ROWS.first { it.second == dialog }.first
            GroupPicker(room, profiles, editedName, onClose)
        }
        ProfileDialog.REMOVE -> RemovePicker(profiles, household.shown.filterNot { it.isDefault }, defaultName, onClose)
    }
}

/** "Groups for <name>" (PROF-FR-22): one row per group across sources, the sources named; a toggle saves at once. */
@Composable
private fun GroupPicker(room: OrgRoom, profiles: ProfileSettings, editedName: String, onClose: () -> Unit) {
    val state by profiles.state.collectAsStateWithLifecycle()
    val loaded by produceState<List<GroupChoice>?>(null, room) { value = profiles.choices(room) }
    val options = loaded ?: return
    val ungrouped = stringResource(R.string.profile_content_ungrouped)
    MultiPickerDialog(
        title = stringResource(R.string.profile_content_picker_title, editedName),
        choices = options.map { PickerChoice(it.key, it.name ?: ungrouped, it.sources.joinToString(", "), tag = "settings-profile-group-${it.key}") },
        chosen = state.restriction.of(room),
        onToggle = { profiles.toggle(room, it) },
        onDismiss = onClose,
        empty = stringResource(R.string.profile_content_none_yet),
        done = stringResource(R.string.category_edit_done),
        tag = "settings-profile-groups",
    )
}

/**
 * Remove a profile (PROF-FR-06): the added profiles, never the first viewer; the choice asks once
 * before everything it kept goes (decision "Spec 04 open questions", Q6).
 */
@Composable
private fun RemovePicker(profiles: ProfileSettings, added: List<Profile>, defaultName: String, onClose: () -> Unit) {
    var confirming by remember { mutableStateOf<Profile?>(null) }
    val target = confirming
    if (target == null) {
        SinglePickerDialog(
            title = stringResource(R.string.profile_remove),
            choices = added.map { PickerChoice(it.id, it.displayName(defaultName), tag = "settings-profile-remove-${it.id}") },
            current = "",
            onChoose = { id -> confirming = added.first { it.id == id } },
            onDismiss = onClose,
        )
        return
    }
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(560.dp).testTag("settings-profile-remove-confirm"), border = Sohva.palette.outline) {
                DialogTitle(stringResource(R.string.addon_ui_remove_named, target.displayName(defaultName)))
                Text(stringResource(R.string.profile_remove_help), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(stringResource(R.string.action_cancel), onClose, Modifier.focusRequester(cancel).testTag("settings-profile-remove-cancel"))
                    TvActionButton(
                        stringResource(R.string.action_confirm),
                        {
                            profiles.remove(target.id)
                            onClose()
                        },
                        Modifier.testTag("settings-profile-remove-yes"),
                        state = SurfaceState(danger = true),
                    )
                }
            }
        }
        // Cancel first: a removal is never one OK away.
        LaunchedEffect(Unit) { cancel.requestFocusWhenAttached() }
    }
}
