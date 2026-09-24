package com.sohva.tv.app.player

import android.app.ActivityManager
import android.os.Build
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildConfig
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.UserAgents
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.player.PlaybackClient
import com.sohva.tv.core.player.PlayerEnvironment
import com.sohva.tv.core.player.ResolvedStream
import com.sohva.tv.feature.player.ExternalStream
import com.sohva.tv.feature.player.PlayerEnvironmentUi
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Call

/**
 * The player's narrow view of the graph (plan/03 §4.4): what the playback service reads once in
 * onCreate, and what the player screen needs. Built on first playback.
 */
class PlayerGraph(private val graph: AppGraph) : PlayerEnvironment {
    private val io get() = graph.dispatchers.io

    override val userAgent: String by lazy { UserAgents.sohva(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE) }

    /**
     * HTTP/1.1, connect 20 s, read 90 s, redirects followed (PLAY-FR-18), sharing the provider
     * client's pool; without its fixed agent, so a playlist's own User-Agent reaches the server.
     */
    override val callFactory: Call.Factory by lazy {
        graph.sync.http.client.newBuilder().apply { interceptors().clear() }.build()
    }

    override val lowMemory: Boolean by lazy {
        val manager = graph.app.getSystemService(ActivityManager::class.java)
        manager.isLowRamDevice || manager.memoryClass < LOW_MEMORY_CLASS
    }

    override val log: DiagnosticsLog get() = graph.diagnostics

    override suspend fun resolveLive(channelKey: String): ResolvedStream? = withContext(io) {
        val row = graph.data.live.playable(channelKey) ?: return@withContext null
        val address = runCatching { graph.data.cipher.decrypt(row.streamUrlEnc) }.getOrNull() ?: return@withContext null
        ResolvedStream(row.key, address, row.sourceId, row.sourceName, row.connectionLimit, row.name, row.userAgent, row.referrer)
    }

    override suspend fun settings(): PlaybackSettings = withContext(io) { graph.data.preferences.playback() }

    override fun setPlaybackActive(active: Boolean) {
        graph.playbackActive.value = active
    }

    /** The screen's side, one per player screen. */
    fun screen(locale: Locale): PlayerEnvironmentUi = object : PlayerEnvironmentUi {
        override val reads: LiveReads = graph.liveReads
        override val client: PlaybackClient = PlaybackClient(graph.app)
        override val clock: Clock get() = graph.clock
        override val locale: Locale = locale
        override val format: CoroutineDispatcher get() = graph.dispatchers.ui

        override suspend fun settings(): PlaybackSettings = this@PlayerGraph.settings()

        override suspend fun recordWatched(channelKey: String) = withContext(io) {
            graph.data.live.recordWatched(channelKey)
            graph.data.preferences.setLastChannel(channelKey)
        }

        override fun logFailure(line: String) = graph.diagnostics.info("player", line)

        override suspend fun externalStream(channelKey: String): ExternalStream? {
            val stream = resolveLive(channelKey) ?: return null
            val headers = buildMap {
                stream.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
                stream.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            }
            return ExternalStream(stream.address, headers)
        }
    }

    private companion object {
        /** Spec 30 §9: a memory class under 192 MB counts as low memory for the buffer. */
        const val LOW_MEMORY_CLASS = 192
    }
}
