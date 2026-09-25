package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Live channels (plan/04 §15.3), updated in place by [key] with a [contentHash] of the provider's
 * fields, so an unchanged channel is never rewritten. The effective columns (`name`, `sort_name`,
 * `group_id`, `logo_url`, `number`, `epg_id`, `display_rank`, `visible`) are what every screen
 * reads: the provider's values with the household's edits from `channel_custom` applied. The
 * `provider_*` columns (and `tvg_id`, `provider_number`, `playlist_order`) keep the playlist's own
 * values, so an edit can be undone without a re-import (spec 21 CHAN-FR-31).
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
        // Channel management's A–Z pages (spec 21 CHAN-NFR-01), per source like its Playlist order.
        Index(value = ["source_id", "sort_name"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "provider_name", defaultValue = "") val providerName: String,
    @ColumnInfo(name = "provider_group_id") val providerGroupId: Long?,
    @ColumnInfo(name = "provider_logo_url") val providerLogoUrl: String?,
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
 * Films (plan/04 §15.4). A source's rows are walked through the UNIQUE key index: every key starts
 * with its source ([KeyRanges]). The wall indexes lead with their equality columns and end with
 * `sort_name` (and the implicit row id), so a wall page is an index range read in order. They are
 * full indexes rather than plan/04's partial ones: Room validates every index it finds against the
 * entities and cannot declare a `WHERE` clause (decision "Wall indexes", M4).
 */
@Entity(
    tableName = "movie",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["group_id", "visible", "group_primary", "sort_name"]),
        Index(value = ["visible", "primary_copy", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "sort_name"]),
        // The other content orders (spec 42 ORG-07): newest/oldest, rating, and manual in a group.
        Index(value = ["group_id", "visible", "group_primary", "year", "sort_name"]),
        Index(value = ["group_id", "visible", "group_primary", "rating_x10", "sort_name"]),
        Index(value = ["group_id", "visible", "group_primary", "item_position", "sort_name"]),
        Index(value = ["visible", "primary_copy", "year", "sort_name"]),
        Index(value = ["visible", "primary_copy", "rating_x10", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "year", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "rating_x10", "sort_name"]),
        // A group's members in row order, for the organisation pass (spec 42 §9.1).
        Index(value = ["group_id"]),
        Index(value = ["work_key"]),
        Index(value = ["similar_key"]),
        Index(value = ["replacement_key"]),
    ],
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
    /** [com.sohva.tv.core.model.vod.QualityChip] bits read from the name at import. */
    @ColumnInfo(name = "quality_mask", defaultValue = "0") val qualityMask: Int = 0,
    /** [com.sohva.tv.core.model.vod.CopyLanguage] bits read from the name at import. */
    @ColumnInfo(name = "claim_mask", defaultValue = "0") val claimMask: Int = 0,
    /** The "largest picture" rank of spec 40 VOD-FR-29. */
    @ColumnInfo(name = "picture_rank", defaultValue = "0") val pictureRank: Int = 0,
    /** Metadata's title (spec 40 VOD-FR-22) and its search form; null until matched. */
    @ColumnInfo(name = "replacement_title") val replacementTitle: String? = null,
    @ColumnInfo(name = "replacement_sort") val replacementSort: String? = null,
    /** Metadata's poster (a TMDB path or an https address) and whether it replaces the provider's (VOD-FR-34). */
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String? = null,
    @ColumnInfo(name = "replace_poster", defaultValue = "0") val replacePoster: Boolean = false,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    /** [com.sohva.tv.core.model.metadata.TitleCleaner.normalizeTitle] of the provider and the replacement title: Similar looks titles up by these (spec 40 §9.7). */
    @ColumnInfo(name = "similar_key") val similarKey: String? = null,
    @ColumnInfo(name = "replacement_key") val replacementKey: String? = null,
    val genre: String?,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "primary_copy") val primaryCopy: Boolean,
    /**
     * The copy that stands for its film in its own group's wall: a film appears in every group
     * that carries a copy (spec 40 VOD-FR-24), while [primaryCopy] stands for it once on walls
     * that span groups.
     */
    @ColumnInfo(name = "group_primary", defaultValue = "1") val groupPrimary: Boolean = true,
    val visible: Boolean,
    @ColumnInfo(name = "item_position") val itemPosition: Int?,
    @ColumnInfo(name = "content_hash") val contentHash: Long,
    val generation: Long,
)

/** Series: as films plus a backdrop; never folded, so `primary_copy` stays true. */
@Entity(
    tableName = "series",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["group_id", "visible", "primary_copy", "sort_name"]),
        Index(value = ["visible", "primary_copy", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "sort_name"]),
        Index(value = ["group_id", "visible", "primary_copy", "year", "sort_name"]),
        Index(value = ["group_id", "visible", "primary_copy", "rating_x10", "sort_name"]),
        Index(value = ["group_id", "visible", "primary_copy", "item_position", "sort_name"]),
        Index(value = ["visible", "primary_copy", "year", "sort_name"]),
        Index(value = ["visible", "primary_copy", "rating_x10", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "year", "sort_name"]),
        Index(value = ["genre", "visible", "primary_copy", "rating_x10", "sort_name"]),
        Index(value = ["group_id"]),
    ],
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
    @ColumnInfo(name = "quality_mask", defaultValue = "0") val qualityMask: Int = 0,
    /** Metadata's title (spec 40 VOD-FR-22) and its search form; null until matched. */
    @ColumnInfo(name = "replacement_title") val replacementTitle: String? = null,
    @ColumnInfo(name = "replacement_sort") val replacementSort: String? = null,
    /** Metadata's poster (a TMDB path or an https address) and whether it replaces the provider's (VOD-FR-34). */
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String? = null,
    @ColumnInfo(name = "replace_poster", defaultValue = "0") val replacePoster: Boolean = false,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    /** [com.sohva.tv.core.model.metadata.TitleCleaner.normalizeTitle] of the provider and the replacement title: Similar looks titles up by these (spec 40 §9.7). */
    @ColumnInfo(name = "similar_key") val similarKey: String? = null,
    @ColumnInfo(name = "replacement_key") val replacementKey: String? = null,
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
