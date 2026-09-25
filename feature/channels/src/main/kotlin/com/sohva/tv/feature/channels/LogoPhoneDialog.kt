package com.sohva.tv.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** "Logo from phone" (CHAN-FR-40): opens the phone page in logo mode for the selected channel. */
@Composable
internal fun LogoFromPhone(model: ChannelsModel) {
    val logoFor by model.logoFor.collectAsStateWithLifecycle()
    val button = remember { FocusRequester() }
    TvActionButton(
        stringResource(R.string.channels_logo_from_phone),
        model::openLogoPhone,
        Modifier.focusRequester(button).testTag("channels-logo-phone"),
        icon = TvIcons.Link,
    )
    logoFor?.let { row -> LogoPhoneDialog(model, row.channel.name, onClose = model::closeLogoPhone) }
    // Focus comes back to the button when the dialog goes (PHONE-FR-02).
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(logoFor) {
        if (logoFor == null && wasOpen) button.requestFocusWhenAttached()
        wasOpen = logoFor != null
    }
}

/**
 * The phone dialog in logo mode (spec 21 §4.4, spec 11 PHONE-FR-50): "Logo for …", the QR on
 * white, the address, or the no-network sentence; Close and Back stop the page (CHAN-NFR-10).
 */
@Composable
private fun LogoPhoneDialog(model: ChannelsModel, channelName: String, onClose: () -> Unit) {
    val phone by model.phone.collectAsStateWithLifecycle()
    val qr by model.qr.collectAsStateWithLifecycle()
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(720.dp).testTag("channels-logo-dialog"), border = Sohva.palette.outline, padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.phone_setup_page_logo_title, channelName),
                        style = Sohva.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = Sohva.palette.textPrimary,
                    )
                    when (val p = phone) {
                        is PhoneSetupState.Open -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            qr?.let { QrCodeImage(it, stringResource(R.string.phone_setup_qr_description)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.phone_setup_page_logo_help), style = Sohva.typography.label.copy(fontSize = 13.sp), color = Sohva.palette.textMuted)
                                Text(p.url, Modifier.testTag("channels-logo-url"), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                            }
                        }
                        PhoneSetupState.NoNetwork -> Text(stringResource(R.string.phone_setup_no_network), style = Sohva.typography.body, color = Sohva.palette.danger)
                        PhoneSetupState.Closed -> Unit
                    }
                    TvActionButton(stringResource(R.string.phone_setup_stop), onClose, Modifier.focusRequester(close).testTag("channels-logo-close"), icon = TvIcons.Close)
                }
            }
        }
        LaunchedEffect(Unit) { close.requestFocusWhenAttached() }
    }
}
