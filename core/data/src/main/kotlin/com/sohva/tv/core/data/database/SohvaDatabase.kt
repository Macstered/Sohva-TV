package com.sohva.tv.core.data.database

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

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
    ],
    version = SohvaDatabase.VERSION,
    exportSchema = true,
    // 1 -> 2 (M1) only adds tables, which Room's generated migration does exactly.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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

    companion object {
        const val VERSION: Int = 2

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
