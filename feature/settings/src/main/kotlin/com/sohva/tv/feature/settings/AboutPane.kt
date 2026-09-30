package com.sohva.tv.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.update.UpdateFailure
import com.sohva.tv.core.model.update.UpdatePhase
import com.sohva.tv.core.model.update.UpdateState
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The repository where translations are corrected (spec 70 SET-FR-97, spec 74). */
internal const val PUBLIC_REPOSITORY: String = "https://github.com/Macstered/Sohva-TV"

/**
 * Settings › About (spec 72 §5.1): Updates, the licences button, Translations and Diagnostics.
 * The section's first control is the update action, or the licences button when updates are off.
 */
@Composable
internal fun AboutPane(about: AboutSettings, start: FocusRequester) {
    val update by about.updates.collectAsStateWithLifecycle()
    // Back from the legal screen, or no update actions: the section's start is the licences button.
    val fromLegal = remember { about.takeLegalReturn() }
    val licencesFirst = fromLegal || update.phase == UpdatePhase.DISABLED
    UpdatesGroup(about, update, if (licencesFirst) null else start)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        TvActionButton(
            stringResource(R.string.settings_about_licenses), about::openLegal,
            (if (licencesFirst) Modifier.focusRequester(start) else Modifier).testTag("settings-about-licenses"), TvIcons.Info,
        )
    }
    val links = rememberLinkOpener(about)
    SettingsGroup {
        SettingsOverline(stringResource(R.string.translate_title))
        val translate = remember { FocusRequester() }
        SettingsValueRow(
            stringResource(R.string.translate_help_title), "", { links.open(PUBLIC_REPOSITORY, translate) },
            Modifier.focusRequester(translate).testTag("settings-translate-help"), TvIcons.Info, stringResource(R.string.translate_help),
        )
    }
    DiagnosticsGroup(about)
    LinkDialog(about, links)
}

