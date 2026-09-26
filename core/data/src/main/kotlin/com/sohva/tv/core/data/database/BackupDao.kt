package com.sohva.tv.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The household's own rows a backup carries (spec 71 §6.2): channel edits, lists, organisation
 * rules and each profile's channels. All bounded by the payload limits (§7.3), never the catalogue.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM channel_custom ORDER BY source_id, channel_key")
    fun customs(): List<ChannelCustomEntity>

    @Query("SELECT * FROM channel_list ORDER BY sort_order, id")
    fun lists(): List<ChannelListEntity>

    @Query("SELECT * FROM channel_list_member ORDER BY list_id, sort_order, channel_key")
    fun members(): List<ChannelListMemberEntity>

    @Query("SELECT * FROM organization_rule ORDER BY room, source_id, group_key, item_key")
    fun rules(): List<OrganizationRuleEntity>

    @Query("SELECT channel_key FROM favourite_channel WHERE profile_id = :profile ORDER BY added_at, channel_key")
    fun favourites(profile: String): List<String>

    @Query("SELECT channel_key FROM recent_channel WHERE profile_id = :profile ORDER BY watched_at DESC LIMIT 20")
    fun recents(profile: String): List<String>

    @Query("SELECT channel_key FROM locked_channel WHERE profile_id = :profile ORDER BY channel_key")
    fun locks(profile: String): List<String>

    @Query("SELECT group_key FROM profile_allowed_group WHERE profile_id = :profile AND room = :room ORDER BY group_key")
    fun allowed(profile: String, room: String): List<String>

    /** A film copy's identity, for a beta 23 rule naming the copy (spec 71 §4.4). */
    @Query("SELECT work_key FROM movie WHERE key = :key")
    fun workKeyOf(key: String): String?

    @Query("DELETE FROM channel_custom")
    fun clearCustoms()

    @Query("DELETE FROM channel_list")
    fun clearLists()

    @Query("DELETE FROM channel_list_member")
    fun clearMembers()

    @Query("DELETE FROM organization_rule")
    fun clearRules()

    @Query("DELETE FROM favourite_channel")
    fun clearFavourites()

    @Query("DELETE FROM recent_channel")
    fun clearRecents()

    @Query("DELETE FROM locked_channel")
    fun clearLocks()

    @Query("DELETE FROM profile_allowed_group")
    fun clearAllowed()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putCustoms(rows: List<ChannelCustomEntity>)

    @Query("UPDATE channel_custom SET custom_logo_url = :url WHERE channel_key = :key")
    fun setLogo(key: String, url: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putLists(rows: List<ChannelListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMembers(rows: List<ChannelListMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun putRules(rows: List<OrganizationRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun putFavourites(rows: List<FavouriteChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun putRecents(rows: List<RecentChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun putLocks(rows: List<LockedChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun putAllowed(rows: List<ProfileAllowedGroupEntity>)
}
