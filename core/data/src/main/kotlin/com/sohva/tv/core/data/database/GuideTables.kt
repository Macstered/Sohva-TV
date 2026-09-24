package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The guide keeps snapshots (plan/04 §15.5): programmes have no stable identity, so an import
 * writes a new snapshot number, flips `source_status.epg_snapshot`, then deletes the old one in
 * chunks. Readers join the active snapshot only.
 */
@Entity(tableName = "epg_channel", primaryKeys = ["source_id", "snapshot", "epg_id"])
data class EpgChannelEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    val snapshot: Long,
    @ColumnInfo(name = "epg_id") val epgId: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "icon_url") val iconUrl: String?,
)

@Entity(
    tableName = "programme",
    indices = [Index(value = ["source_id", "snapshot", "epg_id", "start_at"])],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val snapshot: Long,
    @ColumnInfo(name = "epg_id") val epgId: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "stop_at") val stopAt: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    /** `<category>` texts joined with U+001F. */
    val categories: String?,
    /** The 16-hex programme id of plan/04 §6, which reminders keep. */
    @ColumnInfo(name = "programme_key") val programmeKey: String,
)
