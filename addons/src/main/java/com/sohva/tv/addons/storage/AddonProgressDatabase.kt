package com.sohva.tv.addons.storage

import android.content.Context
import androidx.room.*
import com.sohva.tv.addons.AddonProgressPersistence
import com.sohva.tv.addons.AddonProgressRow

@Entity(tableName = "addon_progress", indices = [Index(value = ["profileId", "updatedAtMillis"])])
data class AddonProgressEntity(@PrimaryKey val key: String, val profileId: String, val encryptedPayload: String, val updatedAtMillis: Long)

@Dao
abstract class AddonProgressDao {
    @Query("SELECT * FROM addon_progress WHERE profileId = :profileId AND `key` = :key")
    abstract suspend fun get(profileId: String, key: String): AddonProgressEntity?
    @Query("SELECT * FROM addon_progress WHERE profileId = :profileId ORDER BY updatedAtMillis DESC LIMIT 200")
    abstract suspend fun recent(profileId: String): List<AddonProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: AddonProgressEntity)
    @Query("DELETE FROM addon_progress WHERE profileId = :profileId AND `key` NOT IN (SELECT `key` FROM addon_progress WHERE profileId = :profileId ORDER BY updatedAtMillis DESC, `key` LIMIT 200)")
    abstract suspend fun prune(profileId: String)
    @Query("DELETE FROM addon_progress WHERE profileId = :profileId AND `key` = :key")
    abstract suspend fun remove(profileId: String, key: String)
    @Transaction
    open suspend fun put(entity: AddonProgressEntity) { insert(entity); prune(entity.profileId) }
}

/** No migration of existing installation or IPTV databases is required. */
@Database(entities = [AddonProgressEntity::class], version = 1, exportSchema = true)
abstract class AddonProgressDatabase : RoomDatabase() {
    abstract fun progress(): AddonProgressDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, AddonProgressDatabase::class.java, "sohva-addon-progress.db").build()
    }
}

class RoomAddonProgressPersistence(private val dao: AddonProgressDao) : AddonProgressPersistence {
    override suspend fun get(profileId: String, key: String) = dao.get(profileId, key)?.row()
    override suspend fun recent(profileId: String) = dao.recent(profileId).map { it.row() }
    override suspend fun put(row: AddonProgressRow) = dao.put(AddonProgressEntity(row.key, row.profileId, row.encryptedPayload, row.updatedAtMillis))
    override suspend fun remove(profileId: String, key: String) = dao.remove(profileId, key)
    private fun AddonProgressEntity.row() = AddonProgressRow(key, profileId, encryptedPayload, updatedAtMillis)
}
