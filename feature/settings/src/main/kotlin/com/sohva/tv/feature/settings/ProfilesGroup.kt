package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Which dialog of the Profiles group is open; the row that opened it gets focus back when it closes. */
internal enum class ProfileDialog { ACTIVE, CONTENT, LIVE, MOVIES, SERIES, REMOVE }

/**
 * Settings › General › Profiles (spec 04 §5.3), in this order: Who is watching; with more than one
 * profile, Ask at start, What this profile may see and the three group rows; the PIN note when a
 * profile is restricted; the add row; Remove a profile.
 */
@Composable
internal fun ProfilesGroup(profiles: ProfileSettings) {
    val household by profiles.household.collectAsStateWithLifecycle()
    val state by profiles.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<ProfileDialog?>(null) }
    var returnTo by remember { mutableStateOf<ProfileDialog?>(null) }
    val rows = remember { ProfileDialog.entries.associateWith { FocusRequester() } }
    val defaultName = stringResource(R.string.profile_default_name)
    val edited = household.shown.firstOrNull { it.id == profiles.edited(household) } ?: household.active
    SettingsOverline(stringResource(R.string.profiles_title))
    SettingsGroup {
        SettingsValueRow(
            title = stringResource(R.string.profile_active_title),
            value = household.active.displayName(defaultName),
            onClick = { open = ProfileDialog.ACTIVE },
            modifier = Modifier.focusRequester(rows.getValue(ProfileDialog.ACTIVE)).testTag("settings-profile-active"),
            icon = TvIcons.Star,
            subtitle = stringResource(R.string.profile_active_help),
        )
        if (household.several) {
            SettingsSwitchRow(
                stringResource(R.string.profile_ask_at_start), household.askAtStart, { profiles.setAskAtStart(!household.askAtStart) },
                Modifier.testTag("settings-profile-ask"), subtitle = stringResource(R.string.profile_ask_at_start_help),
            )
            SettingsValueRow(
                title = stringResource(R.string.profile_content_title),
                value = edited.displayName(defaultName),
                onClick = { open = ProfileDialog.CONTENT },
                modifier = Modifier.focusRequester(rows.getValue(ProfileDialog.CONTENT)).testTag("settings-profile-content"),
                icon = TvIcons.Lock,
                subtitle = stringResource(R.string.profile_content_help),
            )
            for ((room, dialog, title) in GROUP_ROWS) {
                val keys = state.restriction.of(room)
                SettingsValueRow(
                    title = stringResource(title),
                    value = if (keys.isEmpty()) stringResource(R.string.profile_content_all) else pluralStringResource(R.plurals.profile_content_count, keys.size, keys.size),
                    onClick = { open = dialog },
                    modifier = Modifier.focusRequester(rows.getValue(dialog)).testTag("settings-profile-groups-${room.wire.lowercase()}"),
                )
            }
        }
        if (state.anyRestricted) PinNote(household)
        AddRow(profiles, state, household)
        if (household.several) {
            SettingsValueRow(
                title = stringResource(R.string.profile_remove),
                value = "",
                onClick = { open = ProfileDialog.REMOVE },
                modifier = Modifier.focusRequester(rows.getValue(ProfileDialog.REMOVE)).testTag("settings-profile-remove"),
                icon = TvIcons.Delete,
                subtitle = stringResource(R.string.profile_remove_help),
            )
        }
    }
    open?.let { dialog ->
        ProfileDialogs(dialog, profiles, household, edited.displayName(defaultName)) {
            open = null
            returnTo = dialog
        }
    }
    LaunchedEffect(returnTo) {
        val dialog = returnTo ?: return@LaunchedEffect
        // A removed row (the last added profile gone) is not there to take focus back.
        rows[dialog]?.requestFocusWhenAttached(RETURN_ATTEMPTS)
        returnTo = null
    }
}

/** Whether the PIN guards the restriction (PROF-FR-21): muted with a PIN, `danger` without. */
@Composable
private fun PinNote(household: Household) {
    val (text, colour) = if (household.pinConfigured) {
        R.string.profile_content_pin_ready to Sohva.palette.textMuted
    } else {
        R.string.profile_content_pin_missing to Sohva.palette.danger
    }
    Text(
        stringResource(text),
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).testTag("settings-profile-content-pin"),
        style = Sohva.typography.label.copy(fontSize = 13.sp),
        color = colour,
    )
}

/** A name (edit on OK, cut to 24) and Add profile, enabled with a name and room for one more (PROF-FR-03). */
@Composable
private fun AddRow(profiles: ProfileSettings, state: ProfileSettingsState, household: Household) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvUrlField(
            value = state.name,
            onValueChange = profiles::typeName,
            label = stringResource(R.string.profile_name_hint),
            modifier = Modifier.weight(1f).testTag("settings-profile-name"),
            input = NAME_INPUT,
        )
        TvActionButton(
            stringResource(R.string.profile_add),
            profiles::add,
            Modifier.testTag("settings-profile-add"),
            icon = TvIcons.Check,
            state = SurfaceState(enabled = profiles.canAdd(state, household) && !state.busy, keepsFocus = state.busy),
            compact = true,
        )
    }
}

private val NAME_INPUT = FieldInput(keyboard = KeyboardType.Text, compact = true)
private const val RETURN_ATTEMPTS = 6

internal val GROUP_ROWS: List<Triple<OrgRoom, ProfileDialog, Int>> = listOf(
    Triple(OrgRoom.LIVE, ProfileDialog.LIVE, R.string.profile_content_live),
    Triple(OrgRoom.MOVIES, ProfileDialog.MOVIES, R.string.profile_content_movies),
    Triple(OrgRoom.SERIES, ProfileDialog.SERIES, R.string.profile_content_series),
)
