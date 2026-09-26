package com.sohva.tv.feature.discover.ui.setup

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.launch

/** The import methods in the left column (§5.8). */
private enum class ImportMethod { MANUAL, FILE, NUVIO, PHONE, STREMIO }

/**
 * Import addons (spec 50 §4.7, §5.8): the methods feed one pending list; Preview checks it; Install
 * adds the chosen ones. The phone and Stremio pages open inside. Leaving the page or the app going
 * to the background stops any server or authorization and drops what was entered (FR-42, -45, -48).
 */
@Composable
internal fun ImportScreen(model: ImportModel, back: () -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                model.stopPhone()
                model.stopStremio(background = true)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.stopPhone()
            model.stopStremio(background = false)
            model.clear()
        }
    }
    val leave = { if (s.page == ImportPage.METHODS) back() else model.go(ImportPage.METHODS) }
    BackHandler(onBack = leave)
    val title = when (s.page) {
        ImportPage.METHODS -> R.string.addon_ui_import_addons
        ImportPage.PHONE -> R.string.addon_ui_phone_setup
        ImportPage.STREMIO -> R.string.addon_ui_stremio_account
    }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-import"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(title), style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.action_back), leave, Modifier.testTag("discover-import-back"), TvIcons.Back, compact = true)
        }
        when (s.page) {
            ImportPage.METHODS -> Methods(model, s)
            ImportPage.PHONE -> PhonePage(model, s)
            ImportPage.STREMIO -> StremioPage(model, s)
        }
    }
}

@Composable
private fun Methods(model: ImportModel, s: ImportState) {
    var method by remember { mutableStateOf(ImportMethod.MANUAL) }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (s.rows == null) {
                SettingsOverline(stringResource(R.string.addon_ui_import_methods))
                val methods = listOf(
                    ImportMethod.MANUAL to R.string.addon_ui_manual_url,
                    ImportMethod.FILE to R.string.addon_ui_from_a_file,
                    ImportMethod.NUVIO to R.string.addon_ui_choose_nuvio_addon_json_file,
                    ImportMethod.PHONE to R.string.addon_ui_set_up_from_phone,
                    ImportMethod.STREMIO to R.string.addon_ui_copy_from_stremio_account,
                )
                methods.forEachIndexed { i, (m, label) ->
                    TvListRow(
                        stringResource(label), { method = m }, (if (i == 0) Modifier.focusRequester(first) else Modifier).testTag("discover-import-method-${m.name.lowercase()}"),
                        state = SurfaceState(selected = method == m), layout = ListRowLayout(dense = true, labelLines = 2),
                    )
                }
            } else {
                SettingsOverline(stringResource(R.string.addon_ui_review_import))
                Note(stringResource(R.string.addon_ui_select_new_addons_to_include))
                Note(stringResource(R.string.addon_ui_new_addons_are_added_in_list_order_unavailable_entries_do_not_bloc))
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val rows = s.rows
                if (rows == null) MethodContent(model, method) else ReviewList(model, rows, s.committed || s.committing)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).roundFill(Sohva.palette.divider, 0.dp))
            Footer(model, s)
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

