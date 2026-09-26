package com.sohva.tv.feature.discover.store

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Discover's own database (plan/04 §15.9): never joined with the main database, outside Android
 * backup and `.smbak` backups (spec 50 §6). Payloads are encrypted; keys are hashes. Opened lazily
 * on the first query, so Discover costs nothing until used (spec 50 §9 "Start-up").
 */
@Database(
    entities = [InstallationEntity::class, CatalogPrefEntity::class, ProgressEntity::class, LibraryEntity::class],
    version = DiscoverDatabase.VERSION,
    exportSchema = true,
)
abstract class DiscoverDatabase : RoomDatabase() {
    abstract fun installations(): InstallationDao

    abstract fun catalogs(): CatalogPrefDao

    abstract fun progress(): ProgressDao

    abstract fun library(): LibraryDao

    companion object {
        const val VERSION: Int = 1
        const val FILE: String = "discover.db"

        fun open(context: Context): DiscoverDatabase =
            Room.databaseBuilder(context.applicationContext, DiscoverDatabase::class.java, FILE)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/** One installed addon per profile and normalised URL (ADDON-FR-29); [payload] holds the URL and manifest, encrypted. */
@Entity(tableName = "addon_installation", indices = [Index(value = ["profile_id", "fingerprint"], unique = true), Index(value = ["profile_id", "position"])])
data class InstallationEntity(
    @PrimaryKey @ColumnInfo(name = "installation_id") val installationId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val fingerprint: String,
    val payload: String,
    val enabled: Boolean,
    val position: Int,
    val revision: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    override fun toString(): String = "InstallationEntity($installationId, rev=$revision)"
}

/** A catalog's saved place and visibility per profile (ADDON-FR-51…54); [position] null when never ordered. */
@Entity(tableName = "addon_catalog_pref", primaryKeys = ["profile_id", "catalog_key"])
data class CatalogPrefEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "catalog_key") val catalogKey: String,
    val position: Int?,
    val hidden: Boolean,
)

/**
 * Watch progress of one title or episode (ADDON-FR-105); [key] hashes its identity. Home's
 * Continue watching reads only the plaintext-free columns ([titleKey], [resumable], the time) and
 * decrypts just the rows that become cards (spec 02 §9.1).
 */
@Entity(
    tableName = "addon_progress",
    indices = [Index(value = ["profile_id", "updated_at"]), Index(value = ["profile_id", "resumable", "title_key"])],
)
data class ProgressEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Hashes the profile, metadata installation, media type and id: episodes of one series share it (HOME-FR-19). */
    @ColumnInfo(name = "title_key") val titleKey: String,
    /** Not completed and past the start (HOME-FR-17). */
    val resumable: Boolean,
)

/** A Library title (ADDON-FR-110). */
@Entity(tableName = "addon_library", indices = [Index(value = ["profile_id", "added_at"])])
data class LibraryEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val payload: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Dao
interface InstallationDao {
    @Query("SELECT * FROM addon_installation WHERE profile_id = :profile ORDER BY position, installation_id")
    suspend fun of(profile: String): List<InstallationEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM addon_installation WHERE profile_id = :profile")
    suspend fun lastPosition(profile: String): Int

    @Insert
    suspend fun insert(row: InstallationEntity)

    /** Writes only when the row still has [expected] revision (ADDON-FR-30); returns rows written. */
    @Query(
        "UPDATE addon_installation SET payload = :payload, enabled = :enabled, position = :position, revision = revision + 1, updated_at = :now " +
            "WHERE installation_id = :id AND revision = :expected",
    )
    suspend fun update(id: String, expected: Long, payload: String, enabled: Boolean, position: Int, now: Long): Int

    @Query("DELETE FROM addon_installation WHERE installation_id = :id AND profile_id = :profile")
    suspend fun delete(id: String, profile: String): Int

    @Query("DELETE FROM addon_installation WHERE profile_id = :profile")
    suspend fun deleteProfile(profile: String)
}

@Dao
interface CatalogPrefDao {
    @Query("SELECT * FROM addon_catalog_pref WHERE profile_id = :profile")
    suspend fun of(profile: String): List<CatalogPrefEntity>

    @Query("DELETE FROM addon_catalog_pref WHERE profile_id = :profile")
    suspend fun clear(profile: String)

    @Upsert
    suspend fun put(rows: List<CatalogPrefEntity>)

    /** One write for a whole order or visibility change (ADDON-FR-53). */
    @Transaction
    suspend fun replace(profile: String, rows: List<CatalogPrefEntity>) {
        clear(profile)
        put(rows)
    }
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM addon_progress WHERE profile_id = :profile ORDER BY updated_at DESC LIMIT :limit")
    suspend fun recent(profile: String, limit: Int): List<ProgressEntity>

    @Query("SELECT * FROM addon_progress WHERE key = :key")
    suspend fun get(key: String): ProgressEntity?

    @Upsert
    suspend fun put(row: ProgressEntity)

    @Query("DELETE FROM addon_progress WHERE key = :key")
    suspend fun delete(key: String)

    /** Keeps the newest [keep] of a profile (ADDON-FR-107); run only when a new key was inserted. */
    @Query(
        "DELETE FROM addon_progress WHERE profile_id = :profile AND key NOT IN " +
            "(SELECT key FROM addon_progress WHERE profile_id = :profile ORDER BY updated_at DESC LIMIT :keep)",
    )
    suspend fun prune(profile: String, keep: Int)

    @Query("DELETE FROM addon_progress WHERE profile_id = :profile")
    suspend fun deleteProfile(profile: String)

    /**
     * Home's Discover part (HOME-FR-17, -19): per title the newest resumable row, newest first. At
     * most 200 rows per profile are scanned, through the index; ties within a title are removed by the caller.
     */
    @Query(
        "SELECT * FROM addon_progress AS p WHERE p.profile_id = :profile AND p.resumable = 1 AND p.updated_at = " +
            "(SELECT MAX(q.updated_at) FROM addon_progress AS q WHERE q.profile_id = :profile AND q.resumable = 1 AND q.title_key = p.title_key) " +
            "ORDER BY p.updated_at DESC, p.key LIMIT :limit",
    )
    suspend fun resumableTitles(profile: String, limit: Int): List<ProgressEntity>
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM addon_library WHERE profile_id = :profile ORDER BY added_at DESC LIMIT :limit")
    suspend fun newest(profile: String, limit: Int): List<LibraryEntity>

    @Query("SELECT * FROM addon_library WHERE key = :key")
    suspend fun get(key: String): LibraryEntity?

    @Query("SELECT COUNT(*) FROM addon_library WHERE profile_id = :profile")
    suspend fun count(profile: String): Int

    @Upsert
    suspend fun put(row: LibraryEntity)

    /** ADDON-FR-110: capacity checked inside the insert transaction; false when full. */
    @Transaction
    suspend fun add(row: LibraryEntity, capacity: Int): Boolean {
        if (get(row.key) == null && count(row.profileId) >= capacity) return false
        put(row)
        return true
    }

    @Query("DELETE FROM addon_library WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM addon_library WHERE profile_id = :profile")
    suspend fun deleteProfile(profile: String)
}
