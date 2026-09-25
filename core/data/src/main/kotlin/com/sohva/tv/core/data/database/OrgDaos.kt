package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

/** A group row as the organisation pass resolves it (spec 42 ORG-FR-12, -19). */
data class OrgGroupRow(
    val id: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val room: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    val name: String,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    val shown: Boolean,
    val position: Int,
    @ColumnInfo(name = "sort_mode") val sortMode: String?,
)

/** A film or series as the pass resolves it: its keys, its group's keys and its stored results. */
data class OrgTitleRow(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "group_key") val groupKey: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    val visible: Boolean,
    @ColumnInfo(name = "item_position") val itemPosition: Int?,
)

/** A channel as the pass resolves it, with the household's own hidden flag (CHAN-FR-27). */
data class OrgChannelRow(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_key") val groupKey: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    val hidden: Boolean,
    val visible: Boolean,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "playlist_order") val playlistOrder: Int,
    /** The viewer's position from Channel management (legacy position, ORG-FR-34). */
    val position: Long?,
    @ColumnInfo(name = "display_rank") val rank: Long,
)

/** A channel of one sorted group, for its place in the group (GUIDE-FR-32). */
data class RankedGroupChannel(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "playlist_order") val playlistOrder: Int,
    val position: Long?,
    @ColumnInfo(name = "display_rank") val rank: Long,
)

/** A provider group row for the library manager (spec 42 ORG-FR-07, -43). */
data class ManagerGroupRow(
    val id: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    val name: String,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    val shown: Boolean,
    val position: Int,
    @ColumnInfo(name = "sort_mode") val sortMode: String?,
)

/** One film, series or channel row of a managed group: narrow, no stream address (spec 42 §9.2). */
data class ManagerItemRow(
    val key: String,
    @ColumnInfo(name = "work_key") val workKey: String?,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_key") val groupKey: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    val name: String,
    @ColumnInfo(name = "shown_name") val shownName: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val year: Int?,
    @ColumnInfo(name = "rating_x10") val ratingX10: Int?,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    val image: String?,
    val visible: Boolean,
    val hidden: Boolean,
    @ColumnInfo(name = "legacy_position") val legacyPosition: Long?,
)

/** A source for the manager's scope button and its "source disabled" notes. */
data class ManagerSourceRow(val id: String, val name: String, val enabled: Boolean)

/** A custom channel list as a manager group (ORG-FR-40). */
data class ManagerListRow(val id: String, val name: String, @ColumnInfo(name = "sort_order") val sortOrder: Int, val members: Int)

object OrgSql {
    const val FILMS_PAGE = "SELECT m.id, m.key, m.source_id, m.work_key, g.group_key, g.name AS group_name, m.visible, m.item_position " +
        "FROM movie m LEFT JOIN content_group g ON g.id = m.group_id WHERE m.key > :after AND m.key < :until ORDER BY m.key LIMIT :limit"
    const val SERIES_PAGE = "SELECT s.id, s.key, s.source_id, s.work_key, g.group_key, g.name AS group_name, s.visible, s.item_position " +
        "FROM series s LEFT JOIN content_group g ON g.id = s.group_id WHERE s.key > :after AND s.key < :until ORDER BY s.key LIMIT :limit"
    const val FILMS_OF_WORK = "SELECT m.id, m.key, m.source_id, m.work_key, g.group_key, g.name AS group_name, m.visible, m.item_position " +
        "FROM movie m LEFT JOIN content_group g ON g.id = m.group_id WHERE m.work_key IN (:workKeys) OR m.key IN (:keys)"
    const val SERIES_OF_KEYS = "SELECT s.id, s.key, s.source_id, s.work_key, g.group_key, g.name AS group_name, s.visible, s.item_position " +
        "FROM series s LEFT JOIN content_group g ON g.id = s.group_id WHERE s.key IN (:keys)"
    const val CHANNELS_PAGE = "SELECT c.id, c.key, c.source_id, c.group_id, g.group_key, g.name AS group_name, COALESCE(x.hidden, 0) AS hidden, c.visible, " +
        "c.playlist_order, x.position, c.display_rank " +
        "FROM channel c LEFT JOIN content_group g ON g.id = c.group_id LEFT JOIN channel_custom x ON x.channel_key = c.key " +
        "WHERE c.key > :after AND c.key < :until ORDER BY c.key LIMIT :limit"
    const val CHANNELS_OF_KEYS = "SELECT c.id, c.key, c.source_id, c.group_id, g.group_key, g.name AS group_name, COALESCE(x.hidden, 0) AS hidden, c.visible, " +
        "c.playlist_order, x.position, c.display_rank " +
        "FROM channel c LEFT JOIN content_group g ON g.id = c.group_id LEFT JOIN channel_custom x ON x.channel_key = c.key WHERE c.key IN (:keys)"
    const val GROUPS = "SELECT id, source_id, room, group_key, name, provider_order, shown, position, sort_mode FROM content_group WHERE room = :room"

