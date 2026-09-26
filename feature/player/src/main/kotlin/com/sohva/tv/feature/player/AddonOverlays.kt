package com.sohva.tv.feature.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.drawFitted
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The addon loading screen (spec 50 FR-86, -87, §5.5): the backdrop, the pulsing logo or title,
 * the stage text, Cancel (focused) and Subtitles. Only Back and Cancel act; media keys are
 * swallowed so nothing starts or seeks under the loading screen.
 */
@Composable
internal fun AddonLoading(model: PlayerModel, session: AddonSession, stage: AddonStage, pickerOpen: Boolean) {
    val cancel = remember { FocusRequester() }
    val scrim = Sohva.palette.scrim
    Box(
        Modifier.fillMaxSize().background(scrim).onPreviewKeyEvent { swallowMedia(it.nativeKeyEvent) }.testTag("addon-loading"),
    ) {
        LoadingBackdrop(session.play.backdrop)
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val brush = Brush.verticalGradient(0f to scrim.copy(alpha = 0.15f), 0.5f to scrim.copy(alpha = 0.35f), 1f to scrim.copy(alpha = 0.88f))
                onDrawBehind { drawRect(brush) }
            },
        )
        Column(
            Modifier.fillMaxWidth(0.65f).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Pulsing(session.play)
            Text(stageText(stage), Modifier.testTag("addon-stage"), style = Sohva.typography.body.copy(textAlign = TextAlign.Center), color = Sohva.palette.onScrim)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvActionButton(stringResource(R.string.action_cancel), { model.currentKey()?.let(model.navigation::leave) }, Modifier.focusRequester(cancel).testTag("addon-cancel"), compact = true)
            TvActionButton(stringResource(R.string.player_quick_subtitles), { model.pauseAndPick(Picker.SUBTITLES) }, Modifier.testTag("addon-subtitles"), compact = true)
        }
    }
    // Cancel is focused at first and again when the picker closes over the loading screen.
    LaunchedEffect(pickerOpen) { if (!pickerOpen) cancel.requestFocusWhenAttached() }
}

private fun swallowMedia(event: KeyEvent): Boolean = when (event.keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
    -> true
    else -> false
}

@Composable
private fun stageText(stage: AddonStage): String = stringResource(
    when (stage) {
        AddonStage.PREPARING -> R.string.addon_ui_preparing_stream
        AddonStage.SUBTITLES -> R.string.addon_ui_fetching_subtitles
        AddonStage.STARTING -> R.string.addon_ui_starting_playback
    },
)

/** The logo (170 dp, fitted) or the title, pulsing 0.78 ↔ 1.0 over 1,400 ms (§5.5); only the layer's alpha changes. */
@Composable
private fun Pulsing(play: AddonPlay) {
    val pulse by Motion.loadingPulse()
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    var logo by remember(play.logo) { mutableStateOf<ImageBitmap?>(null) }
    var logoFailed by remember(play.logo) { mutableStateOf(play.logo == null) }
    LaunchedEffect(play.logo) {
        val url = play.logo ?: return@LaunchedEffect
        logo = loader.load(url, with(density) { 600.dp.roundToPx() }, with(density) { 170.dp.roundToPx() }, opaque = false)
        logoFailed = logo == null
    }
    Box(Modifier.fillMaxWidth().heightIn(80.dp, 170.dp).graphicsLayer { alpha = pulse }, contentAlignment = Alignment.Center) {
        val image = logo
        if (image != null) {
            Box(Modifier.fillMaxWidth().height(170.dp).drawBehind { drawFitted(image, size.width, size.height) })
        } else if (logoFailed) {
            Text(
                play.title, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.display.copy(fontWeight = FontWeight.Black, textAlign = TextAlign.Center), color = Sohva.palette.onScrim,
            )
        }
    }
}

/** Full screen, cropped, decoded at 960 × 540 at most and opaque (performance rule 5). */
@Composable
private fun LoadingBackdrop(url: String?) {
    val loader = LocalArtwork.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { image = url?.let { loader.load(it, 960, 540, opaque = true) } }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val bitmap = image ?: return@drawBehind
            val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
            val sw = (size.width / scale).toInt()
            val sh = (size.height / scale).toInt()
            drawImage(bitmap, srcOffset = IntOffset((bitmap.width - sw) / 2, (bitmap.height - sh) / 2), srcSize = IntSize(sw, sh), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        },
    )
}

/**
 * FR-91: "Playback stopped…" with Retry with fresh source (focused), and the progress warning
 * when a write failed. Nothing restarts until the viewer asks.
 */
@Composable
internal fun AddonStopBanner(session: AddonSession, stop: AddonStop, modifier: Modifier = Modifier) {
    val retry = remember { FocusRequester() }
    val saveFailed by session.saveFailed.collectAsStateWithLifecycle()
    val on = Sohva.palette.onDangerSurface
    Column(
        modifier.fillMaxWidth().background(Sohva.palette.dangerSurface.copy(alpha = 0.8f)).padding(horizontal = 20.dp, vertical = 12.dp).testTag("addon-stopped"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val line = when (stop) {
            AddonStop.FAILED -> R.string.addon_ui_playback_stopped_retry_or_choose_another_source
            AddonStop.BACKGROUND -> R.string.addon_ui_playback_stopped_while_the_app_was_in_the_background
        }
        Text(stringResource(line), style = Sohva.typography.body, color = on)
        if (saveFailed) Text(stringResource(R.string.addon_ui_watch_progress_could_not_be_saved), style = Sohva.typography.caption, color = on.copy(alpha = 0.72f))
        TvActionButton(stringResource(R.string.addon_ui_retry_with_fresh_source), session::retry, Modifier.padding(top = 8.dp).focusRequester(retry).testTag("addon-retry"))
    }
    LaunchedEffect(stop) { retry.requestFocusWhenAttached() }
}
