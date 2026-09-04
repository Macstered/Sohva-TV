package com.streammate.tv.feature.player

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.error.userMessage
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import com.streammate.tv.app.StreamMateThemeTokens
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.iptv.R
import com.streammate.tv.app.StreamMateBackground
import com.streammate.tv.app.RemoteChannelKeyMode
import com.streammate.tv.app.PlaybackReconnectPolicy
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.feature.common.tickerFlow
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.iptv.metadata.EnrichedMetadata
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.playback.PlaybackRequestExtras
import com.streammate.tv.iptv.repository.GuideChannel
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import java.util.concurrent.Executor
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PlayerLoadState {
    data object Loading : PlayerLoadState
    data class Ready(val controller: MediaController) : PlayerLoadState
    data class Error(val message: String) : PlayerLoadState
}

private enum class TrackPickerType {
    AUDIO,
    SUBTITLES,
}

internal enum class PlayerBackAction {
    DISMISS_TRACK_PICKER,
    DISMISS_CHANNEL_GROUP_BROWSER,
    DISMISS_CHANNEL_BROWSER,
    DISMISS_CHROME,
    EXIT_PLAYER,
}

internal fun playerBackAction(
    trackPickerVisible: Boolean,
    channelGroupBrowserVisible: Boolean,
    channelBrowserVisible: Boolean,
    chromeVisible: Boolean,
): PlayerBackAction = when {
    trackPickerVisible -> PlayerBackAction.DISMISS_TRACK_PICKER
    channelGroupBrowserVisible -> PlayerBackAction.DISMISS_CHANNEL_GROUP_BROWSER
    channelBrowserVisible -> PlayerBackAction.DISMISS_CHANNEL_BROWSER
    chromeVisible -> PlayerBackAction.DISMISS_CHROME
    else -> PlayerBackAction.EXIT_PLAYER
}

internal fun playerBrowserGroup(channels: List<GuideChannel>, channelId: String): String? =
    channels.firstOrNull { it.id == channelId }?.groupTitle?.takeIf(String::isNotBlank)

/** Provider groups in playlist order, without an artificial "all" category. */
internal fun playerBrowserGroups(channels: List<GuideChannel>): List<String> =
    channels.mapNotNull { it.groupTitle?.takeIf(String::isNotBlank) }.distinct()

internal fun playerBrowserChannelsForGroup(
    channels: List<GuideChannel>,
    group: String?,
): List<GuideChannel> = if (group == null) channels else channels.filter { it.groupTitle == group }

/**
 * The channels the browser offers while [channelId] is playing: the ones in the
 * same group, or all of them where the channel being watched has no group to
 * narrow to.
 */
internal fun playerBrowserChannels(
    channels: List<GuideChannel>,
    channelId: String,
): List<GuideChannel> = playerBrowserChannelsForGroup(
    channels = channels,
    group = playerBrowserGroup(channels, channelId),
)

internal fun playerKeyRevealsChrome(keyCode: Int): Boolean =
    keyCode != KeyEvent.KEYCODE_BACK

