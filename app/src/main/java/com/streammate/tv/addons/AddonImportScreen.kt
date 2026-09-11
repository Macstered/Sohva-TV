package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit preview then commit. Neither secret input nor preview enters saved-state bundles. */
@Composable
internal fun AddonImportScreen(
    previewImport: suspend (String) -> AddonImportPreview,
    commitImport: suspend (AddonImportPreview) -> List<AddonImportEntry>,
    onBack: () -> Unit,
    modifier: Modifier,
    stremioImport: (() -> kotlinx.coroutines.flow.Flow<StremioCopyEvent>)? = null,
) {
    val labels = addonStrings()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var queued by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AddonImportPreview?>(null) }
    var results by remember { mutableStateOf<List<AddonImportEntry>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var awaitingDocument by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf(false) }
    var stremio by remember { mutableStateOf(false) }
    var documentIsNuvio by remember { mutableStateOf(false) }
    var selectedLines by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var section by remember { mutableStateOf("transfer") }
    val backFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(preview, results, phone, stremio) {
        if (!phone && !stremio) backFocus.requestFocusWhenAttached()
    }

    fun work(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // URI, content-provider exceptions, manifests and configured URLs are private.
                message = labels(R.string.addon_ui_import_could_not_be_completed_use_a_supported_utf_8_file_up_to_256)
            } finally { busy = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expected = awaitingDocument
        awaitingDocument = false
        if (expected && uri != null) work {
            // No persistent grant, filename query or retained URI. Cloud providers may download
            // their selected document, but no addon is contacted until Preview is pressed.
            check(uri.scheme == "content")
            val text = withContext(Dispatchers.IO) {
                val stream = checkNotNull(context.contentResolver.openInputStream(uri))
                if (documentIsNuvio) AddonCopyExport.readNuvio(stream).asUrlList() else AddonImportText.read(stream)
            }
            input = ""
            queued = text
            preview = null
            results = null
            message = if (text.isBlank()) labels(R.string.addon_ui_the_copied_list_has_no_addons) else null
        }
    }

    if (phone) {
        AddonPhoneScreen({ text -> queued = text; input = ""; message = null; phone = false }, { phone = false }, modifier)
        return
    }
    if (stremio && stremioImport != null) {
        AddonStremioImportScreen(stremioImport, { copied ->
            queued = copied.asUrlList(); input = ""; stremio = false
            message = if (copied.count == 0) labels(R.string.addon_ui_the_stremio_account_has_no_addons) else labels.count(R.plurals.addon_ui_copied_count, copied.count)
        }, { stremio = false }, modifier)
        return
    }

    val collecting = preview == null && results == null
    val entries = results ?: preview?.entries.orEmpty()
    val palette = StreamMateThemeTokens.palette
    AddonSetupPage(if (collecting) labels(R.string.addon_ui_import_addons) else if (results == null) labels(R.string.addon_ui_review_import) else labels(R.string.addon_ui_import_result),
        onBack, labels(R.string.addon_back_catalogs), backFocus, "addon-import-screen", "addon-import-back", modifier) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (collecting) {
                    SettingsOverline(labels(R.string.addon_ui_import_methods))
                    TvListRow(labels(R.string.addon_ui_account_phone), { section = "transfer" }, icon = TvIcons.Link,
                        selected = section == "transfer", labelLines = 2, testTag = "addon-import-section-transfer")
                    TvListRow(labels(R.string.addon_ui_from_a_file), { section = "files" }, icon = TvIcons.Save,
                        selected = section == "files", labelLines = 2, testTag = "addon-import-section-files")
                    TvListRow(labels(R.string.addon_ui_manual_url), { section = "manual" }, icon = TvIcons.Key,
                        selected = section == "manual", labelLines = 2, testTag = "addon-import-section-manual")
                    AddonSetupNote(labels(R.string.addon_ui_up_to_32_addons_per_list_configured_urls_stay_masked), Modifier.padding(14.dp))
                } else {
                    SettingsOverline(if (results == null) labels(R.string.addon_ui_review_your_list) else labels(R.string.addon_ui_import_complete))
                    AddonSetupNote(labels.count(R.plurals.addon_ui_checked_count, entries.size), Modifier.padding(horizontal = 14.dp))
                    AddonSetupNote(labels(R.string.addon_ui_existing_addons_keep_their_order_and_enabled_state_they_are_not_in), Modifier.padding(14.dp))
                    AddonSetupNote(labels(R.string.addon_ui_new_addons_are_added_in_list_order_unavailable_entries_do_not_bloc), Modifier.padding(horizontal = 14.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (busy) AddonSetupNote(labels(R.string.addon_loading), Modifier.padding(14.dp).testTag("addon-import-busy"))
                message?.let { AddonSetupNote(it, Modifier.padding(14.dp).testTag("addon-import-message")) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("addon-import-content"),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (collecting) {
                        when (section) {
                            "transfer" -> {
                                item { SettingsOverline(labels(R.string.addon_ui_copy_your_setup)) }
                                if (stremioImport != null) item {
                                    SettingsValueRow(labels(R.string.addon_ui_copy_from_stremio_account), labels(R.string.addon_ui_open), {
                                        input = ""; queued = ""; message = null; stremio = true
                                    }, subtitle = labels(R.string.addon_ui_authorize_on_your_phone_then_review_your_addon_list_here), icon = TvIcons.Link,
                                        enabled = !busy && !awaitingDocument, testTag = "addon-import-stremio")
                                }
                                item {
                                    SettingsValueRow(labels(R.string.addon_ui_set_up_from_phone), labels(R.string.addon_ui_open), { input = ""; message = null; phone = true },
                                        subtitle = labels(R.string.addon_ui_paste_urls_or_choose_a_text_file_on_your_phone_over_trusted_wi_fi), icon = TvIcons.Channels,
                                        enabled = !busy && !awaitingDocument, testTag = "addon-import-phone")
                                    SettingsRow(labels(R.string.addon_ui_copy_only), subtitle = labels(R.string.addon_ui_your_source_account_addons_and_watch_history_are_not_changed), icon = TvIcons.Lock)
                                }
                            }
                            "files" -> {
                                item { SettingsOverline(labels(R.string.addon_ui_choose_a_saved_list)) }
                                item {
                                    SettingsValueRow(labels(R.string.addon_ui_choose_text_file), labels(R.string.addon_ui_browse), {
                                        try { documentIsNuvio = false; awaitingDocument = true; picker.launch(arrayOf("text/*", "application/octet-stream")) }
                                        catch (_: Exception) { awaitingDocument = false; message = labels(R.string.addon_ui_no_document_picker_is_available_choose_a_text_file_on_your_phone_o) }
                                    }, subtitle = labels(R.string.addon_ui_utf_8_text_one_configured_url_per_line_maximum_256_kib), icon = TvIcons.Save,
                                        enabled = !busy && !awaitingDocument, testTag = "addon-import-file")
                                }
                                item {
                                    SettingsValueRow(labels(R.string.addon_ui_choose_file_on_phone), labels(R.string.addon_ui_open_qr), {
                                        input = ""; message = null; phone = true
                                    }, subtitle = labels(R.string.addon_ui_select_a_utf_8_txt_list_on_your_phone_then_send_it_here_for_review), icon = TvIcons.Channels,
                                        enabled = !busy && !awaitingDocument, testTag = "addon-import-phone-file")
                                }
                                item {
                                    SettingsValueRow(labels(R.string.addon_ui_choose_nuvio_addon_json_file), labels(R.string.addon_ui_browse), {
                                        try { documentIsNuvio = true; awaitingDocument = true; picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                                        catch (_: Exception) { awaitingDocument = false; message = labels(R.string.addon_ui_no_document_picker_is_available_use_manual_url_instead) }
                                    }, subtitle = labels(R.string.addon_ui_addon_urls_only_not_a_full_nuvio_backup), icon = TvIcons.Save,
                                        enabled = !busy && !awaitingDocument, testTag = "addon-import-nuvio")
                                }
                                item {
                                    AddonSetupNote(labels(R.string.addon_ui_for_nuvio_save_api_addons_from_its_local_phone_setup_address_as_a), Modifier.padding(14.dp))
                                }
                            }
                            else -> {
                                item {
                                    SettingsOverline(labels(R.string.addon_ui_add_a_configured_url))
                                    SettingsRow(labels(R.string.addon_ui_private_addon_link), subtitle = labels(R.string.addon_ui_use_the_full_configured_https_or_stremio_url_add_one_link_at_a_tim), icon = TvIcons.Lock)
                                    TvUrlField(input, { if (it.length <= 16_384) input = it }, labels(R.string.addon_ui_private_configured_addon_url),
                                        Modifier.fillMaxWidth().padding(14.dp), testTag = "addon-import-url", keyboardType = KeyboardType.Password,
                                        visualTransformation = PasswordVisualTransformation(), editOnClickOnly = true)
                                    TvActionButton(labels(R.string.addon_ui_add_url_to_list), {
                                        val candidate = listOf(queued, input.trim()).filter { it.isNotBlank() }.joinToString("\n")
                                        try {
                                            AddonImportText.validate(candidate)
                                            queued = candidate; input = ""; message = null
                                        } catch (_: AddonException) { message = labels(R.string.addon_ui_the_list_is_limited_to_32_urls_and_256_kib) }
                                    }, modifier = Modifier.padding(horizontal = 14.dp), compact = true, icon = TvIcons.Check,
                                        enabled = !busy && !awaitingDocument && input.isNotBlank(), testTag = "addon-import-add")
                                }
                            }
                        }
                    } else {
                        item { SettingsOverline(if (results == null) labels(R.string.addon_ui_select_new_addons_to_include) else labels(R.string.addon_ui_results)) }
                        items(entries, key = { it.line }) { entry ->
                            // Positional labels only: no untrusted manifest names or private URLs.
                            val included = entry.line in selectedLines
                            val status = when (entry.status) {
                                AddonImportStatus.READY -> if (included) labels(R.string.addon_ui_ready_to_install) else labels(R.string.addon_ui_not_selected_for_this_import)
                                AddonImportStatus.ALREADY_INSTALLED -> labels(R.string.addon_ui_already_installed_unchanged)
                                AddonImportStatus.DUPLICATE_INPUT -> labels(R.string.addon_ui_duplicate_in_this_list_skipped)
                                AddonImportStatus.INSTALLED -> labels(R.string.addon_ui_installed)
                                AddonImportStatus.FAILED -> when (entry.failure) {
                                    AddonFailure.CONFIGURATION_REQUIRED -> labels(R.string.addon_ui_configure_on_the_provider_s_page_first)
                                    AddonFailure.INVALID_URL, AddonFailure.INSECURE_URL -> labels(R.string.addon_ui_use_a_configured_https_or_stremio_url)
                                    AddonFailure.REDIRECT -> labels(R.string.addon_ui_use_the_provider_s_final_manifest_url)
                                    else -> labels(R.string.addon_ui_unavailable_provider_invalid_response_or_changed_access)
                                }
                            }
                            val selectable = results == null && entry.status == AddonImportStatus.READY
                            val value = if (selectable) {
                                if (included) labels(R.string.addon_ui_include) else labels(R.string.addon_ui_skip)
                            } else when (entry.status) {
                                AddonImportStatus.INSTALLED -> labels(R.string.addon_ui_added)
                                AddonImportStatus.ALREADY_INSTALLED -> labels(R.string.addon_ui_kept)
                                AddonImportStatus.FAILED -> labels(R.string.addon_ui_unavailable)
                                else -> labels(R.string.addon_ui_skipped)
                            }
                            // Read-only rows are still focusable so D-pad users can inspect
                            // long lists containing only duplicates or unavailable addons.
                            SettingsValueRow(labels(R.string.addon_ui_numbered_addon, entry.line), value, {
                                if (selectable && !busy) selectedLines = if (included) selectedLines - entry.line else selectedLines + entry.line
                            }, subtitle = status,
                                modifier = Modifier.testTag("addon-import-entry-${entry.line}"),
                                icon = if (entry.status == AddonImportStatus.FAILED) TvIcons.Info else TvIcons.Channels,
                                chevron = if (selectable) { if (included) TvIcons.Check else TvIcons.Close } else TvIcons.Lock,
                                enabled = !busy, testTag = if (selectable) "addon-import-select-${entry.line}" else "addon-import-status-${entry.line}")
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (collecting) {
                        val count = queued.lineSequence().count { it.isNotBlank() }
                        Text(labels.count(R.plurals.addon_ui_preview_count, count), Modifier.testTag("addon-import-count"),
                            fontSize = StreamMateThemeTokens.typography.body.fontSize)
                        AddonSetupNote(labels(R.string.addon_ui_preview_checks_the_addon_providers_nothing_is_installed_until_you))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvActionButton(labels(R.string.addon_ui_preview_import), {
                                val submitted = queued
                                queued = ""; input = ""
                                work {
                                    val checked = previewImport(submitted)
                                    selectedLines = checked.entries.filter { it.status == AddonImportStatus.READY }.map { it.line }.toSet()
                                    preview = checked
                                }
                            }, compact = true, icon = TvIcons.Check, enabled = !busy && !awaitingDocument && queued.isNotBlank(), testTag = "addon-import-preview")
                            TvActionButton(labels(R.string.addon_ui_clear_list), { queued = ""; input = ""; message = null }, compact = true,
                                enabled = !busy && !awaitingDocument && (queued.isNotBlank() || input.isNotBlank()), testTag = "addon-import-clear")
                        }
                    } else {
                        val pending = preview
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (results == null && pending != null) {
                                val ready = pending.entries.count { it.status == AddonImportStatus.READY && it.line in selectedLines }
                                TvActionButton(labels.count(R.plurals.addon_ui_install_count, ready), {
                                    work {
                                        results = commitImport(pending.selecting(selectedLines))
                                        preview = null // Drop credential-bearing preview after a successful commit.
                                    }
                                }, compact = true, icon = TvIcons.Check, enabled = !busy && ready > 0, testTag = "addon-import-confirm")
                            }
                            TvActionButton(labels(R.string.addon_ui_start_another_list), {
                                preview = null; results = null; input = ""; queued = ""; message = null
                            }, compact = true, enabled = !busy, testTag = "addon-import-reset")
                        }
                    }
                }
            }
        }
    }
}
