package com.sohva.tv.feature.player

import com.sohva.tv.ui.design.window.LocalPictureInPicture
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import com.sohva.tv.core.model.player.PictureShape
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.BufferingIndicator
import com.sohva.tv.ui.design.components.FullScreenMessage
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached

/**
 * The player (spec 30 §5): a black ground, the video, and overlays composed only while shown
 * (§9 "nothing composed while hidden"). The key host is a sibling of the overlays, not their
 * parent, so a focused button receives its own keys.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(model: PlayerModel) {
    val connection by model.connection.collectAsStateWithLifecycle()
    KeepScreenOn()
    PlayerLifecycle(model)
    DisplayRateMatch(model)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("screen-player")) {
        when (connection) {
            Connection.CONNECTING -> FullScreenMessage(stringResource(R.string.player_connecting))
            Connection.FAILED -> FullScreenMessage(stringResource(R.string.player_service_disconnected))
            Connection.READY -> PlayerContent(model)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerContent(model: PlayerModel) {
    val host = remember { FocusRequester() }
    val box by model.boxVisible.collectAsStateWithLifecycle()
    val listOpen by model.channels.open.collectAsStateWithLifecycle()
    val picker by model.picker.collectAsStateWithLifecycle()
    val quick by model.quickActions.collectAsStateWithLifecycle()
    val stats by model.statsOn.collectAsStateWithLifecycle()
    val buffering by model.buffering.collectAsStateWithLifecycle()
    val banner by model.banner.collectAsStateWithLifecycle()
    val dial by model.dial.state.collectAsStateWithLifecycle()
    val controls by model.transport.visible.collectAsStateWithLifecycle()
    val skipped by model.transport.feedback.collectAsStateWithLifecycle()
    VideoSurface(model)
    // In the corner only the picture is drawn; the overlays' state stays in the model (PLAY-FR-111).
    if (LocalPictureInPicture.current) return
    model.addon?.let { session ->
        if (addonStartUp(model, session, picker)) return
    }
    // The clean screen's key handler (PLAY-FR-30).
    Box(
        Modifier.fillMaxSize().focusRequester(host).onKeyEvent { model.keys.onKey(it.nativeKeyEvent) }.focusable().testTag("player-video"),
    )
    if (box || listOpen || controls) ChromeScrim()
    if (stats) InfoLine(model)
    DialReadout(dial, stats)
    if (box) LiveInfoBox(model, Modifier.align(Alignment.BottomStart))
    // Under a picker or the quick actions the controls stay drawn but never take focus.
    if (controls) TransportControls(model, Modifier.align(Alignment.BottomStart).refuseFocus(picker != null || quick))
    skipped?.let { SkipFeedbackLabel(it, Modifier.align(Alignment.BottomCenter)) }
    if (buffering) BufferingIndicator(Modifier.align(Alignment.Center))
    if (listOpen) ChannelListPanel(model, Modifier.align(Alignment.CenterEnd))
    picker?.let { Pickers(model, it) }
    model.ticker?.let { ScoreTicker(it, stats, Modifier.align(Alignment.TopEnd)) }
    if (quick) QuickActions(model)
    banner?.let { ErrorBanner(model, it, Modifier.align(Alignment.BottomCenter)) }
    val addonStop = model.addon?.stop?.collectAsStateWithLifecycle()?.value
    model.addon?.let { session -> addonStop?.let { AddonStopBanner(session, it, Modifier.align(Alignment.BottomCenter)) } }
    // Back peels one layer per press (spec 30 §3.2); the channel list takes its own keys.
    BackHandler(enabled = !listOpen) {
        when {
            picker != null -> model.closePicker()
            quick -> model.closeQuickActions()
            box -> model.hideBox()
            controls -> model.transport.hide()
            else -> model.currentKey()?.let { model.navigation.leave(it) }
        }
    }
    // Closing any overlay returns focus to the video (PLAY-FR-09).
    // The transport controls open unfocused (PLAY-FR-08); when they go, so does any focus in them.
    LaunchedEffect(box, controls, picker, quick, banner?.stopped, addonStop) {
        if (!box && !controls && picker == null && !quick && banner?.stopped != true && addonStop == null) host.requestFocusWhenAttached()
    }
}

/**
 * The addon loading screen during start-up (spec 50 §4.12), with only Back and Cancel acting.
 * True while it is shown: then nothing else of the player is composed.
 */
@Composable
private fun addonStartUp(model: PlayerModel, session: AddonSession, picker: Picker?): Boolean {
    val stage by session.stage.collectAsStateWithLifecycle()
    val current = stage
    if (current != null) {
        AddonLoading(model, session, current, picker != null)
        picker?.let { Pickers(model, it) }
        BackHandler { if (picker != null) model.closePicker() else model.currentKey()?.let { model.navigation.leave(it) } }
        return true
    }
    return false
}

/** An addon playback has its own subtitle picker (spec 50 FR-97); everything else the shared one. */
@Composable
private fun Pickers(model: PlayerModel, which: Picker) {
    val addon = model.addon
    if (addon != null && which == Picker.SUBTITLES) AddonSubtitleLayers(model, addon.subtitles) else TrackPicker(model, which)
}

/** A SurfaceView in an aspect frame with a subtitle view (spec 30 §9 "Surface"): no TextureView, no layers. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(model: PlayerModel) {
    val shape by model.shape.collectAsStateWithLifecycle()
    val size by model.videoSize.collectAsStateWithLifecycle()
    val controller = model.controller
    val holder = remember { Views() }
    AndroidView(
        factory = { context ->
            val frame = AspectRatioFrameLayout(context)
            val surface = SurfaceView(context)
            val subtitles = SubtitleView(context)
            frame.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            frame.addView(subtitles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            holder.subtitles = subtitles
            SubtitleLook.apply(subtitles, model.settings)
            controller?.setVideoSurfaceView(surface)
            frame
        },
        modifier = Modifier.fillMaxSize(),
        update = { frame ->
            frame.resizeMode = when (shape) {
                PictureShape.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PictureShape.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                PictureShape.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            if (size.width > 0 && size.height > 0) frame.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height)
        },
    )
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                holder.subtitles?.setCues(cueGroup.cues)
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
}

/** The subtitle view the factory made, for the cue listener. */
@OptIn(UnstableApi::class)
private class Views {
    var subtitles: SubtitleView? = null
}

/** The screen never sleeps while the player is open; the previous value comes back (PLAY-FR-24). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val before = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
}

/** Leaving the app stops the stream; returning resumes it (PLAY-FR-20). */
@Composable
private fun PlayerLifecycle(model: PlayerModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> model.onStop()
                Lifecycle.Event.ON_START -> model.onStart()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

