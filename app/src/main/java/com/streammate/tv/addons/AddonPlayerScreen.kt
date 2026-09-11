package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.player.SubtitleAppearance
import com.streammate.tv.feature.player.applySubtitleAppearance
import com.streammate.tv.feature.player.BottomTransportControls
import com.streammate.tv.feature.player.LocalPlayerSeekStep
import com.streammate.tv.feature.player.SeekStepper
import com.streammate.tv.feature.player.TrackSelectionOverlay
import com.streammate.tv.feature.player.PlayerTrackChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
@OptIn(UnstableApi::class)
internal fun AddonPlayerScreen(host: AddonHost, profileId: String, identity: AddonWatchIdentity, title: String,
    selection: AddonPlaybackSelection, resume: Boolean, onBack: () -> Unit, modifier: Modifier, artwork: AddonWatchArtwork? = null, startupLogo: String? = null,
    subtitleStartupTimeoutMillis: Long = 20_000) {
    val labels = addonStrings()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = remember { AddonPlayback(context, host, profileId, identity, title, selection, artwork) }
    val player = playback.player
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var busy by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var loadingStage by remember { mutableStateOf(labels(R.string.addon_ui_preparing_stream)) }
    var subtitles by remember { mutableStateOf(false) }
    var subtitleSync by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var playAfterPicker by remember { mutableStateOf(false) }
    var backgroundStopped by remember { mutableStateOf(false) }
    var accessStopped by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsFocused by remember { mutableStateOf(false) }
    var chromeVersion by remember { mutableIntStateOf(0) }
    var focusVersion by remember { mutableIntStateOf(0) }
    var dismissVersion by remember { mutableIntStateOf(0) }
    var restorePicker by remember { mutableStateOf<FocusRequester?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var aspect by remember { mutableIntStateOf(0) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences by host.preferences.collectAsStateWithLifecycle(initialValue = null)
    val appearance = preferences?.let { SubtitleAppearance(it.subtitleTextSize, it.subtitleTextColor, it.subtitleBackground) } ?: SubtitleAppearance.TV
    val step = preferences?.playbackSeekStep?.millis ?: 10_000L
    val stepper = remember { SeekStepper() }
    fun seek(direction: Int) {
        val distance = stepper.step(step, direction, android.os.SystemClock.elapsedRealtime())
        player.seekTo((player.currentPosition + direction * distance).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        chromeVersion++
    }
    LaunchedEffect(attempt, backgroundStopped, accessStopped) {
        if (backgroundStopped || accessStopped) return@LaunchedEffect
        busy = true; failure = null; loadingStage = labels(R.string.addon_ui_preparing_stream)
        try {
            if (attempt == 0) playback.start(resume, playWhenReady = false) else playback.retry(playWhenReady = false)
            val streamReady = withTimeoutOrNull(45_000) { snapshotFlow { playback.ready || playback.failed }.first { it } }
            if (streamReady == null) throw AddonException(AddonFailure.TIMEOUT)
            if (playback.failed) throw AddonException(AddonFailure.NETWORK)
            loadingStage = labels(R.string.addon_ui_fetching_subtitles)
            val subtitlesDone = withTimeoutOrNull(subtitleStartupTimeoutMillis) {
                try { playback.automaticSubtitles() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { playback.useEmbeddedSubtitleFallback() }
                true
            }
            if (subtitlesDone == null) playback.useEmbeddedSubtitleFallback()
            loadingStage = labels(R.string.addon_ui_starting_playback)
            val frameReady = withTimeoutOrNull(20_000) { snapshotFlow {
                playback.failed || (playback.ready && (playback.firstFrameReady || player.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }))
            }.first { it } }
            if (frameReady == null) throw AddonException(AddonFailure.TIMEOUT)
            if (playback.failed) throw AddonException(AddonFailure.NETWORK)
            playback.checkAccess()
            if (subtitles || audio) playAfterPicker = true else if (!backgroundStopped) player.play()
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AddonException) { failure = error.failure }
        catch (_: Exception) { failure = AddonFailure.NETWORK }
        finally { busy = false }
    }
    LaunchedEffect(attempt, backgroundStopped) {
        if (backgroundStopped) return@LaunchedEffect
        while (true) {
            delay(5_000)
            try { playback.checkAccess() }
            catch (error: AddonException) { playback.stopForBackground(); failure = error.failure; accessStopped = true; break }
            playback.snapshot()
        }
    }
    LaunchedEffect(Unit) {
        while (true) { position = player.currentPosition.coerceAtLeast(0); duration = player.duration.coerceAtLeast(0); delay(500) }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { playing = player.isPlaying; tracks = player.currentTracks }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { backgroundStopped = true; playback.stopForBackground() }
            if (event == Lifecycle.Event.ON_START) playback.onForeground()
        }
        lifecycle.addObserver(observer)
        onDispose { player.removeListener(listener); lifecycle.removeObserver(observer); playback.release() }
    }
    fun closePicker() {
        restorePicker = if (subtitles || subtitleSync) subtitleFocus else audioFocus
        subtitles = false; subtitleSync = false; audio = false; chromeVersion++
        if (playAfterPicker && !backgroundStopped && !accessStopped) player.play()
    }
    fun dismissControls() {
        restorePicker = null
        controlsVisible = false; controlsFocused = false
        dismissVersion++
        playerView?.requestFocus()
    }
    LaunchedEffect(subtitles, subtitleSync, audio, restorePicker) {
        if (!subtitles && !subtitleSync && !audio && restorePicker?.requestFocusWhenAttached() == true) restorePicker = null
    }
    LaunchedEffect(playback.failed, failure, backgroundStopped) { if (playback.failed || failure != null || backgroundStopped) chromeVersion++ }
    BackHandler {
        if (subtitleSync) { subtitleSync = false; subtitles = true; player.pause() }
        else if (subtitles || audio) closePicker()
        else if (busy) onBack()
        else if (controlsVisible || controlsFocused) dismissControls()
        else onBack()
    }
    val audioOptions = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
    fun audioLabel(option: Pair<androidx.media3.common.Tracks.Group, Int>): String {
        val format = option.first.getTrackFormat(option.second)
        val language = format.language?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(labels.locale) }?.takeIf { it.isNotBlank() }
        return listOfNotNull(language ?: format.label ?: labels(R.string.addon_ui_audio_number, option.second + 1), format.channelCount.takeIf { it > 0 }?.let { labels(R.string.addon_ui_channels, it) }).joinToString(" · ")
    }
    val audioLabel = audioOptions.firstOrNull { it.first.isTrackSelected(it.second) }?.let(::audioLabel) ?: labels(R.string.addon_ui_auto)
    fun handleKey(event: android.view.KeyEvent): Boolean {
        if (subtitles || subtitleSync || audio) return false
        if (busy) return event.keyCode in listOf(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND, android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        val code = event.keyCode
        val down = event.action == android.view.KeyEvent.ACTION_DOWN
        // A restored picker button must not hold controls forever, but real
        // remote interaction restarts the idle timeout before focus moves.
        if (controlsFocused && down && code in listOf(android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) chromeVersion++
        return when {
            code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (down) { if (player.isPlaying) player.pause() else player.play(); chromeVersion++ }; true }
            code == android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) seek(-1); true }
            code == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) seek(1); true }
            !controlsFocused && code in listOf(android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT) -> {
                if (down) seek(if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); true
            }
            !controlsFocused && code in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN) -> {
                if (down) { chromeVersion++; focusVersion++ }; true
            }
            else -> false
        }
    }
    val currentKeyHandler by rememberUpdatedState(::handleKey)
    Box(modifier.fillMaxSize().background(Color.Black).testTag("addon-player").onPreviewKeyEvent { handleKey(it.nativeKeyEvent) }) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply {
            this.player = player; keepScreenOn = true; useController = false
            // AndroidView owns focus while chrome is hidden. Its remote events do
            // not always traverse Compose's preview handler, so share one handler.
            isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, _, event -> currentKeyHandler(event) }
            applySubtitleAppearance(this, appearance)
            playerView = this
            post { requestFocus() }
        } }, update = { applySubtitleAppearance(it, appearance); it.resizeMode = when (aspect) { 1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            modifier = Modifier.fillMaxSize(), onRelease = { it.player = null; it.keepScreenOn = false; playerView = null })
        if (!busy && playback.ready && failure == null) Box(Modifier.size(1.dp).testTag("addon-player-ready"))
        if (!busy && !subtitleSync && (controlsVisible || failure != null || playback.failed || backgroundStopped)) Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .95f)))))
        // Keep controls composed through both subtitle dialogs. Recreating them
        // replays old dismiss/focus requests and desynchronizes Back visibility.
        if (!busy) Column(Modifier.alpha(if (subtitles || subtitleSync) 0f else 1f).align(Alignment.BottomStart).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playback.failed || backgroundStopped || failure != null) {
                Text(if (backgroundStopped) labels(R.string.addon_ui_playback_stopped_while_the_app_was_in_the_background) else labels(R.string.addon_ui_playback_stopped_retry_or_choose_another_source), color = Color.White)
                TvActionButton(labels(R.string.addon_ui_retry_with_fresh_source), {
                    playback.onForeground(); backgroundStopped = false; accessStopped = false; attempt++
                }, enabled = !busy, compact = true, testTag = "addon-player-retry")
            }
            if (playback.progressFailure) Text(labels(R.string.addon_ui_watch_progress_could_not_be_saved), color = Color.White)
            CompositionLocalProvider(LocalPlayerSeekStep provides step) {
                BottomTransportControls(title, playing, position, duration, chromeVersion, focusVersion,
                    holdVisible = subtitles || subtitleSync || audio || busy || !playing || backgroundStopped || playback.failed,
                    aspectModeLabel = listOf(labels(com.streammate.tv.iptv.R.string.player_resize_fit), labels(com.streammate.tv.iptv.R.string.player_resize_zoom), labels(com.streammate.tv.iptv.R.string.player_resize_fill))[aspect], audioTrackLabel = audioLabel, subtitleTrackLabel = playback.selectedSubtitle ?: labels(com.streammate.tv.iptv.R.string.player_subtitles_off),
                    onBack = ::dismissControls, onCycleAspectMode = { aspect = (aspect + 1) % 3; chromeVersion++ },
                    onCycleAudioTrack = { playAfterPicker = player.playWhenReady; player.pause(); audio = true },
                    onCycleSubtitleTrack = { playAfterPicker = player.playWhenReady; player.pause(); subtitles = true },
                    onRewind = { seek(-1) }, onPlayPause = { if (player.isPlaying) player.pause() else player.play(); chromeVersion++ }, onForward = { seek(1) },
                    onControlsFocusChanged = { controlsFocused = it },
                    onDismissed = { restorePicker = null; controlsFocused = false; playerView?.requestFocus() }, dismissRequest = dismissVersion,
                    onVisibilityChanged = { controlsVisible = it }, audioFocusRequester = audioFocus, subtitleFocusRequester = subtitleFocus,
                    autoHideWhileFocused = true)
            }
        }
        if (busy) AddonPlaybackLoading(title, artwork?.background, startupLogo, loadingStage, onBack,
            { playAfterPicker = false; player.pause(); subtitles = true }, subtitleFocus)
        if (subtitles) Dialog(::closePicker, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            AddonSubtitlePicker(host, playback, ::closePicker, {
                subtitles = false; subtitleSync = true
                if (playAfterPicker && !backgroundStopped && !accessStopped) player.play()
            }, allowTiming = !busy && playback.ready && !backgroundStopped && !accessStopped)
        }
        if (subtitleSync) Dialog({ subtitleSync = false; subtitles = true; player.pause() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            AddonSubtitleSync(playback, playing, {
                if (!backgroundStopped && !accessStopped) {
                    playAfterPicker = !player.playWhenReady
                    if (playAfterPicker) player.play() else player.pause()
                }
            }, { subtitleSync = false; subtitles = true; player.pause() })
        }
        if (audio) Dialog(::closePicker, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            TrackSelectionOverlay(labels(com.streammate.tv.iptv.R.string.player_stats_audio), if (audioOptions.isEmpty()) listOf(PlayerTrackChoice(labels(com.streammate.tv.iptv.R.string.player_no_audio_tracks), true, false)) else audioOptions.map { PlayerTrackChoice(audioLabel(it), it.first.isTrackSelected(it.second)) },
                { index -> audioOptions.getOrNull(index)?.let { option -> scope.launch {
                    try { playback.checkAccess(); player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setOverrideForType(TrackSelectionOverride(option.first.mediaTrackGroup, listOf(option.second))).build(); closePicker() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = AddonFailure.NETWORK; closePicker() }
                } } }, ::closePicker)
        }
    }
}
