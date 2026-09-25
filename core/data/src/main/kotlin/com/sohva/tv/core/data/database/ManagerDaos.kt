package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** One row of channel management's list and editor (spec 21 §4.2–4.3): shown values and the playlist's. */
data class ManagedChannel(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val name: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    val number: Int?,
    @ColumnInfo(name = "provider_number") val providerNumber: Int?,
    @ColumnInfo(name = "tvg_id") val tvgId: String?,
    @ColumnInfo(name = "epg_id") val epgId: String?,
    val visible: Boolean,
    @ColumnInfo(name = "display_rank") val rank: Long,
    @ColumnInfo(name = "sort_name") val sortName: String,
)

/** A source for the Source button, disabled ones included (CHAN-FR-10, -11). */
data class ManagerSource(val id: String, val name: String)

/** A channel of a source's XMLTV feed, for the guide mapping picker (CHAN-FR-24, CHAN-NFR-06). */
data class EpgChannelOption(
    @ColumnInfo(name = "epg_id") val epgId: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
)

/**
 * Channel management's reads (spec 21 CHAN-NFR-01): keyset pages of ≤ 200 within one source,
 * along `(source_id, display_rank)` for Playlist order or `(source_id, sort_name)` for A–Z, with
 * the hidden, group and search filters in SQL. No query returns every channel.
 */
@Dao
interface ManagerDao {
    @Query("SELECT id, name FROM source ORDER BY priority DESC, name, id")
    suspend fun sources(): List<ManagerSource>

    @Query(ManagerSql.PLAYLIST_PAGE)
    suspend fun playlistPage(
        sourceId: String,
        showHidden: Boolean,
        groupName: String?,
        pattern: String?,
        afterRank: Long,
        afterId: Long,
        limit: Int,
    ): List<ManagedChannel>

    @Query(ManagerSql.NAME_PAGE)
    suspend fun namePage(
        sourceId: String,
        showHidden: Boolean,
        groupName: String?,
        pattern: String?,
        afterName: String,
        afterId: Long,
        limit: Int,
    ): List<ManagedChannel>

    /** The row just before (rank, id) under the same filters: the neighbour above on screen (CHAN-FR-29). */
    @Query(ManagerSql.PLAYLIST_BEFORE)
    suspend fun playlistBefore(sourceId: String, showHidden: Boolean, groupName: String?, pattern: String?, rank: Long, id: Long): ManagedChannel?

    @Query(ManagerSql.COUNT)
    suspend fun count(sourceId: String, showHidden: Boolean, groupName: String?, pattern: String?): Int

    /** The shown group titles for the chips, in the source's list order (CHAN-FR-12). */
    @Query(ManagerSql.GROUP_NAMES)
    suspend fun groupNames(sourceId: String): List<String>

    @Query("SELECT * FROM channel_custom WHERE channel_key = :key")
    suspend fun custom(key: String): ChannelCustomEntity?

    /** Channels of [sourceId] whose logo is a phone-sent file (spec 21 §6: deleted with the source). */
    @Query("SELECT channel_key FROM channel_custom WHERE source_id = :sourceId AND custom_logo_url LIKE 'file:%'")
    suspend fun phoneLogoKeys(sourceId: String): List<String>

    @Query(ManagerSql.EPG_OPTIONS)
    suspend fun epgOptions(sourceId: String, pattern: String?, afterName: String, afterId: String, limit: Int): List<EpgChannelOption>

    @Query(ManagerSql.EPG_NAME)
    suspend fun epgName(sourceId: String, epgId: String): String?
}

object ManagerSql {
    const val PAGE: Int = 200

    private const val COLUMNS =
        "c.id, c.key, c.source_id, c.name, c.provider_name, g.name AS group_name, c.logo_url, c.number, c.provider_number, " +
            "c.tvg_id, c.epg_id, c.visible, c.display_rank, c.sort_name"

    // The filters: hidden channels only with Show hidden (CHAN-FR-13); the chosen group by its shown
    // title; the search text in the shown name (sort form) or the group title.
    private const val FILTERS =
        "c.source_id = :sourceId AND (:showHidden OR c.visible = 1) AND (:groupName IS NULL OR g.name = :groupName) " +
            "AND (:pattern IS NULL OR c.sort_name LIKE :pattern ESCAPE '\\' OR g.name LIKE :pattern ESCAPE '\\')"

    const val PLAYLIST_PAGE: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_source_id_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE $FILTERS " +
            "AND (c.display_rank > :afterRank OR (c.display_rank = :afterRank AND c.id > :afterId)) " +
            "ORDER BY c.display_rank, c.id LIMIT :limit"

    const val PLAYLIST_BEFORE: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_source_id_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE $FILTERS " +
            "AND (c.display_rank < :rank OR (c.display_rank = :rank AND c.id < :id)) " +
            "ORDER BY c.display_rank DESC, c.id DESC LIMIT 1"

    const val NAME_PAGE: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_source_id_sort_name " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE $FILTERS " +
            "AND (c.sort_name > :afterName OR (c.sort_name = :afterName AND c.id > :afterId)) " +
            "ORDER BY c.sort_name, c.id LIMIT :limit"

    const val COUNT: String =
        "SELECT COUNT(*) FROM channel c INDEXED BY index_channel_source_id_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE $FILTERS"

    const val GROUP_NAMES: String =
        "SELECT name FROM content_group WHERE room = 'LIVE' AND source_id = :sourceId AND item_count >= 0 " +
            "ORDER BY position, provider_order, id"

    // The active guide snapshot's channels, by display name then id (CHAN-FR-24), searchable.
    const val EPG_OPTIONS: String =
        "SELECT e.epg_id, e.display_name FROM epg_channel e CROSS JOIN source_status s " +
            "ON s.source_id = e.source_id AND s.kind = 'epg' AND e.snapshot = s.epg_snapshot " +
            "WHERE e.source_id = :sourceId AND (:pattern IS NULL OR e.display_name LIKE :pattern ESCAPE '\\' OR e.epg_id LIKE :pattern ESCAPE '\\') " +
            "AND (IFNULL(e.display_name, '') > :afterName OR (IFNULL(e.display_name, '') = :afterName AND e.epg_id > :afterId)) " +
            "ORDER BY IFNULL(e.display_name, ''), e.epg_id LIMIT :limit"

    const val EPG_NAME: String =
        "SELECT e.display_name FROM epg_channel e CROSS JOIN source_status s " +
            "ON s.source_id = e.source_id AND s.kind = 'epg' AND e.snapshot = s.epg_snapshot " +
            "WHERE e.source_id = :sourceId AND e.epg_id = :epgId"
}
