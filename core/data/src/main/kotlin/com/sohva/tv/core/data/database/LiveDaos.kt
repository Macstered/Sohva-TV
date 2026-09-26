package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The guide's and player's reads (spec 20 §9.2, spec 30 §9, plan/04 §15.11). Every list is read in
 * display order straight from an index: keyset pages of at most [LiveSql.PAGE] rows, never joined
 * to programmes, never sorted in memory. The SQL is in [LiveSql] so the plan tests check these very
 * statements.
 */
@Dao
interface LiveDao {
    @Query(LiveSql.SOURCES)
    fun observeSources(): Flow<List<LiveSource>>

    @Query(LiveSql.RAIL)
    suspend fun rail(sourceId: String, profile: String): List<LiveGroup>

    @Query(LiveSql.GROUP_KEYS)
    suspend fun groupKeys(groupId: Long, afterRank: Long, afterId: Long, limit: Int): List<RankKey>

    @Query(LiveSql.SOURCE_KEYS)
    suspend fun sourceKeys(sourceId: String, profile: String, afterRank: Long, afterId: Long, limit: Int): List<RankKey>

    @Query(LiveSql.UNGROUPED_KEYS)
    suspend fun ungroupedKeys(sourceId: String, profile: String, afterRank: Long, afterId: Long, limit: Int): List<RankKey>

    @Query(LiveSql.GROUP_ROWS)
    suspend fun groupRows(groupId: Long, afterRank: Long, afterId: Long, limit: Int): List<LiveChannel>

    @Query(LiveSql.SOURCE_ROWS)
    suspend fun sourceRows(sourceId: String, profile: String, afterRank: Long, afterId: Long, limit: Int): List<LiveChannel>

    @Query(LiveSql.UNGROUPED_ROWS)
    suspend fun ungroupedRows(sourceId: String, profile: String, afterRank: Long, afterId: Long, limit: Int): List<LiveChannel>

    @Query(LiveSql.ROWS_BY_ID)
    suspend fun rowsById(ids: List<Long>): List<LiveChannel>

    @Query(LiveSql.KEYS_BY_CHANNEL_KEY)
    suspend fun keysByChannelKey(sourceId: String, keys: List<String>, profile: String): List<ChannelRankKey>

    @Query(LiveSql.BY_KEY)
    suspend fun byKey(key: String): LiveChannel?

    @Query(LiveSql.GROUP_BY_NUMBER)
    suspend fun groupByNumber(groupId: Long, number: Int): RankKey?

    @Query(LiveSql.SOURCE_BY_NUMBER)
    suspend fun sourceByNumber(sourceId: String, number: Int, profile: String): RankKey?

    @Query(LiveSql.IDS_BY_NUMBER)
    suspend fun idsByNumber(ids: List<Long>, number: Int): List<Long>

    @Query(LiveSql.PLAYABLE)
    suspend fun playable(key: String): PlayableChannel?

    @Query(LiveSql.EPG_STATE)
    suspend fun epgState(sourceId: String): EpgState?

    @Query(LiveSql.WINDOW)
    suspend fun window(sourceId: String, snapshot: Long, epgIds: List<String>, from: Long, to: Long, earliestStart: Long): List<ProgrammeRow>

    @Query(LiveSql.DESCRIPTION)
    suspend fun description(id: Long): String?

    @Query(LiveSql.GROUP_MATCHES)
    suspend fun groupMatches(groupId: Long, sourceId: String, snapshot: Long, namePattern: String, titlePattern: String, from: Long, to: Long, earliestStart: Long): List<RankKey>

    @Query(LiveSql.SOURCE_MATCHES)
    suspend fun sourceMatches(sourceId: String, profile: String, snapshot: Long, namePattern: String, titlePattern: String, from: Long, to: Long, earliestStart: Long): List<RankKey>
}

/** Favourites and recents of a profile (plan/04 §15.8). */
@Dao
interface ViewerDao {
    @Query("SELECT channel_key FROM favourite_channel WHERE profile_id = :profileId ORDER BY added_at, channel_key")
    suspend fun favouriteKeys(profileId: String): List<String>

