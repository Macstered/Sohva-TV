package com.sohva.tv.app.discover

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.player.ResolvedStream
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.StreamKind
import com.sohva.tv.feature.player.AddonPlay
import com.sohva.tv.feature.player.AddonPlaybackEnv
import java.util.UUID
import com.sohva.tv.app.AppLocales
import com.sohva.tv.feature.discover.protocol.Hashes
import com.sohva.tv.feature.discover.ui.failureRes
import com.sohva.tv.feature.player.SubtitleCandidate
import com.sohva.tv.feature.player.SubtitleDownload
import com.sohva.tv.feature.player.SubtitleResults
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Trakt (spec 51 FR-14, -17): the catalog's own ids; custom ids and other types send nothing. */
    override fun scrobbler(token: String): com.sohva.tv.core.model.player.TitleScrobbler? {
        val trakt = graph.trakt ?: return null
        val p = host.playback(token) ?: return null
        val item = com.sohva.tv.feature.trakt.scrobble.TraktItems.addon(p.identity.mediaType, p.identity.mediaId, p.identity.videoId, p.season, p.episode) ?: return null
        val main = kotlinx.coroutines.CoroutineScope(graph.appScope.coroutineContext + graph.dispatchers.main)
        val scrobbler = trakt.scrobbler(p.profile, main)
        main.launch { scrobbler.begin(item.takeIf { trakt.scrobbles(p.profile) }) }
        return scrobbler
    }

    // ---- Subtitles (spec 50 §4.13) ----

    /** A candidate behind its session-only key: its address and provider (null: the stream's own). */
    private class Candidate(val url: String, val provider: String?)

    /** Keys of the latest playback only (performance rule 4: one playback's candidates at a time). */
    private val candidates = HashMap<String, Candidate>()
    private var candidatesOf: String? = null

    private fun remember(token: String, key: String, candidate: Candidate) = synchronized(candidates) {
        if (candidatesOf != token) {
            candidates.clear()
            candidatesOf = token
        }
        candidates[key] = candidate
    }

    private fun key(token: String, url: String): String = Hashes.parts(token, url).take(KEY_LENGTH)

    override fun subtitles(token: String): Flow<SubtitleResults> = channelFlow {
        val p = host.playback(token) ?: return@channelFlow send(SubtitleResults())
        val inline = p.stream.subtitles.mapIndexed { i, s ->
            val k = key(token, s.url)
            remember(token, k, Candidate(s.url, null))
            SubtitleCandidate(k, null, s.lang, i + 1)
        }
        val providers = runCatching {
            withContext(io) { host.sources.providers(p.profile, p.videoType, p.identity.videoId, "subtitles") }
        }.getOrDefault(emptyList())
        val answers = arrayOfNulls<List<SubtitleCandidate>>(providers.size)
        val errors = arrayOfNulls<String>(providers.size)
        val lock = Mutex()
        suspend fun publish() = lock.withLock {
            val done = providers.indices.count { answers[it] != null || errors[it] != null }
            // A file offered twice (the stream and an addon, or one addon twice) is offered once.
            send(SubtitleResults((inline + answers.filterNotNull().flatten()).distinctBy { it.key }, providers.size - done, errors.filterNotNull()))
        }
        publish()
        val texts = AppLocales.texts(graph.app)
        providers.forEachIndexed { index, inst ->
            launch {
                try {
                    val found = host.sources.subtitles(p.profile, inst, p.videoType, p.identity.videoId, p.stream)
                    answers[index] = found.mapIndexed { i, s ->
                        val k = key(token, s.url)
                        remember(token, k, Candidate(s.url, inst.id))
                        SubtitleCandidate(k, inst.name, s.lang, i + 1)
                    }
                } catch (e: AddonException) {
                    errors[index] = "${inst.name}: ${texts.getString(failureRes(e.failure))}"
                }
                publish()
            }
        }
    }

    override suspend fun downloadSubtitle(token: String, key: String): SubtitleDownload {
        val texts = AppLocales.texts(graph.app)
        val failed = SubtitleDownload.Failed(texts.getString(R.string.addon_error_operation))
        val p = host.playback(token) ?: return failed
        val candidate = synchronized(candidates) { candidates[key].takeIf { candidatesOf == token } } ?: return failed
        return AddonSubtitleFiles.download(graph, host, p.profile, candidate.url, candidate.provider)
    }

    override val showAllLanguages: StateFlow<Boolean> get() = host.settings.allLanguages

    override suspend fun setShowAllLanguages(on: Boolean) = withContext(io) { host.settings.setAllLanguages(on) }

    private companion object {
        const val KEY_LENGTH = 16
    }
}
