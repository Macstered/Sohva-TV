package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Live channels (plan/04 §15.3), updated in place by [key] with a [contentHash] of the provider's
 * fields, so an unchanged channel is never rewritten. Effective columns (`epg_id`, `logo_url`,
 * `number`, `display_rank`, `visible`) are the provider's values until channel management (M3)
 * folds in the viewer's edits.
 */
@Entity(
    tableName = "channel",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["group_id", "display_rank"]),
        Index(value = ["source_id", "visible", "display_rank"]),
        Index(value = ["source_id", "display_rank"]),
        Index(value = ["source_id", "number"]),
        Index(value = ["source_id", "epg_id"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "tvg_id") val tvgId: String?,
    @ColumnInfo(name = "epg_id") val epgId: String?,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "stream_url_enc") val streamUrlEnc: String,
    @ColumnInfo(name = "user_agent") val userAgent: String?,
    val referrer: String?,
    @ColumnInfo(name = "playlist_order") val playlistOrder: Int,
    @ColumnInfo(name = "provider_number") val providerNumber: Int?,
    val number: Int?,
    @ColumnInfo(name = "display_rank") val displayRank: Long,
    val visible: Boolean,
    @ColumnInfo(name = "catchup_type") val catchupType: String?,
    @ColumnInfo(name = "catchup_source") val catchupSource: String?,
    @ColumnInfo(name = "catchup_days") val catchupDays: Int?,
    @ColumnInfo(name = "catchup_tz") val catchupTz: String?,
    @ColumnInfo(name = "xtream_stream_id") val xtreamStreamId: String?,
    @ColumnInfo(name = "content_hash") val contentHash: Long,
    val generation: Long,
)

/**
 * Films (plan/04 §15.4). The wall indexes (partial, on `visible` and `primary_copy`) arrive with
 * the walls in M4. A source's rows are walked through the UNIQUE key index: every key starts with
 * its source ([KeyRanges]).
 */
@Entity(
    tableName = "movie",
    indices = [Index(value = ["key"], unique = true)],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val year: Int?,
    val rating: String?,
    @ColumnInfo(name = "rating_x10") val ratingX10: Int?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "stream_url_enc") val streamUrlEnc: String,
    val plot: String?,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    val genre: String?,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "primary_copy") val primaryCopy: Boolean,
    val visible: Boolean,
    @ColumnInfo(name = "item_position") val itemPosition: Int?,
    @ColumnInfo(name = "content_hash") val contentHash: Long,
    val generation: Long,
)

@Entity(
    tableName = "series",
    indices = [Index(value = ["key"], unique = true)],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val year: Int?,
    val rating: String?,
    @ColumnInfo(name = "rating_x10") val ratingX10: Int?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String?,
    val plot: String?,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    val genre: String?,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "primary_copy") val primaryCopy: Boolean,
    val visible: Boolean,
    @ColumnInfo(name = "item_position") val itemPosition: Int?,
    @ColumnInfo(name = "content_hash") val contentHash: Long,
    val generation: Long,
)

/** Episodes; [name] is null when the viewer reads the translated "Episode n" (spec 10 SRC-FR-71). */
@Entity(
    tableName = "episode",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["series_id", "season", "number"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "series_id") val seriesId: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val season: Int,
    val number: Int,
    val name: String?,
    @ColumnInfo(name = "stream_url_enc") val streamUrlEnc: String,
    val plot: String?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Int?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "content_hash") val contentHash: Long,
    val generation: Long,
)
