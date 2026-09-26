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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.settings.TimeZones
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.PickerChoice
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import java.time.Instant

/**
 * Settings › General (spec 70 §4.5): interface language, size, colour theme, channel numbers,
 * time zone, startup screen, refresh interval and the reminders row, then the Profiles group.
 * The language row is the section's first control (SET-FR-14).
 */
@Composable
internal fun GeneralPane(state: SettingsState, model: SettingsModel, start: FocusRequester) {
    val general by model.general.state.collectAsStateWithLifecycle()
    // Coming back from the TV settings shows the new answer (REM-FR-34).
    LifecycleResumeEffect(Unit) {
        model.refreshReminderAccess()
        onPauseOrDispose { }
    }
    SettingsGroup {
        ChoiceRow(
            stringResource(R.string.interface_language_title), general.language, SettingsLabels.languages(), model.general::setLanguage,
            "settings-language", Modifier.focusRequester(start), TvIcons.Info, stringResource(R.string.interface_language_help),
        )
        ChoiceRow(
            stringResource(R.string.interface_scale_title), general.scale, SettingsLabels.scales(), model.general::setScale,
            "settings-interface-scale", icon = TvIcons.Aspect, subtitle = stringResource(R.string.interface_scale_help), divider = true,
        )
        ChoiceRow(
            stringResource(R.string.color_theme_title), general.theme, SettingsLabels.themes(), model.general::setTheme,
            "settings-color-theme", icon = TvIcons.StarOutline, subtitle = stringResource(R.string.color_theme_help), divider = true,
        )
        SwitchRow(
            stringResource(R.string.channel_numbers_title), general.channelNumbers, model.general::toggleChannelNumbers,
            "settings-channel-numbers", TvIcons.Channels, stringResource(R.string.channel_numbers_help),
        )
        ZoneRowWithDialog(model.general, general.zone)
        ChoiceRow(
            stringResource(R.string.startup_title), general.startup, SettingsLabels.startups(), model.general::setStartup,
            "settings-startup", icon = TvIcons.Home, divider = true,
        )
        ChoiceRow(
            stringResource(R.string.source_refresh_schedule), state.refreshInterval,
            RefreshInterval.entries.map { PickerChoice(it, it.label(), tag = "settings-refresh-interval-${it.hours}") },
            model::setRefreshInterval, "settings-refresh-interval", icon = TvIcons.Refresh,
            subtitle = stringResource(R.string.source_refresh_schedule_help), divider = true,
        )
        state.remindersCanOpen?.let { allowed ->
            SettingsValueRow(
                title = stringResource(R.string.reminders_open_title),
                value = stringResource(if (allowed) R.string.reminders_open_allowed else R.string.reminders_open_not_allowed),
                onClick = model::openOverlaySettings,
                modifier = Modifier.testTag("settings-reminders-open"),
                icon = TvIcons.Info,
                subtitle = stringResource(R.string.reminders_open_help),
                divider = true,
            )
        }
    }
    StatusGroup(listOfNotNull(state.messages[SettingsSection.GENERAL]?.resolve()), securityNote = false)
    // The General section's last group (spec 04 §5.3).
    ProfilesGroup(model.profiles)
}

/** The Time zone row: the TV's own zone or the chosen one, and its dialog (SET-FR-54, §4.6). */
@Composable
private fun ZoneRowWithDialog(general: GeneralSettings, zone: String?) {
    var open by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val row = remember { FocusRequester() }
    val value = if (zone == null) {
        stringResource(R.string.sports_timezone_device, general.deviceZone().label)
    } else {
        remember(zone) { runCatching { TimeZones.row(zone, Instant.now()).label }.getOrDefault(zone) }
    }
    SettingsValueRow(
        title = stringResource(R.string.sports_timezone_title),
        value = value,
        onClick = { open = true },
        modifier = Modifier.focusRequester(row).testTag("settings-time-zone"),
        icon = TvIcons.Epg,
        subtitle = stringResource(R.string.sports_timezone_help),
        divider = true,
    )
    if (open) {
        val close = {
            open = false
            returnFocus = true
        }
        TimeZoneDialog(general, zone, onChoose = {
            close()
            if (it != zone) general.setZone(it)
        }, onDismiss = close)
    }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            row.requestFocusWhenAttached(6)
            returnFocus = false
        }
    }
}
