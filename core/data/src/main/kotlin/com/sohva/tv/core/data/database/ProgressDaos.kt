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

object ProgressSql {
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

    @Query(ProgressSql.SEASON)
    fun season(seriesKey: String, season: Int): List<SeasonEpisode>
}
