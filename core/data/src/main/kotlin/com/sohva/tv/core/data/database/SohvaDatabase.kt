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
import com.sohva.tv.core.data.channels.ChannelListDao
import com.sohva.tv.core.data.live.ChannelEffects
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
        WatchProgressEntity::class,
        MetadataMatchEntity::class,
        MetadataCacheEntity::class,
        MetadataPinEntity::class,
        MetadataQueueEntity::class,
        GenreCountEntity::class,
        OrganizationRuleEntity::class,
        MovieSearchEntity::class,
        SeriesSearchEntity::class,
        EpisodeSearchEntity::class,
        ChannelSearchEntity::class,
        ProgrammeSearchEntity::class,
        ProfileAllowedGroupEntity::class,
    ],
    version = SohvaDatabase.VERSION,
    exportSchema = true,
    // 1 -> 2 (M1) and 2 -> 3 (M2: favourites, recents) only add tables, which Room's generated
    // migrations do exactly. 3 -> 4 (M3) adds the edit tables and the channel's provider columns,
    // which [ProviderColumns] fills from the effective ones (nothing was editable before M3).
    // 4 -> 5 (M4) adds the films' claim columns with defaults, the wall indexes and watch_progress;
    // the next import fills the claims (the import hash carries a keys version), so opening the
    // database runs no data migration. 5 -> 6 (M4b) adds the metadata tables and the titles'
    // metadata columns; the next import and the enrichment fill them. 6 -> 7 (M4c) adds the
    // organisation rules and the indexes of the other content orders. 7 -> 8 (M5) adds Search's
    // full-text tables; [SearchTablesCreated] installs their triggers and indexes existing rows once,
    // inside the migration: no released install holds version 7 data, so the one-time cost falls
    // only on test installs (decision "Search index"). 8 -> 9 (M6) adds the profiles' allowed groups.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = SohvaDatabase.ProviderColumns::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = SohvaDatabase.SearchTablesCreated::class),
        AutoMigration(from = 8, to = 9),
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

    abstract fun channelLists(): ChannelListDao

    abstract fun walls(): WallDao

    abstract fun progress(): ProgressDao

    abstract fun titles(): TitleDao

    abstract fun library(): LibraryDao

    abstract fun metadata(): MetadataDao

    abstract fun manager(): ManagerDao

    abstract fun organization(): OrgDao

    abstract fun search(): SearchDao

    abstract fun home(): HomeDao

    abstract fun profiles(): ProfileDao

    /**
     * v3 -> v4: before M3 the effective columns held the playlist's values, so they are copied, and
     * ranks move to M3's scheme (playlist order above every viewer position), or channels an import
     * leaves unchanged would sort against re-imported ones in two different scales.
     */
    class ProviderColumns : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE channel SET provider_name = name, provider_group_id = group_id, provider_logo_url = logo_url")
            db.execSQL("UPDATE channel SET display_rank = ${ChannelEffects.UNPOSITIONED} + playlist_order * ${ChannelEffects.RANK_STEP}")
        }
    }

    /** v7 -> v8: the search tables are new and empty; their triggers go in and existing rows are indexed. */
    class SearchTablesCreated : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            SearchIndex.installTriggers(db)
            SearchIndex.fill(db)
        }
    }

    companion object {
        const val VERSION: Int = 9

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
