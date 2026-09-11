package com.sohva.tv.addons.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "addon_installations", indices = [Index(value = ["profileId", "endpointFingerprint"], unique = true)])
data class AddonInstallationEntity(
    @PrimaryKey val installationId: String,
    val profileId: String,
    val endpointFingerprint: String,
    val encryptedPayload: String,
    val enabled: Boolean,
    val position: Int,
    val revision: Long,
    val updatedAtMillis: Long,
)

@Dao
interface AddonDao {
    @Query("SELECT * FROM addon_installations WHERE profileId = :profileId ORDER BY position, installationId")
    suspend fun list(profileId: String): List<AddonInstallationEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AddonInstallationEntity)
    @Query("UPDATE addon_installations SET encryptedPayload = :payload, revision = revision + 1, updatedAtMillis = :now WHERE profileId = :profileId AND installationId = :id AND revision = :revision")
    suspend fun refresh(profileId: String, id: String, revision: Long, payload: String, now: Long): Int
    @Query("UPDATE addon_installations SET enabled = :enabled, revision = revision + 1 WHERE profileId = :profileId AND installationId = :id")
    suspend fun setEnabled(profileId: String, id: String, enabled: Boolean): Int
    @Query("DELETE FROM addon_installations WHERE profileId = :profileId AND installationId = :id")
    suspend fun remove(profileId: String, id: String): Int
    @Query("UPDATE addon_installations SET position = :position, revision = revision + 1 WHERE profileId = :profileId AND installationId = :id")
    suspend fun setPosition(profileId: String, id: String, position: Int)
}

/** Separate file/schema from the production IPTV database. No destructive migration fallback. */
@Database(entities = [AddonInstallationEntity::class], version = 1, exportSchema = true)
abstract class AddonDatabase : RoomDatabase() {
    abstract fun installations(): AddonDao
    companion object {
        const val FILE_NAME = "sohva-addons.db"
        fun open(context: Context): AddonDatabase = Room.databaseBuilder(
            context.applicationContext, AddonDatabase::class.java, FILE_NAME,
        ).build()
    }
}