    /** One sorted group's channels for their places (GUIDE-FR-32): a single group, through its index. */
    const val GROUP_CHANNELS = "SELECT c.id, c.key, c.sort_name, c.playlist_order, x.position, c.display_rank " +
        "FROM channel c INDEXED BY index_channel_group_id_display_rank LEFT JOIN channel_custom x ON x.channel_key = c.key WHERE c.group_id = :groupId"

    // ---- Library manager (spec 42 §9.2): small tables whole, one group's rows at most 2,000 ----
    const val MANAGER_GROUPS = "SELECT id, source_id, group_key, name, provider_order, item_count, total_count, shown, position, sort_mode " +
        "FROM content_group WHERE room = :room"
    const val MANAGER_SOURCES = "SELECT id, name, enabled FROM source ORDER BY id"
    const val MANAGER_LISTS = "SELECT l.id, l.name, l.sort_order, (SELECT COUNT(*) FROM channel_list_member m WHERE m.list_id = l.id) AS members " +
        "FROM channel_list l ORDER BY l.sort_order, l.name"
    private const val TITLE_COLUMNS = "t.key, t.work_key, t.source_id, g.group_key, g.name AS group_name, t.name, " +
        "COALESCE(t.replacement_title, t.name) AS shown_name, t.sort_name, t.year, t.rating_x10, t.provider_order, " +
        "CASE WHEN t.replace_poster = 1 OR t.poster_url IS NULL THEN COALESCE(t.replacement_poster, t.poster_url) ELSE t.poster_url END AS image, " +
        "t.visible, 0 AS hidden, t.item_position AS legacy_position"
    private const val TITLE_FILTER = "WHERE t.group_id = :groupId AND (:search IS NULL OR instr(t.sort_name, :search) > 0 " +
        "OR instr(COALESCE(t.replacement_sort, ''), :search) > 0) ORDER BY t.sort_name, t.id LIMIT :limit"

    /**
     * A group's films in title order, at most [limit]: the group's rows only, one range of the group
     * index; SQLite sorts that range (a manager read, not a wall page).
     */
    const val MANAGER_FILMS = "SELECT $TITLE_COLUMNS FROM content_group g CROSS JOIN movie t ON t.group_id = g.id $TITLE_FILTER"
    const val MANAGER_SERIES = "SELECT $TITLE_COLUMNS FROM content_group g CROSS JOIN series t ON t.group_id = g.id $TITLE_FILTER"

    /** A group's channels in display order: the group index's own order, no sort. */
    const val MANAGER_CHANNELS = "SELECT c.key, NULL AS work_key, c.source_id, g.group_key, g.name AS group_name, c.name, c.name AS shown_name, " +
        "c.sort_name, NULL AS year, NULL AS rating_x10, c.playlist_order AS provider_order, c.logo_url AS image, c.visible, " +
        "COALESCE(x.hidden, 0) AS hidden, x.position AS legacy_position FROM content_group g CROSS JOIN channel c INDEXED BY " +
        "index_channel_group_id_display_rank ON c.group_id = g.id LEFT JOIN channel_custom x ON x.channel_key = c.key " +
        "WHERE c.group_id = :groupId AND (:search IS NULL OR instr(c.sort_name, :search) > 0) ORDER BY c.display_rank, c.id LIMIT :limit"

    /** A custom list's channels in the list's order, which is their legacy position (ORG-FR-40). */
    const val MANAGER_LIST_MEMBERS = "SELECT c.key, NULL AS work_key, c.source_id, g.group_key, g.name AS group_name, c.name, c.name AS shown_name, " +
        "c.sort_name, NULL AS year, NULL AS rating_x10, c.playlist_order AS provider_order, c.logo_url AS image, c.visible, " +
        "COALESCE(x.hidden, 0) AS hidden, m.sort_order AS legacy_position FROM channel_list_member m CROSS JOIN channel c ON c.key = m.channel_key " +
        "LEFT JOIN content_group g ON g.id = c.group_id LEFT JOIN channel_custom x ON x.channel_key = c.key " +
        "WHERE m.list_id = :listId AND (:search IS NULL OR instr(c.sort_name, :search) > 0) ORDER BY m.sort_order LIMIT :limit"
}

