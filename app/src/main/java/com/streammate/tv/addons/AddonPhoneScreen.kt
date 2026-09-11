package com.streammate.tv.addons

import com.streammate.tv.R
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sohva.tv.addons.AddonPhoneSession
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.requestFocusWhenAttached
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
internal fun AddonPhoneScreen(onReceived: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val labels = addonStrings()
    var session by remember { mutableStateOf<AddonPhoneSession?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // A session exists only while this explicitly opened screen is foreground.
    DisposableEffect(Unit) {
        val created = runCatching {
            val address = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.first { it is Inet4Address && it.isSiteLocalAddress }
            AddonPhoneSession(address)
        }.getOrNull()
        session = created; unavailable = created == null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { created?.close(); session = null; unavailable = true }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); created?.close() }
    }
    LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }
    BackHandler(onBack = onBack)
    AddonSetupPage(labels(R.string.addon_ui_phone_setup), onBack, labels(R.string.addon_ui_back_to_import), focus, "addon-phone-screen", "addon-phone-back", modifier) {
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AddonSetupNote(labels(R.string.addon_ui_use_the_same_trusted_wi_fi_as_this_tv_this_temporary_http_connecti))
        if (unavailable) AddonSetupNote(labels(R.string.addon_ui_no_available_local_connection_return_and_start_phone_setup_again))
        session?.let { current ->
            val input by current.submission.collectAsState()
            val running by current.running.collectAsState()
            LaunchedEffect(input) {
                if (input != null) current.takeSubmission()?.let { text -> current.close(); onReceived(text) }
            }
            if (running) {
                val bitmap = remember(current) {
                    val matrix = QRCodeWriter().encode(current.pairingUrl, BarcodeFormat.QR_CODE, 384, 384)
                    Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).apply {
                        for (y in 0 until 384) for (x in 0 until 384) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap.asImageBitmap(), labels(R.string.addon_ui_scan_to_send_private_addon_urls), Modifier.size(220.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(labels(R.string.addon_ui_scan_with_your_phone), fontSize = StreamMateThemeTokens.typography.headline.fontSize)
                        Text(current.pairingUrl, fontSize = StreamMateThemeTokens.typography.label.fontSize)
                        AddonSetupNote(labels(R.string.addon_ui_choose_a_utf_8_txt_file_on_your_phone_or_paste_configured_addon_ur))
                    }
                }
            } else if (input == null) AddonSetupNote(labels(R.string.addon_ui_session_ended_return_to_start_a_new_one))
        }
      }
    }
}