@Composable
private fun MethodContent(model: ImportModel, method: ImportMethod) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { model.useList(model.readDocument { context.contentResolver.openInputStream(uri) }) }
    }
    val nuvioFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { model.useNuvio(model.readDocument { context.contentResolver.openInputStream(uri) }) }
    }
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (method) {
            ImportMethod.MANUAL -> {
                Note(stringResource(R.string.addon_ui_use_the_full_configured_https_or_stremio_url_add_one_link_at_a_tim))
                TvUrlField(
                    url, { url = it.take(MAX_FIELD) }, stringResource(R.string.addon_url), Modifier.fillMaxWidth().testTag("discover-import-url"),
                    input = FieldInput(transformation = PasswordVisualTransformation()),
                )
                TvActionButton(
                    stringResource(R.string.addon_ui_add_url_to_list), { if (model.addManual(url)) url = "" },
                    Modifier.testTag("discover-import-add"), compact = true, state = SurfaceState(enabled = url.isNotBlank()),
                )
            }
            ImportMethod.FILE -> {
                Note(stringResource(R.string.addon_ui_choose_a_utf_8_txt_file_on_your_phone_or_paste_configured_addon_ur))
                TvActionButton(stringResource(R.string.addon_ui_choose_text_file), {
                    try {
                        listFile.launch(arrayOf("text/*", "application/octet-stream"))
                    } catch (e: ActivityNotFoundException) {
                        model.noPicker()
                    }
                }, Modifier.testTag("discover-import-file"), compact = true)
            }
            ImportMethod.NUVIO -> {
                Note(stringResource(R.string.addon_ui_for_nuvio_save_api_addons_from_its_local_phone_setup_address_as_a))
                Note(stringResource(R.string.addon_ui_addon_urls_only_not_a_full_nuvio_backup))
                TvActionButton(stringResource(R.string.addon_ui_choose_nuvio_addon_json_file), {
                    try {
                        nuvioFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    } catch (e: ActivityNotFoundException) {
                        model.noPicker()
                    }
                }, Modifier.testTag("discover-import-nuvio"), compact = true)
            }
            ImportMethod.PHONE -> {
                Note(stringResource(R.string.addon_ui_select_a_utf_8_txt_list_on_your_phone_then_send_it_here_for_review))
                TvActionButton(stringResource(R.string.addon_ui_set_up_from_phone), { model.go(ImportPage.PHONE) }, Modifier.testTag("discover-import-phone"), compact = true)
                TvActionButton(stringResource(R.string.addon_ui_choose_file_on_phone), { model.go(ImportPage.PHONE) }, compact = true)
            }
            ImportMethod.STREMIO -> {
                Note(stringResource(R.string.addon_ui_authorize_on_your_phone_then_review_your_addon_list_here))
                TvActionButton(stringResource(R.string.addon_ui_copy_from_stremio_account), { model.go(ImportPage.STREMIO) }, Modifier.testTag("discover-import-stremio"), compact = true)
            }
        }
    }
}