@Composable
private fun UpdatesGroup(about: AboutSettings, update: UpdateState, start: FocusRequester?) {
    val first = start ?: remember { FocusRequester() }
    var actionsFocused by remember { mutableStateOf(false) }
    val permissionHelp by about.permissionHelp.collectAsStateWithLifecycle()
    SettingsGroup {
        SettingsOverline(stringResource(R.string.update_title))
        Text(stringResource(R.string.update_installed, about.installedVersion), Modifier.padding(horizontal = 14.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        Text(
            if (about.updatesFromStore) stringResource(R.string.update_from_play) else statusText(update),
            Modifier.padding(horizontal = 14.dp).testTag("settings-update-status"),
            style = Sohva.typography.body,
            color = if (update.phase == UpdatePhase.FAILED) Sohva.palette.danger else Sohva.palette.textPrimary,
        )
        if (update.phase == UpdatePhase.NEEDS_PERMISSION && permissionHelp) {
            Text(stringResource(R.string.update_permission_where), Modifier.padding(horizontal = 14.dp).testTag("settings-update-permission-help"), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
        }
        if (update.phase != UpdatePhase.DISABLED) {
            Row(
                Modifier.padding(horizontal = 14.dp).onFocusChanged { actionsFocused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) { UpdateActions(about, update.phase, first) }
        }
        Notes(about, update)
        // The checksum note describes the GitHub updater only.
        if (!about.updatesFromStore) {
            Text(stringResource(R.string.update_help), Modifier.padding(horizontal = 14.dp), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
        }
    }
    // A phase that swaps the buttons keeps focus on the row's first one, never on a vanished one (§4.6).
    LaunchedEffect(update.phase) { if (actionsFocused) first.requestFocusWhenAttached() }
}

/** §4.6's buttons. Busy buttons stay focusable but do nothing, so focus never falls off the row. */
@Composable
private fun UpdateActions(about: AboutSettings, phase: UpdatePhase, first: FocusRequester) {
    val modifier = Modifier.focusRequester(first)
    when (phase) {
        UpdatePhase.AVAILABLE ->
            TvActionButton(stringResource(R.string.update_download), about::download, modifier.testTag("settings-update-download"), TvIcons.Save)
        UpdatePhase.DOWNLOADING ->
            TvActionButton(stringResource(R.string.update_checking_button), {}, modifier.testTag("settings-update-download"), TvIcons.Save, SurfaceState(enabled = false, keepsFocus = true))
        UpdatePhase.DOWNLOADED ->
            TvActionButton(stringResource(R.string.update_install), about::install, modifier.testTag("settings-update-install"), TvIcons.Play)
        UpdatePhase.NEEDS_PERMISSION -> {
            TvActionButton(stringResource(R.string.update_open_permission), about::openPermission, modifier.testTag("settings-update-permission"), TvIcons.Settings)
            TvActionButton(stringResource(R.string.update_install), about::install, Modifier.testTag("settings-update-install"), TvIcons.Play)
        }
        else -> TvActionButton(
            stringResource(R.string.update_check), about::check, modifier.testTag("settings-update-check"), TvIcons.Refresh,
            SurfaceState(enabled = phase != UpdatePhase.CHECKING, keepsFocus = true),
        )
    }
}

@Composable
private fun Notes(about: AboutSettings, update: UpdateState) {
    // The Lab build's notes are its safety notice (ABOUT-FR-03).
    val notes = if (about.labNotice) stringResource(R.string.lab_safety_notice) else update.notes ?: return
    val version = update.notesVersion
    if (version != null && !about.labNotice) {
        Text(
            stringResource(R.string.update_notes_for, version),
            Modifier.padding(horizontal = 14.dp).testTag("settings-update-notes-title"),
            style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold),
            color = Sohva.palette.textPrimary,
        )
    }
    Text(notes, Modifier.padding(horizontal = 14.dp).testTag("settings-update-notes"), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
}

@Composable
private fun statusText(update: UpdateState): String {
    val version = update.update?.version.orEmpty()
    return when (update.phase) {
        UpdatePhase.DISABLED -> stringResource(R.string.update_disabled_development)
        UpdatePhase.IDLE -> stringResource(R.string.update_idle)
        UpdatePhase.CHECKING -> stringResource(R.string.update_checking)
        UpdatePhase.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
        UpdatePhase.AVAILABLE -> stringResource(R.string.update_available, version)
        UpdatePhase.DOWNLOADING -> stringResource(R.string.update_downloading, version, update.percent)
        UpdatePhase.DOWNLOADED -> stringResource(R.string.update_downloaded, version)
        UpdatePhase.NEEDS_PERMISSION -> stringResource(R.string.update_needs_permission)
        UpdatePhase.FAILED -> stringResource(
            when (update.failure) {
                UpdateFailure.NO_CHECKSUMS -> R.string.update_failed_no_checksums
                UpdateFailure.CHECKSUM_MISMATCH -> R.string.update_failed_checksum
                UpdateFailure.INSTALL_BLOCKED -> R.string.update_failed_install
                UpdateFailure.DOWNLOAD_FAILED -> R.string.update_failed_download
                UpdateFailure.NETWORK, null -> R.string.update_failed_network
            },
        )
    }
}

/** Diagnostics (ABOUT-FR-29, -30): the picker names the file by the local date and time. */
@Composable
private fun DiagnosticsGroup(about: AboutSettings) {
    val outcome by about.diagnostics.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { about.saveDiagnostics(it) }
    SettingsGroup {
        SettingsOverline(stringResource(R.string.diagnostics_title))
        SettingsRow(stringResource(R.string.diagnostics_save), icon = TvIcons.Save, subtitle = stringResource(R.string.diagnostics_help)) {
            TvActionButton(
                stringResource(R.string.diagnostics_save),
                {
                    val name = "sohva-tv-diagnostics-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.txt"
                    try {
                        save.launch(name)
                    } catch (_: ActivityNotFoundException) {
                        about.noPicker()
                    }
                },
                Modifier.testTag("settings-diagnostics-save"), TvIcons.Save, compact = true,
            )
        }
        val text = when (val o = outcome) {
            null -> null
            DiagnosticsOutcome.Saved -> stringResource(R.string.diagnostics_saved)
            DiagnosticsOutcome.OpenFailed -> stringResource(R.string.diagnostics_open_failed)
            DiagnosticsOutcome.NoPicker -> stringResource(R.string.diagnostics_no_picker)
            is DiagnosticsOutcome.Failed -> o.text ?: stringResource(R.string.error_unknown)
        }
        if (text != null) {
            Text(
                text,
                Modifier.padding(horizontal = 14.dp).testTag("settings-diagnostics-status"),
                style = Sohva.typography.caption,
                color = if (outcome == DiagnosticsOutcome.Saved) Sohva.palette.focus else Sohva.palette.danger,
            )
        }
    }
}
