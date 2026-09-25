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
)

object OrgSql {
    const val FILMS_PAGE = "SELECT m.id, m.key, m.source_id, m.work_key, g.group_key, g.name AS group_name, m.visible, m.item_position " +
        "FROM movie m LEFT JOIN content_group g ON g.id = m.group_id WHERE m.key > :after AND m.key < :until ORDER BY m.key LIMIT :limit"
    const val SERIES_PAGE = "SELECT s.id, s.key, s.source_id, s.work_key, g.group_key, g.name AS group_name, s.visible, s.item_position " +
        "FROM series s LEFT JOIN content_group g ON g.id = s.group_id WHERE s.key > :after AND s.key < :until ORDER BY s.key LIMIT :limit"
    const val FILMS_OF_WORK = "SELECT m.id, m.key, m.source_id, m.work_key, g.group_key, g.name AS group_name, m.visible, m.item_position " +
        "FROM movie m LEFT JOIN content_group g ON g.id = m.group_id WHERE m.work_key IN (:workKeys) OR m.key IN (:keys)"
    const val SERIES_OF_KEYS = "SELECT s.id, s.key, s.source_id, s.work_key, g.group_key, g.name AS group_name, s.visible, s.item_position " +
        "FROM series s LEFT JOIN content_group g ON g.id = s.group_id WHERE s.key IN (:keys)"
    const val CHANNELS_PAGE = "SELECT c.id, c.key, c.source_id, g.group_key, g.name AS group_name, COALESCE(x.hidden, 0) AS hidden, c.visible " +
        "FROM channel c LEFT JOIN content_group g ON g.id = c.group_id LEFT JOIN channel_custom x ON x.channel_key = c.key " +
        "WHERE c.key > :after AND c.key < :until ORDER BY c.key LIMIT :limit"
    const val CHANNELS_OF_KEYS = "SELECT c.id, c.key, c.source_id, g.group_key, g.name AS group_name, COALESCE(x.hidden, 0) AS hidden, c.visible " +
        "FROM channel c LEFT JOIN content_group g ON g.id = c.group_id LEFT JOIN channel_custom x ON x.channel_key = c.key WHERE c.key IN (:keys)"
    const val GROUPS = "SELECT id, source_id, room, group_key, name, shown, position, sort_mode FROM content_group WHERE room = :room"
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
}
