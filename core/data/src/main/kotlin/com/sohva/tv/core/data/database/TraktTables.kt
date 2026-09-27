package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

/**
 * The profile's Trakt paused positions and watched marks (spec 51 §6, plan/04 §4.9): a cache,
 * replaced per sync, never a history of its own. [key] is `movie:<id>` or
 * `episode:<showId>:<season>:<number>`, the id `tmdb:<n>` when Trakt gave one, else `imdb:<tt…>`.
 */
@Entity(
    tableName = "trakt_state",
    primaryKeys = ["profile_id", "key"],
    // (profile_id, progress): Continue watching reads the few paused rows without walking the history.
    indices = [Index(value = ["profile_id", "tmdb"]), Index(value = ["profile_id", "imdb"]), Index(value = ["profile_id", "progress"])],
)
data class TraktStateEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val key: String,
    /** `movie` or `episode`. */
    val kind: String,
    val tmdb: Long?,
    val imdb: String?,
    val season: Int?,
    val number: Int?,
    /** Percent, 0 when not paused. */
    val progress: Double,
    val watched: Boolean,
    val plays: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A library title's metadata match, for Trakt's TMDB-only identity (spec 51 FR-13). */
data class TraktMatchRow(val provider: String?, @ColumnInfo(name = "external_id") val externalId: String?)

/** A library film copy for a Trakt pause (FR-34): the lowest key per film identity is used. */
data class TraktFilmCopy(
    val key: String,
    @ColumnInfo(name = "work_key") val workKey: String,
    val name: String,
    @ColumnInfo(name = "replacement_title") val replacementTitle: String?,
    val year: Int?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String?,
    @ColumnInfo(name = "replace_poster") val replacePoster: Boolean,
)

/** A library series TMDB confirmed (FR-32: TVmaze ids look alike and never count). */
data class TraktSeriesCopy(
    val tmdb: String,
    val id: Long,
    val key: String,
    val name: String,
    @ColumnInfo(name = "replacement_title") val replacementTitle: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String?,
    @ColumnInfo(name = "replace_poster") val replacePoster: Boolean,
)

/** One episode of a library series, with its playlist runtime (FR-33). */
data class TraktEpisodeCopy(
    val key: String,
    val season: Int,
    val number: Int,
    val name: String?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Int?,
)

/** An episode's series and numbers (FR-13). */
data class TraktEpisodeRow(@ColumnInfo(name = "series_key") val seriesKey: String, val season: Int, val number: Int)

@Dao
interface TraktDao {
    @Query("SELECT provider, external_id FROM metadata_match WHERE content_key = :contentKey AND status = 'matched'")
    fun match(contentKey: String): TraktMatchRow?

    @Query("SELECT s.key AS series_key, e.season, e.number FROM episode e JOIN series s ON s.id = e.series_id WHERE e.key = :episodeKey")
    fun episode(episodeKey: String): TraktEpisodeRow?

    /** One page of a profile's rows of one kind, in key order (keyset paging, AGENTS §4 rule 2). */
    @Query("SELECT * FROM trakt_state WHERE profile_id = :profile AND kind = :kind AND key > :after ORDER BY key LIMIT :limit")
    fun page(profile: String, kind: String, after: String, limit: Int): List<TraktStateEntity>

    @Upsert
    fun upsert(rows: List<TraktStateEntity>)

    @Query("DELETE FROM trakt_state WHERE profile_id = :profile AND key IN (:keys)")
    fun delete(profile: String, keys: List<String>)

    @Query("DELETE FROM trakt_state WHERE profile_id = :profile")
    fun deleteProfile(profile: String)

    /** Rows for the titles on screen, by IMDb id (index (profile_id, imdb)); ≤ 200 ids per call. */
    @Query("SELECT * FROM trakt_state WHERE profile_id = :profile AND imdb IN (:ids)")
    fun byImdb(profile: String, ids: List<String>): List<TraktStateEntity>

    /** Rows for the titles on screen, by TMDB id (index (profile_id, tmdb)); ≤ 200 ids per call. */
    @Query("SELECT * FROM trakt_state WHERE profile_id = :profile AND tmdb IN (:ids)")
    fun byTmdb(profile: String, ids: List<Long>): List<TraktStateEntity>

