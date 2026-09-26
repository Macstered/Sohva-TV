package com.sohva.tv.feature.channels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The editor pane for the selected channel (spec 21 §4.3–4.5, §5): header with the status line,
 * the fields, the guide mapping, the channel lists and the action row. Scrolls to the focused
 * control; composes nothing heavy (the mapping picker only while open).
 */
@Composable
internal fun ChannelEditor(model: ChannelsModel, modifier: Modifier) {
    val selected by model.selected.collectAsStateWithLifecycle()
    Box(
        modifier
            .roundFill(Sohva.palette.surface, Sohva.shapes.medium)
            .border(1.dp, Sohva.palette.outline.copy(alpha = 0.72f), RoundedCornerShape(Sohva.shapes.medium))
            .padding(16.dp),
    ) {
        val row = selected
        if (row == null) {
            Text(stringResource(R.string.channels_select), Modifier.align(Alignment.Center), color = Sohva.palette.textMuted)
            return@Box
        }
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("channels-editor")) {
            EditorHeader(model, row)
            Spacer(Modifier.height(12.dp))
            EditorFieldsBlock(model, row)
            Spacer(Modifier.height(10.dp))
            MappingBlock(model, row)
            Spacer(Modifier.height(10.dp))
            ListsBlock(model)
            Spacer(Modifier.height(12.dp))
            ActionRow(model, row)
        }
    }
}

@Composable
private fun EditorHeader(model: ChannelsModel, row: ChannelRow) {
    val status by model.status.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile(row.channel.name, row.channel.logoUrl, 64.dp, padding = 6.dp, fontSize = 22.sp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.channel.name, style = Sohva.typography.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary, maxLines = 1)
            Text(sources.firstOrNull { it.id == row.channel.sourceId }?.name.orEmpty(), style = Sohva.typography.caption, color = Sohva.palette.focus, maxLines = 1)
            Text(
                stringResource(R.string.channels_original_name, row.channel.providerName),
                style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal),
                color = Sohva.palette.textMuted,
                maxLines = 1,
            )
        }
        status?.let { Text(stringResource(it.text), Modifier.testTag("channels-status"), style = Sohva.typography.caption, color = Sohva.palette.focus) }
    }
}

@Composable
private fun EditorFieldsBlock(model: ChannelsModel, row: ChannelRow) {
    val fields by model.fields.collectAsStateWithLifecycle()
    val text = FieldInput(keyboard = KeyboardType.Text)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvUrlField(fields.name, { v -> model.editFields { it.copy(name = v.take(100)) } }, stringResource(R.string.channels_custom_name), Modifier.testTag("channels-name"), input = text)
        TvUrlField(fields.group, { v -> model.editFields { it.copy(group = v.take(100)) } }, stringResource(R.string.channels_custom_group), Modifier.testTag("channels-group"), input = text)
        TvUrlField(fields.logoUrl, { v -> model.editFields { it.copy(logoUrl = v.take(500)) } }, stringResource(R.string.channels_logo_url), Modifier.testTag("channels-logo"))
        TvUrlField(
            fields.number,
            { v -> model.editFields { it.copy(number = v.filter(Char::isDigit).take(5)) } },
            stringResource(R.string.channels_number),
            Modifier.testTag("channels-number"),
            input = FieldInput(keyboard = KeyboardType.Number),
        )
        val playlistNumber = row.channel.providerNumber?.takeIf { it > 0 }
        Text(
            if (playlistNumber != null) stringResource(R.string.channels_playlist_number, playlistNumber) else stringResource(R.string.channels_playlist_number_none),
            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal),
            color = Sohva.palette.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        LogoFromPhone(model)
    }
}

