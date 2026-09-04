package com.streammate.tv.app

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.streammate.tv.iptv.playback.PlaybackSource
import com.streammate.tv.iptv.playback.PlaybackRequestExtras
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class StreamMatePlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private var activeBufferProfile = PlaybackBufferProfile.DEFAULT
    private var pendingBufferProfile: PlaybackBufferProfile? = null
    private var shuttingDown = false
    @Volatile private var activeSource: PlaybackSource? = null
    @Volatile private var activeVodContentKey: String? = null
    private var progressJob: Job? = null

    private val container: StreamMateContainer
        get() = (application as StreamMateApplication).container

    override fun onCreate() {
        super.onCreate()
        val upstreamFactory = OkHttpDataSource.Factory(container.playbackHttpClient)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory, ::resolveDataSpec)
        mediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory)
        activeBufferProfile = runCatching {
            runBlocking(Dispatchers.IO) {
                container.preferencesRepository.preferences.first().playbackBufferProfile
            }
        }.getOrDefault(PlaybackBufferProfile.DEFAULT)
        player = createPlayer(activeBufferProfile)
        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setCallback(SessionCallback())
            .build()
        serviceScope.launch {
            container.preferencesRepository.preferences
                .map { preferences -> preferences.playbackBufferProfile }
                .distinctUntilChanged()
                .collect { profile -> requestBufferProfile(profile) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        player.clearMediaItems()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        shuttingDown = true
        releaseActiveSource()
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val source = activeSource
            ?.takeIf { dataSpec.uri.scheme == PLACEHOLDER_SCHEME }
            ?: throw IOException("Playback source is no longer available")
        return dataSpec
            .withUri(Uri.parse(source.streamUrl))
            .withAdditionalHeaders(source.headers)
    }

    private fun releaseActiveSource() {
        saveVodProgressSnapshot()
        progressJob?.cancel()
        progressJob = null
        activeVodContentKey = null
        activeSource?.close()
        activeSource = null
    }

    private fun createPlayer(bufferProfile: PlaybackBufferProfile): ExoPlayer {
        val builder = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true),
            )
            .setMediaSourceFactory(mediaSourceFactory)
        PlaybackBufferPolicy.loadControl(bufferProfile)?.let(builder::setLoadControl)
        return builder.build().also { exoPlayer ->
            exoPlayer.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        if (exoPlayer.mediaItemCount == 0) {
                            releaseActiveSource()
                            applyPendingBufferProfileIfIdle()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) saveVodProgressSnapshot()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) saveVodProgressSnapshot()
                    }
                },
            )
        }
    }

    private fun requestBufferProfile(profile: PlaybackBufferProfile) {
        if (shuttingDown) return
        if (profile == activeBufferProfile) {
            pendingBufferProfile = null
            return
        }
        if (player.mediaItemCount == 0) {
            replaceIdlePlayer(profile)
        } else {
            pendingBufferProfile = profile
        }
    }

    private fun applyPendingBufferProfileIfIdle() {
        if (shuttingDown) return
        val profile = pendingBufferProfile ?: return
        if (player.mediaItemCount != 0) return
        replaceIdlePlayer(profile)
    }

    private fun replaceIdlePlayer(profile: PlaybackBufferProfile) {
        check(player.mediaItemCount == 0)
        val previousPlayer = player
        val replacement = createPlayer(profile)
        player = replacement
        activeBufferProfile = profile
        pendingBufferProfile = null
        mediaSession.setPlayer(replacement)
        previousPlayer.release()
    }

    private fun startVodProgressTracking(contentKey: String) {
        activeVodContentKey = contentKey
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive && activeVodContentKey == contentKey) {
                delay(PROGRESS_UPDATE_INTERVAL_MILLIS)
                container.catalogueRepository.updateProgress(contentKey, player.currentPosition, player.duration)
            }
        }
    }

    private fun saveVodProgressSnapshot() {
        val contentKey = activeVodContentKey ?: return
        val position = player.currentPosition
        val duration = player.duration
        serviceScope.launch {
            container.catalogueRepository.updateProgress(contentKey, position, duration)
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName == packageName || controller.isTrusted || session.isMediaNotificationController(controller)) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val result = SettableFuture.create<List<MediaItem>>()
            val request = mediaItems.firstOrNull()
            if (request == null || request.mediaId.isBlank()) {
                result.setException(IllegalArgumentException("A channel ID is required"))
                return result
            }
            serviceScope.launch {
                runCatching {
                    releaseActiveSource()
                    val extras = request.mediaMetadata.extras
                    val catchupStart = extras
                        ?.takeIf { it.containsKey(PlaybackRequestExtras.CATCHUP_START_EPOCH_MILLIS) }
                        ?.getLong(PlaybackRequestExtras.CATCHUP_START_EPOCH_MILLIS)
                    val catchupStop = extras
                        ?.takeIf { it.containsKey(PlaybackRequestExtras.CATCHUP_STOP_EPOCH_MILLIS) }
                        ?.getLong(PlaybackRequestExtras.CATCHUP_STOP_EPOCH_MILLIS)
                    val isVod = extras?.getBoolean(PlaybackRequestExtras.VOD_CONTENT, false) == true
                    require((catchupStart == null) == (catchupStop == null)) {
                        "Catch-up request is incomplete"
                    }
                    require(!isVod || catchupStart == null) { "VOD and catch-up cannot be combined" }
                    val source = requireNotNull(
                        if (isVod) {
                            container.playbackRepository.vodSourceFor(request.mediaId)
                        } else if (catchupStart != null && catchupStop != null) {
                            container.playbackRepository.catchupSourceFor(
                                channelId = request.mediaId,
                                programmeStartEpochMillis = catchupStart,
                                programmeStopEpochMillis = catchupStop,
                            )
                        } else {
                            container.playbackRepository.sourceFor(request.mediaId)
                        },
                    ) {
                        "Media is no longer available"
                    }
                    activeSource = source
                    if (isVod) startVodProgressTracking(request.mediaId)
                    request.buildUpon()
                        .setUri(
                            Uri.Builder()
                                .scheme(PLACEHOLDER_SCHEME)
                                .authority(PLACEHOLDER_AUTHORITY)
                                .appendPath(source.channelId)
                                .build(),
                        )
                        // The placeholder URI carries no extension, so the container
                        // has to be declared explicitly or HLS and DASH streams fall
                        // back to a progressive source and never play.
                        .setMimeType(StreamMimeTypes.fromStreamUrl(source.streamUrl))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(
                                    if (catchupStart == null) source.channelName else "Arkisto · ${source.channelName}",
                                )
                                .setIsPlayable(true)
                                .build(),
                        )
                        .build()
                }.fold(
                    onSuccess = { result.set(listOf(it)) },
                    onFailure = {
                        releaseActiveSource()
                        result.setException(it)
                    },
                )
            }
            return result
        }
    }

    companion object {
        const val SESSION_ID = "streammate-live-tv"
        private const val PLACEHOLDER_SCHEME = "streammate"
        private const val PLACEHOLDER_AUTHORITY = "channel"
        private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 10_000L
    }
}
