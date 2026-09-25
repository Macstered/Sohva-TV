package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** A recently watched channel as Home shows it (spec 02 HOME-FR-34). */
data class RecentChannelRow(
    val id: Long,
    val key: String,
    val name: String,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    val number: Int?,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "epg_id") val epgId: String?,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int,
)

/** The programme on a channel at one moment, with its times as the guide shows them (offset applied by the caller). */
data class NowProgrammeRow(
    val id: Long,
    val title: String,
    val subtitle: String?,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "stop_at") val stopAt: Long,
)

/**
 * Home's reads (spec 02 §9.3): each starts from its small side (the ≤ 20 recent channel keys; one
 * channel's programmes at one moment) and reaches rows by key or through an index, never a walk
 * of the playlist or the guide.
 */
object HomeSql {
    const val CHANNELS_OF_KEYS = "SELECT c.id, c.key, c.name, c.logo_url, c.number, c.source_id, c.epg_id, src.epg_offset_minutes AS offset_minutes " +
        "FROM channel c CROSS JOIN source src ON src.id = c.source_id WHERE c.key IN (:keys) AND c.visible = 1 AND src.enabled = 1"

    // The last programme to start at or before [at] in the source's active guide, if it has not ended.
    const val PROGRAMME_AT = "SELECT p.id, p.title, p.subtitle, p.start_at, p.stop_at FROM source_status st " +
        "CROSS JOIN programme p ON p.source_id = st.source_id AND p.snapshot = st.epg_snapshot " +
        "WHERE st.source_id = :sourceId AND st.kind = 'epg' AND p.epg_id = :epgId AND p.start_at <= :at " +
        "ORDER BY p.start_at DESC LIMIT 1"
}

@Dao
interface HomeDao {
    @Query(HomeSql.CHANNELS_OF_KEYS)
    suspend fun channelsOfKeys(keys: List<String>): List<RecentChannelRow>

    @Query(HomeSql.PROGRAMME_AT)
    suspend fun programmeAt(sourceId: String, epgId: String, at: Long): NowProgrammeRow?
}
