package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** What a progress write needs to know about a film: its source and film identity. */
data class FilmFacts(@ColumnInfo(name = "source_id") val sourceId: String, @ColumnInfo(name = "work_key") val workKey: String?)

/** What a progress write needs to know about an episode: its source and its series' key. */
data class EpisodeFacts(@ColumnInfo(name = "source_id") val sourceId: String, @ColumnInfo(name = "series_key") val seriesKey: String)

/** A progress row's key, finished flag and time: enough to decide a watched tick. */
data class TickRow(@ColumnInfo(name = "tick_key") val key: String, val completed: Boolean, @ColumnInfo(name = "updated_at") val updatedAt: Long)

/** An episode of a season, for "Mark season as watched". */
data class SeasonEpisode(val key: String, @ColumnInfo(name = "source_id") val sourceId: String, @ColumnInfo(name = "duration_s") val durationSeconds: Int?)

/**
 * A started, unfinished title for Continue watching (spec 40 VOD-FR-99): the progress row and what
 * the card draws. [name] is the film's, or the series' for an episode.
 */
data class ContinueRow(
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "series_key") val seriesKey: String?,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val name: String,
    @ColumnInfo(name = "replacement_title") val replacementTitle: String?,
    val year: Int?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String?,
    @ColumnInfo(name = "replace_poster") val replacePoster: Boolean,
    @ColumnInfo(name = "external_id") val externalId: String?,
    val season: Int?,
    val number: Int?,
    @ColumnInfo(name = "episode_name") val episodeName: String?,
)

object ProgressSql {
    /**
     * Continue watching (VOD-FR-99), newest first through the (profile, completed, updated_at)
     * index; titles are reached by key, and only visible titles of enabled sources count.
     */
    // One row per film (its copies share the work key) and per series, the most recently watched,
    // before the limit (spec 02 §8: collapsing after the limit let one series fill the row). SQLite
    // takes the other columns from the row that holds MAX(updated_at); the grouping runs over the
    // profile's paused rows only.
    const val CONTINUE_FILMS = "SELECT MAX(w.updated_at) AS updated_at, w.content_key, w.work_key, NULL AS series_key, w.position_ms, w.duration_ms, " +
        "m.name, m.replacement_title, m.year, m.poster_url, m.replacement_poster, m.replace_poster, m.external_id, " +
        "NULL AS season, NULL AS number, NULL AS episode_name FROM watch_progress w CROSS JOIN movie m ON m.key = w.content_key " +
        "CROSS JOIN source src ON src.id = m.source_id WHERE w.profile_id = :profile AND w.completed = 0 AND w.content_type = 'MOVIE' " +
        "AND w.position_ms > 0 AND m.visible = 1 AND src.enabled = 1 AND ${AllowedSql.MOVIES_M} GROUP BY COALESCE(w.work_key, w.content_key) " +
        "ORDER BY updated_at DESC LIMIT :limit"
    const val CONTINUE_EPISODES = "SELECT MAX(w.updated_at) AS updated_at, w.content_key, NULL AS work_key, s.key AS series_key, w.position_ms, w.duration_ms, " +
        "s.name, s.replacement_title, NULL AS year, s.poster_url, s.replacement_poster, s.replace_poster, s.external_id, " +
        "e.season, e.number, e.name AS episode_name FROM watch_progress w CROSS JOIN episode e ON e.key = w.content_key " +
        "CROSS JOIN series s ON s.id = e.series_id CROSS JOIN source src ON src.id = e.source_id " +
        "WHERE w.profile_id = :profile AND w.completed = 0 AND w.content_type = 'EPISODE' AND w.position_ms > 0 " +
        "AND s.visible = 1 AND src.enabled = 1 AND ${AllowedSql.SERIES_S} GROUP BY s.id ORDER BY updated_at DESC LIMIT :limit"
    const val GET = "SELECT * FROM watch_progress WHERE profile_id = :profile AND content_key = :key"
    const val NEWEST_OF_WORK = "SELECT * FROM watch_progress WHERE profile_id = :profile AND work_key = :workKey " +
        "ORDER BY updated_at DESC LIMIT 1"
    const val OF_SERIES = "SELECT * FROM watch_progress WHERE profile_id = :profile AND series_key = :seriesKey"
    const val DELETE = "DELETE FROM watch_progress WHERE profile_id = :profile AND content_key = :key"
    const val DELETE_WORK = "DELETE FROM watch_progress WHERE profile_id = :profile AND work_key = :workKey"
    const val FILM = "SELECT source_id, work_key FROM movie WHERE key = :key"
    const val EPISODE = "SELECT e.source_id, s.key AS series_key FROM episode e CROSS JOIN series s ON s.id = e.series_id WHERE e.key = :key"
    /** Watched ticks (spec 40 VOD-FR-37): ≤ 200 keys per call, own rows and film-identity rows. */
    const val TICKS_OWN = "SELECT content_key AS tick_key, completed, updated_at FROM watch_progress WHERE profile_id = :profile AND content_key IN (:keys)"
    const val TICKS_WORK = "SELECT work_key AS tick_key, completed, updated_at FROM watch_progress WHERE profile_id = :profile AND work_key IN (:workKeys)"
    const val SEASON = "SELECT e.key, e.source_id, e.duration_s FROM series s CROSS JOIN episode e ON e.series_id = s.id " +
        "WHERE s.key = :seriesKey AND e.season = :season"
}

@Dao
interface ProgressDao {
    @Query(ProgressSql.GET)
    fun get(profile: String, key: String): WatchProgressEntity?

    @Query(ProgressSql.NEWEST_OF_WORK)
    fun newestOfWork(profile: String, workKey: String): WatchProgressEntity?

    @Query(ProgressSql.OF_SERIES)
    fun ofSeries(profile: String, seriesKey: String): List<WatchProgressEntity>

    @Upsert
    fun put(row: WatchProgressEntity)

    @Upsert
    fun putAll(rows: List<WatchProgressEntity>)

    @Query(ProgressSql.DELETE)
    fun delete(profile: String, key: String)

    @Query(ProgressSql.DELETE_WORK)
    fun deleteWork(profile: String, workKey: String)

    @Query(ProgressSql.FILM)
    fun film(key: String): FilmFacts?

    @Query(ProgressSql.EPISODE)
    fun episode(key: String): EpisodeFacts?

    @Query(ProgressSql.TICKS_OWN)
    fun ticksOwn(profile: String, keys: List<String>): List<TickRow>

    @Query(ProgressSql.TICKS_WORK)
    fun ticksWork(profile: String, workKeys: List<String>): List<TickRow>

    /** An episode's playlist runtime in milliseconds, 0 when unknown (spec 51 FR-33). */
    @Query("SELECT COALESCE(duration_s, 0) * 1000 FROM episode WHERE key = :key")
    fun episodeRuntime(key: String): Long

    @Query(ProgressSql.SEASON)
    fun season(seriesKey: String, season: Int): List<SeasonEpisode>

    @Query(ProgressSql.CONTINUE_FILMS)
    fun continueFilms(profile: String, limit: Int): List<ContinueRow>

    @Query(ProgressSql.CONTINUE_EPISODES)
    fun continueEpisodes(profile: String, limit: Int): List<ContinueRow>
}