@Composable
@OptIn(UnstableApi::class)
fun PlayerScreen(
    channelId: String,
    catchupStartEpochMillis: Long? = null,
    catchupStopEpochMillis: Long? = null,
    vodContentKey: String? = null,
    resumePositionMillis: Long = 0L,
    sessionToken: SessionToken,
    guideRepository: GuideRepository,
    metadataRepository: MetadataRepository,
    timeZoneId: String,
    remoteChannelKeyMode: RemoteChannelKeyMode,
    playbackReconnectPolicy: PlaybackReconnectPolicy,
    autoFrameRateEnabled: Boolean,
    preferredAudioLanguage: String? = null,
    secondaryAudioLanguage: String? = null,
    preferredSubtitleLanguage: String? = null,
    secondarySubtitleLanguage: String? = null,
    onChannelChange: (String) -> Unit,
    onOpenExternal: suspend (String) -> Result<Unit>,
    onBack: () -> Unit,
    onPlaybackEnded: () -> Unit = {},
    previewArtworkUrl: String? = null,
) {
    val context = LocalContext.current
    val serviceDisconnectedMessage = stringResource(R.string.player_service_disconnected)
    val connectingMessage = stringResource(R.string.player_connecting)
    var state by remember { mutableStateOf<PlayerLoadState>(PlayerLoadState.Loading) }
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    // These are the two heaviest queries in the app and they were keyed on the
    // minute, so both were torn down and re-subscribed every 60 seconds while
    // video was decoding. `now` still ticks for the progress bar; the queries
    // are anchored to coarser buckets instead.
    //
    // observeGuide joins the currently-airing programme, so its bucket bounds
    // how stale the channel browser's now-playing line can be. observeTimeline
    // loads a seven-hour window, so where that window is anchored barely
    // matters and it can re-run far less often.
    val guideNowBucket = now / PLAYER_EPG_NOW_BUCKET_MILLIS
    val channels by remember(guideRepository, guideNowBucket) {
        guideRepository.observeGuide(guideNowBucket * PLAYER_EPG_NOW_BUCKET_MILLIS)
    }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val guideWindowStart = (now / PLAYER_EPG_WINDOW_BUCKET_MILLIS) *
        PLAYER_EPG_WINDOW_BUCKET_MILLIS - PLAYER_EPG_HISTORY_MILLIS
    val guideTimeline by remember(guideRepository, guideWindowStart) {
        guideRepository.observeTimeline(
            fromEpochMillis = guideWindowStart,
            toEpochMillis = guideWindowStart + PLAYER_EPG_WINDOW_MILLIS,
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentIndex = channels.indexOfFirst { it.id == channelId }
    val showTransportControls = shouldShowTransportControls(
        catchupStartEpochMillis = catchupStartEpochMillis,
        catchupStopEpochMillis = catchupStopEpochMillis,
        vodContentKey = vodContentKey,
    )
    val previousChannelId = if (!showTransportControls && currentIndex >= 0 && channels.size > 1) {
        channels[(currentIndex - 1 + channels.size) % channels.size].id
    } else {
        null
    }
    val nextChannelId = if (!showTransportControls && currentIndex >= 0 && channels.size > 1) {
        channels[(currentIndex + 1) % channels.size].id
    } else {
        null
    }
    val unknownErrorMessage = stringResource(CoreR.string.error_unknown)
    val listener = remember(serviceDisconnectedMessage, unknownErrorMessage) {
        object : MediaController.Listener {
            override fun onError(controller: MediaController, sessionError: SessionError) {
                // SessionError is media3's own type, not a Throwable, so it goes
                // through the redactor directly rather than via userMessage.
                state = PlayerLoadState.Error(
                    SecretRedactor.redact(sessionError.message) ?: unknownErrorMessage,
                )
            }

            override fun onDisconnected(controller: MediaController) {
                state = PlayerLoadState.Error(serviceDisconnectedMessage)
            }
        }
    }
    val controllerFuture = remember(sessionToken, listener) {
        MediaController.Builder(context, sessionToken).setListener(listener).buildAsync()
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val mainExecutor = remember(mainHandler) { Executor { command -> mainHandler.post(command) } }

    LaunchedEffect(Unit) {
        tickerFlow(periodMillis = PLAYER_EPG_REFRESH_MILLIS, emitImmediately = false).collect {
            now = System.currentTimeMillis()
        }
    }
    DisposableEffect(controllerFuture, mainExecutor) {
        var disposed = false
        controllerFuture.addListener(
            {
                if (!disposed) {
                    state = runCatching { controllerFuture.get() }
                        .fold(
                            onSuccess = PlayerLoadState::Ready,
                            onFailure = {
                                PlayerLoadState.Error(
                                    (it.cause ?: it).userMessage(context),
                                )
                            },
                        )
                }
            },
            mainExecutor,
        )
        onDispose {
            disposed = true
            MediaController.releaseFuture(controllerFuture)
        }
    }

    when (val currentState = state) {
        PlayerLoadState.Loading -> PlayerMessage(connectingMessage, onBack)
        is PlayerLoadState.Error -> PlayerMessage(currentState.message, onBack)
        is PlayerLoadState.Ready -> ActivePlayer(
            controller = currentState.controller,
            channelId = channelId,
            catchupStartEpochMillis = catchupStartEpochMillis,
            catchupStopEpochMillis = catchupStopEpochMillis,
            vodContentKey = vodContentKey,
            resumePositionMillis = resumePositionMillis,
            showTransportControls = showTransportControls,
            channels = channels,
            guideChannel = guideTimeline.firstOrNull { it.id == channelId },
            nowEpochMillis = now,
            timeZoneId = timeZoneId,
            metadataRepository = metadataRepository,
            remoteChannelKeyMode = remoteChannelKeyMode,
            playbackReconnectPolicy = playbackReconnectPolicy,
            preferredAudioLanguage = preferredAudioLanguage,
            secondaryAudioLanguage = secondaryAudioLanguage,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            secondarySubtitleLanguage = secondarySubtitleLanguage,
            onChannelChange = onChannelChange,
            onPreviousChannel = previousChannelId?.let { id -> { onChannelChange(id) } },
            onNextChannel = nextChannelId?.let { id -> { onChannelChange(id) } },
            onOpenExternal = onOpenExternal.takeUnless { showTransportControls },
            onBack = onBack,
            onPlaybackEnded = onPlaybackEnded,
            autoFrameRateEnabled = autoFrameRateEnabled,
            previewArtworkUrl = previewArtworkUrl,
        )
    }
}

internal fun shouldShowTransportControls(
    catchupStartEpochMillis: Long?,
    catchupStopEpochMillis: Long?,
    vodContentKey: String?,
): Boolean = vodContentKey != null ||
    (catchupStartEpochMillis != null && catchupStopEpochMillis != null)

internal fun shouldNotifyVodPlaybackEnded(
    playbackState: Int,
    vodContentKey: String?,
    alreadyHandled: Boolean,
): Boolean = playbackState == Player.STATE_ENDED && vodContentKey != null && !alreadyHandled

@Composable
@OptIn(UnstableApi::class)
private fun ActivePlayer(
    controller: MediaController,
    channelId: String,
    catchupStartEpochMillis: Long?,
    catchupStopEpochMillis: Long?,
    vodContentKey: String?,
    resumePositionMillis: Long,
    showTransportControls: Boolean,
    channels: List<GuideChannel>,
    guideChannel: GuideTimelineChannel?,
    nowEpochMillis: Long,
    timeZoneId: String,
    metadataRepository: MetadataRepository,
    remoteChannelKeyMode: RemoteChannelKeyMode,
    playbackReconnectPolicy: PlaybackReconnectPolicy,
    autoFrameRateEnabled: Boolean,
    preferredAudioLanguage: String?,
    secondaryAudioLanguage: String?,
    preferredSubtitleLanguage: String?,
    secondarySubtitleLanguage: String?,
    onChannelChange: (String) -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    onOpenExternal: (suspend (String) -> Result<Unit>)?,
    onBack: () -> Unit,
    onPlaybackEnded: () -> Unit,
    previewArtworkUrl: String?,
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    val resources = LocalResources.current
    val defaultChannelName = stringResource(R.string.player_channel)
    val automaticAudioLabel = stringResource(R.string.player_audio_automatic)
    val subtitlesOffLabel = stringResource(R.string.player_subtitles_off)
    val noAudioTracksLabel = stringResource(R.string.player_no_audio_tracks)
    val noSubtitlesLabel = stringResource(R.string.player_no_subtitles)
    val displayLocale = resources.configuration.primaryLocale()
    val fallbackTrackLabel: (Int) -> String = { index ->
        resources.getString(R.string.player_track_number, index)
    }
    val resizeModeLabels = listOf(
        stringResource(R.string.player_resize_fit),
        stringResource(R.string.player_resize_fill),
        stringResource(R.string.player_resize_zoom),
    )
    // Every opening starts in the group of the channel actually playing. A
    // group picked inside the browser is deliberately temporary until a
    // channel is tuned from it.
    val currentBrowserGroup = remember(channels, channelId) {
        playerBrowserGroup(channels, channelId)
    }
    val browserGroups = remember(channels) { playerBrowserGroups(channels) }
    val browserGroupCounts = remember(channels) {
        channels.mapNotNull { channel ->
            channel.groupTitle?.takeIf(String::isNotBlank)
        }.groupingBy { it }.eachCount()
    }
    var browserGroup by remember(channelId, currentBrowserGroup) {
        mutableStateOf(currentBrowserGroup)
    }
    val browserChannels = remember(channels, browserGroup) {
        playerBrowserChannelsForGroup(channels, browserGroup)
    }
    val browserCurrentIndex = browserChannels.indexOfFirst { it.id == channelId }
    val guideChannelName = channels.firstOrNull { it.id == channelId }?.name
    var channelName by remember(channelId, defaultChannelName, guideChannelName) {
        mutableStateOf(guideChannelName ?: defaultChannelName)
    }
    var playbackError by remember(channelId) { mutableStateOf<String?>(null) }
    var reconnectAttempt by remember(channelId) { mutableIntStateOf(0) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var audioTrackLabel by remember(channelId, automaticAudioLabel) { mutableStateOf(automaticAudioLabel) }
    var subtitleTrackLabel by remember(channelId, subtitlesOffLabel) { mutableStateOf(subtitlesOffLabel) }
    var chromeVersion by remember(channelId) { mutableIntStateOf(0) }
    var externalPlayerBusy by remember(channelId) { mutableStateOf(false) }
    var externalPlayerError by remember(channelId) { mutableStateOf<String?>(null) }
    var channelBrowserVisible by remember(channelId) { mutableStateOf(false) }
    var channelGroupBrowserVisible by remember(channelId) { mutableStateOf(false) }
    var browserSelectionIndex by remember(channelId) {
        mutableIntStateOf(browserCurrentIndex.coerceAtLeast(0))
    }
    var browserGroupSelectionIndex by remember(channelId, browserGroups, currentBrowserGroup) {
        mutableIntStateOf(browserGroups.indexOf(currentBrowserGroup).coerceAtLeast(0))
    }
    val browserListState = rememberLazyListState()
    val browserGroupListState = rememberLazyListState()
    var isPlaying by remember(controller) { mutableStateOf(controller.isPlaying) }
    var isBuffering by remember(controller) {
        mutableStateOf(controller.playbackState == Player.STATE_BUFFERING)
    }
    var statsVisible by remember(controller) { mutableStateOf(false) }
    var playbackStats by remember(controller) { mutableStateOf(controller.collectPlaybackStats()) }
    // A 60 Hz panel showing 50 fps football has to double some frames and not
    // others, which is the judder on a pan across a pitch. Matching the display
    // to the stream removes it; leaving it alone is better than a mode that does
    // not divide evenly, since switching blanks most televisions for a second.
    AutoFrameRateEffect(
        contentFrameRate = playbackStats.frameRate,
        enabled = autoFrameRateEnabled,
    )
    var playbackPositionMillis by remember(controller) { androidx.compose.runtime.mutableLongStateOf(0L) }
    var playbackDurationMillis by remember(controller) { androidx.compose.runtime.mutableLongStateOf(0L) }
    var programmeMetadata by remember(channelId) { mutableStateOf<EnrichedMetadata?>(null) }
    var playerView by remember(controller) { mutableStateOf<PlayerView?>(null) }
    var transportControlsFocused by remember(channelId) { mutableStateOf(false) }
    var liveInfoVisible by remember(channelId) { mutableStateOf(false) }
    var transportControlsVisible by remember(channelId) { mutableStateOf(showTransportControls) }
    var chromeDismissRequest by remember(channelId) { mutableIntStateOf(0) }
    var controlsFocusVersion by remember(channelId) { mutableIntStateOf(0) }
    var liveInfoFocusVersion by remember(channelId) { mutableIntStateOf(0) }
    var trackPickerType by remember(channelId) { mutableStateOf<TrackPickerType?>(null) }
    var quickActionsVisible by remember(channelId) { mutableStateOf(false) }
    var preferredAudioApplied by remember(channelId, preferredAudioLanguage, secondaryAudioLanguage) {
        mutableStateOf(false)
    }
    var preferredSubtitlesApplied by remember(
        channelId,
        preferredSubtitleLanguage,
        secondarySubtitleLanguage,
    ) { mutableStateOf(false) }
    var manualAudioSelection by remember(channelId) { mutableStateOf(false) }
    var manualSubtitleSelection by remember(channelId) { mutableStateOf(false) }
    var playbackEndedHandled by remember(channelId) { mutableStateOf(false) }
    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val scope = rememberCoroutineScope()

    fun applyVodPreferredTracks() {
        if (vodContentKey == null) return
        val audioOptions = trackOptions(
            controller,
            C.TRACK_TYPE_AUDIO,
            displayLocale,
            fallbackTrackLabel,
        )
        // Track discovery arrives incrementally. Do not make a subtitle choice
        // until audio is known, because whether the primary audio exists is the
        // rule that decides if subtitles should be off.
        if (audioOptions.isEmpty()) return
        val primaryAudioAvailable = shouldDisableAutomaticVodSubtitles(
            primaryAudioLanguage = preferredAudioLanguage,
            availableAudioLanguages = audioOptions.map(PlayerTrackOption::languageCode),
        )
        if (!preferredAudioApplied && !manualAudioSelection) {
            preferredAudioApplied = applyPreferredTrack(
                controller,
                C.TRACK_TYPE_AUDIO,
                preferredAudioLanguage,
                secondaryAudioLanguage,
                displayLocale,
                fallbackTrackLabel,
            )
        }
        if (!preferredSubtitlesApplied && !manualSubtitleSelection) {
            preferredSubtitlesApplied = if (primaryAudioAvailable && !manualAudioSelection) {
                selectTrackOption(controller, C.TRACK_TYPE_TEXT, null)
                true
            } else {
                applyPreferredTrack(
                    controller,
                    C.TRACK_TYPE_TEXT,
                    preferredSubtitleLanguage,
                    secondarySubtitleLanguage,
                    displayLocale,
                    fallbackTrackLabel,
                )
            }
        }
    }

    // Moved on the keypress itself rather than in reaction to the selection
    // changing, so the highlight and the list arrive in the same frame.
    val scrollBrowserToSelection: (Int) -> Unit = { index ->
        scope.launch {
            val onScreen = browserListState.layoutInfo.visibleItemsInfo.size
                .takeIf { it > 0 }
                ?: CHANNEL_BROWSER_ROWS_ON_SCREEN
            browserListState.scrollToItem(playerBrowserScrollTarget(index, onScreen))
        }
    }
    val scrollBrowserGroupToSelection: (Int) -> Unit = { index ->
        scope.launch {
            val onScreen = browserGroupListState.layoutInfo.visibleItemsInfo.size
                .takeIf { it > 0 }
                ?: CHANNEL_GROUP_BROWSER_ROWS_ON_SCREEN
            browserGroupListState.scrollToItem(playerBrowserScrollTarget(index, onScreen))
        }
    }

    fun openChannelBrowser() {
        val defaultChannels = playerBrowserChannelsForGroup(channels, currentBrowserGroup)
        browserGroup = currentBrowserGroup
        browserSelectionIndex = defaultChannels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
        browserGroupSelectionIndex = browserGroups.indexOf(currentBrowserGroup).coerceAtLeast(0)
        channelGroupBrowserVisible = false
        channelBrowserVisible = true
    }

    fun dismissChannelBrowser() {
        channelBrowserVisible = false
        channelGroupBrowserVisible = false
        browserGroup = currentBrowserGroup
    }
    val resizeMode = RESIZE_MODES[resizeModeIndex]
    val liveProgramme = guideChannel?.currentProgrammeAt(nowEpochMillis)
    val chromeVisibilityKey = "$channelId-$chromeVersion"
    val maxAutomaticReconnectAttempts = reconnectMaxAutomaticAttempts(playbackReconnectPolicy)

    fun dismissPlayerChrome() {
        chromeDismissRequest += 1
        transportControlsFocused = false
        playerView?.requestFocus()
    }

    BackHandler {
        when (
            playerBackAction(
                trackPickerVisible = trackPickerType != null,
                channelGroupBrowserVisible = channelGroupBrowserVisible,
                channelBrowserVisible = channelBrowserVisible,
                chromeVisible = liveInfoVisible || transportControlsVisible ||
                    (showTransportControls && transportControlsFocused),
            )
        ) {
            PlayerBackAction.DISMISS_TRACK_PICKER -> {
                trackPickerType = null
                if (showTransportControls) {
                    controlsFocusVersion += 1
                } else {
                    playerView?.requestFocus()
                }
                chromeVersion += 1
            }
            PlayerBackAction.DISMISS_CHANNEL_GROUP_BROWSER -> {
                channelGroupBrowserVisible = false
            }
            PlayerBackAction.DISMISS_CHANNEL_BROWSER -> {
                dismissChannelBrowser()
                chromeVersion += 1
            }
            PlayerBackAction.DISMISS_CHROME -> dismissPlayerChrome()
            PlayerBackAction.EXIT_PLAYER -> onBack()
        }
    }

    LaunchedEffect(channelId, channels.map(GuideChannel::id), currentBrowserGroup) {
        if (!channelBrowserVisible) {
            browserGroup = currentBrowserGroup
            browserSelectionIndex = playerBrowserChannelsForGroup(channels, currentBrowserGroup)
                .indexOfFirst { it.id == channelId }
                .coerceAtLeast(0)
            browserGroupSelectionIndex = browserGroups.indexOf(currentBrowserGroup).coerceAtLeast(0)
        }
    }

    LaunchedEffect(liveProgramme?.id, showTransportControls, metadataRepository) {
        programmeMetadata = null
        val programme = liveProgramme ?: return@LaunchedEffect
        if (showTransportControls || !metadataRepository.isEnabled()) return@LaunchedEffect
        delay(METADATA_LOOKUP_DELAY_MILLIS)
        programmeMetadata = metadataRepository.enrich(
            MetadataLookup(
                mediaType = MetadataMediaType.PROGRAMME,
                title = programme.title,
            ),
        )
    }

    LaunchedEffect(
        controller,
        channelId,
        catchupStartEpochMillis,
        catchupStopEpochMillis,
        vodContentKey,
        previewArtworkUrl,
    ) {
        if (previewArtworkUrl != null) return@LaunchedEffect
        val request = playbackRequest(
            vodContentKey ?: channelId,
            catchupStartEpochMillis,
            catchupStopEpochMillis,
            vodContentKey != null,
        )
        controller.setMediaItem(request, resumePositionMillis.coerceAtLeast(0L))
        controller.prepare()
        controller.play()
    }

    // Leaving the app must end the stream, not background it. A provider counts
    // an open stream against the account's connection limit whether or not
    // anyone is watching, so a forgotten background playback can lock the
    // household out of its own subscription - and on a television, audio
    // continuing after Home reads as the app refusing to close.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (PlaybackLifecycle.actionFor(event, hasMedia = controller.mediaItemCount > 0)) {
                PlaybackLifecycleAction.STOP -> controller.stop()
                PlaybackLifecycleAction.RESUME -> {
                    controller.prepare()
                    controller.play()
                }
                PlaybackLifecycleAction.NONE -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(controller, channelId) {
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = resources.getString(R.string.player_playback_failed, error.errorCodeName)
                reconnectAttempt += 1
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = controller.isPlaying
                isBuffering = playbackState == Player.STATE_BUFFERING
                playbackPositionMillis = controller.currentPosition.coerceAtLeast(0L)
                playbackDurationMillis = controller.duration.validDuration()
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    externalPlayerError = null
                    reconnectAttempt = 0
                }
                if (shouldNotifyVodPlaybackEnded(playbackState, vodContentKey, playbackEndedHandled)) {
                    playbackEndedHandled = true
                    currentOnPlaybackEnded()
                }
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                channelName = mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: defaultChannelName
            }

            override fun onTracksChanged(tracks: Tracks) {
                applyVodPreferredTracks()
                audioTrackLabel = selectedTrackLabel(
                    controller,
                    C.TRACK_TYPE_AUDIO,
                    displayLocale,
                    fallbackTrackLabel,
                ) ?: automaticAudioLabel
                subtitleTrackLabel = selectedTrackLabel(
                    controller,
                    C.TRACK_TYPE_TEXT,
                    displayLocale,
                    fallbackTrackLabel,
                ) ?: subtitlesOffLabel
            }
        }
        controller.addListener(playerListener)
        applyVodPreferredTracks()
        isPlaying = controller.isPlaying
        playbackPositionMillis = controller.currentPosition.coerceAtLeast(0L)
        playbackDurationMillis = controller.duration.validDuration()
        audioTrackLabel = selectedTrackLabel(
            controller,
            C.TRACK_TYPE_AUDIO,
            displayLocale,
            fallbackTrackLabel,
        ) ?: automaticAudioLabel
        subtitleTrackLabel = selectedTrackLabel(
            controller,
            C.TRACK_TYPE_TEXT,
            displayLocale,
            fallbackTrackLabel,
        ) ?: subtitlesOffLabel
        onDispose {
            controller.removeListener(playerListener)
            controller.stop()
            controller.clearMediaItems()
        }
    }

    LaunchedEffect(controller, showTransportControls) {
        if (!showTransportControls) return@LaunchedEffect
        // The collector resumes on this LaunchedEffect's dispatcher, so the
        // controller is still only touched from the main thread.
        tickerFlow(periodMillis = PLAYBACK_POSITION_REFRESH_MILLIS).collect {
            playbackPositionMillis = controller.currentPosition.coerceAtLeast(0L)
            playbackDurationMillis = controller.duration.validDuration()
            isPlaying = controller.isPlaying
        }
    }

    LaunchedEffect(playbackError, reconnectAttempt, controller, playbackReconnectPolicy) {
        if (playbackError != null && reconnectAttempt in 1..maxAutomaticReconnectAttempts) {
            delay(reconnectDelayMillis(playbackReconnectPolicy, reconnectAttempt))
            playbackError = null
            controller.prepare()
            controller.play()
        }
    }

    val openExternalAction: (() -> Unit)? = onOpenExternal?.let { externalLauncher ->
        {
            if (!externalPlayerBusy) {
                scope.launch {
                    externalPlayerBusy = true
                    externalPlayerError = null
                    controller.stop()
                    controller.clearMediaItems()
                    delay(EXTERNAL_PLAYER_RELEASE_DELAY_MILLIS)
                    externalLauncher(channelId).fold(
                        onSuccess = { onBack() },
                        onFailure = { error ->
                            externalPlayerError = error.userMessage(context)
                            controller.setMediaItem(playbackRequest(channelId, null, null, false))
                            controller.prepare()
                            controller.play()
                        },
                    )
                    externalPlayerBusy = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    this.resizeMode = resizeMode
                    player = controller
                    isFocusable = true
                    isFocusableInTouchMode = true
                    playerView = this
                    post { requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = controller
                playerView.resizeMode = resizeMode
                playerView.useController = false
                playerView.hideController()
                playerView.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) {
                        false
                    } else {
                        if (playerKeyRevealsChrome(keyCode)) {
                            chromeVersion += 1
                        }
                        if (channelBrowserVisible) {
                            if (channelGroupBrowserVisible) {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        browserGroupSelectionIndex =
                                            browserGroups.previousIndex(browserGroupSelectionIndex)
                                        scrollBrowserGroupToSelection(browserGroupSelectionIndex)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        browserGroupSelectionIndex =
                                            browserGroups.nextIndex(browserGroupSelectionIndex)
                                        scrollBrowserGroupToSelection(browserGroupSelectionIndex)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER -> {
                                        browserGroups.getOrNull(browserGroupSelectionIndex)?.let { group ->
                                            browserGroup = group
                                            browserSelectionIndex =
                                                playerBrowserChannelsForGroup(channels, group)
                                                    .indexOfFirst { it.id == channelId }
                                                    .coerceAtLeast(0)
                                            scrollBrowserToSelection(browserSelectionIndex)
                                        }
                                        channelGroupBrowserVisible = false
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_BACK -> {
                                        channelGroupBrowserVisible = false
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        browserSelectionIndex =
                                            browserChannels.previousIndex(browserSelectionIndex)
                                        scrollBrowserToSelection(browserSelectionIndex)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        browserSelectionIndex =
                                            browserChannels.nextIndex(browserSelectionIndex)
                                        scrollBrowserToSelection(browserSelectionIndex)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER -> {
                                        val selectedChannelId =
                                            browserChannels.getOrNull(browserSelectionIndex)?.id
                                        dismissChannelBrowser()
                                        if (selectedChannelId != null && selectedChannelId != channelId) {
                                            onChannelChange(selectedChannelId)
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (browserGroups.isNotEmpty()) {
                                            browserGroupSelectionIndex =
                                                browserGroups.indexOf(browserGroup).coerceAtLeast(0)
                                            scrollBrowserGroupToSelection(browserGroupSelectionIndex)
                                            channelGroupBrowserVisible = true
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_BACK -> {
                                        dismissChannelBrowser()
                                        true
                                    }
                                    else -> false
                                }
                            }
                        } else when (keyCode) {
                            KeyEvent.KEYCODE_CHANNEL_UP -> onPreviousChannel?.let { it(); true } ?: false
                            KeyEvent.KEYCODE_CHANNEL_DOWN -> onNextChannel?.let { it(); true } ?: false
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN -> when {
                                showTransportControls -> {
                                    controlsFocusVersion += 1
                                    true
                                }
                                // While the box is up, up and down belong to
                                // it: they step into the row of buttons along
                                // its bottom, which were otherwise unreachable.
                                // The channel list is still a key away from a
                                // clear screen, and has a button here besides.
                                liveInfoVisible -> {
                                    liveInfoFocusVersion += 1
                                    true
                                }
                                remoteChannelKeyMode == RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS &&
                                    browserChannels.isNotEmpty() -> {
                                    openChannelBrowser()
                                    true
                                }
                                else -> false
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER -> when {
                                // A held key repeats; the first repeat is the
                                // long press, about half a second in.
                                event.repeatCount > 0 -> {
                                    quickActionsVisible = true
                                    true
                                }
                                showTransportControls -> {
                                    controlsFocusVersion += 1
                                    true
                                }
                                else -> false
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> if (showTransportControls) {
                                controller.seekBy(-SEEK_INCREMENT_MILLIS)
                                true
                            } else {
                                false
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> if (showTransportControls) {
                                controller.seekBy(SEEK_INCREMENT_MILLIS)
                                true
                            } else {
                                false
                            }
                            KeyEvent.KEYCODE_INFO -> {
                                statsVisible = !statsVisible
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> {
                                trackPickerType = TrackPickerType.AUDIO
                                true
                            }
                            KeyEvent.KEYCODE_CAPTIONS -> {
                                trackPickerType = TrackPickerType.SUBTITLES
                                true
                            }
                            else -> false
                        }
                    }
                }
            },
        )
        if (previewArtworkUrl != null) {
            AsyncImage(
                model = previewArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // One wash under all of it. Drawn only while something is on top of the
        // picture, so an untouched player is the picture and nothing else.
        androidx.compose.animation.AnimatedVisibility(
            visible = liveInfoVisible || transportControlsVisible || channelBrowserVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            PlayerChromeScrim()
        }
        if (statsVisible) {
            Text(
                text = playerClockLabel(timeZoneId, nowEpochMillis),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 40.dp, top = 24.dp)
                    .testTag("player-clock"),
                color = StreamMateThemeTokens.palette.textPrimary,
                fontSize = StreamMateThemeTokens.typography.headline.fontSize,
                lineHeight = StreamMateThemeTokens.typography.headline.lineHeight,
                fontWeight = FontWeight.Bold,
            )
        }
        LiveProgrammeInfoOverlay(
            channel = guideChannel,
            streamName = channelName,
            nowEpochMillis = nowEpochMillis,
            timeZoneId = timeZoneId,
            metadata = programmeMetadata,
            aspectModeLabel = resizeModeLabels[resizeModeIndex],
            audioTrackLabel = audioTrackLabel,
            subtitleTrackLabel = subtitleTrackLabel,
            statsVisible = statsVisible,
            onBack = ::dismissPlayerChrome,
            onCycleAspectMode = { resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.size },
            onCycleAudioTrack = {
                trackPickerType = TrackPickerType.AUDIO
            },
            onCycleSubtitleTrack = {
                trackPickerType = TrackPickerType.SUBTITLES
            },
            // The same three things the remote's own keys reach, given a
            // control each for a remote that has no such keys.
            onOpenChannelBrowser = ::openChannelBrowser,
            onToggleStats = { statsVisible = !statsVisible },
            onOpenQuickActions = { quickActionsVisible = true },
            externalPlayerBusy = externalPlayerBusy,
            onOpenExternal = openExternalAction,
            visibilityKey = "$chromeVisibilityKey-${liveProgramme?.id}",
            enabled = !showTransportControls && !channelBrowserVisible,
            dismissRequest = chromeDismissRequest,
            focusRequestKey = liveInfoFocusVersion,
            onVisibilityChanged = { liveInfoVisible = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, end = 40.dp, bottom = 28.dp),
        )
        if (showTransportControls) {
            BottomTransportControls(
                title = channelName,
                isPlaying = isPlaying,
                positionMillis = playbackPositionMillis,
                durationMillis = playbackDurationMillis,
                visibilityKey = chromeVisibilityKey,
                focusRequestKey = controlsFocusVersion,
                holdVisible = trackPickerType != null,
                aspectModeLabel = resizeModeLabels[resizeModeIndex],
                audioTrackLabel = audioTrackLabel,
                subtitleTrackLabel = subtitleTrackLabel,
                onBack = ::dismissPlayerChrome,
                onCycleAspectMode = {
                    resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.size
                    chromeVersion += 1
                },
                onCycleAudioTrack = {
                    trackPickerType = TrackPickerType.AUDIO
                    chromeVersion += 1
                },
                onCycleSubtitleTrack = {
                    trackPickerType = TrackPickerType.SUBTITLES
                    chromeVersion += 1
                },
                onRewind = {
                    controller.seekBy(-SEEK_INCREMENT_MILLIS)
                    chromeVersion += 1
                },
                onPlayPause = {
                    if (controller.isPlaying) controller.pause() else controller.play()
                    chromeVersion += 1
                },
                onForward = {
                    controller.seekBy(SEEK_INCREMENT_MILLIS)
                    chromeVersion += 1
                },
                onControlsFocusChanged = { transportControlsFocused = it },
                dismissRequest = chromeDismissRequest,
                onVisibilityChanged = { transportControlsVisible = it },
                onDismissed = {
                    transportControlsFocused = false
                    playerView?.requestFocus()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, end = 40.dp, bottom = 28.dp),
            )
        }
        if (isBuffering) {
            PlayerBufferingIndicator(
                label = stringResource(R.string.player_buffering),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (statsVisible) {
            LaunchedEffect(controller) {
                tickerFlow(periodMillis = PLAYER_STATS_SAMPLE_MILLIS).collect {
                    playbackStats = controller.collectPlaybackStats()
                }
            }
            PlayerStatsOverlay(
                stats = playbackStats,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 40.dp, top = 24.dp),
            )
        }
        ChannelBrowserOverlay(
            channels = browserChannels,
            listState = browserListState,
            selectedIndex = browserSelectionIndex,
            groups = browserGroups,
            groupCounts = browserGroupCounts,
            selectedGroupIndex = browserGroupSelectionIndex,
            groupListState = browserGroupListState,
            groupsVisible = channelGroupBrowserVisible,
            visible = channelBrowserVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        trackPickerType?.let { pickerType ->
            val options = trackOptions(
                controller,
                if (pickerType == TrackPickerType.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT,
                displayLocale,
                fallbackTrackLabel,
            )
            val choices = when (pickerType) {
                TrackPickerType.AUDIO -> if (options.isEmpty()) {
                    listOf(PlayerTrackChoice(noAudioTracksLabel, selected = true, enabled = false))
                } else {
                    options.map { option ->
                        PlayerTrackChoice(
                            label = option.label,
                            selected = option.group.isTrackSelected(option.trackIndex),
                        )
                    }
                }
                TrackPickerType.SUBTITLES -> buildList {
                    add(
                        PlayerTrackChoice(
                            label = subtitlesOffLabel,
                            selected = options.none { it.group.isTrackSelected(it.trackIndex) },
                        ),
                    )
                    addAll(
                        options.map { option ->
                            PlayerTrackChoice(
                                label = option.label,
                                selected = option.group.isTrackSelected(option.trackIndex),
                            )
                        },
                    )
                    if (options.isEmpty()) {
                        add(PlayerTrackChoice(noSubtitlesLabel, selected = false, enabled = false))
                    }
                }
            }
            TrackSelectionOverlay(
                title = stringResource(
                    if (pickerType == TrackPickerType.AUDIO) {
                        R.string.player_select_audio
                    } else {
                        R.string.player_select_subtitles
                    },
                ),
                choices = choices,
                onSelect = { choiceIndex ->
                    when (pickerType) {
                        TrackPickerType.AUDIO -> options.getOrNull(choiceIndex)?.let { option ->
                            manualAudioSelection = true
                            selectTrackOption(controller, C.TRACK_TYPE_AUDIO, option)
                            audioTrackLabel = option.label
                        }
                        TrackPickerType.SUBTITLES -> {
                            manualSubtitleSelection = true
                            val option = options.getOrNull(choiceIndex - 1)
                            selectTrackOption(controller, C.TRACK_TYPE_TEXT, option)
                            subtitleTrackLabel = option?.label ?: subtitlesOffLabel
                        }
                    }
                    trackPickerType = null
                    if (showTransportControls) controlsFocusVersion += 1 else playerView?.requestFocus()
                    chromeVersion += 1
                },
                onDismiss = {
                    trackPickerType = null
                    if (showTransportControls) controlsFocusVersion += 1 else playerView?.requestFocus()
                    chromeVersion += 1
                },
            )
        }
        if (quickActionsVisible && trackPickerType == null) {
            fun close() {
                quickActionsVisible = false
                if (showTransportControls) controlsFocusVersion += 1 else playerView?.requestFocus()
                chromeVersion += 1
            }
            PlayerQuickActionsOverlay(
                actions = listOf(
                    PlayerQuickAction(
                        label = stringResource(R.string.player_quick_audio),
                        value = audioTrackLabel,
                        testTag = "quick-audio",
                        onSelect = {
                            quickActionsVisible = false
                            trackPickerType = TrackPickerType.AUDIO
                        },
                    ),
                    PlayerQuickAction(
                        label = stringResource(R.string.player_quick_subtitles),
                        value = subtitleTrackLabel,
                        testTag = "quick-subtitles",
                        onSelect = {
                            quickActionsVisible = false
                            trackPickerType = TrackPickerType.SUBTITLES
                        },
                    ),
                    PlayerQuickAction(
                        label = stringResource(R.string.player_quick_picture),
                        value = resizeModeLabels[resizeModeIndex],
                        testTag = "quick-picture",
                        // Cycles in place rather than closing: shape is judged by
                        // looking at the picture, and a menu that shut on every
                        // press would mean re-opening it to try the next one.
                        onSelect = {
                            resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.size
                        },
                    ),
                    PlayerQuickAction(
                        label = stringResource(R.string.player_quick_stats),
                        value = stringResource(
                            if (statsVisible) {
                                R.string.player_quick_stats_on
                            } else {
                                R.string.player_quick_stats_off
                            },
                        ),
                        testTag = "quick-stats",
                        onSelect = {
                            statsVisible = !statsVisible
                            close()
                        },
                    ),
                ),
                onDismiss = ::close,
            )
        }
        (externalPlayerError ?: playbackError)?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC7A1624))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (externalPlayerError != null) {
                        stringResource(R.string.player_external_failed, error)
                    } else if (reconnectAttempt <= maxAutomaticReconnectAttempts) {
                        stringResource(
                            R.string.player_reconnecting,
                            error,
                            reconnectAttempt,
                            maxAutomaticReconnectAttempts,
                        )
                    } else {
                        stringResource(R.string.player_reconnect_stopped, error)
                    },
                    color = Color.White,
                )
                TvActionButton(
                    label = stringResource(R.string.player_reconnect),
                    onClick = {
                        reconnectAttempt = 0
                        playbackError = null
                        controller.prepare()
                        controller.play()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    testTag = "player-reconnect",
                )
            }
        }
    }
}

@Composable
fun KeepScreenOnEffect() {
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val previousKeepScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = previousKeepScreenOn }
    }
}

@Composable
fun PlayerChromeOverlay(
    channelName: String,
    onBack: () -> Unit,
    visibilityKey: Any? = channelName,
    aspectModeLabel: String? = null,
    onCycleAspectMode: (() -> Unit)? = null,
    audioTrackLabel: String? = null,
    onCycleAudioTrack: (() -> Unit)? = null,
    subtitleTrackLabel: String? = null,
    onCycleSubtitleTrack: (() -> Unit)? = null,
    externalPlayerBusy: Boolean = false,
    onOpenExternal: (() -> Unit)? = null,
) {
    var visible by remember(visibilityKey) { mutableStateOf(true) }
    LaunchedEffect(visibilityKey) {
        delay(PLAYER_CONTROLS_TIMEOUT_MILLIS.toLong())
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvActionButton(
                label = stringResource(R.string.action_back),
                onClick = onBack,
                testTag = "player-back",
            )
            Text(text = channelName, modifier = Modifier.padding(start = 16.dp), color = Color.White)
            if (aspectModeLabel != null && onCycleAspectMode != null) {
                TvActionButton(
                    label = stringResource(R.string.player_picture_mode, aspectModeLabel),
                    onClick = onCycleAspectMode,
                    modifier = Modifier.padding(start = 16.dp),
                    testTag = "player-aspect",
                )
            }
            if (audioTrackLabel != null && onCycleAudioTrack != null) {
                TvActionButton(
                    label = stringResource(R.string.player_audio_track, audioTrackLabel),
                    onClick = onCycleAudioTrack,
                    modifier = Modifier.padding(start = 10.dp),
                    compact = true,
                    testTag = "player-audio",
                )
            }
            if (subtitleTrackLabel != null && onCycleSubtitleTrack != null) {
                TvActionButton(
                    label = stringResource(R.string.player_subtitle_track, subtitleTrackLabel),
                    onClick = onCycleSubtitleTrack,
                    modifier = Modifier.padding(start = 10.dp),
                    compact = true,
                    testTag = "player-subtitles",
                )
            }
            if (onOpenExternal != null) {
                TvActionButton(
                    label = if (externalPlayerBusy) {
                        stringResource(R.string.player_opening_external)
                    } else {
                        stringResource(R.string.player_external)
                    },
                    onClick = onOpenExternal,
                    enabled = !externalPlayerBusy,
                    modifier = Modifier.padding(start = 10.dp),
                    compact = true,
                    testTag = "player-external",
                )
            }
        }
    }
}

private const val EXTERNAL_PLAYER_RELEASE_DELAY_MILLIS = 150L
private const val PLAYBACK_POSITION_REFRESH_MILLIS = 500L
private const val SEEK_INCREMENT_MILLIS = 10_000L
private const val MINUTE_MILLIS = 60_000L
private const val PLAYER_EPG_NOW_BUCKET_MILLIS = 5L * 60_000L
private const val PLAYER_EPG_WINDOW_BUCKET_MILLIS = 30L * 60_000L
private const val PLAYER_STATS_SAMPLE_MILLIS = 1_000L
private const val PLAYER_EPG_REFRESH_MILLIS = 30_000L
private const val PLAYER_EPG_HISTORY_MILLIS = 60L * MINUTE_MILLIS
private const val PLAYER_EPG_WINDOW_MILLIS = 7L * 60L * MINUTE_MILLIS
private const val METADATA_LOOKUP_DELAY_MILLIS = 350L
const val PLAYER_CONTROLS_TIMEOUT_MILLIS = 5_000
@OptIn(UnstableApi::class)
private val RESIZE_MODES = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)
@OptIn(UnstableApi::class)
private data class PlayerTrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val languageCode: String?,
)

@OptIn(UnstableApi::class)
private fun trackOptions(
    player: Player,
    trackType: Int,
    displayLocale: Locale,
    fallbackTrackLabel: (Int) -> String,
): List<PlayerTrackOption> =
    player.currentTracks.groups
        .filter { group -> group.type == trackType }
        .flatMap { group ->
            (0 until group.length)
                .filter(group::isTrackSupported)
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language
                        ?.let(Locale::forLanguageTag)
                        ?.getDisplayLanguage(displayLocale)
                        ?.takeIf(String::isNotBlank)
                    val details = listOfNotNull(
                        format.label?.takeIf(String::isNotBlank),
                        language,
                        format.channelCount.takeIf { it > 0 }?.let { "$it ch" },
                    ).distinct()
                    PlayerTrackOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = details.joinToString(" · ").ifBlank { fallbackTrackLabel(trackIndex + 1) },
                        languageCode = normalizeTrackLanguage(format.language),
                    )
                }
        }

@OptIn(UnstableApi::class)
private fun selectedTrackLabel(
    player: Player,
    trackType: Int,
    displayLocale: Locale,
    fallbackTrackLabel: (Int) -> String,
): String? =
    trackOptions(player, trackType, displayLocale, fallbackTrackLabel)
        .firstOrNull { option -> option.group.isTrackSelected(option.trackIndex) }
        ?.label

@OptIn(UnstableApi::class)
private fun selectTrackOption(player: Player, trackType: Int, option: PlayerTrackOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(trackType)
        .setTrackTypeDisabled(trackType, option == null)
    if (option != null) {
        builder.setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
}

@OptIn(UnstableApi::class)
private fun applyPreferredTrack(
    player: Player,
    trackType: Int,
    primaryLanguage: String?,
    secondaryLanguage: String?,
    displayLocale: Locale,
    fallbackTrackLabel: (Int) -> String,
): Boolean {
    val options = trackOptions(player, trackType, displayLocale, fallbackTrackLabel)
    if (options.isEmpty()) return false
    val preferred = listOfNotNull(
        normalizeTrackLanguage(primaryLanguage),
        normalizeTrackLanguage(secondaryLanguage),
    ).distinct()
    if (preferred.isEmpty()) return true
    val selected = preferred.firstNotNullOfOrNull { language ->
        options.firstOrNull { it.languageCode == language }
    }
    if (selected != null) {
        selectTrackOption(player, trackType, selected)
    } else if (trackType == C.TRACK_TYPE_TEXT) {
        selectTrackOption(player, trackType, null)
    }
    return true
}

/** Primary-language audio makes subtitles redundant for automatic VOD setup. */
internal fun shouldDisableAutomaticVodSubtitles(
    primaryAudioLanguage: String?,
    availableAudioLanguages: List<String?>,
): Boolean {
    val primary = normalizeTrackLanguage(primaryAudioLanguage) ?: return false
    return availableAudioLanguages.any { normalizeTrackLanguage(it) == primary }
}

internal fun normalizeTrackLanguage(language: String?): String? {
    val value = language?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty) ?: return null
    val base = value.substringBefore('-').substringBefore('_')
    return when (base) {
        "fin" -> "fi"
        "eng" -> "en"
        "swe" -> "sv"
        "dan" -> "da"
        "nor", "nob", "nno" -> "no"
        "est" -> "et"
        "deu", "ger" -> "de"
        "fra", "fre" -> "fr"
        "spa" -> "es"
        "ita" -> "it"
        "nld", "dut" -> "nl"
        else -> base
    }
}

private fun playbackRequest(
    mediaId: String,
    catchupStartEpochMillis: Long?,
    catchupStopEpochMillis: Long?,
    vodContent: Boolean,
): MediaItem {
    val builder = MediaItem.Builder().setMediaId(mediaId)
    if (catchupStartEpochMillis != null && catchupStopEpochMillis != null || vodContent) {
        val extras = Bundle().apply {
            if (catchupStartEpochMillis != null && catchupStopEpochMillis != null) {
                putLong(PlaybackRequestExtras.CATCHUP_START_EPOCH_MILLIS, catchupStartEpochMillis)
                putLong(PlaybackRequestExtras.CATCHUP_STOP_EPOCH_MILLIS, catchupStopEpochMillis)
            }
            putBoolean(PlaybackRequestExtras.VOD_CONTENT, vodContent)
        }
        builder.setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
    }
    return builder.build()
}

private fun Long.validDuration(): Long = takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

private fun MediaController.seekBy(offsetMillis: Long) {
    val duration = duration.validDuration()
    val target = (currentPosition + offsetMillis).coerceAtLeast(0L)
    seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
}

private fun List<*>.previousIndex(current: Int): Int = when {
    isEmpty() -> 0
    current <= 0 -> lastIndex
    else -> current - 1
}

private fun List<*>.nextIndex(current: Int): Int = when {
    isEmpty() -> 0
    current !in indices || current == lastIndex -> 0
    else -> current + 1
}

private fun Configuration.primaryLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    locales[0]
} else {
    @Suppress("DEPRECATION")
    locale
}

@Composable
private fun PlayerMessage(message: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier.fillMaxSize().background(StreamMateBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = Color.White)
        TvActionButton(
            label = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        )
    }
}
