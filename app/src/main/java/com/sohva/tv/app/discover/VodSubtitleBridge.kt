package com.sohva.tv.app.discover

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.AppLocales
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.Hashes
import com.sohva.tv.feature.discover.ui.failureRes
import com.sohva.tv.feature.player.SubtitleCandidate
import com.sohva.tv.feature.player.SubtitleDownload
import com.sohva.tv.feature.player.SubtitleResults
import com.sohva.tv.feature.player.SubtitleSource
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Discover's subtitle addons for a film or an episode from the library (spec 30 PLAY-FR-141). The
 * addons are asked by IMDb id (an episode by the series' id, season and episode) and nothing else:
 * never the provider's address, file name or size. The id comes from the title's details (TMDB or
 * TVmaze); a title without one says so in the picker. Candidates are kept in memory for this
 * playback only, by a key made from the title and the file's address.
 */
internal class VodSubtitleBridge(private val graph: AppGraph, private val host: DiscoverHost, private val contentKey: String) : SubtitleSource {
    private val io get() = graph.dispatchers.io
    private val candidates = HashMap<String, Pair<String, String?>>()

    private data class Target(val type: String, val videoId: String)

    /** The title as Discover's addons know it: `movie`/`tt…`, or `series`/`tt…:season:episode`; null without an IMDb id. */
    private suspend fun target(): Target? = withContext(io) {
        val titles = graph.data.titles
        val metadata = graph.metadata.service
        val film = titles.film(contentKey)
        if (film != null) {
            val record = metadata.filmDetails(MetadataRequest(MediaType.MOVIE, film.name, film.year, contentKey = film.key))
            return@withContext record?.imdb?.takeIf { it.startsWith("tt") }?.let { Target("movie", it) }
        }
        val episode = graph.data.traktLibrary.episode(contentKey) ?: return@withContext null
        val series = titles.series(episode.seriesKey) ?: return@withContext null
        val record = metadata.seriesDetails(MetadataRequest(MediaType.SERIES, series.name, series.year, contentKey = series.key))
        record?.imdb?.takeIf { it.startsWith("tt") }?.let { Target("series", "$it:${episode.season}:${episode.number}") }
    }

    override fun subtitles(): Flow<SubtitleResults> = channelFlow {
        val profile = graph.data.profiles.activeId
        val texts = AppLocales.texts(graph.app)
        val target = runCatching { target() }.getOrNull()
        if (target == null) {
            send(SubtitleResults(errors = listOf(texts.getString(R.string.vod_subtitles_no_id))))
            return@channelFlow
        }
        val providers = runCatching {
            withContext(io) { host.sources.providers(profile, target.type, target.videoId, "subtitles") }
        }.getOrDefault(emptyList())
        val answers = arrayOfNulls<List<SubtitleCandidate>>(providers.size)
        val errors = arrayOfNulls<String>(providers.size)
        val lock = Mutex()
        suspend fun publish() = lock.withLock {
            val done = providers.indices.count { answers[it] != null || errors[it] != null }
            // A file offered twice is offered once (the picker's list refuses a repeated key).
            send(SubtitleResults(answers.filterNotNull().flatten().distinctBy { it.key }, providers.size - done, errors.filterNotNull()))
        }
        publish()
        providers.forEachIndexed { index, inst ->
            launch {
                try {
                    val found = host.sources.subtitles(profile, inst, target.type, target.videoId, null)
                    answers[index] = found.mapIndexed { i, s ->
                        val key = Hashes.parts(contentKey, s.url).take(KEY_LENGTH)
                        synchronized(candidates) { candidates[key] = s.url to inst.id }
                        SubtitleCandidate(key, inst.name, s.lang, i + 1)
                    }
                } catch (e: AddonException) {
                    errors[index] = "${inst.name}: ${texts.getString(failureRes(e.failure))}"
                }
                publish()
            }
        }
    }

    override suspend fun download(key: String): SubtitleDownload {
        val (url, provider) = synchronized(candidates) { candidates[key] }
            ?: return SubtitleDownload.Failed(AppLocales.texts(graph.app).getString(R.string.addon_error_operation))
        return AddonSubtitleFiles.download(graph, host, graph.data.profiles.activeId, url, provider)
    }

    override val showAllLanguages: StateFlow<Boolean> get() = host.settings.allLanguages

    override suspend fun setShowAllLanguages(on: Boolean) = withContext(io) { host.settings.setAllLanguages(on) }

    private companion object {
        const val KEY_LENGTH = 16
    }
}
