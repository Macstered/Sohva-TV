package com.sohva.tv.addons.storage

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
@Database(entities = [AddonProgressEntity::class], version = 4, exportSchema = true)
abstract class AddonProgressDatabase : RoomDatabase() {
    abstract fun progress(): AddonProgressDao
    companion object {
        /**
         * Versions 2 and 3 came from the private Trakt trial build (app codes 16-18),
         * which added two side tables and left `addon_progress` itself untouched.
         * Version 4 removes them; the progress rows are kept as they are.
         */
        private fun dropTrialTables(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS addon_projection_receipts")
            db.execSQL("DROP TABLE IF EXISTS addon_progress_display")
        }
        val MIGRATION_1_4 = object : Migration(1, 4) { override fun migrate(db: SupportSQLiteDatabase) = dropTrialTables(db) }
        val MIGRATION_2_4 = object : Migration(2, 4) { override fun migrate(db: SupportSQLiteDatabase) = dropTrialTables(db) }
        val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) = dropTrialTables(db) }
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, AddonProgressDatabase::class.java, "sohva-addon-progress.db")
            .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4).build()
    }
}

class RoomAddonProgressPersistence(private val dao: AddonProgressDao) : AddonProgressPersistence {
    override suspend fun get(profileId: String, key: String) = dao.get(profileId, key)?.row()
    override suspend fun recent(profileId: String) = dao.recent(profileId).map { it.row() }
    override suspend fun put(row: AddonProgressRow) = dao.put(AddonProgressEntity(row.key, row.profileId, row.encryptedPayload, row.updatedAtMillis))
    override suspend fun remove(profileId: String, key: String) = dao.remove(profileId, key)
    private fun AddonProgressEntity.row() = AddonProgressRow(key, profileId, encryptedPayload, updatedAtMillis)
}
