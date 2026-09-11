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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.settings.SettingsOverline
import com.streammate.tv.feature.settings.SettingsRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** QR/account material is foreground-only remembered state, never a saved-state bundle. */
@Composable
internal fun AddonStremioImportScreen(
    copy: () -> Flow<StremioCopyEvent>, onReceived: (AddonCopyList) -> Unit,
    onBack: () -> Unit, modifier: Modifier,
) {
    val labels = addonStrings()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = remember { FocusRequester() }
    var link by remember { mutableStateOf<StremioImportLink?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val currentCopy by rememberUpdatedState(copy)
    val currentReceived by rememberUpdatedState(onReceived)
    fun cancel() { generation++; request?.cancel(); request = null; link = null; busy = false }
    DisposableEffect(context) {
        var current = context
        while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
        val window = (current as? android.app.Activity)?.window
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        val alreadySecure = window?.attributes?.flags?.and(flag) != 0
        window?.addFlags(flag)
        onDispose { if (!alreadySecure) window.clearFlags(flag) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cancel()
                message = labels(R.string.addon_ui_authorization_stopped_while_the_app_was_in_the_background_start_ag)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); cancel() }
    }
    LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }
    BackHandler { cancel(); onBack() }
    AddonSetupPage(labels(R.string.addon_ui_stremio_account), { cancel(); onBack() }, labels(R.string.addon_ui_back_to_import), focus,
        "addon-stremio-screen", "addon-stremio-back", modifier) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = link
            if (current == null) {
                SettingsOverline(labels(R.string.addon_ui_copy_your_addon_list))
                SettingsRow(labels(R.string.addon_ui_authorize_step), subtitle = labels(R.string.addon_ui_scan_the_qr_and_sign_in_on_stremio_s_own_website_sohva_never_asks), icon = TvIcons.Link)
                SettingsRow(labels(R.string.addon_ui_review_step), subtitle = labels(R.string.addon_ui_choose_which_new_addons_to_install_existing_addons_stay_unchanged), icon = TvIcons.Check)
            } else {
                val bitmap = remember(current) {
                    val matrix = QRCodeWriter().encode(current.authorizationUrl, BarcodeFormat.QR_CODE, 384, 384)
                    Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).apply {
                        for (y in 0 until 384) for (x in 0 until 384) setPixel(x, y,
                            if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap.asImageBitmap(), labels(R.string.addon_ui_scan_to_authorize_with_stremio), Modifier.size(200.dp).testTag("addon-stremio-qr"))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(labels(R.string.addon_ui_scan_with_your_phone), fontSize = StreamMateThemeTokens.typography.headline.fontSize)
                        Text(current.authorizationUrl, fontSize = StreamMateThemeTokens.typography.body.fontSize)
                        AddonSetupNote(labels(R.string.addon_ui_keep_this_screen_open_while_you_authorize_your_addon_list_will_app))
                        AddonSetupNote(labels(R.string.addon_ui_this_attempt_stops_after_10_minutes_on_back_or_when_sohva_leaves_t))
                    }
                }
            }
            AddonSetupNote(labels(R.string.addon_ui_authorization_grants_an_account_credential_not_addon_only_access_s), Modifier.padding(horizontal = 14.dp))
            AddonSetupNote(labels(R.string.addon_ui_your_stremio_addons_history_and_settings_are_not_changed_up_to_32), Modifier.padding(horizontal = 14.dp))
        }
        message?.let { AddonSetupNote(it, Modifier.padding(vertical = 10.dp).testTag("addon-stremio-message")) }
        Spacer(Modifier.height(12.dp))
        TvActionButton(if (busy) labels(R.string.addon_ui_waiting_for_authorization) else labels(R.string.addon_ui_start_stremio_authorization), {
            val attempt = ++generation
            busy = true; message = null; link = null
            request = scope.launch {
                try {
                    currentCopy().collect { event ->
                        if (attempt != generation) return@collect
                        when (event) {
                            is StremioCopyEvent.Waiting -> link = event.link
                            is StremioCopyEvent.Ready -> {
                                link = null
                                currentReceived(event.addons)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: AddonException) {
                    if (attempt != generation) return@launch
                    message = when (error.failure) {
                        AddonFailure.TIMEOUT -> labels(R.string.addon_ui_authorization_expired_or_timed_out_start_again_when_you_are_ready)
                        AddonFailure.RESPONSE_TOO_LARGE -> labels(R.string.addon_ui_the_account_list_exceeds_the_import_limit_use_a_smaller_url_list_i)
                        AddonFailure.ACCESS_DENIED -> labels(R.string.addon_ui_this_profile_can_no_longer_import_addons)
                        else -> labels(R.string.addon_ui_stremio_authorization_could_not_be_completed_try_again_or_use_a_ur)
                    }
                    link = null
                } catch (_: Exception) {
                    if (attempt != generation) return@launch
                    message = labels(R.string.addon_ui_stremio_authorization_could_not_be_completed_try_again_or_use_a_ur)
                    link = null
                } finally { if (attempt == generation) busy = false }
            }
        }, enabled = !busy, compact = true, icon = TvIcons.Link, testTag = "addon-stremio-start")
    }
}