@Dao
interface OrgDao {
    @Query("SELECT * FROM organization_rule")
    fun rules(): List<OrganizationRuleEntity>

    @Query("SELECT * FROM organization_rule WHERE room = :room")
    fun rulesOf(room: String): List<OrganizationRuleEntity>

    @Query("SELECT COUNT(*) FROM organization_rule")
    fun ruleCount(): Int

    @Upsert
    fun putRules(rows: List<OrganizationRuleEntity>)

    @Delete
    fun deleteRules(rows: List<OrganizationRuleEntity>)

    @Query(OrgSql.GROUPS)
    fun groups(room: String): List<OrgGroupRow>

    @Query("UPDATE content_group SET shown = :shown, position = :position, sort_mode = :sortMode WHERE id = :id")
    fun setGroup(id: Long, shown: Boolean, position: Int, sortMode: String?)

    @Query(OrgSql.FILMS_PAGE)
    fun filmsPage(after: String, until: String, limit: Int): List<OrgTitleRow>

    @Query(OrgSql.SERIES_PAGE)
    fun seriesPage(after: String, until: String, limit: Int): List<OrgTitleRow>

    @Query(OrgSql.FILMS_OF_WORK)
    fun filmsOf(workKeys: List<String>, keys: List<String>): List<OrgTitleRow>

    @Query(OrgSql.SERIES_OF_KEYS)
    fun seriesOf(keys: List<String>): List<OrgTitleRow>

    @Query(OrgSql.CHANNELS_PAGE)
    fun channelsPage(after: String, until: String, limit: Int): List<OrgChannelRow>

    @Query(OrgSql.CHANNELS_OF_KEYS)
    fun channelsOf(keys: List<String>): List<OrgChannelRow>

    @Query(OrgSql.GROUP_CHANNELS)
    fun groupChannels(groupId: Long): List<RankedGroupChannel>

    @Query("UPDATE channel SET display_rank = :rank WHERE id = :id")
    fun setRank(id: Long, rank: Long)

    @Query("UPDATE movie SET visible = :visible, item_position = :position WHERE id = :id")
    fun setFilm(id: Long, visible: Boolean, position: Int?)

    @Query("UPDATE series SET visible = :visible, item_position = :position WHERE id = :id")
    fun setSeries(id: Long, visible: Boolean, position: Int?)

    @Query("UPDATE channel SET visible = :visible WHERE id = :id")
    fun setChannel(id: Long, visible: Boolean)

    @Query("SELECT EXISTS (SELECT 1 FROM organization_rule WHERE room = :room AND item_key = :itemKey)")
    fun namesItem(room: String, itemKey: String): Boolean

    /** Rules follow a film to its new identity; a rule already there wins (decision "Film identity in rules"). */
    @Query(
        "INSERT OR IGNORE INTO organization_rule (room, source_id, group_key, item_key, enabled, sort_mode, position) " +
            "SELECT room, source_id, group_key, :to, enabled, sort_mode, position FROM organization_rule WHERE room = 'MOVIES' AND item_key = :from",
    )
    fun copyFilmRules(from: String, to: String)
    @Query(OrgSql.MANAGER_GROUPS)
    fun managerGroups(room: String): List<ManagerGroupRow>

    @Query(OrgSql.MANAGER_SOURCES)
    fun managerSources(): List<ManagerSourceRow>

    @Query(OrgSql.MANAGER_LISTS)
    fun managerLists(): List<ManagerListRow>

    @Query(OrgSql.MANAGER_FILMS)
    fun managerFilms(groupId: Long, search: String?, limit: Int): List<ManagerItemRow>

    @Query(OrgSql.MANAGER_SERIES)
    fun managerSeries(groupId: Long, search: String?, limit: Int): List<ManagerItemRow>

    @Query(OrgSql.MANAGER_CHANNELS)
    fun managerChannels(groupId: Long, search: String?, limit: Int): List<ManagerItemRow>

    @Query(OrgSql.MANAGER_LIST_MEMBERS)
    fun managerListMembers(listId: String, search: String?, limit: Int): List<ManagerItemRow>
}
