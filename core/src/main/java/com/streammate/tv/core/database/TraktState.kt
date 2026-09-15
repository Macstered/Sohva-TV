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
    val tmdbId: Long? = null,
    val imdbId: String? = null,
)

@Dao
abstract class TraktStateDao {
    @Query(TRAKT_CONTINUE_WATCHING_SQL)
    abstract fun observeVodContinueWatching(profileId: String): Flow<List<TraktContinueRow>>

    @Query("SELECT * FROM trakt_state WHERE profileId = :profileId")
    abstract fun observe(profileId: String): Flow<List<TraktStateEntity>>

    @Query(TRAKT_MOVIE_OVERLAY_SQL)
    abstract fun observeVodMovies(profileId: String): Flow<List<TraktVodStateRow>>

    @Query(TRAKT_EPISODE_OVERLAY_SQL)
    abstract fun observeVodEpisodes(profileId: String): Flow<List<TraktVodStateRow>>

    @Query(TRAKT_SELECTED_MOVIES_SQL)
    abstract fun observeSelectedMovies(profileId: String, contentKeys: List<String>): Flow<List<TraktVodStateRow>>

    @Query(TRAKT_SELECTED_SERIES_SQL)
    abstract fun observeSelectedSeries(profileId: String, sourceId: String, seriesId: String): Flow<List<TraktVodStateRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rows: List<TraktStateEntity>)

    @Query("DELETE FROM trakt_state WHERE profileId = :profileId")
    abstract suspend fun deleteProfile(profileId: String)

    @Query("SELECT * FROM trakt_state WHERE profileId=:profileId AND `key` IN (:keys)")
    protected abstract suspend fun rowsForKeys(profileId: String, keys: List<String>): List<TraktStateEntity>

    @Query("DELETE FROM trakt_state WHERE profileId=:profileId AND watched=0")
    protected abstract suspend fun deleteUnwatched(profileId: String)

    @Query("UPDATE trakt_state SET progress=0 WHERE profileId=:profileId AND progress!=0")
    protected abstract suspend fun clearPausedPositions(profileId: String)

    /** A complete playback response can refresh the row while the larger watched lists are still loading. */
    @Transaction
    open suspend fun replacePlayback(profileId: String, paused: List<TraktStateEntity>) {
        val previous = paused.map { it.key }.chunked(500).flatMap { rowsForKeys(profileId, it) }.associateBy { it.key }
        deleteUnwatched(profileId)
        clearPausedPositions(profileId)
        val merged = paused.map { row ->
            previous[row.key]?.let { row.copy(watched = it.watched, plays = it.plays, updatedAtMillis = maxOf(row.updatedAtMillis, it.updatedAtMillis)) } ?: row
        }
        merged.chunked(500).forEach { insertAll(it) }
    }

    /** The whole picture for a profile at once, so a card never sees half a sync. */
    @Transaction
    open suspend fun replace(profileId: String, rows: List<TraktStateEntity>) {
        deleteProfile(profileId)
        if (rows.isNotEmpty()) rows.chunked(500).forEach { insertAll(it) }
    }
}
