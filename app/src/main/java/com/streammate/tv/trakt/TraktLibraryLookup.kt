package com.streammate.tv.trakt

import com.streammate.tv.core.database.MetadataDao
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.VodMovie
import com.streammate.tv.iptv.repository.VodSeries

/** Where a Trakt title lives in the library, when the metadata lookup matched a copy to the same TMDB record. */
sealed interface TraktLibraryRoute {
    data class Movie(val movie: VodMovie) : TraktLibraryRoute
    data class Series(val series: VodSeries) : TraktLibraryRoute
}

class TraktLibraryLookup(private val metadata: MetadataDao, private val catalogue: CatalogueRepository) {
    suspend fun route(title: TraktHomeTitle): TraktLibraryRoute? {
        val tmdb = title.ids.tmdb?.toString() ?: return null
        val keys = metadata.contentKeysForExternalId(tmdb)
        return if (title.kind == "movie") {
            keys.firstOrNull { it.startsWith("vod:movie:") }?.let { catalogue.movie(it) }?.let(TraktLibraryRoute::Movie)
        } else {
            // A TVmaze id looks like a TMDB id; only a TMDB-produced match counts.
            if (metadata.providerKnows("tmdb", tmdb) == 0) return null
            keys.firstOrNull { it.startsWith("series:") }?.let { key ->
                val parts = key.split(':', limit = 3)
                if (parts.size == 3) catalogue.series(parts[1], parts[2]) else null
            }?.let(TraktLibraryRoute::Series)
        }
    }
}