    @Query("SELECT channel_key FROM favourite_channel WHERE profile_id = :profileId")
    fun observeFavouriteKeys(profileId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavourite(row: FavouriteChannelEntity)

    @Query("DELETE FROM favourite_channel WHERE profile_id = :profileId AND channel_key = :key")
    suspend fun removeFavourite(profileId: String, key: String): Int

    @Query("SELECT channel_key FROM recent_channel WHERE profile_id = :profileId ORDER BY watched_at DESC LIMIT $RECENT_LIMIT")
    suspend fun recentKeys(profileId: String): List<String>

    @Query("SELECT channel_key FROM recent_channel WHERE profile_id = :profileId ORDER BY watched_at DESC LIMIT $RECENT_LIMIT")
    fun observeRecentKeys(profileId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecent(row: RecentChannelEntity)

    @Query(
        "DELETE FROM recent_channel WHERE profile_id = :profileId AND channel_key NOT IN " +
            "(SELECT channel_key FROM recent_channel WHERE profile_id = :profileId ORDER BY watched_at DESC LIMIT $RECENT_LIMIT)",
    )
    suspend fun trimRecents(profileId: String)

    /** Front of the list, trimmed to [RECENT_LIMIT] (spec 30 PLAY-FR-57). */
    @Transaction
    suspend fun recordRecent(row: RecentChannelEntity) {
        putRecent(row)
        trimRecents(row.profileId)
    }
}

/** An enabled source that has visible channels (GUIDE-FR-10). */
data class LiveSource(
    val id: String,
    val name: String,
    @ColumnInfo(name = "epg_offset_minutes") val epgOffsetMinutes: Int,
)

/** A rail group (GUIDE-FR-20): the resolved order and the count come from the import. */
data class LiveGroup(
    val id: Long,
    @ColumnInfo(name = "group_key") val groupKey: String,
    val name: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    val position: Int,
)

/** A row's place in display order: the keyset key. */
data class RankKey(
    val id: Long,
    @ColumnInfo(name = "display_rank") val rank: Long,
)

data class ChannelRankKey(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "display_rank") val rank: Long,
    @ColumnInfo(name = "sort_name") val sortName: String,
)

/** One guide row as read, without programmes (GUIDE-FR-01, -30). */
data class LiveChannel(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    val name: String,
    @ColumnInfo(name = "epg_id") val epgId: String?,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    val number: Int?,
    @ColumnInfo(name = "display_rank") val rank: Long,
    @ColumnInfo(name = "catchup_type") val catchupType: String?,
    @ColumnInfo(name = "catchup_days") val catchupDays: Int?,
    /** Whether the playlist gave a catch-up template; the template itself stays in the row (spec 22 §4.3). */
    @ColumnInfo(name = "has_catchup_template") val hasCatchupTemplate: Boolean,
)

/** What the player needs to open a live stream (spec 30 PLAY-FR-14). The address stays sealed. */
data class PlayableChannel(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "connection_limit") val connectionLimit: Int,
    val name: String,
    @ColumnInfo(name = "stream_url_enc") val streamUrlEnc: String,
    @ColumnInfo(name = "user_agent") val userAgent: String?,
    val referrer: String?,
    @ColumnInfo(name = "catchup_type") val catchupType: String?,
    @ColumnInfo(name = "catchup_source") val catchupSource: String?,
    @ColumnInfo(name = "catchup_days") val catchupDays: Int?,
    @ColumnInfo(name = "catchup_tz") val catchupTz: String?,
    @ColumnInfo(name = "xtream_stream_id") val xtreamStreamId: String?,
) {
    // The template can carry the provider's own tokens; it never reaches a log line.
    override fun toString(): String = "PlayableChannel(key=$key, source=$sourceId)"
}

data class EpgState(
    @ColumnInfo(name = "epg_snapshot") val snapshot: Long?,
    @ColumnInfo(name = "epg_max_duration_ms") val maxDurationMs: Long?,
)

