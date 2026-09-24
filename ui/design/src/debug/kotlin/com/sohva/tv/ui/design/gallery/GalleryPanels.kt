package com.sohva.tv.ui.design.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.components.BufferingIndicator
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.InitialsTile
import com.sohva.tv.ui.design.components.LiveBadge
import com.sohva.tv.ui.design.components.LiveDot
import com.sohva.tv.ui.design.components.OptionsSheet
import com.sohva.tv.ui.design.components.PickerRow
import com.sohva.tv.ui.design.components.PlayerProgressTrack
import com.sohva.tv.ui.design.components.ProgressBar
import com.sohva.tv.ui.design.components.SectionMessage
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.WatchedBadge
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

@Composable
internal fun SettingsSection() {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(470.dp)) {
            SettingsOverline("General")
            SettingsGroup {
                SettingsRow("Autoplay next episode", icon = TvIcons.Play, subtitle = "Starts the next episode when one ends") {
                    SettingsSwitch(checked = true, onToggle = {})
                }
                SettingsRow("Show channel numbers", icon = TvIcons.Channels) { SettingsSwitch(checked = false, onToggle = {}) }
                SettingsRow("Locked by the parental PIN", icon = TvIcons.Lock) {
                    SettingsSwitch(checked = false, onToggle = {}, enabled = false)
                }
            }
            SettingsValueRow("Color theme", "Original", {}, icon = TvIcons.Settings, subtitle = "Changes the interface colors")
            SettingsValueRow("Interface size", "Normal (100 %)", {}, icon = TvIcons.Aspect, state = SurfaceState(showFocused = true))
        }
        Column(Modifier.width(370.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("FIELDS")
            TvUrlField("https://provider.example/list.m3u", {}, "Playlist address", icon = TvIcons.Link)
            TvUrlField("", {}, "XMLTV address (optional)", icon = TvIcons.Epg, state = SurfaceState(showFocused = true))
            TvUrlField(
                "hunter2",
                {},
                "Password",
                icon = TvIcons.Key,
                input = FieldInput(KeyboardType.Password, PasswordVisualTransformation(), compact = true),
            )
            Spacer(Modifier.height(6.dp))
            SectionTitle("PICKER")
            DialogCard(border = Sohva.palette.outline) {
                DialogTitle("Interface size")
                PickerRow("Normal (100 %)", {}, state = SurfaceState(selected = true))
                PickerRow("Compact (90 %)", {}, description = "More fits on screen", state = SurfaceState(showFocused = true))
            }
        }
    }
}

@Composable
internal fun StatusSection() {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("PROGRESS")
            ProgressBar({ 0.42f })
            ProgressBar({ 0.7f }, height = 3.dp, track = Sohva.palette.textPrimary.copy(alpha = 0.18f))
            PlayerProgressTrack({ 0.33f })
            SectionTitle("STATUS")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LiveDot()
                LiveBadge("63'")
                WatchedBadge()
                WatchedBadge(size = 22.dp)
            }
            BufferingIndicator()
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("PLACEHOLDERS")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InitialsTile("Northstar One", Modifier.size(64.dp))
                InitialsTile("Ben 10", Modifier.size(44.dp))
                InitialsTile("Yle", Modifier.size(width = 64.dp, height = 96.dp), corner = 12.dp)
            }
            SectionTitle("MESSAGES")
            Box(Modifier.width(360.dp)) {
                SectionMessage("No channels in this group.", detail = "Choose another group from the rail.")
            }
            Text("Plain text in the inherited style (16/24 sp, 0.5 sp tracking)", color = Sohva.palette.textMuted)
        }
    }
}

@Composable
internal fun SheetSection() {
    Box(Modifier.fillMaxSize()) {
        OptionsSheet("Guide options", subtitle = "Live TV and programme information") {
            val wide = Modifier.fillMaxWidth()
            TvActionButton("Source: Northstar Demo Library", {}, wide, state = SurfaceState(showFocused = true))
            TvActionButton("Sort: Playlist", {}, wide)
            TvActionButton("Edit", {}, wide, icon = TvIcons.Check, state = SurfaceState(selected = true))
            TvActionButton("Settings", {}, wide, icon = TvIcons.Settings)
            TvActionButton("Close", {}, wide, icon = TvIcons.Close)
        }
    }
}
