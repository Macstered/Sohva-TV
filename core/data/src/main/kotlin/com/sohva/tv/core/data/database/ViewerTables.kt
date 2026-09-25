package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Viewer data carries a profile id from the first table (plan/02 principle 4, plan/04 §15.8).
 * Profiles arrive in M6; until then every row belongs to [DEFAULT_PROFILE]. Channels are named by
 * their stable key, so favourites survive re-imports and backups.
 */
@Entity(
    tableName = "favourite_channel",
    primaryKeys = ["profile_id", "channel_key"],
    indices = [Index(value = ["profile_id", "added_at"])],
)
data class FavouriteChannelEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "channel_key") val channelKey: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** The profile's recently watched channels, trimmed to [RECENT_LIMIT] on every write. */
@Entity(
    tableName = "recent_channel",
    primaryKeys = ["profile_id", "channel_key"],
    indices = [Index(value = ["profile_id", "watched_at"])],
)
data class RecentChannelEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "channel_key") val channelKey: String,
    @ColumnInfo(name = "watched_at") val watchedAt: Long,
)

const val DEFAULT_PROFILE: String = "default"
const val RECENT_LIMIT: Int = 20

/**
 * Where the profile got to in a film or episode (plan/04 §15.8, spec 40 §4.12). Films carry their
 * film identity ([workKey]) so every copy of a film resumes from the copy played last; episodes
 * carry their series' key for History and Continue watching. Rows are keyed by content key, not
 * row id, so they survive a re-import.
 */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profile_id", "content_key"],
    indices = [
        Index(value = ["profile_id", "completed", "updated_at"]),
        Index(value = ["profile_id", "content_type", "updated_at", "content_key"]),
        Index(value = ["profile_id", "work_key", "updated_at"]),
        Index(value = ["profile_id", "series_key", "updated_at"]),
        // The metadata queue puts watched titles first, whoever watched them (spec 41 META-FR-65).
        Index(value = ["content_key"]),
    ],
)
data class WatchProgressEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    /** [CONTENT_MOVIE] or [CONTENT_EPISODE]. */
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "series_key") val seriesKey: String?,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val completed: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

const val CONTENT_MOVIE: String = "MOVIE"
const val CONTENT_EPISODE: String = "EPISODE"
