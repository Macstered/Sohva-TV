package com.sohva.tv.app.trakt

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.vod.ContentKeys
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.net.metadata.MetadataProvider
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import com.sohva.tv.feature.trakt.scrobble.TraktItems

/**
 * A library title as a Trakt item (spec 51 FR-13): the TMDB id the library's matching assigned,
 * only when TMDB produced it (TVmaze ids look alike and are never used); otherwise one lookup by
 * name and year, accepted only when TMDB answers. Nothing else identifies a title to Trakt.
 */
class TraktVodIdentity(private val graph: AppGraph) {
    suspend fun item(contentKey: String): TraktItem? {
        if (ContentKeys.isEpisode(contentKey)) {
            val episode = graph.data.traktLibrary.episode(contentKey) ?: return null
            val show = seriesTmdb(episode.seriesKey) ?: return null
            return TraktItem.Episode(TraktIds(tmdb = show), episode.season, episode.number).takeIf { episode.season >= 0 && episode.number >= 0 }
        }
        return filmTmdb(contentKey)?.let { TraktItem.Movie(TraktIds(tmdb = it)) }
    }

    private suspend fun filmTmdb(key: String): Long? {
        graph.data.traktLibrary.match(key)?.let { m -> return TraktItems.tmdbId(m.provider, m.externalId) }
        val film = graph.data.titles.film(key) ?: return null
        return lookup(MetadataRequest(MediaType.MOVIE, film.name, film.year, contentKey = key))
    }

    private suspend fun seriesTmdb(key: String): Long? {
        graph.data.traktLibrary.match(key)?.let { m -> return TraktItems.tmdbId(m.provider, m.externalId) }
        val series = graph.data.titles.series(key) ?: return null
        return lookup(MetadataRequest(MediaType.SERIES, series.name, series.year, contentKey = key))
    }

    private suspend fun lookup(request: MetadataRequest): Long? {
        val record = graph.metadata.enrich(request) ?: return null
        return TraktItems.tmdbId(if (record.provider == MetadataProvider.TMDB) "tmdb" else null, record.externalId)
    }
}
