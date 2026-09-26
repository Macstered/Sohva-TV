package com.sohva.tv.feature.discover.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.WatchEntry
import com.sohva.tv.feature.discover.ui.failureText
import com.sohva.tv.feature.discover.ui.typeLabel
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Where Addons & setup sends the viewer outside itself. */
class SetupActions(
    val back: () -> Unit,
    val openGrid: (CatalogEntry) -> Unit,
    val openHistory: (WatchEntry) -> Unit,
    val organise: @Composable (SetupModel) -> Unit,
    val import: @Composable (SetupModel) -> Unit,
)

/**
 * Addons & setup (spec 50 §4.6, §5.7): a 200 dp column of sections (Installed addons, Add /
 * import, Catalogs, Subtitles, Watch history) and the chosen section's list on the right.
 */
@Composable
fun SetupScreen(model: SetupModel, actions: SetupActions) {
    val page by model.page.collectAsStateWithLifecycle()
    BackHandler { if (!model.back()) actions.back() }
    when (val p = page) {
        SetupPage.Catalogs -> actions.organise(model)
        SetupPage.Import -> actions.import(model)
        SetupPage.History -> HistoryPage(model, actions)
        is SetupPage.AddonCatalogs -> AddonCatalogsPage(model, p.installation, actions)
        else -> Sections(model, p, actions)
    }
}

@Composable
private fun Sections(model: SetupModel, page: SetupPage, actions: SetupActions) {
    val sectionFocus = remember {
        listOf(SetupPage.Installed, SetupPage.Add, SetupPage.Catalogs, SetupPage.Subtitles, SetupPage.History).associateWith { FocusRequester() }
    }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-setup")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.addon_ui_addons_setup), Modifier.weight(1f), style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.addon_ui_back_to_catalogs), actions.back, Modifier.testTag("discover-setup-back"), TvIcons.Back, compact = true)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((section, pair) in listOf(
                    SetupPage.Installed to (R.string.addon_ui_installed_addons to TvIcons.Channels),
                    SetupPage.Add to (R.string.addon_ui_add_import to TvIcons.Settings),
                    SetupPage.Catalogs to (R.string.addon_ui_catalogs to TvIcons.Guide),
                    SetupPage.Subtitles to (R.string.player_quick_subtitles to TvIcons.Subtitles),
                    SetupPage.History to (R.string.addon_ui_watch_history to TvIcons.Play),
                )) {
                    TvListRow(
                        stringResource(pair.first), { model.show(section) }, Modifier.focusRequester(sectionFocus.getValue(section)).testTag("discover-setup-${section.javaClass.simpleName.lowercase()}"),
                        pair.second, state = SurfaceState(selected = section == page), layout = ListRowLayout(dense = true, labelLines = 2),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                when (page) {
                    SetupPage.Add -> AddSection(model)
                    SetupPage.Subtitles -> SubtitlesSection(model)
                    else -> InstalledSection(model)
                }
            }
        }
    }
    LaunchedEffect(Unit) { sectionFocus[model.returnTo.takeIf { it == page } ?: page]?.requestFocusWhenAttached() }
}

