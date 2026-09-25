package com.sohva.tv.core.data.database

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import com.sohva.tv.core.data.reminder.ReminderDao
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The main database. Milestones add their tables; every change bumps [VERSION] and adds its
 * migration and migration test in the same edit (AGENTS.md §8).
 */
@Database(
    entities = [
        AppMetaEntity::class,
        SourceEntity::class,
        SourceStatusEntity::class,
        ContentGroupEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        EpgChannelEntity::class,
        ProgrammeEntity::class,
        FavouriteChannelEntity::class,
        RecentChannelEntity::class,
        ChannelCustomEntity::class,
        ChannelListEntity::class,
        ChannelListMemberEntity::class,
        LockedChannelEntity::class,
        ReminderEntity::class,
    ],
    version = SohvaDatabase.VERSION,
    exportSchema = true,
    // 1 -> 2 (M1) and 2 -> 3 (M2: favourites, recents) only add tables, which Room's generated
    // migrations do exactly. 3 -> 4 (M3) adds the edit tables and the channel's provider columns,
    // which [ProviderColumns] fills from the effective ones (nothing was editable before M3).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = SohvaDatabase.ProviderColumns::class),
    ],
)
abstract class SohvaDatabase : RoomDatabase() {
    abstract fun appMeta(): AppMetaDao

    abstract fun sources(): SourceDao

    abstract fun sourceStatus(): SourceStatusDao

    abstract fun channelImport(): ChannelImportDao

    abstract fun movieImport(): MovieImportDao

    abstract fun seriesImport(): SeriesImportDao

    abstract fun episodeImport(): EpisodeImportDao

    abstract fun groupImport(): GroupImportDao

    abstract fun guideImport(): GuideImportDao

    abstract fun live(): LiveDao

    abstract fun viewer(): ViewerDao

    abstract fun channelEdits(): ChannelEditDao

    abstract fun reminders(): ReminderDao

    /** v3 -> v4: before M3 the effective columns held the playlist's values, so they are copied. */
    class ProviderColumns : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE channel SET provider_name = name, provider_group_id = group_id, provider_logo_url = logo_url")
        }
    }

    companion object {
        const val VERSION: Int = 4

        /** Not beta 23's `streammate.db`, which the one-time importer reads (decision A1). */
        const val FILE_NAME: String = "sohva.db"
    }
}

/** Small key/value facts about the installation, such as the one-time import marker. */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE key = :key")
    suspend fun value(key: String): String?

    @Upsert
    suspend fun put(entry: AppMetaEntity)
}
