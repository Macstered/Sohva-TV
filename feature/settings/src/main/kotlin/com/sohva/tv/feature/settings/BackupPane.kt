package com.sohva.tv.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.backup.BackupCipher
import com.sohva.tv.core.model.backup.BackupProblem
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

private val PASSWORD_FIELD = FieldInput(keyboard = KeyboardType.Password, transformation = PasswordVisualTransformation(), compact = true)

/** Beta 23's document type and name (BACKUP-FR-06, -17); removable media often says octet-stream. */
private const val MIME = "application/vnd.streammate.backup"
private const val FILE_NAME = "sohva-tv-backup.smbak"
private val OPEN_TYPES = arrayOf(MIME, "application/octet-stream")

/**
 * Settings › Backup & tools (spec 71 §5): the encrypted backup with its own status line, then
 * Maintenance's Clear all guide data. The password field takes focus when the section opens.
 */
@Composable
internal fun BackupPane(backup: BackupSettings, start: FocusRequester) {
    val state by backup.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { backup.save(it) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { backup.open(it) }
    val restoreButton = remember { FocusRequester() }
    // Some TV builds have no DocumentsUI (spec 71 §8).
    fun launch(block: () -> Unit) = try {
        block()
    } catch (_: ActivityNotFoundException) {
        backup.noPicker()
    }
    SettingsGroup {
        Text(stringResource(R.string.backup_title), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
        Text(stringResource(R.string.backup_description), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvUrlField(
                state.password, backup::type, stringResource(R.string.backup_passphrase),
                Modifier.weight(1f).focusRequester(start).testTag("settings-backup-passphrase"), TvIcons.Info, PASSWORD_FIELD,
                SurfaceState(enabled = state.busy == null, keepsFocus = true),
            )
            val acts = SurfaceState(enabled = state.canAct, keepsFocus = true)
            TvActionButton(stringResource(R.string.backup_save), { launch { save.launch(FILE_NAME) } }, Modifier.testTag("settings-backup-export"), state = acts, compact = true)
            TvActionButton(
                stringResource(R.string.backup_restore), { launch { open.launch(OPEN_TYPES) } },
                Modifier.focusRequester(restoreButton).testTag("settings-backup-restore"), state = acts, compact = true,
            )
        }
        Text(stringResource(R.string.backup_warning), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
        BackupStatusLine(state)
    }
    MaintenanceGroup(backup, state)
    val removes = state.removes
    if (removes != null) RestoreConfirm(backup, removes, restoreButton)
}

@Composable
private fun BackupStatusLine(state: BackupState) {
    val (text, failed) = when (val status = state.status) {
        null -> when (state.busy) {
            BackupBusy.SAVING -> stringResource(R.string.backup_status_saving) to false
            BackupBusy.RESTORING -> if (state.removes == null) stringResource(R.string.backup_status_restoring) to false else return
            null -> return
        }
        BackupStatus.Unfinished -> stringResource(R.string.backup_restore_incomplete) to true
        BackupStatus.NoPicker -> stringResource(R.string.backup_no_picker) to true
        is BackupStatus.Done -> when (val outcome = status.outcome) {
            BackupOutcome.Saved -> stringResource(R.string.settings_backup_saved) to false
            BackupOutcome.Restored -> stringResource(R.string.settings_backup_restored) to false
            is BackupOutcome.Opened -> return
            is BackupOutcome.Failed -> {
                val reason = problemText(outcome.problem, outcome.field)
                (if (outcome.fileKept) stringResource(R.string.backup_partial_file_kept, reason) else reason) to true
            }
        }
    }
    Text(
        text,
        Modifier.testTag("settings-backup-status"),
        style = Sohva.typography.caption,
        color = if (failed) Sohva.palette.danger else Sohva.palette.focus,
    )
}

/** Maintenance (SET-FR-96): Clear all guide data, at once, with its status. */
@Composable
private fun MaintenanceGroup(backup: BackupSettings, state: BackupState) {
    SettingsGroup {
        SettingsOverline(stringResource(R.string.maintenance_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_clear_all_help), Modifier.weight(1f), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            TvActionButton(
                stringResource(R.string.guide_clear_all), backup::clearGuide, Modifier.testTag("settings-clear-guide"), TvIcons.Delete,
                SurfaceState(enabled = !state.clearingGuide, danger = true, keepsFocus = true), compact = true,
            )
        }
        if (state.guideCleared) {
            Text(
                stringResource(R.string.guide_cache_cleared),
                Modifier.testTag("settings-maintenance-status"),
                style = Sohva.typography.caption,
                color = Sohva.palette.focus,
            )
        }
    }
}

/** Asks before a restore removes sources, naming them (spec 71 §8); focus goes back to Restore first. */
@Composable
private fun RestoreConfirm(backup: BackupSettings, removes: List<String>, restoreButton: FocusRequester) {
    val cancel = remember { FocusRequester() }
    fun close(then: () -> Unit) {
        restoreButton.requestFocus()
        then()
    }
    Dialog(onDismissRequest = { close(backup::cancelRestore) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(560.dp).testTag("settings-backup-confirm"), border = Sohva.palette.outline) {
                DialogTitle(stringResource(R.string.backup_restore))
                Text(stringResource(R.string.backup_restore_removes, removes.joinToString(", ")), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(stringResource(R.string.action_cancel), { close(backup::cancelRestore) }, Modifier.focusRequester(cancel).testTag("settings-backup-confirm-cancel"))
                    TvActionButton(
                        stringResource(R.string.action_confirm), { close(backup::confirmRestore) },
                        Modifier.testTag("settings-backup-confirm-yes"), state = SurfaceState(danger = true),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocusWhenAttached() }
}

@Composable
private fun problemText(problem: BackupProblem?, field: String?): String = when (problem) {
    null -> stringResource(R.string.error_unknown)
    BackupProblem.OPEN -> stringResource(R.string.backup_error_open)
    BackupProblem.TOO_LARGE -> stringResource(R.string.backup_error_too_large)
    BackupProblem.PASSWORD_TOO_SHORT -> stringResource(R.string.error_backup_passphrase_too_short, BackupCipher.MIN_PASSWORD)
    BackupProblem.NOT_SOHVA -> stringResource(R.string.error_backup_not_streammate)
    BackupProblem.ENVELOPE_VERSION -> stringResource(R.string.error_backup_version_unsupported)
    BackupProblem.KEY_FORMAT -> stringResource(R.string.error_backup_key_format)
    BackupProblem.STRUCTURE -> stringResource(R.string.error_backup_structure)
    BackupProblem.TRAILING_DATA -> stringResource(R.string.error_backup_trailing_data)
    BackupProblem.WRONG_PASSWORD -> stringResource(R.string.error_backup_wrong_passphrase)
    BackupProblem.FORMAT_VERSION -> stringResource(R.string.backup_error_version)
    BackupProblem.PIN -> stringResource(R.string.backup_error_pin)
    BackupProblem.TOO_MANY_PREFERENCES -> stringResource(R.string.backup_error_too_many_preferences)
    BackupProblem.TOO_MANY_LISTS -> stringResource(R.string.backup_error_too_many_lists)
    BackupProblem.TOO_MANY_MEMBERS -> stringResource(R.string.backup_error_too_many_members)
    BackupProblem.DUPLICATE_PREFERENCE -> stringResource(R.string.backup_error_duplicate_preference)
    BackupProblem.MISSING_SOURCE -> stringResource(R.string.backup_error_missing_source)
    BackupProblem.DUPLICATE_LIST -> stringResource(R.string.backup_error_duplicate_list)
    BackupProblem.MISSING_LIST -> stringResource(R.string.backup_error_missing_list)
    BackupProblem.DUPLICATE_MEMBER -> stringResource(R.string.backup_error_duplicate_member)
    BackupProblem.STARTUP -> stringResource(R.string.backup_error_startup)
    BackupProblem.REMOTE -> stringResource(R.string.backup_error_remote)
    BackupProblem.MISSING_FIELD -> stringResource(R.string.backup_error_missing_field, field.orEmpty())
    BackupProblem.BLANK_FIELD -> stringResource(R.string.backup_error_blank_field, field.orEmpty())
    BackupProblem.LONG_FIELD -> stringResource(R.string.backup_error_long_field, field.orEmpty())
}
