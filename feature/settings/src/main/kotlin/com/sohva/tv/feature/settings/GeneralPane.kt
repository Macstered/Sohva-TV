package com.sohva.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.PickerChoice
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.SinglePickerDialog
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached

/**
 * Settings › General. M1 brings the playlist and guide refresh interval (spec 10 SRC-FR-35); the
 * other rows arrive with their milestones, above it in beta 23's order.
 */
@Composable
internal fun GeneralPane(state: SettingsState, model: SettingsModel, start: FocusRequester) {
    var picking by remember { mutableStateOf(false) }
    // Set only when the picker closes, so focus returns to the row (SET-FR-08) and never lands on it unasked.
    var returnFocus by remember { mutableStateOf(false) }
    val row = remember { FocusRequester() }
    SettingsGroup {
        SettingsValueRow(
            title = stringResource(R.string.source_refresh_schedule),
            value = state.refreshInterval.label(),
            onClick = { picking = true },
            modifier = Modifier.focusRequester(row).focusRequester(start).testTag("settings-refresh-interval"),
            icon = TvIcons.Refresh,
            subtitle = stringResource(R.string.source_refresh_schedule_help),
        )
    }
    StatusGroup(listOfNotNull(state.messages[SettingsSection.GENERAL]?.resolve()), securityNote = false)
    if (picking) {
        val choices = RefreshInterval.entries.map { PickerChoice(it, it.label(), tag = "settings-refresh-interval-${it.hours}") }
        val close = {
            picking = false
            returnFocus = true
        }
        SinglePickerDialog(
            title = stringResource(R.string.source_refresh_schedule),
            choices = choices,
            current = state.refreshInterval,
            onChoose = {
                model.setRefreshInterval(it)
                close()
            },
            onDismiss = close,
        )
    }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            row.requestFocusWhenAttached()
            returnFocus = false
        }
    }
}