@Composable
private fun InstalledSection(model: SetupModel) {
    val s by model.state.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Installation?>(null) }
    val list = s.installed
    LazyColumn(Modifier.fillMaxWidth().testTag("discover-setup-installed"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SettingsOverline(stringResource(R.string.addon_ui_installed_count, list?.size ?: 0)) }
        s.failure?.let { f -> item { Text(failureText(f), Modifier.testTag("discover-setup-failure"), style = Sohva.typography.label, color = Sohva.palette.danger) } }
        if (list != null && list.isEmpty()) {
            item { SettingsRow(stringResource(R.string.addon_ui_no_addons_installed), subtitle = stringResource(R.string.addon_ui_use_add_import_to_set_up_your_services)) }
        }
        items(list.orEmpty(), key = { it.id }) { inst ->
            val status = stringResource(if (inst.enabled) R.string.addon_enabled else R.string.addon_disabled)
            val catalogs = inst.manifest.catalogs.size
            // §5.7: the addon's row with its enable switch, then its actions indented below.
            SettingsRow(
                inst.name, Modifier.testTag("discover-addon-${inst.name}"), TvIcons.Channels,
                subtitle = pluralStringResource(R.plurals.addon_ui_catalog_count_status, catalogs, catalogs, status),
            ) {
                SettingsSwitch(inst.enabled, { model.setEnabled(inst.id, !inst.enabled) }, Modifier.testTag("discover-addon-switch-${inst.name}"), enabled = !s.busy)
            }
            Row(Modifier.padding(start = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvActionButton(stringResource(R.string.addon_ui_catalogs), { model.show(SetupPage.AddonCatalogs(inst.id)) }, compact = true, state = SurfaceState(enabled = inst.enabled && catalogs > 0))
                TvActionButton(stringResource(R.string.action_refresh), { model.refresh(inst.id) }, Modifier.testTag("discover-addon-refresh-${inst.name}"), compact = true)
                TvActionButton(stringResource(R.string.addon_ui_priority), { model.raise(inst.id) }, Modifier.testTag("discover-addon-raise-${inst.name}"), compact = true)
                TvActionButton(stringResource(R.string.addon_remove), { removing = inst }, Modifier.testTag("discover-addon-remove-${inst.name}"), compact = true, state = SurfaceState(danger = true))
            }
        }
        item {
            SettingsRow(stringResource(R.string.addon_ui_provider_priority), subtitle = stringResource(R.string.addon_ui_priority_changes_addon_source_order_use_catalogs_to_arrange_or_hid))
        }
        item {
            SettingsValueRow(stringResource(R.string.addon_ui_reload_saved_addons), "", model::reload, Modifier.testTag("discover-setup-reload"), TvIcons.Refresh)
        }
    }
    removing?.let { inst ->
        RemoveDialog(inst.name, { removing = null }) {
            removing = null
            model.remove(inst.id)
        }
    }
}

/** FR-12 remove confirmation: Cancel focused, Remove addon in the danger style. */
@Composable
private fun RemoveDialog(name: String, cancel: () -> Unit, remove: () -> Unit) {
    val cancelButton = remember { FocusRequester() }
    Dialog(onDismissRequest = cancel) {
        DialogCard(Modifier.width(480.dp).testTag("discover-remove-dialog"), padding = 24.dp) {
            Text(stringResource(R.string.addon_ui_remove_named, name), style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(stringResource(R.string.addon_cancel), cancel, Modifier.focusRequester(cancelButton).testTag("discover-remove-cancel"), compact = true)
                TvActionButton(stringResource(R.string.addon_ui_remove_addon), remove, Modifier.testTag("discover-remove-confirm"), compact = true, state = SurfaceState(danger = true))
            }
        }
    }
    LaunchedEffect(Unit) { cancelButton.requestFocusWhenAttached() }
}

/** §5.7 "Add / import": the import page and one masked manual URL (cleared on Install). */
@Composable
private fun AddSection(model: SetupModel) {
    val s by model.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxWidth().testTag("discover-setup-add"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SettingsOverline(stringResource(R.string.addon_ui_add_your_services)) }
        item {
            SettingsRow(stringResource(R.string.addon_ui_separate_addon_data), subtitle = stringResource(R.string.addon_ui_sohva_backups_do_not_yet_include_addons_library_or_addon_watch_his))
        }
        item {
            SettingsValueRow(
                stringResource(R.string.addon_ui_import_addons), stringResource(R.string.addon_ui_open), { model.show(SetupPage.Import) }, Modifier.testTag("discover-setup-import"),
                subtitle = stringResource(R.string.addon_ui_copy_from_stremio_send_from_your_phone_or_use_a_saved_list),
            )
        }
        item { SettingsOverline(stringResource(R.string.addon_ui_manual_setup)) }
        item {
            SettingsRow(stringResource(R.string.addon_ui_configured_addon_url), icon = TvIcons.Lock, subtitle = stringResource(R.string.addon_ui_keep_private_configuration_links_secret_they_may_contain_credentia))
        }
        item {
            TvUrlField(
                url, { url = it.take(MAX_URL) }, stringResource(R.string.addon_url), Modifier.fillMaxWidth().padding(16.dp).testTag("discover-setup-url"),
                input = FieldInput(transformation = PasswordVisualTransformation()),
            )
        }
        item {
            TvActionButton(
                stringResource(R.string.addon_install), {
                    val typed = url
                    url = ""
                    model.install(typed)
                },
                Modifier.padding(horizontal = 16.dp).testTag("discover-setup-install"), compact = true, state = SurfaceState(enabled = url.isNotBlank() && !s.busy),
            )
        }
        s.failure?.let { f -> item { Text(failureText(f), Modifier.padding(16.dp).testTag("discover-setup-failure"), style = Sohva.typography.label, color = Sohva.palette.danger) } }
        if (s.busy) item { Text(stringResource(R.string.addon_loading), Modifier.padding(16.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted) }
    }
}