    /** Rows by key, for the titles a page shows (FR-32 scoped variants); ≤ 200 keys per call. */
    @Query(TraktSql.BY_KEYS)
    fun byKeys(profile: String, keys: List<String>): List<TraktStateEntity>

    /** Paused rows (FR-34), through the (profile_id, progress) index; Trakt keeps ≤ 500, the caller sorts them. */
    @Query(TraktSql.PAUSED)
    fun paused(profile: String, limit: Int): List<TraktStateEntity>

    /** A series' TMDB id, only when TMDB produced the match (FR-32). */
    @Query(TraktSql.SERIES_TMDB)
    fun seriesTmdb(seriesKey: String): String?

    @Query(TraktSql.SERIES_EPISODES)
    fun seriesEpisodes(seriesKey: String): List<TraktEpisodeCopy>

    /** Visible, allowed copies of these film identities on enabled sources (FR-34); the caller keeps the lowest key. */
    @Query(TraktSql.FILM_COPIES)
    fun filmCopies(profile: String, workKeys: List<String>): List<TraktFilmCopy>

    /** Visible, allowed series TMDB matched to these ids (FR-34); the caller keeps the lowest key. */
    @Query(TraktSql.SERIES_COPIES)
    fun seriesCopies(profile: String, tmdbs: List<String>): List<TraktSeriesCopy>

    /** One episode of a series on enabled sources (FR-34); the caller keeps the lowest key. */
    @Query(TraktSql.EPISODE_AT)
    fun episodeAt(seriesId: Long, season: Int, number: Int): List<TraktEpisodeCopy>

    /** A Trakt card's film in the library (FR-30): the first copy of that film identity. */
    @Query(TraktSql.FILM_ROUTE)
    fun filmRoute(workKey: String): String?

    /** A Trakt card's series in the library (FR-30): only a series TMDB matched. */
    @Query(TraktSql.SERIES_ROUTE)
    fun seriesRoute(tmdb: String): String?

    @Query("SELECT COUNT(*) FROM trakt_state WHERE profile_id = :profile")
    fun count(profile: String): Int
}

/** Trakt's library lookups (spec 51 FR-32 to -34): each starts from keys, never from the catalogue (§9). */
object TraktSql {
    const val BY_KEYS = "SELECT * FROM trakt_state WHERE profile_id = :profile AND key IN (:keys)"
    const val PAUSED = "SELECT * FROM trakt_state WHERE profile_id = :profile AND progress > 0 AND progress < 100 LIMIT :limit"
    const val SERIES_TMDB = "SELECT external_id FROM metadata_match WHERE content_key = :seriesKey AND status = 'matched' AND provider = 'tmdb'"
    const val SERIES_EPISODES = "SELECT e.key, e.season, e.number, e.name, e.duration_s FROM series s CROSS JOIN episode e ON e.series_id = s.id " +
        "WHERE s.key = :seriesKey"
    const val FILM_COPIES = "SELECT m.key, m.work_key, m.name, m.replacement_title, m.year, m.poster_url, m.replacement_poster, m.replace_poster " +
        "FROM movie m CROSS JOIN source src ON src.id = m.source_id WHERE m.work_key IN (:workKeys) AND m.visible = 1 " +
        "AND src.enabled = 1 AND ${AllowedSql.MOVIES_M}"
    const val SERIES_COPIES = "SELECT mm.external_id AS tmdb, s.id, s.key, s.name, s.replacement_title, s.poster_url, s.replacement_poster, s.replace_poster " +
        "FROM metadata_match mm CROSS JOIN series s ON s.key = mm.content_key WHERE mm.external_id IN (:tmdbs) " +
        "AND mm.status = 'matched' AND mm.provider = 'tmdb' AND s.visible = 1 AND ${AllowedSql.SERIES_S}"
    const val FILM_ROUTE = "SELECT MIN(key) FROM movie WHERE work_key = :workKey"
    const val SERIES_ROUTE = "SELECT MIN(content_key) FROM metadata_match WHERE external_id = :tmdb AND provider = 'tmdb' " +
        "AND status = 'matched' AND media_type = 'series'"
    const val EPISODE_AT = "SELECT e.key, e.season, e.number, e.name, e.duration_s FROM episode e CROSS JOIN source src ON src.id = e.source_id " +
        "WHERE e.series_id = :seriesId AND e.season = :season AND e.number = :number AND src.enabled = 1"
}
