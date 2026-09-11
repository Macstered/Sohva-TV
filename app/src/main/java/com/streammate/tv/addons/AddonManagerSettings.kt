package com.streammate.tv.addons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.*
import com.streammate.tv.feature.settings.*

@Composable
internal fun AddonManagerSettings(host: AddonHost, preferences: AppPreferences, installations: List<InstalledAddon>,
    loaded: Boolean, busy: Boolean, failure: AddonFailure?, backFocus: FocusRequester, onBack: () -> Unit, modifier: Modifier,
    onOperation: (suspend () -> Unit) -> Unit, onBrowse: (InstalledAddon) -> Unit, onImport: () -> Unit,
    onHistory: () -> Unit, onCatalogOrder: () -> Unit, section: String, onSection: (String) -> Unit) {
    val labels = addonStrings()
    val profile = preferences.activeProfileId
    val showAll by host.showAllSubtitleLanguages.collectAsStateWithLifecycle()
    // Never save configured URLs in state bundles, logs or plain preferences.
    var configuredUrl by remember { mutableStateOf("") }
    var removal by remember { mutableStateOf<InstalledAddon?>(null) }
    val palette = StreamMateThemeTokens.palette
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(labels(R.string.addon_ui_addons_setup), Modifier.weight(1f), fontSize = StreamMateThemeTokens.typography.display.fontSize, fontWeight = FontWeight.Black)
            TvActionButton(labels(R.string.addon_ui_back_to_catalogs), onBack, icon = TvIcons.Back, compact = true, focusRequester = backFocus, testTag = "addon-manager-back")
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvListRow(labels(R.string.addon_ui_installed_addons), { onSection("addons") }, icon = TvIcons.Channels, selected = section == "addons", labelLines = 2, testTag = "addon-settings-addons")
                TvListRow(labels(R.string.addon_ui_add_import), { onSection("setup") }, icon = TvIcons.Settings, selected = section == "setup", labelLines = 2, testTag = "addon-settings-setup")
                TvListRow(labels(R.string.addon_ui_catalogs), onCatalogOrder, icon = TvIcons.Guide, labelLines = 2, testTag = "addon-catalog-order")
                TvListRow(labels(com.streammate.tv.iptv.R.string.player_quick_subtitles), { onSection("subtitles") }, icon = TvIcons.Subtitles, selected = section == "subtitles", labelLines = 2, testTag = "addon-settings-subtitles")
                TvListRow(labels(R.string.addon_ui_watch_history), onHistory, icon = TvIcons.Play, labelLines = 2, testTag = "addon-history")
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag(if (loaded) "addon-manager-list" else "addon-manager-loading"), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    if (busy) Text(stringResource(R.string.addon_loading), color = palette.textMuted)
                    failure?.let { Text(stringResource(it.messageResource()), color = palette.danger) }
                }
                if (section == "addons") {
                    item { SettingsOverline(labels(R.string.addon_ui_installed_count, installations.size)) }
                    itemsIndexed(installations, key = { _, it -> it.installationId }) { index, installation ->
                        SettingsRow(installation.manifest.name, subtitle = labels.count(R.plurals.addon_ui_catalog_count_status, installation.manifest.catalogs.size, labels(if (installation.enabled) R.string.addon_enabled else R.string.addon_disabled)), icon = TvIcons.Channels) {
                            SettingsSwitch(installation.enabled, { enabled -> onOperation { host.manager.setEnabled(profile, installation.installationId, enabled) } }, enabled = !busy, testTag = "addon-toggle-${installation.installationId}")
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvActionButton(labels(R.string.addon_ui_catalogs), { onBrowse(installation) }, enabled = !busy && installation.enabled && installation.manifest.catalogs.isNotEmpty(), compact = true, testTag = "addon-browse-${installation.installationId}")
                            TvActionButton(labels(com.streammate.tv.iptv.R.string.action_refresh), { onOperation { if (!host.manager.refresh(profile, installation.installationId)) throw AddonException(AddonFailure.CONFLICT) } }, enabled = !busy, compact = true)
                            TvActionButton(labels(R.string.addon_ui_priority), { onOperation {
                                val ids = installations.map { it.installationId }.toMutableList()
                                java.util.Collections.swap(ids, index, index - 1); host.manager.reorder(profile, ids)
                            } }, enabled = !busy && index > 0, compact = true)
                            TvActionButton(labels(R.string.addon_remove), { removal = installation }, enabled = !busy, compact = true, danger = true, testTag = "addon-remove-${installation.installationId}")
                        }
                    }
                    item {
                        if (loaded && installations.isEmpty()) SettingsRow(labels(R.string.addon_ui_no_addons_installed), subtitle = labels(R.string.addon_ui_use_add_import_to_set_up_your_services), icon = TvIcons.Info)
                        SettingsRow(labels(R.string.addon_ui_provider_priority), subtitle = labels(R.string.addon_ui_priority_changes_addon_source_order_use_catalogs_to_arrange_or_hid), icon = TvIcons.Info)
                        SettingsValueRow(labels(R.string.addon_ui_reload_saved_addons), "", { onOperation {} }, enabled = !busy, icon = TvIcons.Refresh)
                    }
                } else if (section == "setup") {
                    item {
                        SettingsOverline(labels(R.string.addon_ui_add_your_services))
                        SettingsRow(labels(R.string.addon_ui_separate_addon_data), subtitle = labels(R.string.addon_ui_sohva_backups_do_not_yet_include_addons_library_or_addon_watch_his), icon = TvIcons.Info)
                        SettingsValueRow(labels(R.string.addon_ui_import_addons), labels(R.string.addon_ui_open), onImport, subtitle = labels(R.string.addon_ui_copy_from_stremio_send_from_your_phone_or_use_a_saved_list), icon = TvIcons.Channels, testTag = "addon-import", enabled = !busy)
                        SettingsOverline(labels(R.string.addon_ui_manual_setup))
                        SettingsRow(labels(R.string.addon_ui_configured_addon_url), subtitle = labels(R.string.addon_ui_keep_private_configuration_links_secret_they_may_contain_credentia), icon = TvIcons.Lock)
                        TvUrlField(configuredUrl, { if (it.length <= 16_384) configuredUrl = it }, stringResource(R.string.addon_url), Modifier.fillMaxWidth().padding(16.dp),
                            testTag = "addon-url", visualTransformation = PasswordVisualTransformation(), editOnClickOnly = true)
                        TvActionButton(labels(R.string.addon_install), {
                            val submitted = configuredUrl; configuredUrl = ""
                            onOperation { host.manager.install(profile, submitted) }
                        }, enabled = !busy && configuredUrl.isNotBlank(), testTag = "addon-install", modifier = Modifier.padding(start = 16.dp))
                    }
                } else {
                    item {
                        SettingsOverline(labels(R.string.addon_subtitle_results))
                        SettingsRow(labels(R.string.addon_ui_show_all_languages), subtitle = labels(R.string.addon_ui_off_limits_results_to_your_primary_and_secondary_subtitle_language), icon = TvIcons.Subtitles) {
                            SettingsSwitch(showAll, { value -> onOperation { host.setShowAllSubtitleLanguages(value) } }, enabled = !busy, testTag = "addon-subtitle-languages")
                        }
                        SettingsRow(labels(R.string.addon_ui_preferred_languages), subtitle = labels(R.string.addon_ui_preferred_languages_value, preferences.preferredSubtitleLanguage?.let(labels::languageName) ?: labels(R.string.addon_ui_not_set), preferences.secondarySubtitleLanguage?.let(labels::languageName) ?: labels(R.string.addon_ui_not_set)), icon = TvIcons.Subtitles)
                        SettingsRow(labels(R.string.addon_ui_universal_playback_settings), subtitle = labels(R.string.addon_ui_set_preferred_languages_in_sohva_settings_vod_audio_and_subtitles), icon = TvIcons.Settings)
                        SettingsOverline(labels(com.streammate.tv.iptv.R.string.metadata_language_title))
                        SettingsRow(labels(R.string.addon_ui_titles_and_synopses), subtitle = labels(R.string.addon_ui_choose_these_languages_in_your_metadata_addon_s_configuration_sohv), icon = TvIcons.Info)
                    }
                }
            }
        }
    }
    removal?.let { entry ->
        Dialog({ removal = null }) {
            val cancelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { cancelFocus.requestFocusWhenAttached() }
            Column(Modifier.fillMaxWidth().background(palette.panel.copy(alpha = 1f), StreamMateThemeTokens.shapes.medium).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(labels(R.string.addon_ui_remove_named, entry.manifest.name), color = palette.textPrimary)
                TvActionButton(labels(R.string.addon_cancel), { removal = null }, focusRequester = cancelFocus)
                TvActionButton(labels(R.string.addon_ui_remove_addon), { removal = null; onOperation { host.manager.remove(profile, entry.installationId) } },
                    danger = true, enabled = !busy, testTag = "addon-confirm-remove-${entry.installationId}")
            }
        }
    }
}
