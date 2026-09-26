package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * One group a restricted profile may see (spec 04 PROF-FR-20, §9): the organisation group key of
 * a room. A profile with no row for a room sees everything in it. The primary key is the look-up
 * the paged queries make per row: (profile, room, key).
 */
@Entity(tableName = "profile_allowed_group", primaryKeys = ["profile_id", "room", "group_key"])
data class ProfileAllowedGroupEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val room: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
)

/** A group a profile could be limited to: its key, its title and the source that carries it (PROF-FR-22). */
data class GroupChoiceRow(
    @ColumnInfo(name = "group_key") val groupKey: String,
    val name: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
)

/** Allowed groups, locked channels and the removal of a profile's rows (spec 04 §4). */
@Dao
interface ProfileDao {
    @Query("SELECT room, group_key FROM profile_allowed_group WHERE profile_id = :profileId")
    suspend fun allowed(profileId: String): List<AllowedRow>

    @Query("SELECT DISTINCT profile_id FROM profile_allowed_group")
    suspend fun restrictedProfiles(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun allow(row: ProfileAllowedGroupEntity)

    @Query("DELETE FROM profile_allowed_group WHERE profile_id = :profileId AND room = :room AND group_key = :groupKey")
    suspend fun disallow(profileId: String, room: String, groupKey: String)

    /**
     * The room's groups across enabled sources, one row per group and source, for the pickers.
     * Groups number in the hundreds; the picker folds them by key.
     */
    @Query(
        "SELECT g.group_key, g.name, s.name AS source_name FROM content_group g CROSS JOIN source s ON s.id = g.source_id " +
            "WHERE g.room = :room AND s.enabled = 1 AND g.shown = 1 AND g.item_count > 0 ORDER BY s.priority, s.name, g.position, g.id",
    )
    suspend fun groupChoices(room: String): List<GroupChoiceRow>

    @Query(AllowedSql.GROUP)
    suspend fun groupAllowed(profile: String, room: String, groupId: Long): Boolean

    /** Null when no channel has [key]. */
    @Query(AllowedSql.CHANNEL)
    suspend fun channelAllowed(profile: String, key: String): Boolean?

    @Query("SELECT channel_key FROM locked_channel WHERE profile_id = :profileId")
    suspend fun lockedKeys(profileId: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM locked_channel WHERE profile_id = :profileId AND channel_key = :key)")
    suspend fun isLocked(profileId: String, key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun lock(row: LockedChannelEntity)

    @Query("DELETE FROM locked_channel WHERE profile_id = :profileId AND channel_key = :key")
    suspend fun unlock(profileId: String, key: String)

    /** Removing the PIN drops every profile's locks (PROF-FR-33). */
    @Query("DELETE FROM locked_channel")
    suspend fun unlockAll()

    @Query("DELETE FROM favourite_channel WHERE profile_id = :profileId")
    suspend fun deleteFavourites(profileId: String)

    @Query("DELETE FROM recent_channel WHERE profile_id = :profileId")
    suspend fun deleteRecents(profileId: String)

    @Query("DELETE FROM locked_channel WHERE profile_id = :profileId")
    suspend fun deleteLocks(profileId: String)

    @Query("DELETE FROM profile_allowed_group WHERE profile_id = :profileId")
    suspend fun deleteAllowed(profileId: String)

    @Query("DELETE FROM watch_progress WHERE profile_id = :profileId")
    suspend fun deleteProgress(profileId: String)

    /** Everything a profile kept in the database (PROF-FR-06, plan/04 §15.8), in one transaction. */
    @Transaction
    suspend fun deleteProfile(profileId: String) {
        deleteFavourites(profileId)
        deleteRecents(profileId)
        deleteLocks(profileId)
        deleteAllowed(profileId)
        deleteProgress(profileId)
    }
}

data class AllowedRow(val room: String, @ColumnInfo(name = "group_key") val groupKey: String)

/**
 * The restriction as SQL (spec 04 §9, decision "Restriction in queries"): a row passes when the
 * active profile (`:profile`) has no allowed groups for the room, or its group's key is one of
 * them. The first term does not depend on the row, so SQLite evaluates it once per statement and
 * an unrestricted profile never runs the per-row look-up. A row without a group fails for a
 * restricted room. Both look-ups are primary-key searches.
 */
object AllowedSql {
    private const val ROOM_ROWS = "SELECT 1 FROM profile_allowed_group pr WHERE pr.profile_id = :profile AND pr.room = "
    private const val KEY_ROW = "SELECT 1 FROM profile_allowed_group pa WHERE pa.profile_id = :profile AND pa.room = "
    private const val GROUP_ROW = "SELECT 1 FROM content_group pg CROSS JOIN profile_allowed_group pa ON pa.profile_id = :profile AND pa.room = "

    /** Only the unrestricted pass: rows without a group (the player's "ungrouped" list). */
    const val LIVE_OPEN: String = "NOT EXISTS ($ROOM_ROWS'LIVE')"

    /** Live rows joined to their group as `g`. */
    const val LIVE_G: String = "(NOT EXISTS ($ROOM_ROWS'LIVE') OR EXISTS ($KEY_ROW'LIVE' AND pa.group_key = g.group_key))"

    /** Live rows of `channel c` without the group joined. */
    const val LIVE_C: String = "(NOT EXISTS ($ROOM_ROWS'LIVE') OR EXISTS ($GROUP_ROW'LIVE' AND pa.group_key = pg.group_key WHERE pg.id = c.group_id))"

    const val MOVIES_G: String = "(NOT EXISTS ($ROOM_ROWS'MOVIES') OR EXISTS ($KEY_ROW'MOVIES' AND pa.group_key = g.group_key))"
    const val MOVIES_M: String = "(NOT EXISTS ($ROOM_ROWS'MOVIES') OR EXISTS ($GROUP_ROW'MOVIES' AND pa.group_key = pg.group_key WHERE pg.id = m.group_id))"
    const val MOVIES_ROW: String =
        "(NOT EXISTS ($ROOM_ROWS'MOVIES') OR EXISTS ($GROUP_ROW'MOVIES' AND pa.group_key = pg.group_key WHERE pg.id = movie.group_id))"

    const val SERIES_G: String = "(NOT EXISTS ($ROOM_ROWS'SERIES') OR EXISTS ($KEY_ROW'SERIES' AND pa.group_key = g.group_key))"
    const val SERIES_S: String = "(NOT EXISTS ($ROOM_ROWS'SERIES') OR EXISTS ($GROUP_ROW'SERIES' AND pa.group_key = pg.group_key WHERE pg.id = s.group_id))"
    const val SERIES_ROW: String =
        "(NOT EXISTS ($ROOM_ROWS'SERIES') OR EXISTS ($GROUP_ROW'SERIES' AND pa.group_key = pg.group_key WHERE pg.id = series.group_id))"

    /** `content_group` rows of the room bound as `:room`, unaliased. */
    const val ROOM_GROUPS: String = "(NOT EXISTS ($ROOM_ROWS:room) OR EXISTS ($KEY_ROW:room AND pa.group_key = content_group.group_key))"

    /** One group by id, for a list or wall of that group: checked once, not per row. */
    const val GROUP: String = "SELECT (NOT EXISTS ($ROOM_ROWS:room) OR EXISTS ($GROUP_ROW:room AND pa.group_key = pg.group_key WHERE pg.id = :groupId))"

    /** One channel by key, before it plays (spec 01 SHELL-FR-20: the group check comes first). */
    const val CHANNEL: String = "SELECT $LIVE_C FROM channel c WHERE c.key = :key"
}
