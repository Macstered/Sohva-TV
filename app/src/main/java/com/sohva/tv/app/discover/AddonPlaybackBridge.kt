package com.sohva.tv.app.discover

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.player.ResolvedStream
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.StreamKind
import com.sohva.tv.feature.player.AddonPlay
import com.sohva.tv.feature.player.AddonPlaybackEnv
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The player's view of Discover (spec 50 §4.12, §4.14): streams by in-memory token, the 5 s
 * re-validation, progress snapshots on the app scope, and Retry with a fresh source. URLs and
 * headers stay in memory and never reach a log.
 */
class AddonPlaybackBridge(private val graph: AppGraph, private val host: DiscoverHost) : AddonPlaybackEnv {
    private val io get() = graph.dispatchers.io

    /** What the player shows while the stream starts; null when the token is unknown (a restarted process). */
    fun play(token: String): AddonPlay? = host.playback(token)?.let { p ->
        AddonPlay(p.token, p.startMs, p.title, p.artwork.background, p.logo, p.traktFraction)
    }

    /** The playback service's side: the chosen HTTP stream with its own headers (FR-95). */
    fun resolve(token: String): ResolvedStream? {
        val p = host.playback(token) ?: return null
        val url = p.stream.url?.takeIf { p.stream.kind == StreamKind.HTTP } ?: return null
        return ResolvedStream("addon:$token", url, "addon", p.source.name, 0, p.title, null, null, addonHeaders = p.stream.headers)
    }

    override suspend fun stillAllowed(token: String): Boolean {
        val p = host.playback(token) ?: return false
        if (p.stream.kind != StreamKind.HTTP || p.stream.url == null) return false
        return withContext(io) { host.sources.stillValid(p.profile, p.source, p.videoType, p.identity.videoId, p.identity.installation.ifEmpty { null }) }
    }

    override fun saveProgress(token: String, positionMs: Long, durationMs: Long?, ended: Boolean, sequence: Long, failed: () -> Unit) {
        val p = host.playback(token) ?: return
        // The app scope: the last snapshot outlives the player screen (FR-106).
        graph.appScope.launch(io) {
            try {
                host.progress.save(p.profile, p.identity, p.savedTitle, p.artwork, positionMs, durationMs, ended, p.session, sequence)
            } catch (e: AddonException) {
                failed()
            } catch (e: android.database.SQLException) {
                failed()
            }
        }
    }

    override suspend fun freshToken(token: String): String? {
        val p = host.playback(token) ?: return null
        return try {
            val stream = host.sources.fresh(p.profile, p.source, p.videoType, p.identity.videoId, p.stream)
            val fresh = p.withStream(UUID.randomUUID().toString(), stream)
            host.keepPlayback(fresh)
            fresh.token
        } catch (e: AddonException) {
            null
        }
    }

    override fun milestone(name: String, sinceStartMs: Long) = graph.diagnostics.info("addon", "$name ${sinceStartMs}ms")
}
