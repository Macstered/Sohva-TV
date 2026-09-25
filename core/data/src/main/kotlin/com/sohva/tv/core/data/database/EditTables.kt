package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The household's edits of one channel (spec 21 §6, plan/04 §15.3). Imports never touch this
 * table; they copy its effects into `channel`'s effective columns after every playlist import,
 * and an edit applies its own channel at once. A missing row means "as the playlist says".
 */
@Entity(
    tableName = "channel_custom",
    // Walked by source in key order after every import (keyset pages).
    indices = [Index(value = ["source_id", "channel_key"])],
)
data class ChannelCustomEntity(
    @PrimaryKey @ColumnInfo(name = "channel_key") val channelKey: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "custom_name") val customName: String?,
    @ColumnInfo(name = "custom_group_title") val customGroupTitle: String?,
    @ColumnInfo(name = "custom_group_key") val customGroupKey: String?,
    val hidden: Boolean,
    val position: Long?,
    @ColumnInfo(name = "manual_epg_id") val manualEpgId: String?,
    @ColumnInfo(name = "custom_logo_url") val customLogoUrl: String?,
    @ColumnInfo(name = "custom_number") val customNumber: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The household's own channel lists (CHAN-FR-50); at most 1,000. */
@Entity(tableName = "channel_list")
data class ChannelListEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A channel in a list, in the list's own order (CHAN-FR-53); a channel can be in many lists. */
@Entity(
    tableName = "channel_list_member",
    primaryKeys = ["list_id", "channel_key"],
    indices = [Index(value = ["list_id", "sort_order"]), Index(value = ["channel_key"])],
)
data class ChannelListMemberEntity(
    @ColumnInfo(name = "list_id") val listId: String,
    @ColumnInfo(name = "channel_key") val channelKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** A profile's PIN-locked channels (CHAN-FR-70); kept only while a parental PIN exists (M6). */
@Entity(tableName = "locked_channel", primaryKeys = ["profile_id", "channel_key"])
data class LockedChannelEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "channel_key") val channelKey: String,
)

/** A programme or match reminder (spec 22 §6): household, never in backups. */
@Entity(tableName = "reminder", indices = [Index(value = ["start_at"])])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "event_id") val eventId: String?,
    @ColumnInfo(name = "channel_key") val channelKey: String?,
    val title: String,
    val subtitle: String?,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
