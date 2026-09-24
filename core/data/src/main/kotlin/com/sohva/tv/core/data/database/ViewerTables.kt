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
