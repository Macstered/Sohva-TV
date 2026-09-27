package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

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
}
