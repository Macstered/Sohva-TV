package com.sohva.tv.feature.trakt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Settings › Accounts (spec 51 §5.1): overline "Trakt", the profile row with the state, the
 * primary button (Settings focuses it when the section opens), the pairing block while signing in, the
 * message line, the help note, and Disconnect with its note when an account exists.
 */
@Composable
fun TraktAccountsPane(model: TraktPanelModel, profileName: String, start: FocusRequester) {
    val s by model.state.collectAsStateWithLifecycle()
    val account = s.account
    SettingsGroup {
        SettingsOverline(stringResource(R.string.trakt_title))
        val subtitle = when {
            account == null -> stringResource(R.string.trakt_not_connected)
            account.reauthorize -> stringResource(R.string.trakt_reauthorization)
            else -> stringResource(R.string.trakt_connected, account.username)
        }
        SettingsRow(stringResource(R.string.trakt_profile, profileName), Modifier.testTag("trakt-account"), icon = TvIcons.Link, subtitle = subtitle)
        val label = when {
            s.signingIn -> R.string.trakt_cancel
            account != null -> R.string.trakt_reconnect
            else -> R.string.trakt_connect
        }
        TvActionButton(
            stringResource(label), model::primary, Modifier.padding(horizontal = 14.dp).focusRequester(start).testTag("trakt-primary"),
            compact = true, state = SurfaceState(enabled = s.configured),
        )
        if (!s.configured) Note(stringResource(R.string.trakt_unconfigured), "trakt-unconfigured")
        s.prompt?.let { Pairing(it) }
        s.message?.let { Text(messageText(it), Modifier.padding(horizontal = 14.dp).testTag("trakt-message"), style = Sohva.typography.body, color = Sohva.palette.textMuted) }
        Note(stringResource(R.string.trakt_help), "trakt-help")
        if (account != null) {
            TvActionButton(
                stringResource(R.string.trakt_disconnect), model::disconnect, Modifier.padding(horizontal = 14.dp).testTag("trakt-disconnect"),
                compact = true, state = SurfaceState(danger = true),
            )
            Note(stringResource(R.string.trakt_disconnect_help), "trakt-disconnect-help")
        }
    }
    // While signing in the first Back cancels it; the next is ordinary Settings navigation (§3).
    BackHandler(enabled = s.signingIn) { model.cancel(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.cancel(TraktMessage.BACKGROUND) }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.cancel(null)
        }
    }
}

/** QR 168 dp beside the heading, the note, the address and the code (§5.1). */
@Composable
private fun Pairing(prompt: TraktPrompt) {
    Row(Modifier.padding(horizontal = 14.dp).testTag("trakt-pairing"), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        prompt.qr?.let { QrCodeImage(it, stringResource(R.string.trakt_qr_description), Modifier.size(168.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trakt_waiting), style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
            Text(stringResource(R.string.trakt_scan), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            Text(prompt.url, Modifier.testTag("trakt-url"), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
            Text(prompt.code, Modifier.testTag("trakt-code"), style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
        }
    }
}

@Composable
private fun Note(text: String, tag: String) {
    Text(text, Modifier.padding(horizontal = 14.dp).testTag(tag), style = Sohva.typography.body, color = Sohva.palette.textMuted)
}

@Composable
private fun messageText(message: TraktMessage): String = stringResource(
    when (message) {
        TraktMessage.DECLINED -> R.string.trakt_denied
        TraktMessage.EXPIRED -> R.string.trakt_expired
        TraktMessage.UNUSABLE -> R.string.trakt_code_unusable
        TraktMessage.RATE_LIMITED -> R.string.trakt_rate_limited
        TraktMessage.CONFIGURATION -> R.string.trakt_unconfigured
        TraktMessage.BACKGROUND -> R.string.trakt_cancelled_background
        TraktMessage.OTHER -> R.string.trakt_connection_error
    },
)
