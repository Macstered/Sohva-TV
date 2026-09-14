package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * What Trakt knows about one title for one profile: a paused position, a
 * watched mark, or both. A local cache of the account's playback and watched
 * lists, replaced wholesale on each sync; never a history of its own.
 *
 * [key] is `movie:<tmdb>` or `episode:<showTmdb>:<season>:<number>` when the
 * TMDB id is known, otherwise the IMDb form `movie:<imdb>` / `episode:<imdb>:…`.
 */
@Entity(
    tableName = "trakt_state",
    primaryKeys = ["profileId", "key"],
    indices = [Index(value = ["profileId", "tmdb"]), Index(value = ["profileId", "imdb"])],
)
data class TraktStateEntity(
    val profileId: String,
    val key: String,
    /** `movie` or `episode`. */
    val kind: String,
    /** The movie's or the show's ids, whichever Trakt returned. */
    val tmdb: Long?,
    val imdb: String?,
    val season: Int?,
    val number: Int?,
    /** Paused position as a percentage, 0 when there is none. */
    val progress: Double,
    val watched: Boolean,
    val plays: Int,
    /** When Trakt last saw activity on it: the pause time or the last watch. */
    val updatedAtMillis: Long,
)

/** A Trakt row mapped onto one VOD content key, with the copy's runtime where the playlist gave one. */
data class TraktVodStateRow(
    val contentKey: String,
    val durationSeconds: Int?,
    val progress: Double,
    val watched: Boolean,
    val updatedAtMillis: Long,
)

/** A Trakt pause on a library title, with what a Home card needs to show it. */
data class TraktContinueRow(
    val contentKey: String,
    val contentType: String,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val seriesName: String?,
    val seriesKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val durationSeconds: Int?,
    val progress: Double,
    val updatedAtMillis: Long,
)

@Dao
abstract class TraktStateDao {
    /**
     * Paused Trakt titles the library carries, for Home's Continue Watching.
     * Joined through the base tables rather than the organization views, which
     * scan the whole catalogue when joined.
     */
    @Query(
        """
        SELECT metadata.contentKey AS contentKey, 'movie' AS contentType, movie.name AS title, movie.year AS year,
            movie.posterUrl AS posterUrl, NULL AS seriesName, NULL AS seriesKey, NULL AS seasonNumber, NULL AS episodeNumber,
            NULL AS durationSeconds, state.progress AS progress, state.updatedAtMillis AS updatedAtMillis
        FROM trakt_state state
        INNER JOIN catalogue_metadata_overrides metadata ON metadata.externalId = CAST(state.tmdb AS TEXT)
        INNER JOIN vod_movies movie ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state import ON import.sourceId = movie.sourceId AND import.kind = 'catalogue' AND import.activeSnapshotId = movie.snapshotId
        WHERE state.profileId = :profileId AND state.kind = 'movie' AND state.tmdb IS NOT NULL
            AND state.progress > 0 AND state.progress < 100
        UNION ALL
        SELECT 'vod:episode:' || episode.sourceId || ':' || episode.episodeId AS contentKey, 'episode' AS contentType,
            episode.name AS title, NULL AS year, item.posterUrl AS posterUrl, item.name AS seriesName, metadata.contentKey AS seriesKey,
            episode.seasonNumber AS seasonNumber, episode.episodeNumber AS episodeNumber,
            episode.durationSeconds AS durationSeconds, state.progress AS progress, state.updatedAtMillis AS updatedAtMillis
        FROM trakt_state state
        INNER JOIN catalogue_metadata_overrides metadata ON metadata.externalId = CAST(state.tmdb AS TEXT)
            AND metadata.contentKey LIKE 'series:%'
        INNER JOIN vod_episodes episode ON metadata.contentKey = 'series:' || episode.sourceId || ':' || episode.seriesId
            AND episode.seasonNumber = state.season AND episode.episodeNumber = state.number
        INNER JOIN vod_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state import ON import.sourceId = item.sourceId AND import.kind = 'catalogue' AND import.activeSnapshotId = item.snapshotId
        WHERE state.profileId = :profileId AND state.kind = 'episode' AND state.tmdb IS NOT NULL
            AND state.progress > 0 AND state.progress < 100
            AND EXISTS (SELECT 1 FROM metadata_cache cache WHERE cache.provider = 'tmdb' AND cache.externalId = metadata.externalId)
        ORDER BY updatedAtMillis DESC
        LIMIT 20
        """,
    )
    abstract fun observeVodContinueWatching(profileId: String): Flow<List<TraktContinueRow>>

    @Query("SELECT * FROM trakt_state WHERE profileId = :profileId")
    abstract fun observe(profileId: String): Flow<List<TraktStateEntity>>

    /**
     * Trakt movies joined to the library's copies through the TMDB id the
     * metadata lookup assigned. Movies are only ever matched by TMDB.
     */
    @Query(
        """
        SELECT metadata.contentKey AS contentKey, NULL AS durationSeconds,
            state.progress AS progress, state.watched AS watched, state.updatedAtMillis AS updatedAtMillis
        FROM trakt_state state
        INNER JOIN catalogue_metadata_overrides metadata ON metadata.externalId = CAST(state.tmdb AS TEXT)
        INNER JOIN vod_movies movie ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE state.profileId = :profileId AND state.kind = 'movie' AND state.tmdb IS NOT NULL
        """,
    )
    abstract fun observeVodMovies(profileId: String): Flow<List<TraktVodStateRow>>

    /**
     * Trakt episodes joined to library episodes through the series' TMDB id and
     * the season and episode numbers. Series can also be matched by TVmaze,
     * whose numeric ids look the same, so the id must be one TMDB produced.
     */
    @Query(
        """
        SELECT 'vod:episode:' || episode.sourceId || ':' || episode.episodeId AS contentKey,
            episode.durationSeconds AS durationSeconds,
            state.progress AS progress, state.watched AS watched, state.updatedAtMillis AS updatedAtMillis
        FROM trakt_state state
        INNER JOIN catalogue_metadata_overrides metadata ON metadata.externalId = CAST(state.tmdb AS TEXT)
            AND metadata.contentKey LIKE 'series:%'
        INNER JOIN vod_episodes episode ON metadata.contentKey = 'series:' || episode.sourceId || ':' || episode.seriesId
            AND episode.seasonNumber = state.season AND episode.episodeNumber = state.number
        WHERE state.profileId = :profileId AND state.kind = 'episode' AND state.tmdb IS NOT NULL
            AND EXISTS (SELECT 1 FROM metadata_cache cache WHERE cache.provider = 'tmdb' AND cache.externalId = metadata.externalId)
        """,
    )
    abstract fun observeVodEpisodes(profileId: String): Flow<List<TraktVodStateRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rows: List<TraktStateEntity>)

    @Query("DELETE FROM trakt_state WHERE profileId = :profileId")
    abstract suspend fun deleteProfile(profileId: String)

    /** The whole picture for a profile at once, so a card never sees half a sync. */
    @Transaction
    open suspend fun replace(profileId: String, rows: List<TraktStateEntity>) {
        deleteProfile(profileId)
        if (rows.isNotEmpty()) rows.chunked(500).forEach { insertAll(it) }
    }
}