/** A programme as the grid reads it: no description (GUIDE-NFR-13), times as stored. */
data class ProgrammeRow(
    val id: Long,
    @ColumnInfo(name = "epg_id") val epgId: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "stop_at") val stopAt: Long,
    val title: String,
    val subtitle: String?,
    @ColumnInfo(name = "has_description") val hasDescription: Boolean,
    val categories: String?,
    @ColumnInfo(name = "programme_key") val programmeKey: String,
)

object LiveSql {
    const val PAGE: Int = 200
    const val KEY_PAGE: Int = 2_000

    const val SOURCES: String =
        "SELECT s.id, s.name, s.epg_offset_minutes FROM source s WHERE s.enabled = 1 AND EXISTS " +
            "(SELECT 1 FROM channel c WHERE c.source_id = s.id AND c.visible = 1) ORDER BY s.priority DESC, s.name, s.id"

    /** The small `content_group` table, never a GROUP BY over channels (plan/04 §15.11). */
    const val RAIL: String =
        "SELECT g.id, g.group_key, g.name, g.item_count, g.position FROM content_group g " +
            "WHERE g.room = 'LIVE' AND g.source_id = :sourceId AND g.shown = 1 AND g.item_count > 0 AND ${AllowedSql.LIVE_G} " +
            "ORDER BY g.provider_order, g.id"

    private const val AFTER = "(c.display_rank > :afterRank OR (c.display_rank = :afterRank AND c.id > :afterId))"
    private const val ORDER = "ORDER BY c.display_rank, c.id LIMIT :limit"
    private const val COLUMNS =
        "c.id, c.key, c.source_id, c.group_id, g.name AS group_name, c.name, c.epg_id, c.logo_url, c.number, " +
            "c.display_rank, c.catchup_type, c.catchup_days, " +
            "(c.catchup_source IS NOT NULL AND c.catchup_source != '') AS has_catchup_template"

    // `INDEXED BY` pins each walk to its index: the planner may otherwise prefer the primary key for
    // `ORDER BY …, id`, which would read the whole table (AGENTS.md §4 rule 3).
    const val GROUP_KEYS: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_group_id_display_rank " +
            "WHERE c.group_id = :groupId AND c.visible = 1 AND $AFTER $ORDER"
    const val SOURCE_KEYS: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_source_id_visible_display_rank " +
            "WHERE c.source_id = :sourceId AND c.visible = 1 AND ${AllowedSql.LIVE_C} AND $AFTER $ORDER"

    /** Channels without a group: the player's list for them (spec 30 PLAY-FR-50). */
    const val UNGROUPED_KEYS: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_source_id_visible_display_rank " +
            "WHERE c.source_id = :sourceId AND c.visible = 1 AND c.group_id IS NULL AND ${AllowedSql.LIVE_OPEN} AND $AFTER $ORDER"

    const val GROUP_ROWS: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_group_id_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE c.group_id = :groupId AND c.visible = 1 AND $AFTER $ORDER"
    const val SOURCE_ROWS: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_source_id_visible_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id WHERE c.source_id = :sourceId AND c.visible = 1 AND ${AllowedSql.LIVE_G} AND $AFTER $ORDER"
    const val UNGROUPED_ROWS: String =
        "SELECT $COLUMNS FROM channel c INDEXED BY index_channel_source_id_visible_display_rank " +
            "LEFT JOIN content_group g ON g.id = c.group_id " +
            "WHERE c.source_id = :sourceId AND c.visible = 1 AND c.group_id IS NULL AND ${AllowedSql.LIVE_OPEN} AND $AFTER $ORDER"

    /** Named rows (favourites, recents, a page of a named list), ≤ 500 ids a call. */
    const val ROWS_BY_ID: String = "SELECT $COLUMNS FROM channel c LEFT JOIN content_group g ON g.id = c.group_id WHERE c.id IN (:ids)"

    const val KEYS_BY_CHANNEL_KEY: String =
        "SELECT c.id, c.key, c.display_rank, c.sort_name FROM channel c WHERE c.key IN (:keys) AND c.source_id = :sourceId AND c.visible = 1 " +
            "AND ${AllowedSql.LIVE_C}"