/** "Programme guide mapping": the current mapping, Change EPG channel (a paged picker) and Automatic (CHAN-FR-24). */
@Composable
private fun MappingBlock(model: ChannelsModel, row: ChannelRow) {
    val fields by model.fields.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Text(stringResource(R.string.channels_epg_mapping), style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
    val current = if (fields.manualEpgId != null) {
        stringResource(R.string.channels_current, fields.manualEpgName ?: fields.manualEpgId.orEmpty())
    } else {
        stringResource(R.string.channels_automatic_epg, row.channel.tvgId ?: stringResource(R.string.channels_no_tvg_id))
    }
    Text(current, Modifier.testTag("channels-mapping"), style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted, maxLines = 1)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TvActionButton(stringResource(R.string.channels_change_epg), { picking = true }, Modifier.testTag("channels-change-epg"))
        TvActionButton(stringResource(R.string.channels_automatic), { model.chooseEpg(null, null) }, Modifier.testTag("channels-automatic"))
    }
    if (picking) EpgPicker(model, fields.manualEpgId, onDismiss = { picking = false })
}

/** "Custom channel lists" (CHAN-FR-51…54): the help without lists, else the list, membership and delete. */
@Composable
private fun ListsBlock(model: ChannelsModel) {
    val lists by model.lists.collectAsStateWithLifecycle()
    val index by model.listIndex.collectAsStateWithLifecycle()
    val memberOf by model.memberOf.collectAsStateWithLifecycle()
    Text(stringResource(R.string.channels_custom_lists), style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
    Spacer(Modifier.height(6.dp))
    val list = lists.getOrNull(index.coerceAtMost(lists.lastIndex))
    if (list == null) {
        Text(stringResource(R.string.channels_create_list_help), style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted)
        return
    }
    val member = list.id in memberOf
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TvActionButton(stringResource(R.string.channels_list, list.name), model::nextList, Modifier.testTag("channels-list-choice"))
        TvActionButton(
            stringResource(if (member) R.string.channels_remove_from_list else R.string.channels_add_to_list),
            model::toggleMembership,
            Modifier.testTag("channels-list-membership"),
        )
        TvActionButton(stringResource(R.string.channels_delete_list), model::deleteList, Modifier.testTag("channels-list-delete"))
    }
}

/**
 * Save, Hide/Show, Lock, ↑, ↓, Reset (CHAN-FR-25…31). Lock needs the parental PIN (spec 04
 * PROF-FR-40): without one it reads "Configure PIN in Settings" and is disabled; with one it locks
 * or unlocks the channel for the active profile. The moves need Playlist order.
 */
@Composable
private fun ActionRow(model: ChannelsModel, row: ChannelRow) {
    val filter by model.filter.collectAsStateWithLifecycle()
    val playlist = filter.sort == ChannelSort.PLAYLIST
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TvActionButton(stringResource(R.string.action_save), model::save, Modifier.testTag("channels-save"), icon = TvIcons.Check)
        TvActionButton(
            stringResource(if (row.hidden) R.string.channels_show_in_guide else R.string.channels_hide_from_guide),
            model::toggleHidden,
            Modifier.testTag("channels-hide"),
        )
        val pin by model.pinConfigured.collectAsStateWithLifecycle()
        val locked by model.locked.collectAsStateWithLifecycle()
        val lockLabel = when {
            !pin -> R.string.channels_configure_pin
            locked -> R.string.channels_remove_pin_lock
            else -> R.string.channels_lock_with_pin
        }
        TvActionButton(stringResource(lockLabel), model::toggleLock, Modifier.testTag("channels-lock"), icon = TvIcons.Lock, state = SurfaceState(enabled = pin))
        TvActionButton("↑", { model.move(up = true) }, Modifier.testTag("channels-up"), state = SurfaceState(enabled = playlist, keepsFocus = true))
        TvActionButton("↓", { model.move(up = false) }, Modifier.testTag("channels-down"), state = SurfaceState(enabled = playlist, keepsFocus = true))
        TvActionButton(stringResource(R.string.action_reset), model::reset, Modifier.testTag("channels-reset"))
    }
}
