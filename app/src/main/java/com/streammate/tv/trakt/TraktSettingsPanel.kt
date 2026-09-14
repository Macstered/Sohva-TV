package com.streammate.tv.trakt

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sohva.tv.trakt.*
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.settings.SettingsOverline
import com.streammate.tv.feature.settings.SettingsRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Settings > Accounts > Trakt: connect, see who is connected, disconnect. */
@Composable
internal fun TraktSettingsPanel(service: TraktService, profileId: String, profileName: String, firstFocus: FocusRequester) = key(profileId) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val account by service.account(profileId).collectAsStateWithLifecycle(service.currentAccount(profileId))
    var pairing by remember { mutableStateOf<TraktPairingPrompt?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var request by remember { mutableStateOf<Job?>(null) }

    fun cancel() { request?.cancel(); request = null; pairing = null; busy = false }
    fun connect() {
        cancel()
        busy = true; message = null
        request = scope.launch {
            try { service.connect(profileId) { prompt -> pairing = prompt } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.messageResource() }
            finally { pairing = null; busy = false }
        }
    }
    fun disconnect() {
        cancel()
        busy = true; message = null
        request = scope.launch {
            try { service.disconnect(profileId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = R.string.trakt_connection_error }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && busy) { cancel(); message = R.string.trakt_cancelled_background }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); cancel() }
    }
    // First Back cancels pairing, second Back uses the normal settings navigation.
    BackHandler(enabled = busy) { cancel() }

    Column(Modifier.fillMaxWidth().testTag("trakt-settings"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsOverline(stringResource(R.string.trakt_title))
        SettingsRow(stringResource(R.string.trakt_profile, profileName), icon = TvIcons.Link,
            subtitle = when {
                account?.needsReauthorization == true -> stringResource(R.string.trakt_reauthorization)
                account != null -> stringResource(R.string.trakt_connected, account?.username.orEmpty())
                else -> stringResource(R.string.trakt_not_connected)
            })
        TvActionButton(
            label = stringResource(when {
                busy -> R.string.trakt_cancel
                account != null -> R.string.trakt_reconnect
                else -> R.string.trakt_connect
            }),
            onClick = { if (busy) cancel() else connect() },
            enabled = service.configured,
            focusRequester = firstFocus, compact = true, testTag = "trakt-primary",
        )
        if (!service.configured) Note(R.string.trakt_unconfigured)
        pairing?.let { prompt -> Pairing(prompt) }
        message?.let { Text(stringResource(it), Modifier.testTag("trakt-message"), color = StreamMateThemeTokens.palette.textMuted) }
        Note(R.string.trakt_help)
        if (account != null) {
            TvActionButton(stringResource(R.string.trakt_disconnect), ::disconnect, enabled = !busy, compact = true, danger = true, testTag = "trakt-disconnect")
            Note(R.string.trakt_disconnect_help)
        }
    }
}

@Composable private fun Note(resource: Int) {
    Text(stringResource(resource), fontSize = StreamMateThemeTokens.typography.body.fontSize, color = StreamMateThemeTokens.palette.textMuted)
}

@Composable private fun Pairing(prompt: TraktPairingPrompt) {
    val bitmap = remember(prompt) {
        val matrix = QRCodeWriter().encode(prompt.verificationUrl, BarcodeFormat.QR_CODE, 320, 320)
        Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 320) for (x in 0 until 320) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }.asImageBitmap()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(bitmap, stringResource(R.string.trakt_qr_description), Modifier.size(168.dp).testTag("trakt-qr"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trakt_waiting), fontSize = StreamMateThemeTokens.typography.headline.fontSize)
            Note(R.string.trakt_scan)
            Text(prompt.verificationUrl)
            Text(prompt.userCode, fontSize = StreamMateThemeTokens.typography.headline.fontSize)
        }
    }
}

private fun Exception.messageResource(): Int = when (this) {
    is TraktAuthorizationException -> when (reason) {
        TraktAuthorizationEnd.DENIED -> R.string.trakt_denied
        TraktAuthorizationEnd.EXPIRED -> R.string.trakt_expired
        else -> R.string.trakt_code_unusable
    }
    is TraktException -> when (failure) {
        TraktFailure.RATE_LIMITED -> R.string.trakt_rate_limited
        TraktFailure.CONFIGURATION -> R.string.trakt_unconfigured
        else -> R.string.trakt_connection_error
    }
    else -> R.string.trakt_connection_error
}
