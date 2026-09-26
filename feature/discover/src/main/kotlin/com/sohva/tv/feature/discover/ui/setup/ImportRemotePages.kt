package com.sohva.tv.feature.discover.ui.setup

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sohva.tv.feature.discover.net.StremioProblem
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Phone setup for addons (spec 50 §4.7.1, §5.8): the warning, the QR at 220 dp, the address. */
@Composable
internal fun PhonePage(model: ImportModel, s: ImportState) {
    LaunchedEffect(Unit) { model.startPhone() }
    Column(Modifier.testTag("discover-import-phone-page"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Note(stringResource(R.string.addon_ui_paste_urls_or_choose_a_text_file_on_your_phone_over_trusted_wi_fi))
        when (val phone = s.phone) {
            is PhoneUi.Open -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                phone.qr?.let { QrCodeImage(it, stringResource(R.string.addon_ui_scan_to_send_private_addon_urls), Modifier.size(220.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.addon_ui_scan_with_your_phone), style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
                    Text(phone.url, Modifier.testTag("discover-import-phone-url"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                    Note(stringResource(R.string.addon_ui_select_a_utf_8_txt_list_on_your_phone_then_send_it_here_for_review))
                }
            }
            PhoneUi.NoNetwork -> Note(stringResource(R.string.addon_ui_no_available_local_connection_return_and_start_phone_setup_again))
            PhoneUi.Ended -> Note(stringResource(R.string.addon_ui_session_ended_return_to_start_a_new_one))
            PhoneUi.Off -> Note(stringResource(R.string.addon_loading))
        }
    }
}

/**
 * Stremio account copy (spec 50 §4.7.2, §5.8). Nothing starts until the button; the window is
 * FLAG_SECURE while this page is shown and restored after (FR-48).
 */
@Composable
internal fun StremioPage(model: ImportModel, s: ImportState) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = window != null && window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val start = remember { FocusRequester() }
    val stremio = s.stremio
    Column(Modifier.testTag("discover-import-stremio-page"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (stremio is StremioUi.Waiting) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                stremio.qr?.let { QrCodeImage(it, stringResource(R.string.addon_ui_scan_to_authorize_with_stremio), Modifier.size(200.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.addon_ui_scan_with_your_phone), style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
                    Text(stremio.link, style = Sohva.typography.body, color = Sohva.palette.textPrimary)
                    Note(stringResource(R.string.addon_ui_scan_the_qr_and_sign_in_on_stremio_s_own_website_sohva_never_asks))
                    Note(stringResource(R.string.addon_ui_keep_this_screen_open_while_you_authorize_your_addon_list_will_app))
                }
            }
        } else {
            SettingsOverline(stringResource(R.string.addon_ui_copy_your_addon_list))
            SettingsRow(stringResource(R.string.addon_ui_authorize_step))
            SettingsRow(stringResource(R.string.addon_ui_review_step))
        }
        Note(stringResource(R.string.addon_ui_authorization_grants_an_account_credential_not_addon_only_access_s))
        Note(stringResource(R.string.addon_ui_your_stremio_addons_history_and_settings_are_not_changed_up_to_32))
        failureText(stremio)?.let { Text(it, Modifier.testTag("discover-import-stremio-error"), style = Sohva.typography.label, color = Sohva.palette.danger) }
        val waiting = stremio is StremioUi.Waiting || stremio is StremioUi.Starting
        TvActionButton(
            stringResource(if (waiting) R.string.addon_ui_waiting_for_authorization else R.string.addon_ui_start_stremio_authorization),
            model::startStremio, Modifier.focusRequester(start).testTag("discover-import-stremio-start"), compact = true,
            state = SurfaceState(enabled = !waiting),
        )
    }
    LaunchedEffect(Unit) { start.requestFocusWhenAttached() }
}

@Composable
private fun failureText(stremio: StremioUi): String? = when (stremio) {
    StremioUi.Background -> stringResource(R.string.addon_ui_authorization_stopped_while_the_app_was_in_the_background_start_ag)
    is StremioUi.Failed -> stringResource(
        when (stremio.problem) {
            StremioProblem.TIMEOUT -> R.string.addon_ui_authorization_expired_or_timed_out_start_again_when_you_are_ready
            StremioProblem.TOO_MANY -> R.string.addon_ui_the_account_list_exceeds_the_import_limit_use_a_smaller_url_list_i
            StremioProblem.ACCESS_DENIED -> R.string.addon_ui_this_profile_can_no_longer_import_addons
            StremioProblem.OTHER -> R.string.addon_ui_stremio_authorization_could_not_be_completed_try_again_or_use_a_ur
        },
    )
    else -> null
}