/** §5.7 "Subtitles": the show-all switch, the preferred languages, and where they are set. */
@Composable
private fun SubtitlesSection(model: SetupModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val all by model.allLanguages.collectAsStateWithLifecycle()
    val notSet = stringResource(R.string.addon_ui_not_set)
    LazyColumn(Modifier.fillMaxWidth().testTag("discover-setup-subtitles"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SettingsOverline(stringResource(R.string.addon_subtitle_results)) }
        item {
            SettingsRow(stringResource(R.string.addon_ui_show_all_languages), subtitle = stringResource(R.string.addon_ui_off_limits_results_to_your_primary_and_secondary_subtitle_language)) {
                SettingsSwitch(all, { model.setAllLanguages(!all) }, Modifier.testTag("discover-setup-all-languages"))
            }
        }
        item {
            SettingsRow(
                stringResource(R.string.addon_ui_preferred_languages),
                subtitle = stringResource(R.string.addon_ui_preferred_languages_value, s.languages.subtitles ?: notSet, s.languages.subtitlesSecond ?: notSet),
            )
        }
        item {
            SettingsRow(stringResource(R.string.addon_ui_universal_playback_settings), subtitle = stringResource(R.string.addon_ui_set_preferred_languages_in_sohva_settings_vod_audio_and_subtitles))
        }
        item { SettingsOverline(stringResource(R.string.metadata_language_title)) }
        item {
            SettingsRow(stringResource(R.string.addon_ui_titles_and_synopses), subtitle = stringResource(R.string.addon_ui_choose_these_languages_in_your_metadata_addon_s_configuration_sohv))
        }
    }
}

/** FR-108: newest first, each opening its title; "Watched" or the position; Forget progress. */
@Composable
private fun HistoryPage(model: SetupModel, actions: SetupActions) {
    val s by model.state.collectAsStateWithLifecycle()
    val backButton = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.addon_ui_addon_watch_history), style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.addon_back_catalogs), { model.back() }, Modifier.focusRequester(backButton), TvIcons.Back, compact = true)
        }
        val history = s.history
        when {
            history == null -> Text(stringResource(R.string.addon_ui_loading_watch_history), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            history.isEmpty() -> Text(stringResource(R.string.addon_ui_no_addon_watch_history_yet), Modifier.testTag("discover-history-empty"), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.identity.key("") }) { e ->
                    val seconds = e.positionMs / 1_000
                    val where = if (e.completed) stringResource(R.string.catalogue_watched) else stringResource(R.string.addon_ui_watch_position, (seconds / 60).toInt(), (seconds % 60).toInt())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvListRow(e.title, { actions.openHistory(e) }, Modifier.weight(1f).testTag("discover-history-${e.identity.videoId}"), supporting = where)
                        TvActionButton(stringResource(R.string.addon_ui_forget_progress), { model.forget(e) }, Modifier.testTag("discover-history-forget-${e.identity.videoId}"), compact = true)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { backButton.requestFocusWhenAttached() }
}

/** FR-33: one addon's catalogs, hidden and required-choice ones included, each opening its grid. */
@Composable
private fun AddonCatalogsPage(model: SetupModel, installation: String, actions: SetupActions) {
    val catalogs by produceState<List<CatalogEntry>?>(null, installation) { value = runCatching { model.catalogsOf(installation) }.getOrDefault(emptyList()) }
    val backButton = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-addon-catalogs"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvActionButton(stringResource(R.string.addon_back_catalogs), { model.back() }, Modifier.focusRequester(backButton), TvIcons.Back, compact = true)
        catalogs?.firstOrNull()?.let { Text(it.installation.name, style = Sohva.typography.headline, color = Sohva.palette.textPrimary) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(catalogs.orEmpty(), key = { it.key }) { e ->
                TvListRow("${e.catalog.name} · ${typeLabel(e.catalog.type)}", { actions.openGrid(e) }, Modifier.testTag("discover-addon-catalog-${e.catalog.id}"))
            }
        }
    }
    LaunchedEffect(Unit) { backButton.requestFocusWhenAttached() }
}

private const val MAX_URL = 16_384
