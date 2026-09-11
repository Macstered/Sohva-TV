package com.sohva.tv.addons.storage

import android.content.Context
import androidx.room.*
import com.sohva.tv.addons.*

@Entity(tableName = "addon_library", indices = [Index(value = ["profileId", "addedAtMillis"])])
data class AddonLibraryEntity(@PrimaryKey val key: String, val profileId: String, val encryptedPayload: String, val addedAtMillis: Long)

@Dao
abstract class AddonLibraryDao {
    @Query("SELECT * FROM addon_library WHERE profileId = :profile AND `key` = :key")
    abstract suspend fun get(profile: String, key: String): AddonLibraryEntity?
    @Query("SELECT * FROM addon_library WHERE profileId = :profile ORDER BY addedAtMillis DESC, `key` LIMIT 1001")
    abstract suspend fun list(profile: String): List<AddonLibraryEntity>
    @Query("SELECT COUNT(*) FROM addon_library WHERE profileId = :profile")
    abstract suspend fun count(profile: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: AddonLibraryEntity)
    @Query("DELETE FROM addon_library WHERE profileId = :profile AND `key` = :key")
    abstract suspend fun remove(profile: String, key: String)
    @Transaction
    open suspend fun put(entity: AddonLibraryEntity): Boolean {
        if (get(entity.profileId, entity.key) == null && count(entity.profileId) >= AddonLibraryRepository.MAX_TITLES) return false
        insert(entity); return true
    }
}

/** Separate from installations, continue-watching progress and production IPTV. */
@Database(entities = [AddonLibraryEntity::class], version = 1, exportSchema = true)
abstract class AddonLibraryDatabase : RoomDatabase() {
    abstract fun library(): AddonLibraryDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, AddonLibraryDatabase::class.java, "sohva-addon-library.db").build()
    }
}
class RoomAddonLibraryPersistence(private val dao: AddonLibraryDao) : AddonLibraryPersistence {
    override suspend fun get(profile: String, key: String) = dao.get(profile, key)?.row()
    override suspend fun list(profile: String) = dao.list(profile).map { it.row() }
    override suspend fun put(row: AddonLibraryRow) = dao.put(AddonLibraryEntity(row.key, row.profileId, row.encryptedPayload, row.addedAtMillis))
    override suspend fun remove(profile: String, key: String) = dao.remove(profile, key)
    private fun AddonLibraryEntity.row() = AddonLibraryRow(key, profileId, encryptedPayload, addedAtMillis)
}
