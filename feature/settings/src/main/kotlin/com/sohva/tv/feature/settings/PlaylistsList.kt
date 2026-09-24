package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.errorMessage
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached

/**
 * Settings › Playlists, the list page (spec 10 §4.2): one row per source, the add buttons and the
 * status. The first row, or "+ Add M3U source" when there is none, is the section's first control
 * (SET-FR-14). Nothing is drawn until the list has loaded, so focus never lands on a stand-in.
 */
@Composable
internal fun PlaylistsList(state: SettingsState, model: SettingsModel, start: FocusRequester) {
    val sources = state.sources ?: return
    val rows = remember { HashMap<String, FocusRequester>() }
    SettingsOverline(stringResource(R.string.settings_overline_sources))
    // One group, as in beta 23: its hairline under the overline, rows parted by hairlines.
    SettingsGroup {
        if (sources.isEmpty()) {
            SettingsRow(stringResource(R.string.source_none), icon = TvIcons.Info)
        }
        sources.forEachIndexed { index, source -> key(source.id) {
            val requester = remember { FocusRequester() }
            // Registered for the focus return below; idempotent across recompositions.
            rows[source.id] = requester
            // Format names, untranslated as in beta 23.
            val type = if (source.type == SourceType.M3U) "M3U" else "Xtream"
            val reason = source.failure?.let { " · " + errorMessage(it) }.orEmpty()
            SettingsValueRow(
                title = source.name,
                value = stringResource(if (source.enabled) R.string.source_row_on else R.string.source_row_off),
                onClick = { model.openSource(source.id) },
                modifier = Modifier.focusRequester(requester).then(if (index == 0) Modifier.focusRequester(start) else Modifier)
                    .testTag("source-${source.id}"),
                icon = if (source.type == SourceType.M3U) TvIcons.Channels else TvIcons.Link,
                subtitle = type + reason,
                divider = index > 0,
            )
        } }
        FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                stringResource(R.string.source_add_m3u),
                { model.addSource(SourceType.M3U) },
                (if (sources.isEmpty()) Modifier.focusRequester(start) else Modifier).testTag("source-add-m3u"),
                compact = true,
            )
            TvActionButton(stringResource(R.string.source_add_xtream), { model.addSource(SourceType.XTREAM) }, Modifier.testTag("source-add-xtream"), compact = true)
            PhoneButton(state, model)
        }
    }
    val message = state.messages[SettingsSection.SOURCES]?.resolve()
        ?: stringResource(if (sources.isEmpty()) R.string.settings_add_first_source else R.string.settings_sources_loaded)
    StatusGroup(listOf(message), securityNote = true)

    // Back from a source page returns to its row (SRC-FR-06).
    val target = state.focus.target
    LaunchedEffect(state.focus.serial) {
        if (target is FocusTarget.SourceRowOf) rows[target.sourceId]?.requestFocusWhenAttached()
    }
}

/**
 * "Set up from a phone" (spec 11 PHONE-01): opens the page and its dialog; reads "Close the phone
 * page" while the page runs (PHONE-FR-01). Focus comes back here when the dialog goes, whether the
 * viewer closed it or the 15-minute limit did (PHONE-FR-02).
 */
@Composable
private fun PhoneButton(state: SettingsState, model: SettingsModel) {
    val button = remember { FocusRequester() }
    val open = state.phone != PhoneSetupState.Closed
    TvActionButton(
        stringResource(if (state.phone is PhoneSetupState.Open) R.string.phone_setup_stop else R.string.phone_setup_start),
        { if (open) model.closePhoneSetup() else model.openPhoneSetup() },
        Modifier.focusRequester(button).testTag("source-add-phone"),
        icon = TvIcons.Link,
        compact = true,
    )
    if (open) PhoneSetupDialog(state.phone, state.qr, model::closePhoneSetup)
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open && wasOpen) button.requestFocusWhenAttached()
        wasOpen = open
    }
}