/** FR-40's rows: "Addon N", the status line and its value; read-only rows stay focusable. */
@Composable
private fun ReviewList(model: ImportModel, rows: List<ImportRow>, done: Boolean) {
    val first = remember { FocusRequester() }
    LazyColumn(Modifier.fillMaxSize().testTag("discover-import-review"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(rows, key = { _, r -> r.number }) { i, row ->
            val (subtitle, value) = statusTexts(row)
            SettingsValueRow(
                stringResource(R.string.addon_ui_numbered_addon, row.number), value, { if (!done) model.toggle(i) },
                (if (i == 0) Modifier.focusRequester(first) else Modifier).testTag("discover-import-row-${row.number}"),
                icon = statusIcon(row), subtitle = subtitle,
            )
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

@Composable
private fun statusTexts(row: ImportRow): Pair<String, String> = when (row.status) {
    ImportStatus.CHECKING -> stringResource(R.string.addon_loading) to ""
    ImportStatus.READY -> if (row.selected) {
        stringResource(R.string.addon_ui_ready_to_install) to stringResource(R.string.addon_ui_include)
    } else {
        stringResource(R.string.addon_ui_not_selected_for_this_import) to stringResource(R.string.addon_ui_skip)
    }
    ImportStatus.ALREADY_INSTALLED -> stringResource(R.string.addon_ui_already_installed_unchanged) to stringResource(R.string.addon_ui_kept)
    ImportStatus.DUPLICATE_INPUT -> stringResource(R.string.addon_ui_duplicate_in_this_list_skipped) to stringResource(R.string.addon_ui_skipped)
    ImportStatus.NEEDS_CONFIGURATION -> stringResource(R.string.addon_ui_configure_on_the_provider_s_page_first) to stringResource(R.string.addon_ui_unavailable)
    ImportStatus.BAD_URL -> stringResource(R.string.addon_ui_use_a_configured_https_or_stremio_url) to stringResource(R.string.addon_ui_unavailable)
    ImportStatus.REDIRECT -> stringResource(R.string.addon_ui_use_the_provider_s_final_manifest_url) to stringResource(R.string.addon_ui_unavailable)
    ImportStatus.UNAVAILABLE -> stringResource(R.string.addon_ui_unavailable_provider_invalid_response_or_changed_access) to stringResource(R.string.addon_ui_unavailable)
    ImportStatus.INSTALLED -> stringResource(R.string.addon_ui_installed) to stringResource(R.string.addon_ui_added)
}

private fun statusIcon(row: ImportRow): Int? = when (row.status) {
    ImportStatus.READY -> if (row.selected) TvIcons.Check else TvIcons.Close
    ImportStatus.ALREADY_INSTALLED, ImportStatus.DUPLICATE_INPUT -> TvIcons.Lock
    ImportStatus.INSTALLED -> TvIcons.Check
    else -> null
}

/** The count, the note, then Preview + Clear, or Install + Start another list (§5.8). */
@Composable
private fun Footer(model: ImportModel, s: ImportState) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = s.rows
        val count = if (rows == null) {
            pluralStringResource(R.plurals.addon_ui_preview_count, s.pending, s.pending)
        } else {
            pluralStringResource(R.plurals.addon_ui_checked_count, rows.count { it.status != ImportStatus.CHECKING }, rows.count { it.status != ImportStatus.CHECKING })
        }
        Text(count, Modifier.testTag("discover-import-count"), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
        Text(noteText(s), Modifier.testTag("discover-import-note"), style = Sohva.typography.label, color = Sohva.palette.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows == null) {
                TvActionButton(stringResource(R.string.addon_ui_preview_import), model::preview, Modifier.testTag("discover-import-preview"), compact = true, state = SurfaceState(enabled = s.pending > 0))
                TvActionButton(stringResource(R.string.addon_ui_clear_list), model::clear, Modifier.testTag("discover-import-clear"), compact = true, state = SurfaceState(enabled = s.pending > 0))
            } else {
                if (!s.committed) {
                    TvActionButton(
                        pluralStringResource(R.plurals.addon_ui_install_count, s.selectedCount, s.selectedCount), model::commit, Modifier.testTag("discover-import-install"),
                        compact = true, state = SurfaceState(enabled = s.selectedCount > 0 && !s.previewing && !s.committing),
                    )
                }
                TvActionButton(stringResource(R.string.addon_ui_start_another_list), model::clear, Modifier.testTag("discover-import-another"), compact = true)
            }
        }
    }
}

@Composable
private fun noteText(s: ImportState): String = when (val n = s.note) {
    ImportNote.ListFull -> stringResource(R.string.addon_ui_the_list_is_limited_to_32_urls_and_256_kib)
    ImportNote.Invalid, ImportNote.Failed -> stringResource(R.string.addon_ui_import_could_not_be_completed_use_a_supported_utf_8_file_up_to_256)
    ImportNote.NoPicker -> stringResource(R.string.addon_ui_no_document_picker_is_available_choose_a_text_file_on_your_phone_o)
    is ImportNote.Copied -> pluralStringResource(R.plurals.addon_ui_copied_count, n.count, n.count)
    ImportNote.NoAddons -> stringResource(R.string.addon_ui_the_stremio_account_has_no_addons)
    ImportNote.Complete -> stringResource(R.string.addon_ui_import_complete)
    null -> stringResource(R.string.addon_ui_preview_checks_the_addon_providers_nothing_is_installed_until_you)
}

@Composable
internal fun Note(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = Sohva.typography.label, color = Sohva.palette.textDim)
}

private const val MAX_FIELD = 16_384
