package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert

/** A channel's edit row with the channel's playlist values and its current shown values. */
data class EditedChannelRow(
    @Embedded val custom: ChannelCustomEntity,
    @ColumnInfo(name = "channel_id") val channelId: Long,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "provider_group_id") val providerGroupId: Long?,
    @ColumnInfo(name = "provider_logo_url") val providerLogoUrl: String?,
    @ColumnInfo(name = "tvg_id") val tvgId: String?,
    @ColumnInfo(name = "provider_number") val providerNumber: Int?,
    @ColumnInfo(name = "playlist_order") val playlistOrder: Int,
    @ColumnInfo(name = "cur_name") val name: String,
    @ColumnInfo(name = "cur_group_id") val groupId: Long?,
    @ColumnInfo(name = "cur_logo_url") val logoUrl: String?,
    @ColumnInfo(name = "cur_number") val number: Int?,
    @ColumnInfo(name = "cur_epg_id") val epgId: String?,
    @ColumnInfo(name = "cur_visible") val visible: Boolean,
    @ColumnInfo(name = "cur_display_rank") val displayRank: Long,
)

/** A channel's playlist values, for applying or undoing one edit. */
data class ProviderChannelRow(
    val id: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "provider_group_id") val providerGroupId: Long?,
    @ColumnInfo(name = "provider_logo_url") val providerLogoUrl: String?,
    @ColumnInfo(name = "tvg_id") val tvgId: String?,
    @ColumnInfo(name = "provider_number") val providerNumber: Int?,
    @ColumnInfo(name = "playlist_order") val playlistOrder: Int,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val visible: Boolean,
)

/** The household's channel edits (spec 21). Writes run on the bulk or user-write dispatcher. */
@Dao
interface ChannelEditDao {
    @Query(EditSql.EDITED_PAGE)
    fun editedPage(sourceId: String, afterKey: String, limit: Int): List<EditedChannelRow>

    @Query(EditSql.PROVIDER_ROW)
    fun providerRow(key: String): ProviderChannelRow?

    @Query(EditSql.CUSTOM)
    fun custom(key: String): ChannelCustomEntity?

    @Upsert
    fun putCustom(row: ChannelCustomEntity)

    @Query("DELETE FROM channel_custom WHERE channel_key = :key")
    fun deleteCustom(key: String)

    @Query(EditSql.UPDATE_SHOWN)
    fun updateShown(
        id: Long,
        name: String,
        sortName: String,
        groupId: Long?,
        logoUrl: String?,
        number: Int?,
        epgId: String?,
        visible: Boolean,
        displayRank: Long,
    )

    @Query(EditSql.GROUP_BY_KEY)
    fun groupId(sourceId: String, groupKey: String): Long?

    @Query(EditSql.RECOUNT_GROUP)
    fun recountGroup(groupId: Long)
}

object EditSql {
    // CROSS JOIN pins the walk to channel_custom's (source_id, channel_key) index, then looks each
    // channel up by its unique key (AGENTS §4.3).
    const val EDITED_PAGE: String =
        "SELECT cc.*, c.id AS channel_id, c.provider_name, c.provider_group_id, c.provider_logo_url, c.tvg_id, " +
            "c.provider_number, c.playlist_order, c.name AS cur_name, c.group_id AS cur_group_id, c.logo_url AS cur_logo_url, " +
            "c.number AS cur_number, c.epg_id AS cur_epg_id, c.visible AS cur_visible, c.display_rank AS cur_display_rank " +
            "FROM channel_custom cc CROSS JOIN channel c ON c.key = cc.channel_key " +
            "WHERE cc.source_id = :sourceId AND cc.channel_key > :afterKey ORDER BY cc.channel_key LIMIT :limit"

    const val PROVIDER_ROW: String =
        "SELECT id, source_id, provider_name, provider_group_id, provider_logo_url, tvg_id, provider_number, playlist_order, " +
            "group_id, visible FROM channel WHERE key = :key"

    const val CUSTOM: String = "SELECT * FROM channel_custom WHERE channel_key = :key"

    const val UPDATE_SHOWN: String =
        "UPDATE channel SET name = :name, sort_name = :sortName, group_id = :groupId, logo_url = :logoUrl, number = :number, " +
            "epg_id = :epgId, visible = :visible, display_rank = :displayRank WHERE id = :id"

    const val GROUP_BY_KEY: String = "SELECT id FROM content_group WHERE source_id = :sourceId AND room = 'LIVE' AND group_key = :groupKey"

    // One group's shown channels, through the (group_id, display_rank) index: a single edit only.
    const val RECOUNT_GROUP: String =
        "UPDATE content_group SET item_count = (SELECT COUNT(*) FROM channel WHERE group_id = :groupId AND visible = 1) WHERE id = :groupId"
}
