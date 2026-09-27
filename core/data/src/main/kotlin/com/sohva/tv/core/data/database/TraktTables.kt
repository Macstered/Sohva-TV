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
    indices = [Index(value = ["profile_id", "tmdb"]), Index(value = ["profile_id", "imdb"])],
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

    @Query("SELECT COUNT(*) FROM trakt_state WHERE profile_id = :profile")
    fun count(profile: String): Int
}
