package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The phone setup dialog (spec 11 PHONE-FR-50): 720 dp, the QR on white whatever the theme, the
 * address, what was received, the privacy note, and "Close the phone page" with focus. Back and
 * Close stop the page; the caller returns focus to the button that opened it (PHONE-FR-02).
 */
@Composable
internal fun PhoneSetupDialog(phone: PhoneSetupState, qr: QrMatrix?, onClose: () -> Unit) {
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(720.dp).testTag("phone-setup-dialog"), border = Sohva.palette.outline, padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.phone_setup_title),
                        style = Sohva.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = Sohva.palette.textPrimary,
                    )
                    when (phone) {
                        is PhoneSetupState.Open -> OpenPage(phone, qr)
                        else -> Text(
                            stringResource(R.string.phone_setup_no_network),
                            Modifier.testTag("phone-setup-no-network"),
                            style = Sohva.typography.body,
                            color = Sohva.palette.danger,
                        )
                    }
                    TvActionButton(
                        stringResource(R.string.phone_setup_stop),
                        onClose,
                        Modifier.focusRequester(close).testTag("phone-setup-close"),
                        icon = TvIcons.Close,
                    )
                }
            }
        }
        LaunchedEffect(Unit) { close.requestFocusWhenAttached() }
    }
}

@Composable
private fun OpenPage(phone: PhoneSetupState.Open, qr: QrMatrix?) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        // The address shows at once; the code joins it when built (PHONE-FR-51), or never (PHONE-FR-52).
        if (qr != null) QrCodeImage(qr, stringResource(R.string.phone_setup_qr_description))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.phone_setup_help), style = Sohva.typography.label.copy(fontSize = 13.sp), color = Sohva.palette.textMuted)
            Text(
                phone.url,
                Modifier.testTag("phone-setup-url"),
                style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold),
                color = Sohva.palette.textPrimary,
            )
            val received = when {
                phone.received == 0 -> null
                phone.lastWasKeys -> stringResource(R.string.phone_setup_received_keys)
                else -> stringResource(R.string.phone_setup_received, phone.lastSource.orEmpty())
            }
            if (received != null) {
                Text(received, Modifier.testTag("phone-setup-received"), style = Sohva.typography.label.copy(fontSize = 13.sp), color = Sohva.palette.focus)
            }
            Text(stringResource(R.string.phone_setup_privacy), style = Sohva.typography.caption.copy(fontSize = 12.sp), color = Sohva.palette.textMuted)
        }
    }
}
