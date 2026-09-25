package com.sohva.tv.core.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val leases = ConnectionLeases()
    private var lease: ConnectionLeases.Lease? = null
    private var profile = BufferProfile.DEFAULT

    // The main dispatcher is the player's application thread (Q-10 keeps the dedicated looper open).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        env = (application as PlayerHost).playerEnvironment()
        registry = StreamRegistry(env.userAgent)
        player = PlayerFactory.create(this, env, registry, profile)
        player.addListener(Watcher())
        session = MediaSession.Builder(this, player)
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
        releaseStream()
        scope.cancel()
        session.release()
        player.release()
        env.setPlaybackActive(false)
        super.onDestroy()
    }

    private fun releaseStream() {
        lease?.release()
        lease = null
        registry.clear()
    }

    private inner class Watcher : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = env.setPlaybackActive(isPlaying)

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
                // A catch-up request carries the programme's start and stop (spec 22 CATCH-FR-40).
                val extras = item.requestMetadata.extras
                val stream = if (extras != null && extras.containsKey(EXTRA_ARCHIVE_START)) {
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
                val taken = leases.acquire(stream.sourceId, stream.connectionLimit)
                if (taken == null) {
                    val extras = Bundle().apply {
                        putString(PlaybackErrors.EXTRA_SOURCE_NAME, stream.sourceName)
                        putInt(PlaybackErrors.EXTRA_LIMIT, stream.connectionLimit)
                    }
                    fail(controller, PlaybackErrors.CONNECTION_LIMIT, extras)
                    result.setException(IllegalStateException("Connection limit reached"))
                    return@launch
                }
                lease = taken
                val placeholder = registry.register(stream)
                val item = MediaItem.Builder()
                    .setMediaId(stream.key)
                    .setUri(placeholder)
                    .setMimeType(StreamRegistry.mimeType(stream.address))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(stream.title).build())
                    .build()
                result.set(mutableListOf(item))
            }
            return result
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
        session.player = next
        player.release()
        player = next
    }

    companion object {
        const val SESSION_ID: String = "streammate-live-tv"
        const val EXTRA_CODE: String = "com.sohva.tv.player.CODE"

        /** Request extras of a catch-up item: the programme's start and stop, epoch milliseconds. */
        const val EXTRA_ARCHIVE_START: String = "com.sohva.tv.player.ARCHIVE_START"
        const val EXTRA_ARCHIVE_STOP: String = "com.sohva.tv.player.ARCHIVE_STOP"
    }
}