    const val BY_KEY: String = "SELECT $COLUMNS FROM channel c LEFT JOIN content_group g ON g.id = c.group_id WHERE c.key = :key"

    /** Own numbers within a list (GUIDE-NFR-15): indexed look-ups, never a scan of a source. */
    const val GROUP_BY_NUMBER: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_group_id_display_rank " +
            "WHERE c.group_id = :groupId AND c.visible = 1 AND c.number = :number ORDER BY c.display_rank, c.id LIMIT 1"
    // MIN picks the first in display order among the few channels sharing a number without a sort;
    // GROUP BY makes "none" an empty result rather than a row of nulls.
    const val SOURCE_BY_NUMBER: String =
        "SELECT c.id, MIN(c.display_rank) AS display_rank FROM channel c INDEXED BY index_channel_source_id_number " +
            "WHERE c.source_id = :sourceId AND c.number = :number AND c.visible = 1 AND ${AllowedSql.LIVE_C} GROUP BY c.number"
    const val IDS_BY_NUMBER: String = "SELECT c.id FROM channel c WHERE c.id IN (:ids) AND c.number = :number"

    const val PLAYABLE: String =
        "SELECT c.id, c.key, c.source_id, s.name AS source_name, s.connection_limit, c.name, c.stream_url_enc, " +
            "c.user_agent, c.referrer, c.catchup_type, c.catchup_source, c.catchup_days, c.catchup_tz, c.xtream_stream_id " +
            "FROM channel c CROSS JOIN source s ON s.id = c.source_id " +
            "WHERE c.key = :key AND c.visible = 1 AND s.enabled = 1"

    const val EPG_STATE: String =
        "SELECT epg_snapshot, epg_max_duration_ms FROM source_status WHERE source_id = :sourceId AND kind = 'epg'"

    /**
     * The bounded window read of plan/04 §15.5 for ≤ 80 guide ids; [from]/[to] are already moved
     * back by the source's EPG offset, [earliestStart] = from − the longest programme.
     */
    const val WINDOW: String =
        "SELECT p.id, p.epg_id, p.start_at, p.stop_at, p.title, p.subtitle, " +
            "(p.description IS NOT NULL AND p.description != '') AS has_description, p.categories, p.programme_key " +
            "FROM programme p WHERE p.source_id = :sourceId AND p.snapshot = :snapshot AND p.epg_id IN (:epgIds) " +
            "AND p.start_at >= :earliestStart AND p.start_at < :to AND p.stop_at > :from"

    const val DESCRIPTION: String = "SELECT description FROM programme WHERE id = :id"

    private const val TITLES: String =
        "SELECT p.epg_id FROM programme p WHERE p.source_id = :sourceId AND p.snapshot = :snapshot " +
            "AND p.start_at >= :earliestStart AND p.start_at < :to AND p.stop_at > :from AND p.title LIKE :titlePattern ESCAPE '\\'"

    /**
     * Find programme (GUIDE-FR-92): rows of the list whose folded name contains the query, or whose
     * guide id has a title matching in the window. One statement: the title matches are a subquery
     * (a list of guide ids could pass SQLite's 999-variable limit). Names match the folded `sort_name`
     * (Unicode-aware); titles match SQLite's ASCII-only LIKE, as in beta 23 (§8). Snapshot −1 = no guide.
     */
    const val GROUP_MATCHES: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_group_id_display_rank " +
            "WHERE c.group_id = :groupId AND c.visible = 1 AND (c.sort_name LIKE :namePattern ESCAPE '\\' OR c.epg_id IN ($TITLES)) " +
            "ORDER BY c.display_rank, c.id"
    const val SOURCE_MATCHES: String =
        "SELECT c.id, c.display_rank FROM channel c INDEXED BY index_channel_source_id_visible_display_rank " +
            "WHERE c.source_id = :sourceId AND c.visible = 1 AND ${AllowedSql.LIVE_C} " +
            "AND (c.sort_name LIKE :namePattern ESCAPE '\\' OR c.epg_id IN ($TITLES)) ORDER BY c.display_rank, c.id"
}
