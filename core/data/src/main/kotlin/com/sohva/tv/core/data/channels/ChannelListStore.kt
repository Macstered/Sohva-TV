package com.sohva.tv.core.data.channels

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sohva.tv.core.data.database.ChannelListEntity
import com.sohva.tv.core.data.database.ChannelListMemberEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.channel.ChannelEdits
import com.sohva.tv.core.model.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The household's own channel lists (spec 21 §4.5): small tables, at most 1,000 lists. */
@Dao
interface ChannelListDao {
    @Query("SELECT * FROM channel_list ORDER BY sort_order, name, id")
    fun lists(): Flow<List<ChannelListEntity>>

    @Query("SELECT COUNT(*) FROM channel_list")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(list: ChannelListEntity)

    @Query("DELETE FROM channel_list WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM channel_list_member WHERE list_id = :id")
    fun deleteMembers(id: String)

    /** The lists a channel is in, for the editor (CHAN-NFR-05: the selected channel only). */
    @Query("SELECT list_id FROM channel_list_member WHERE channel_key = :key")
    fun listsOf(key: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM channel_list_member WHERE list_id = :listId")
    fun memberCount(listId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addMember(member: ChannelListMemberEntity)

    @Query("DELETE FROM channel_list_member WHERE list_id = :listId AND channel_key = :key")
    fun removeMember(listId: String, key: String)

    /** A list's members of one source, in the list's own order (CHAN-FR-55): a list is small. */
    @Query(
        "SELECT m.channel_key FROM channel_list_member m CROSS JOIN channel c ON c.key = m.channel_key " +
            "WHERE m.list_id = :listId AND c.source_id = :sourceId AND c.visible = 1 ORDER BY m.sort_order, m.channel_key",
    )
    suspend fun memberKeys(listId: String, sourceId: String): List<String>

    /** The same members with their place in the list, which the manager's places are compared with (ORG-FR-22). */
    @Query(
        "SELECT m.channel_key, m.sort_order FROM channel_list_member m CROSS JOIN channel c ON c.key = m.channel_key " +
            "WHERE m.list_id = :listId AND c.source_id = :sourceId AND c.visible = 1 ORDER BY m.sort_order, m.channel_key",
    )
    suspend fun members(listId: String, sourceId: String): List<ListMemberPlace>
}

data class ListMemberPlace(@ColumnInfo(name = "channel_key") val key: String, @ColumnInfo(name = "sort_order") val sortOrder: Long)

class ChannelListStore(private val db: SohvaDatabase, private val write: CoroutineDispatcher, private val clock: Clock) {
    private val dao get() = db.channelLists()

    val lists: Flow<List<ChannelListEntity>> get() = dao.lists()

    fun listsOf(key: String): Flow<List<String>> = dao.listsOf(key)

    /** Create list (CHAN-FR-50): a blank name does nothing; returns the new list's id. */
    suspend fun create(name: String): String? = withContext(write) {
        val trimmed = ChannelEdits.text(name, ChannelEdits.NAME_MAX) ?: return@withContext null
        val id = UUID.randomUUID().toString()
        db.runInTransaction { dao.insert(ChannelListEntity(id, trimmed, dao.count(), clock.wallMillis())) }
        id
    }

    /** Delete list (CHAN-FR-54): the list and all its memberships at once. */
    suspend fun delete(listId: String) = withContext(write) {
        db.runInTransaction {
            dao.deleteMembers(listId)
            dao.delete(listId)
        }
    }

    /** Add to list appends at the end (CHAN-FR-53). */
    suspend fun add(listId: String, key: String) = withContext(write) {
        db.runInTransaction { dao.addMember(ChannelListMemberEntity(listId, key, dao.memberCount(listId))) }
    }

    suspend fun remove(listId: String, key: String) = withContext(write) { dao.removeMember(listId, key) }

    suspend fun memberKeys(listId: String, sourceId: String): List<String> = withContext(write) { dao.memberKeys(listId, sourceId) }
}
