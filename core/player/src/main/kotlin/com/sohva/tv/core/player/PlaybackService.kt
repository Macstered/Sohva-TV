package com.sohva.tv.core.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sohva.tv.core.model.player.BufferProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one playback engine (spec 30 §4.1): an ExoPlayer in a media session service. The screen sends
 * a media item whose id is the channel key; the service resolves the stream, takes a connection
 * lease, and hands the player a placeholder address, so no stream address enters the session.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var env: PlayerEnvironment
    private lateinit var registry: StreamRegistry
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var keeper: SurfaceKeeper
    private val leases = ConnectionLeases()
    private var lease: ConnectionLeases.Lease? = null
    private var profile = BufferProfile.DEFAULT

    /** The film or episode playing, whose position is saved (spec 30 §4.21); null for live and catch-up. */
    private var vodKey: String? = null

    /** The film or episode's Trakt scrobbles (spec 51 FR-16); never for live or catch-up. */
    private var scrobbler: com.sohva.tv.core.model.player.TitleScrobbler? = null
    private var progressJob: Job? = null

    // The main dispatcher is the player's application thread (Q-10 keeps the dedicated looper open).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Progress writes outlive the service: the last one starts in onDestroy, after which [scope] is cancelled.
    private val saves = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        env = (application as PlayerHost).playerEnvironment()
        registry = StreamRegistry(env.userAgent)
        player = PlayerFactory.create(this, env, registry, profile)
        player.addListener(Watcher())
        keeper = SurfaceKeeper(player, output = null)
        session = MediaSession.Builder(this, keeper)
            .setId(SESSION_ID)
            .setCallback(Callback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    /** Removing the task stops the stream and the service (PLAY-FR-21). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        player.clearMediaItems()
        stopSelf()
    }

    override fun onDestroy() {
        saveProgress()
        releaseStream()
        scope.cancel()
        session.release()
        player.release()
        env.setPlaybackActive(false)
        super.onDestroy()
    }

    private fun releaseStream() {
        progressJob?.cancel()
        scrobbler?.release(player.currentPosition, durationOrZero())
        scrobbler = null
        vodKey = null
        lease?.release()
        lease = null
        registry.clear()
    }

    /**
     * Saves the VOD position now: every 10 s while it plays, when it stops (pause, stall, stop,
     * the next item), at its end and when the service goes (PLAY-FR-130). The position is read on
     * the player's thread; the store writes on its own.
     */
    private fun saveProgress() {
        val key = vodKey ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        saves.launch { runCatching { env.saveProgress(key, position, duration) } }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(PROGRESS_MS)
                if (player.isPlaying) {
                    saveProgress()
                    scrobbler?.progress(player.currentPosition, durationOrZero())
                }
            }
        }
    }

    private fun durationOrZero(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    private inner class Watcher : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            env.setPlaybackActive(isPlaying)
            if (!isPlaying) saveProgress()
            scrobbler?.playing(isPlaying, player.currentPosition, durationOrZero())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                saveProgress()
                scrobbler?.ended()
            }
        }

        /** Clearing the items releases the source and its lease (PLAY-FR-23). */
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (player.mediaItemCount == 0) releaseStream()
        }
    }

    private inner class Callback : MediaSession.Callback {
        /** Only the app itself, trusted system controllers and the notification (PLAY-FR-12). */
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val allowed = controller.packageName == packageName || controller.isTrusted || session.isMediaNotificationController(controller)
            return if (allowed) super.onConnect(session, controller) else MediaSession.ConnectionResult.reject()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val result = SettableFuture.create<MutableList<MediaItem>>()
            val item = mediaItems.singleOrNull()
            val request = item?.mediaId?.takeIf { it.isNotBlank() }
            if (request == null) {
                result.setException(IllegalArgumentException("A channel ID is required"))
                return result
            }
            scope.launch {
                // Release the previous stream before taking a lease, so a zap works at limit 1 (PLAY-FR-16).
                releaseStream()
                applyProfile()
                // A catch-up request carries the programme's start and stop (spec 22 CATCH-FR-40);
                // a film or an episode says so (PLAY-FR-01).
                val extras = item.requestMetadata.extras
                val vod = extras?.getBoolean(EXTRA_VOD) == true
                val addon = extras?.getBoolean(EXTRA_ADDON) == true
                val stream = if (addon) {
                    env.resolveAddon(request)
                } else if (vod) {
                    env.resolveVod(request)
                } else if (extras != null && extras.containsKey(EXTRA_ARCHIVE_START)) {
                    when (val archive = env.resolveArchive(request, extras.getLong(EXTRA_ARCHIVE_START), extras.getLong(EXTRA_ARCHIVE_STOP))) {
                        is ArchiveResult.Ready -> archive.stream
                        ArchiveResult.Gone -> null
                        ArchiveResult.Unavailable -> {
                            fail(controller, PlaybackErrors.ARCHIVE_UNAVAILABLE, Bundle.EMPTY)
                            result.setException(IllegalStateException("The archive address cannot be built"))
                            return@launch
                        }
                    }
                } else {
                    env.resolveLive(request)
                }
                if (stream == null) {
                    fail(controller, PlaybackErrors.NO_LONGER_AVAILABLE, Bundle.EMPTY)
                    result.setException(IllegalStateException("Media is no longer available"))
                    return@launch
                }
                // An addon stream is not an IPTV connection: no provider limit applies (spec 50 §4.12).
                val taken = if (addon) null else leases.acquire(stream.sourceId, stream.connectionLimit)
                if (!addon && taken == null) {
                    val extras = Bundle().apply {
                        putString(PlaybackErrors.EXTRA_SOURCE_NAME, stream.sourceName)
                        putInt(PlaybackErrors.EXTRA_LIMIT, stream.connectionLimit)
                    }
                    fail(controller, PlaybackErrors.CONNECTION_LIMIT, extras)
                    result.setException(IllegalStateException("Connection limit reached"))
                    return@launch
                }
                lease = taken
                if (vod) {
                    vodKey = stream.key
                    scrobbler = env.vodScrobbler(stream.key)
                    startProgressLoop()
                }
                val placeholder = registry.register(stream)
                val item = MediaItem.Builder()
                    .setMediaId(stream.key)
                    .setUri(placeholder)
                    .setMimeType(StreamRegistry.mimeType(stream.address))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(stream.title).build())
                    .setSubtitleConfigurations(if (addon || vod) sideSubtitle(extras) else emptyList())
                    .build()
                result.set(mutableListOf(item))
            }
            return result
        }

        /** An addon playback's or a library title's chosen subtitle, kept in memory (spec 50 ADDON-FR-101, spec 30 PLAY-FR-141). */
        private fun sideSubtitle(extras: Bundle?): List<MediaItem.SubtitleConfiguration> {
            val key = extras?.getString(EXTRA_SUBTITLE_KEY) ?: return emptyList()
            val mime = extras.getString(EXTRA_SUBTITLE_MIME) ?: return emptyList()
            return listOf(
                MediaItem.SubtitleConfiguration.Builder(SideSubtitles.uri(key))
                    .setMimeType(mime)
                    .setLanguage(extras.getString(EXTRA_SUBTITLE_LANGUAGE))
                    .setId(SideSubtitles.TRACK_ID)
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build(),
            )
        }

        /** Tells the screen why nothing plays, so it can say so in words (PLAY-FR-95). */
        @OptIn(UnstableApi::class)
        private fun fail(controller: MediaSession.ControllerInfo, code: Int, extras: Bundle) {
            val payload = Bundle(extras).apply { putInt(EXTRA_CODE, code) }
            session.sendError(controller, SessionError(SessionError.ERROR_UNKNOWN, "code:$code", payload))
        }
    }

    /** A changed profile applies when nothing is loaded (PLAY-FR-87): the idle player is rebuilt. */
    private suspend fun applyProfile() {
        val wanted = env.settings().buffer
        if (wanted == profile || player.mediaItemCount != 0) return
        profile = wanted
        val next = PlayerFactory.create(this, env, registry, profile)
        next.addListener(Watcher())
        // The picture's surface goes with the player: freed by the old one before the new one takes it.
        val output = keeper.output
        player.clearVideoSurface()
        keeper = SurfaceKeeper(next, output)
        session.player = keeper
        player.release()
        player = next
        // PLAY-26: the next playback runs on the chosen buffer profile.
        env.log.info("player", "buffer profile ${wanted.name}")
    }

    companion object {
        const val SESSION_ID: String = "streammate-live-tv"
        const val EXTRA_CODE: String = "com.sohva.tv.player.CODE"

        /** Request extras of a catch-up item: the programme's start and stop, epoch milliseconds. */
        const val EXTRA_ARCHIVE_START: String = "com.sohva.tv.player.ARCHIVE_START"
        const val EXTRA_ARCHIVE_STOP: String = "com.sohva.tv.player.ARCHIVE_STOP"

        /** Request extra of a film or an episode (boolean). */
        const val EXTRA_VOD: String = "com.sohva.tv.player.VOD"

        /** An addon stream by its token (spec 50 §4.12): Discover writes its own progress. */
        const val EXTRA_ADDON: String = "com.sohva.tv.player.ADDON"

        /** An addon playback's side-loaded subtitle: its [SideSubtitles] key, MIME type and language. */
        const val EXTRA_SUBTITLE_KEY: String = "com.sohva.tv.player.SUBTITLE_KEY"
        const val EXTRA_SUBTITLE_MIME: String = "com.sohva.tv.player.SUBTITLE_MIME"
        const val EXTRA_SUBTITLE_LANGUAGE: String = "com.sohva.tv.player.SUBTITLE_LANGUAGE"

        const val PROGRESS_MS: Long = 10_000
    }
}
