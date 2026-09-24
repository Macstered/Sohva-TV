package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.source.EpgOffsetLabel
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Remove source (SRC-FR-16 item 4) with the confirmation plan/09 adds: Delete turns the row into
 * a question with Delete (danger) and Cancel; focus waits on Cancel, and returns to Delete when
 * the question is withdrawn.
 */
@Composable
internal fun DeleteRow(page: SourceDraft, state: SettingsState, model: SettingsModel) {
    val delete = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val asking = state.confirmingDelete
    val subtitle = if (asking) stringResource(R.string.source_delete_confirm, page.name) else stringResource(R.string.source_delete_help)
    SettingsRow(stringResource(R.string.source_delete_title), subtitle = subtitle) {
        if (asking) {
            TvActionButton(
                stringResource(R.string.action_delete),
                model::confirmDelete,
                Modifier.testTag("settings-source-delete-confirm"),
                icon = TvIcons.Delete,
                state = SurfaceState(danger = true, enabled = !state.busy, keepsFocus = true),
                compact = true,
            )
            Spacer(Modifier.width(12.dp))
            TvActionButton(stringResource(R.string.action_cancel), model::cancelDelete, Modifier.focusRequester(cancel).testTag("settings-source-delete-cancel"), compact = true)
        } else {
            TvActionButton(
                stringResource(R.string.action_delete),
                model::askDelete,
                Modifier.focusRequester(delete).testTag("settings-source-delete"),
                icon = TvIcons.Delete,
                state = SurfaceState(danger = true, enabled = !state.busy, keepsFocus = true),
                compact = true,
            )
        }
    }
    var wasAsking by remember { mutableStateOf(false) }
    LaunchedEffect(asking) {
        if (asking) cancel.requestFocusWhenAttached() else if (wasAsking) delete.requestFocusWhenAttached()
        wasAsking = asking
    }
}

/** Content to import: three buttons, the current one selected (SRC-FR-16 item 5). */
@Composable
internal fun ScopeRow(page: SourceDraft, model: SettingsModel) {
    SettingsRow(stringResource(R.string.source_import_scope), subtitle = stringResource(R.string.source_import_scope_help)) {
        listOf(
            Triple(ImportScope.LIVE_TV, R.string.source_import_live, "live_tv"),
            Triple(ImportScope.VOD, R.string.source_import_vod, "vod"),
            Triple(ImportScope.BOTH, R.string.source_import_both, "both"),
        ).forEachIndexed { index, (scope, label, tag) ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            TvActionButton(
                stringResource(label),
                { model.edit { it.copy(scope = scope) } },
                Modifier.testTag("settings-import-$tag"),
                state = SurfaceState(selected = page.scope == scope),
                compact = true,
            )
        }
    }
}

/**
 * EPG time correction, a bare row (SRC-FR-16 item 6): −30 min / value / +30 min within ±12 h.
 * The step labels are untranslated, as in beta 23.
 */
@Composable
internal fun EpgOffsetRow(page: SourceDraft, model: SettingsModel) {
    val range = SourceRules.EPG_OFFSETS
    val step = SourceRules.EPG_OFFSET_STEP
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.source_epg_offset), style = Sohva.typography.label.copy(fontSize = 13.sp), color = Sohva.palette.textMuted)
        Spacer(Modifier.width(14.dp))
        TvActionButton(
            "−30 min",
            { model.edit { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes - step).coerceIn(range)) } },
            Modifier.testTag("settings-epg-offset-down"),
            state = SurfaceState(enabled = page.epgOffsetMinutes > range.first, keepsFocus = true),
            compact = true,
        )
        Box(Modifier.width(74.dp), contentAlignment = Alignment.Center) {
            Text(
                EpgOffsetLabel.of(page.epgOffsetMinutes),
                Modifier.testTag("settings-epg-offset-value"),
                style = Sohva.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = Sohva.palette.textPrimary,
                maxLines = 1,
            )
        }
        TvActionButton(
            "+30 min",
            { model.edit { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes + step).coerceIn(range)) } },
            Modifier.testTag("settings-epg-offset-up"),
            state = SurfaceState(enabled = page.epgOffsetMinutes < range.last, keepsFocus = true),
            compact = true,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.source_epg_offset_help),
            Modifier.weight(1f),
            style = Sohva.typography.caption.copy(fontSize = 12.sp),
            color = Sohva.palette.textMuted,
        )
    }
}

/** The action buttons of SRC-FR-17, at most three to a row; disabled while an action runs (SRC-FR-19) but still focusable, so OK on one keeps focus there. */
@Composable
internal fun SourceActions(page: SourceDraft, busy: Boolean, model: SettingsModel) {
    val state = SurfaceState(enabled = !busy, keepsFocus = true)
    val live = page.scope.includesLive
    FlowRow(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
    ) {
        Action(R.string.source_save_securely, TvIcons.Save, "settings-save", state, model::save)
        if (page.type == SourceType.M3U) {
            Action(R.string.source_test_address, TvIcons.Check, "settings-test-m3u", state, model::test)
        } else {
            Action(R.string.source_test_connection, TvIcons.Check, "settings-test-xtream", state, model::test)
        }
        Action(R.string.source_sync_everything, TvIcons.Refresh, "settings-sync-everything", state, model::syncEverything)
        if (live) {
            val label = if (page.type == SourceType.M3U) R.string.source_refresh_playlist else R.string.source_refresh_channels
            Action(label, TvIcons.Refresh, "settings-refresh-playlist", state) { model.refresh(RefreshKind.PLAYLIST) }
        }
        if (page.scope.includesVod) {
            Action(R.string.source_refresh_catalogue, TvIcons.Play, "settings-refresh-catalogue", state) { model.refresh(RefreshKind.CATALOGUE) }
        }
        if (page.canRefreshGuide) {
            Action(R.string.source_refresh_epg, TvIcons.Epg, "settings-refresh-epg", state) { model.refresh(RefreshKind.EPG) }
        }
    }
}

@Composable
private fun Action(label: Int, icon: Int, tag: String, state: SurfaceState, onClick: () -> Unit) {
    TvActionButton(stringResource(label), onClick, Modifier.testTag(tag), icon = icon, state = state)
}
