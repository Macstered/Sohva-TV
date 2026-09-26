package com.sohva.tv.core.data.search

import com.sohva.tv.core.data.database.ChannelHit
import com.sohva.tv.core.data.database.EpisodeHit
import com.sohva.tv.core.data.database.ProgrammeHit
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TitleHit
import com.sohva.tv.core.model.search.SearchTerms
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Channels first, then programmes, at most [SearchReads.LIVE_MAX] together (SEARCH-FR-11). */
data class LiveHits(val channels: List<ChannelHit>, val programmes: List<ProgrammeHit>)

/**
 * Search's groups (spec 03 §4.2), each one bounded query on the read threads. Room's suspend
 * queries carry a cancellation signal, so a superseded search stops its statements (§9). Every
 * group keeps to what the active profile ([profile]) may see, before its limit (spec 04 PROF-FR-23).
 */
class SearchReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val profile: () -> String) {
    private val dao get() = db.search()

    suspend fun live(term: String): LiveHits = withContext(io) {
        val match = SearchTerms.match(term) ?: return@withContext LiveHits(emptyList(), emptyList())
        val who = profile()
        val channels = dao.channels(match, who, LIVE_MAX)
        val programmes = if (channels.size < LIVE_MAX) dao.programmes(match, who, LIVE_MAX - channels.size) else emptyList()
        LiveHits(channels, programmes)
    }

    suspend fun films(term: String): List<TitleHit> = titles(term) { match, sources -> dao.films(match, sources, profile(), TITLES_MAX) }

    suspend fun series(term: String): List<TitleHit> = titles(term) { match, sources -> dao.series(match, sources, profile(), TITLES_MAX) }

    suspend fun episodes(term: String): List<EpisodeHit> = titles(term) { match, sources -> dao.episodes(match, sources, profile(), TITLES_MAX) }

    private suspend fun <T> titles(term: String, read: suspend (String, List<String>) -> List<T>): List<T> = withContext(io) {
        val match = SearchTerms.match(term) ?: return@withContext emptyList()
        val sources = dao.enabledSources()
        if (sources.isEmpty()) emptyList() else read(match, sources)
    }

    companion object {
        const val LIVE_MAX: Int = 80
        const val TITLES_MAX: Int = 40
    }
}
