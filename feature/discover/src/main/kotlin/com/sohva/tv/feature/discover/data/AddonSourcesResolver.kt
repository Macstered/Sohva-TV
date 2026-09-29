package com.sohva.tv.feature.discover.data

import com.sohva.tv.feature.discover.net.AddonClient
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonSourceParser
import com.sohva.tv.feature.discover.protocol.AddonStream
import com.sohva.tv.feature.discover.protocol.AddonSubtitle
import com.sohva.tv.feature.discover.protocol.StreamKind
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.InstallationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One provider's answer on a title page (FR-79). */
sealed interface ProviderState {
    data object Loading : ProviderState

    data class Failed(val failure: AddonFailure) : ProviderState

    data class Ready(val streams: List<AddonStream>) : ProviderState
}

class ProviderSources(val installation: Installation, val state: ProviderState)

/**
 * Streams and subtitles for a video (spec 50 §4.11, §4.13): every enabled installation serving the
 * resource answers on its own (the client's four-request limit applies), shown in priority order
 * whatever the arrival order. Never cached: signed URLs are resolved again every time.
 */
class AddonSourcesResolver(
    private val access: DiscoverAccess,
    private val store: InstallationStore,
    private val client: AddonClient,
    private val io: CoroutineDispatcher,
) {
    /** Providers of `stream` for the video, in priority order; empty means none supports it. */
    suspend fun providers(profile: String, type: String, videoId: String, resource: String = "stream"): List<Installation> {
        if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
        return store.list(profile).filter { it.enabled && it.manifest.supports(resource, type, videoId) }
    }

    /** FR-79: a Loading slot per provider at once, then each result as it arrives. */
    fun streams(profile: String, type: String, videoId: String): Flow<List<ProviderSources>> = channelFlow {
        val providers = providers(profile, type, videoId)
        val states = LinkedHashMap<String, ProviderState>()
        providers.forEach { states[it.id] = ProviderState.Loading }
        val lock = Mutex()
        suspend fun publish() = lock.withLock { send(providers.map { ProviderSources(it, states.getValue(it.id)) }) }
        publish()
        providers.forEach { inst ->
            launch {
                val state = try {
                    ProviderState.Ready(fetch(profile, inst, type, videoId))
                } catch (e: AddonException) {
                    ProviderState.Failed(e.failure)
                }
                lock.withLock { states[inst.id] = state }
                publish()
            }
        }
    }

    suspend fun fetch(profile: String, inst: Installation, type: String, videoId: String): List<AddonStream> = withContext(io) {
        val answer = client.get(inst.endpoint.resource("stream", type, videoId))
        val streams = AddonSourceParser.streams(answer.body)
        check(profile, inst, "stream", type, videoId)
        streams
    }

    /**
     * FR-100: subtitle results from every subtitle addon, with only the stream's hash, size and file
     * name as extras (never its headers). One call per provider; the caller shows them as they come.
     * A library title (spec 30 PLAY-FR-141) has no addon stream: no extras at all.
     */
    suspend fun subtitles(profile: String, inst: Installation, type: String, videoId: String, stream: AddonStream?): List<AddonSubtitle> = withContext(io) {
        val extras = buildMap {
            stream?.videoHash?.let { put("videoHash", it) }
            stream?.videoSize?.let { put("videoSize", it.toString()) }
            stream?.filename?.let { put("filename", it) }
        }
        val answer = client.get(inst.endpoint.resource("subtitles", type, videoId, extras))
        val subtitles = AddonSourceParser.subtitles(answer.body)
        check(profile, inst, "subtitles", type, videoId)
        subtitles
    }

    /**
     * FR-84: before playback and every 5 s during it: the profile may still use Discover, the source
     * addon is enabled at the same revision and still serves the video, and so is the title's
     * metadata addon (when one is named).
     */
    suspend fun stillValid(profile: String, source: Installation, type: String, videoId: String, metadata: String?): Boolean = runCatching {
        check(profile, source, "stream", type, videoId)
        if (metadata != null && metadata != source.id) {
            val meta = store.find(profile, metadata) ?: return@runCatching false
            if (!meta.enabled) return@runCatching false
        }
        true
    }.getOrDefault(false)

    /**
     * FR-91 Retry with fresh source: the same provider again, and exactly one stream with the same
     * name and file name (or, without one, the same description); anything else is CONFLICT, so a
     * different provider or quality is never chosen silently.
     */
    suspend fun fresh(profile: String, source: Installation, type: String, videoId: String, chosen: AddonStream): AddonStream {
        val current = store.find(profile, source.id) ?: throw AddonException(AddonFailure.NOT_FOUND)
        val matches = fetch(profile, current, type, videoId).filter { s ->
            s.kind == StreamKind.HTTP && s.name == chosen.name &&
                if (chosen.filename != null) s.filename == chosen.filename else s.description == chosen.description
        }
        return matches.singleOrNull() ?: throw AddonException(AddonFailure.CONFLICT)
    }

    private suspend fun check(profile: String, inst: Installation, resource: String, type: String, videoId: String) {
        if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
        val now = store.find(profile, inst.id) ?: throw AddonException(AddonFailure.NOT_FOUND)
        if (now.revision != inst.revision || !now.enabled) throw AddonException(AddonFailure.CONFLICT)
        if (!now.manifest.supports(resource, type, videoId)) throw AddonException(AddonFailure.UNSUPPORTED_RESOURCE)
    }
}
