package com.sohva.tv.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt` in local time (ABOUT-FR-29). */
private fun diagnosticsName(): String = "sohva-tv-diagnostics-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.txt"

/**
 * Diagnostics (ABOUT-FR-29, -30). A TV without a document picker (Google TV on the Chromecast)
 * saves straight to the Download folder instead, and any TV can hand the file to a phone through
 * the phone page (rebuild).
 */
@Composable
internal fun DiagnosticsGroup(about: AboutSettings) {
    val outcome by about.diagnostics.collectAsStateWithLifecycle()
    val phoneOpen by about.phoneOpen.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { about.saveDiagnostics(it) }
    val phone = remember { FocusRequester() }
    SettingsGroup {
        SettingsOverline(stringResource(R.string.diagnostics_title))
        SettingsRow(stringResource(R.string.diagnostics_save), icon = TvIcons.Save, subtitle = stringResource(R.string.diagnostics_help)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(
                    stringResource(R.string.diagnostics_save),
                    {
                        val name = diagnosticsName()
                        if (!about.filePickerAvailable()) {
                            about.noPicker(name)
                        } else {
                            try {
                                save.launch(name)
                            } catch (_: ActivityNotFoundException) {
                                about.noPicker(name)
                            }
                        }
                    },
                    Modifier.testTag("settings-diagnostics-save"), TvIcons.Save, compact = true,
                )
                TvActionButton(
                    stringResource(R.string.diagnostics_send_phone), { about.openPhone(diagnosticsName()) },
                    Modifier.focusRequester(phone).testTag("settings-diagnostics-phone"), TvIcons.Link, compact = true,
                )
            }
        }
        DiagnosticsStatus(outcome)
    }
    // Focus goes back to the button the dialog came from, once the dialog has gone (lessons 4.1).
    var returning by remember { mutableStateOf(false) }
    if (phoneOpen) {
        DiagnosticsPhoneDialog(about) {
            returning = true
            about.closePhone()
        }
    }
    LaunchedEffect(phoneOpen, returning) {
        if (phoneOpen || !returning) return@LaunchedEffect
        phone.requestFocusWhenAttached()
        returning = false
    }
}

@Composable
private fun DiagnosticsStatus(outcome: DiagnosticsOutcome?) {
    val text = when (outcome) {
        null -> return
        DiagnosticsOutcome.Saved -> stringResource(R.string.diagnostics_saved)
        is DiagnosticsOutcome.SavedToDownloads -> stringResource(R.string.diagnostics_saved_downloads, outcome.name)
        DiagnosticsOutcome.OpenFailed -> stringResource(R.string.diagnostics_open_failed)
        DiagnosticsOutcome.NoPicker -> stringResource(R.string.diagnostics_no_picker)
        is DiagnosticsOutcome.Failed -> outcome.text ?: stringResource(R.string.error_unknown)
    }
    val saved = outcome == DiagnosticsOutcome.Saved || outcome is DiagnosticsOutcome.SavedToDownloads
    Text(
        text,
        Modifier.padding(horizontal = 14.dp).testTag("settings-diagnostics-status"),
        style = Sohva.typography.caption,
        color = if (saved) Sohva.palette.focus else Sohva.palette.danger,
    )
}

/** The phone page for the diagnostics file: the QR on white, the address, and whether a phone has it. */
@Composable
private fun DiagnosticsPhoneDialog(about: AboutSettings, onClose: () -> Unit) {
    val phone by about.diagnosticsPhone.collectAsStateWithLifecycle(DiagnosticsPhone.Closed)
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(720.dp).testTag("settings-diagnostics-phone-dialog"), border = Sohva.palette.outline, padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.diagnostics_phone_title), style = Sohva.typography.title.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                    when (val p = phone) {
                        is DiagnosticsPhone.Open -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            p.qr?.let { QrCodeImage(it, stringResource(R.string.phone_setup_qr_description), Modifier.size(200.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.diagnostics_phone_help), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                                Text(p.url, Modifier.testTag("settings-diagnostics-phone-url"), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                                if (p.sent) {
                                    Text(stringResource(R.string.diagnostics_phone_sent), Modifier.testTag("settings-diagnostics-phone-sent"), style = Sohva.typography.body, color = Sohva.palette.focus)
                                }
                            }
                        }
                        DiagnosticsPhone.NoNetwork -> Text(stringResource(R.string.phone_setup_no_network), style = Sohva.typography.body, color = Sohva.palette.danger)
                        DiagnosticsPhone.Closed -> Unit
                    }
                    TvActionButton(stringResource(R.string.phone_setup_stop), onClose, Modifier.focusRequester(close).testTag("settings-diagnostics-phone-close"), icon = TvIcons.Close)
                }
            }
        }
        LaunchedEffect(Unit) { close.requestFocusWhenAttached() }
    }
}
