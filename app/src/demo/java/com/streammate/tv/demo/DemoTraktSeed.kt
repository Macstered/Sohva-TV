package com.streammate.tv.demo

import android.content.Context
import com.sohva.tv.trakt.TraktIds
import com.sohva.tv.trakt.TraktTokens
import com.streammate.tv.core.database.CatalogueMetadataOverrideEntity
import com.streammate.tv.core.database.MetadataCacheEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.TraktStateEntity
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.trakt.TraktAccountStore
import com.streammate.tv.trakt.TraktHomeTitle

/**
 * What a connected Trakt account looks like in the screenshot build: a
 * fictional viewer, pauses and watched marks on library titles, a Watch next
 * shelf and recommendations. Nothing here reaches the network; the demo
 * build's Trakt service is offline.
 */
internal object DemoTraktSeed {
    private const val PROFILE = "default"
    private const val TMDB_SIGNAL = 900_001L
    private const val TMDB_LIGHTHOUSE = 900_002L
    private const val TMDB_HARBOR = 910_001L
    private const val TMDB_NORTH = 910_002L
    private const val TMDB_MERIDIAN = 910_003L
    private const val TMDB_PAPER_MOONS = 900_003L

    suspend fun seed(
        context: Context, database: StreamMateDatabase, cipher: SecretCipher, now: Long,
        movieKey: (String) -> String, seriesKey: (String) -> String,
        moviePosters: List<String>, seriesPosters: List<String>,
    ) {
        val metadata = database.metadataDao()
        // The library's copies matched to (fictional) TMDB records, as the metadata lookup would leave them.
        metadata.upsertCatalogueMetadataOverrides(listOf(
            override(movieKey("signal-at-dawn"), "Signal at Dawn", TMDB_SIGNAL, now),
            override(movieKey("last-lighthouse"), "The Last Lighthouse", TMDB_LIGHTHOUSE, now),
            override(movieKey("paper-moons"), "Paper Moons", TMDB_PAPER_MOONS, now),
            override(seriesKey("harbor-9"), "Harbor 9", TMDB_HARBOR, now),
            override(seriesKey("north-of-tomorrow"), "North of Tomorrow", TMDB_NORTH, now),
        ))
        for ((id, type, title) in listOf(
            Triple(TMDB_SIGNAL, "movie", "Signal at Dawn"), Triple(TMDB_LIGHTHOUSE, "movie", "The Last Lighthouse"),
            Triple(TMDB_PAPER_MOONS, "movie", "Paper Moons"),
            Triple(TMDB_HARBOR, "series", "Harbor 9"), Triple(TMDB_NORTH, "series", "North of Tomorrow"),
        )) {
            metadata.upsert(MetadataCacheEntity(
                lookupKey = "demo:$type:$id", provider = "tmdb", status = "positive", externalId = id.toString(), mediaType = type,
                matchedTitle = title, displayTitle = title, overview = null, posterUrl = null, backdropUrl = null, year = null,
                seasonNumber = null, episodeNumber = null, attributionName = "Demo", attributionUrl = "https://demo.invalid/",
                confidence = 1.0, cachedAtEpochMillis = now, expiresAtEpochMillis = now + 365L * 24 * 60 * 60 * 1000,
            ))
        }

        // Trakt's view: a movie paused on another device, one watched, episodes watched and one paused.
        val hour = 60 * 60_000L
        database.traktStateDao().replace(PROFILE, listOf(
            TraktStateEntity(PROFILE, "movie:tmdb:$TMDB_LIGHTHOUSE", "movie", TMDB_LIGHTHOUSE, null, null, null, 58.0, false, 0, now - 5 * hour),
            TraktStateEntity(PROFILE, "movie:tmdb:$TMDB_PAPER_MOONS", "movie", TMDB_PAPER_MOONS, null, null, null, 0.0, true, 1, now - 2 * 24 * hour),
            TraktStateEntity(PROFILE, "episode:tmdb:$TMDB_HARBOR:1:1", "episode", TMDB_HARBOR, null, 1, 1, 0.0, true, 1, now - 3 * 24 * hour),
            TraktStateEntity(PROFILE, "episode:tmdb:$TMDB_NORTH:1:1", "episode", TMDB_NORTH, null, 1, 1, 0.0, true, 1, now - 8 * 24 * hour),
            TraktStateEntity(PROFILE, "episode:tmdb:$TMDB_NORTH:1:2", "episode", TMDB_NORTH, null, 1, 2, 0.0, true, 1, now - 7 * 24 * hour),
            TraktStateEntity(PROFILE, "episode:tmdb:$TMDB_NORTH:1:3", "episode", TMDB_NORTH, null, 1, 3, 35.0, false, 0, now - 26 * hour),
        ))

        val store = TraktAccountStore(context, cipher)
        store.save(PROFILE, TraktTokens("demo-access", "demo-refresh", now + 365L * 24 * hour), "demo_viewer")
        fun title(kind: String, tmdb: Long, name: String, year: Int, overview: String, poster: String, fanart: String,
            season: Int? = null, number: Int? = null, episodeTitle: String? = null, updatedAt: Long = 0L) =
            TraktHomeTitle(kind, TraktIds(tmdb = tmdb), name, year, overview, poster, fanart, season, number, episodeTitle, updatedAt)
        val harbor = seriesPosters[0]; val north = seriesPosters[1]
        val signal = moviePosters[0]; val lighthouse = moviePosters[1]
        store.saveTitles(PROFILE, TraktAccountStore.NEXT_UP, TraktAccountStore.Titles(now, listOf(
            title("show", TMDB_HARBOR, "Harbor 9", 2026, "A detective returns to the ferry terminal where her partner vanished, and the tide brings back more than the past.",
                harbor, harbor, 1, 2, "The Second Crossing", now - 3 * 24 * hour),
            title("show", TMDB_NORTH, "North of Tomorrow", 2025, "An arctic observatory crew discovers a signal inside the aurora.",
                north, north, 1, 4, "Below the Ice", now - 26 * hour),
            title("show", TMDB_MERIDIAN, "Glass Meridian", 2025, "A cartographer maps a city that keeps rearranging its streets overnight.",
                north, harbor, 2, 1, "New Coordinates", now - 6 * 24 * hour),
        )))
        store.saveTitles(PROFILE, TraktAccountStore.RECOMMENDATIONS, TraktAccountStore.Titles(now, listOf(
            title("movie", 920_001, "The Quiet Array", 2022, "A radio astronomer hears a pattern nobody else believes is there.", signal, signal),
            title("show", 921_001, "Ferry Light", 2021, "Night shifts on a harbour ferry, and the passengers who never disembark.", harbor, harbor),
            title("movie", 920_002, "Storm Warden", 2019, "A lighthouse keeper's last winter before the light is automated.", lighthouse, lighthouse),
            title("show", 921_002, "Observatory", 2024, "A year at the top of the world, one season per episode.", north, north),
            title("movie", 920_003, "Dawn Chorus", 2020, "Two rivals race to decode a transmission from the edge of the solar system.", signal, lighthouse),
            title("show", 921_003, "Cobalt Harbour", 2022, "A port town, a missing ferry and the family who ran it.", harbor, north),
        )))
    }

    private fun override(contentKey: String, title: String, tmdb: Long, now: Long) = CatalogueMetadataOverrideEntity(
        contentKey = contentKey, providerPosterUrl = null, replacementPosterUrl = null, replaceProviderPoster = false,
        replacementTitle = title, externalId = tmdb.toString(), updatedAtEpochMillis = now,
    )
}
