package com.streammate.tv.trakt

import com.sohva.tv.trakt.TraktIds
import com.sohva.tv.trakt.TraktItem
import com.streammate.tv.core.database.CatalogueDao
import com.streammate.tv.core.database.MetadataDao
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.seriesContentKey

/**
 * Turns a VOD content key into something Trakt can address by TMDB id.
 *
 * The library's background enrichment assigns ids over time, so a title may
 * not have one yet when it is played. In that case the same lookup the
 * details screens use runs on demand: cached when the title was seen before,
 * one provider request otherwise. Series may have been matched by TVmaze,
 * whose numeric ids look identical, so only ids TMDB produced are accepted.
 * There is no name matching against Trakt itself.
 */
class TraktVodIdentity(
    private val dao: CatalogueDao, private val metadataDao: MetadataDao,
    private val catalogue: CatalogueRepository, private val metadata: MetadataRepository,
) {
    suspend fun resolve(contentKey: String): TraktItem? {
        val parts = contentKey.split(':', limit = 4)
        if (parts.size != 4 || parts[0] != "vod") return null
        val sourceId = parts[2]
        return when (parts[1]) {
            "movie" -> {
                val id = confirmed(dao.metadataExternalId(contentKey), "movie") ?: run {
                    val movie = catalogue.movie(contentKey) ?: return null
                    lookup(MetadataLookup(MetadataMediaType.MOVIE, movie.name, movie.year), "movie")
                } ?: return null
                TraktItem.Movie(TraktIds(tmdb = id))
            }
            "episode" -> {
                val episode = dao.activeEpisode(sourceId, parts[3])
                if (episode == null) { DiagnosticsLog.i(TAG, "episode not in an active catalogue"); return null }
                val show = confirmed(dao.metadataExternalId(seriesContentKey(sourceId, episode.seriesId)), "series") ?: run {
                    val series = catalogue.series(sourceId, episode.seriesId) ?: return null
                    lookup(MetadataLookup(MetadataMediaType.SERIES, series.name, series.year), "series")
                } ?: return null
                TraktItem.Episode(TraktIds(tmdb = show), episode.seasonNumber, episode.episodeNumber)
            }
            else -> null
        }
    }

    /** An id the library already assigned, if TMDB was the provider that produced it. */
    private suspend fun confirmed(externalId: String?, kind: String): Long? {
        val raw = externalId?.trim()?.removePrefix("tmdb:")?.takeIf { it.isNotEmpty() } ?: return null
        val id = raw.toLongOrNull()?.takeIf { it > 0 } ?: return null
        if (metadataDao.providerKnows(TMDB, raw) == 0) { DiagnosticsLog.i(TAG, "$kind library id is not from TMDB"); return null }
        return id
    }

    private suspend fun lookup(lookup: MetadataLookup, kind: String): Long? {
        val match = metadata.enrich(lookup)
        if (match == null) { DiagnosticsLog.i(TAG, "$kind not matched by any metadata provider"); return null }
        if (match.provider != TMDB) { DiagnosticsLog.i(TAG, "$kind matched by ${match.provider}, not TMDB"); return null }
        return match.externalId.toLongOrNull()?.takeIf { it > 0 }
    }

    private companion object { const val TAG = "Trakt"; const val TMDB = "tmdb" }
}
