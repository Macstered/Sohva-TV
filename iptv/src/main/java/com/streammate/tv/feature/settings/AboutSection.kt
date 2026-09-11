package com.streammate.tv.feature.settings

import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.iptv.R
import com.streammate.tv.feature.common.requestFocusWhenAttached
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import com.streammate.tv.core.error.StoredFailureMessage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons

/** The About page's update panel, the phone-setup panel and the import-error text. */

/**
 * Updates, in About. One line says where things stand, one button does the
 * next thing: check, download, install, or open the permission page Android
 * wants first. What changed shows for the newer beta once one is known, and
 * for the installed build otherwise, from its own published release.
 */
@Composable
internal fun AppUpdateSection(
    state: AppUpdateUiState,
    actions: AppUpdateActions,
    focusRequester: FocusRequester,
) {
    val palette = StreamMateThemeTokens.palette
    SettingsGroup {
        SettingsGroupHeading(stringResource(R.string.update_title))
        Text(
            text = stringResource(R.string.update_installed, state.installedVersionName),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        val statusText = when (state.phase) {
            AppUpdateUiState.Phase.DISABLED -> stringResource(R.string.update_disabled_development)
            AppUpdateUiState.Phase.IDLE -> stringResource(R.string.update_idle)
            AppUpdateUiState.Phase.CHECKING -> stringResource(R.string.update_checking)
            AppUpdateUiState.Phase.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
            AppUpdateUiState.Phase.AVAILABLE -> stringResource(R.string.update_available, state.versionName.orEmpty())
            AppUpdateUiState.Phase.DOWNLOADING ->
                stringResource(R.string.update_downloading, state.versionName.orEmpty(), state.percent ?: 0)
            AppUpdateUiState.Phase.DOWNLOADED -> stringResource(R.string.update_downloaded, state.versionName.orEmpty())
            AppUpdateUiState.Phase.NEEDS_PERMISSION -> stringResource(R.string.update_needs_permission)
            AppUpdateUiState.Phase.FAILED -> when (state.failure) {
                AppUpdateUiState.Failure.NO_CHECKSUMS -> stringResource(R.string.update_failed_no_checksums)
                AppUpdateUiState.Failure.CHECKSUM_MISMATCH -> stringResource(R.string.update_failed_checksum)
                AppUpdateUiState.Failure.INSTALL_BLOCKED -> stringResource(R.string.update_failed_install)
                AppUpdateUiState.Failure.NETWORK, null -> stringResource(R.string.update_failed_network)
            }
        }
        Text(
            text = statusText,
            color = if (state.phase == AppUpdateUiState.Phase.FAILED) palette.danger else palette.textPrimary,
            modifier = Modifier.testTag("settings-update-status"),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state.phase) {
                AppUpdateUiState.Phase.DISABLED -> Unit
                AppUpdateUiState.Phase.AVAILABLE -> TvActionButton(
                    label = stringResource(R.string.update_download),
                    icon = TvIcons.Save,
                    onClick = actions.onDownload,
                    focusRequester = focusRequester,
                    testTag = "settings-update-download",
                )
                AppUpdateUiState.Phase.DOWNLOADED -> TvActionButton(
                    label = stringResource(R.string.update_install),
                    icon = TvIcons.Play,
                    onClick = actions.onInstall,
                    focusRequester = focusRequester,
                    testTag = "settings-update-install",
                )
                AppUpdateUiState.Phase.NEEDS_PERMISSION -> {
                    TvActionButton(
                        label = stringResource(R.string.update_open_permission),
                        icon = TvIcons.Settings,
                        onClick = actions.onOpenInstallPermission,
                        focusRequester = focusRequester,
                        testTag = "settings-update-permission",
                    )
                    TvActionButton(
                        label = stringResource(R.string.update_install),
                        icon = TvIcons.Play,
                        onClick = actions.onInstall,
                        testTag = "settings-update-install",
                    )
                }
                AppUpdateUiState.Phase.DOWNLOADING -> TvActionButton(
                    label = stringResource(R.string.update_checking_button),
                    onClick = {},
                    enabled = false,
                    focusRequester = focusRequester,
                )
                else -> TvActionButton(
                    label = stringResource(R.string.update_check),
                    icon = TvIcons.Refresh,
                    onClick = actions.onCheck,
                    enabled = state.phase != AppUpdateUiState.Phase.CHECKING,
                    focusRequester = focusRequester,
                    testTag = "settings-update-check",
                )
            }
        }
        val notes = state.notes ?: state.installedNotes
        val notesVersion = if (state.notes != null) state.versionName.orEmpty() else state.installedVersionName
        notes?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_notes_for, notesVersion),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.testTag("settings-update-notes-title"),
            )
            Text(
                text = it.take(MAX_UPDATE_NOTES_LENGTH),
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.testTag("settings-update-notes"),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.update_help),
            color = palette.textMuted,
            fontSize = 12.sp,
        )
    }
}

internal const val MAX_UPDATE_NOTES_LENGTH = 4_000

/**
 * The QR code and address a phone opens, as a dialog over the screen that
 * asked for it. It used to sit inline in a scrolling column, where a 180 dp
 * square in a narrow pane was clipped at the bottom, or at the top once
 * focus had scrolled past it. A dialog is measured on its own and is never
 * clipped. Closing it, with the button or Back, closes the page on the TV.
 */
@Composable
internal fun PhoneSetupDialog(
    state: PhoneSetupUiState,
    title: String,
    onClose: () -> Unit,
    /** What arrived while the dialog was open, when there is something to say. */
    received: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(PHONE_DIALOG_WIDTH)
                .background(palette.panel, StreamMateThemeTokens.shapes.medium)
                .border(1.dp, palette.outline, StreamMateThemeTokens.shapes.medium)
                .padding(20.dp)
                .testTag("phone-setup-dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (state.noNetwork) {
                Text(
                    text = stringResource(R.string.phone_setup_no_network),
                    color = palette.danger,
                    modifier = Modifier.testTag("phone-setup-no-network"),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.qrCode?.let { code ->
                        Image(
                            bitmap = code,
                            contentDescription = stringResource(R.string.phone_setup_qr_description),
                            modifier = Modifier
                                .size(PHONE_QR_SIZE)
                                .background(Color.White)
                                .padding(8.dp)
                                .testTag("phone-setup-qr"),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.phone_setup_help), color = palette.textMuted, fontSize = 13.sp)
                        Text(
                            text = state.url.orEmpty(),
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("phone-setup-url"),
                        )
                        received?.let {
                            Text(text = it, color = palette.focus, fontSize = 13.sp, modifier = Modifier.testTag("phone-setup-received"))
                        }
                        Text(text = stringResource(R.string.phone_setup_privacy), color = palette.textMuted, fontSize = 12.sp)
                    }
                }
            }
            TvActionButton(
                label = stringResource(R.string.phone_setup_stop),
                onClick = onClose,
                focusRequester = closeFocus,
                testTag = "phone-setup-close",
            )
        }
    }
}

private val PHONE_DIALOG_WIDTH = 720.dp
private val PHONE_QR_SIZE = 280.dp

/** A refresh state's last error in words for this build; see [StoredFailureMessage]. */
internal fun readableImportError(resources: Resources, lastError: String?): String? =
    StoredFailureMessage.resolve(resources, lastError)
