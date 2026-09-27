package com.sohva.tv.app.player

import com.sohva.tv.app.profile.admit
import com.sohva.tv.core.model.profile.ChannelAdmission
import android.app.ActivityManager
import android.os.Build
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildConfig
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.guide.CatchupRules
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.player.UserAgents
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.net.catchup.CatchupAddress
import com.sohva.tv.core.net.catchup.CatchupRequest
import com.sohva.tv.core.player.ArchiveResult
import com.sohva.tv.core.player.PlaybackClient
import com.sohva.tv.core.player.PlayerEnvironment
import com.sohva.tv.core.player.ResolvedStream
import com.sohva.tv.feature.player.ExternalStream
import com.sohva.tv.feature.player.PlayerEnvironmentUi
import com.sohva.tv.ui.design.R
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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

    override suspend fun resolveArchive(channelKey: String, start: Long, stop: Long): ArchiveResult = withContext(io) {
        val row = graph.data.live.playable(channelKey) ?: return@withContext ArchiveResult.Gone
        val now = graph.clock.wallMillis()
        // The guide offered it, but the clock may have moved past the archive's reach since (CATCH-FR-41).
        if (!CatchupRules.offers(row.catchupType, row.catchupDays, !row.catchupSource.isNullOrBlank(), start, now)) {
            return@withContext ArchiveResult.Unavailable
        }
        val live = runCatching { graph.data.cipher.decrypt(row.streamUrlEnc) }.getOrNull() ?: return@withContext ArchiveResult.Gone
        val request = CatchupRequest(live, row.catchupType, row.catchupSource, row.xtreamStreamId, row.catchupTz, ZoneId.systemDefault(), start, stop, now)
        val address = CatchupAddress.build(request) ?: return@withContext ArchiveResult.Unavailable
        val title = com.sohva.tv.app.AppLocales.texts(graph.app).getString(R.string.player_archive_title, row.name)
        ArchiveResult.Ready(ResolvedStream(row.key, address, row.sourceId, row.sourceName, row.connectionLimit, title, row.userAgent, row.referrer))
    }

    override suspend fun resolveVod(contentKey: String): ResolvedStream? = withContext(io) {
        val title = graph.data.titles.playable(contentKey) ?: return@withContext null
        val address = runCatching { graph.data.cipher.decrypt(title.streamUrlEnc) }.getOrNull() ?: return@withContext null
        // An episode reads "Series · S1 E2 · Title"; a film its own name.
        val name = if (title.seriesName == null) {
            title.title
        } else {
            listOf(title.seriesName, com.sohva.tv.app.AppLocales.texts(graph.app).getString(R.string.series_episode_label, title.season ?: 0, title.number ?: 0), title.title)
                .filter { !it.isNullOrBlank() }.joinToString(" · ")
        }
        ResolvedStream(title.key, address, title.sourceId, title.sourceName, title.connectionLimit, name, null, null)
    }

    /** An addon stream by its in-memory token (spec 50 FR-95); unknown after a process restart. */
    override suspend fun resolveAddon(token: String): ResolvedStream? = graph.addonPlayback?.resolve(token)

    /** Trakt (spec 51 FR-13, -16): the scrobbler starts silent and learns its item off the main thread. */
    override fun vodScrobbler(contentKey: String): com.sohva.tv.core.model.player.TitleScrobbler? {
        val trakt = graph.trakt ?: return null
        if (!trakt.configured) return null
        val profile = graph.data.profiles.activeId
        val main = kotlinx.coroutines.CoroutineScope(graph.appScope.coroutineContext + graph.dispatchers.main)
        val scrobbler = trakt.scrobbler(profile, main)
        main.launch {
            val item = if (trakt.scrobbles(profile)) runCatching { com.sohva.tv.app.trakt.TraktVodIdentity(graph).item(contentKey) }.getOrNull() else null
            scrobbler.begin(item)
        }
        return scrobbler
    }

    override suspend fun saveProgress(contentKey: String, positionMs: Long, durationMs: Long) =
        graph.data.progress.save(contentKey, positionMs, durationMs)

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

        override val remoteMapping: Flow<RemoteMapping> = graph.data.preferences.remoteMapping.flowOn(io)

        override suspend fun recordWatched(channelKey: String) = withContext(io) {
            graph.data.live.recordWatched(channelKey)
            graph.data.preferences.setLastChannel(graph.data.profiles.activeId, channelKey)
        }

        override suspend fun admit(channelKey: String): ChannelAdmission = graph.admit(channelKey)

        override fun logFailure(line: String) = graph.diagnostics.info("player", line)

        override val addon: com.sohva.tv.feature.player.AddonPlaybackEnv? get() = graph.addonPlayback

        override val ticker: com.sohva.tv.feature.player.ScoreTickerSource? get() = if (graph.flags.sport) graph.sport.ticker else null

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
