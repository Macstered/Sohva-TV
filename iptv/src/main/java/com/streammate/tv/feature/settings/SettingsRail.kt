package com.streammate.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.streammate.tv.iptv.R
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow

/** The settings rail: which sections exist, their labels and icons, and the column that lists them. */

internal enum class SettingsSection {
    GENERAL,
    SOURCES,
    PLAYBACK,
    REMOTE,
    METADATA,
    SPORT,
    PARENTAL,
    BACKUP,
    ABOUT,
}

@Composable
internal fun SettingsSectionRail(
    modifier: Modifier = Modifier,
    selected: SettingsSection,
    onSelected: (SettingsSection) -> Unit,
) {
    LazyColumn(
        modifier = modifier.testTag("settings-sections"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(SettingsSection.entries) { section ->
            TvListRow(
                label = section.localizedLabel(),
                icon = section.icon,
                onClick = { onSelected(section) },
                modifier = Modifier.height(SETTINGS_RAIL_ROW_HEIGHT),
                selected = selected == section,
                testTag = "settings-section-${section.name.lowercase()}",
            )
        }
    }
}

@Composable
internal fun SettingsSection.localizedLabel(): String = stringResource(
    when (this) {
        SettingsSection.GENERAL -> R.string.settings_section_general
        SettingsSection.SOURCES -> R.string.settings_section_sources
        SettingsSection.PLAYBACK -> R.string.settings_section_playback
        SettingsSection.REMOTE -> R.string.settings_section_remote
        SettingsSection.METADATA -> R.string.settings_section_metadata
        SettingsSection.SPORT -> R.string.settings_section_sport
        SettingsSection.PARENTAL -> R.string.settings_section_parental
        SettingsSection.BACKUP -> R.string.settings_section_backup
        SettingsSection.ABOUT -> R.string.settings_section_about
    },
)

internal val SettingsSection.icon: Int
    get() = when (this) {
        SettingsSection.GENERAL -> TvIcons.Settings
        SettingsSection.SOURCES -> TvIcons.Channels
        SettingsSection.PLAYBACK -> TvIcons.Play
        SettingsSection.REMOTE -> TvIcons.Aspect
        SettingsSection.METADATA -> TvIcons.Info
        SettingsSection.SPORT -> TvIcons.Target
        SettingsSection.PARENTAL -> TvIcons.Lock
        SettingsSection.BACKUP -> TvIcons.Save
        SettingsSection.ABOUT -> TvIcons.Guide
    }
