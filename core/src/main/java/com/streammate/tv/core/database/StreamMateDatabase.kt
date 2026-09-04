package com.streammate.tv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streammate.tv.core.model.IptvSourceConfiguration

@Database(
    entities = [
        IptvSourceStateEntity::class,
        ChannelPreferenceEntity::class,
        CustomChannelListEntity::class,
        CustomChannelListMemberEntity::class,
        IptvChannelEntity::class,
        XmlTvChannelEntity::class,
        TvProgrammeEntity::class,
        VodMovieEntity::class,
        VodSeriesEntity::class,
        VodEpisodeEntity::class,
        PlaybackProgressEntity::class,
        MetadataCacheEntity::class,
        CatalogueMetadataOverrideEntity::class,
        CatalogueGenreEntity::class,
        CatalogueMetadataWorkEntity::class,
        SportsApiCacheEntity::class,
        ImportStateEntity::class,
        SourceRefreshStateEntity::class,
        TeamAliasEntity::class,
        EventChannelDecisionEntity::class,
        OrganizationRuleEntity::class,
        OrganizationAliasEntity::class,
    ],
    views = [OrganizationMembershipView::class, OrganizationEligibleView::class, OrganizationVisibleMovie::class, OrganizationVisibleSeries::class, OrganizationVisibleChannel::class],
    version = 23,
    exportSchema = true,
)
abstract class StreamMateDatabase : RoomDatabase() {
    abstract fun guideDao(): GuideDao
    abstract fun catalogueDao(): CatalogueDao
    abstract fun metadataDao(): MetadataDao
    abstract fun sportsCacheDao(): SportsCacheDao
    abstract fun organizationDao(): OrganizationDao

    companion object {
        fun create(context: Context): StreamMateDatabase = Room.databaseBuilder(
            context.applicationContext,
            StreamMateDatabase::class.java,
            "streammate.db",
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
            )
            .build()

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS organization_rules (room TEXT NOT NULL, sourceId TEXT NOT NULL, groupKey TEXT NOT NULL, itemKey TEXT NOT NULL, enabled INTEGER, sortMode TEXT, position INTEGER, PRIMARY KEY(room, sourceId, groupKey, itemKey))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_organization_rules_itemKey ON organization_rules(itemKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS organization_aliases (alias TEXT NOT NULL PRIMARY KEY, identity TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_organization_aliases_identity ON organization_aliases(identity)")
                db.execSQL("ALTER TABLE channel_preferences ADD COLUMN customOrganizationGroupKey TEXT DEFAULT NULL")
                db.query("SELECT channelId, customGroupTitle FROM channel_preferences WHERE customGroupTitle IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) db.execSQL("UPDATE channel_preferences SET customOrganizationGroupKey = ? WHERE channelId = ?", arrayOf(com.streammate.tv.core.model.organizationGroupKey(cursor.getString(1)), cursor.getString(0)))
                }
                for ((table, name) in listOf("iptv_channels" to "groupTitle", "vod_movies" to "categoryName", "vod_series" to "categoryName")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN organizationGroupKey TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN organizationNameKey TEXT NOT NULL DEFAULT ''")
                    // SQLite LOWER is ASCII-only; match Kotlin's normalized names for Finnish groups too.
                    db.query("SELECT DISTINCT `$name` FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) {
                            val title = if (cursor.isNull(0)) null else cursor.getString(0)
                            val key = com.streammate.tv.core.model.organizationGroupKey(title)
                            db.execSQL("UPDATE `$table` SET organizationGroupKey = ?, organizationNameKey = ? WHERE `$name` IS ?", arrayOf(key, key, title))
                        }
                    }
                }
                db.execSQL("CREATE VIEW `organization_memberships` AS ${ORGANIZATION_MEMBERSHIPS_SQL.trim()}")
                db.execSQL("CREATE VIEW `organization_eligible_items` AS ${ORGANIZATION_ELIGIBLE_SQL.trim()}")
                db.execSQL("CREATE VIEW `organization_visible_movies` AS ${ORGANIZATION_VISIBLE_MOVIES_SQL.trim()}")
                db.execSQL("CREATE VIEW `organization_visible_series` AS ${ORGANIZATION_VISIBLE_SERIES_SQL.trim()}")
                db.execSQL("CREATE VIEW `organization_visible_channels` AS ${ORGANIZATION_VISIBLE_LIVE_SQL.trim()}")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `team_aliases` (
                        `sport` TEXT NOT NULL,
                        `normalizedCanonicalName` TEXT NOT NULL,
                        `normalizedAlias` TEXT NOT NULL,
                        PRIMARY KEY(`sport`, `normalizedCanonicalName`, `normalizedAlias`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_team_aliases_sport` ON `team_aliases` (`sport`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `event_channel_decisions` (
                        `eventId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `decision` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`, `channelId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_event_channel_decisions_eventId`
                    ON `event_channel_decisions` (`eventId`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channel_preferences` (
                        `channelId` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `customName` TEXT,
                        `customGroupTitle` TEXT,
                        `hidden` INTEGER NOT NULL,
                        `sortOrder` INTEGER,
                        `manualXmltvChannelId` TEXT,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`channelId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channel_preferences_sourceId` " +
                        "ON `channel_preferences` (`sourceId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channel_lists` (
                        `listId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`listId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channel_list_members` (
                        `listId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`listId`, `channelId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channel_list_members_channelId` " +
                        "ON `channel_list_members` (`channelId`)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchupType` TEXT")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchupSource` TEXT")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchupDays` INTEGER")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `xtreamStreamId` TEXT")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchupTimeZone` TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vod_movies` (
                        `sourceId` TEXT NOT NULL, `snapshotId` TEXT NOT NULL,
                        `movieId` TEXT NOT NULL, `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL, `categoryName` TEXT,
                        `posterUrl` TEXT, `encryptedStreamUrl` TEXT NOT NULL,
                        `year` INTEGER, `rating` TEXT, `plot` TEXT,
                        PRIMARY KEY(`sourceId`, `snapshotId`, `movieId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_movies_sourceId_snapshotId` ON `vod_movies` (`sourceId`, `snapshotId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_movies_normalizedName` ON `vod_movies` (`normalizedName`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vod_series` (
                        `sourceId` TEXT NOT NULL, `snapshotId` TEXT NOT NULL,
                        `seriesId` TEXT NOT NULL, `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL, `categoryName` TEXT,
                        `posterUrl` TEXT, `backdropUrl` TEXT, `year` INTEGER,
                        `rating` TEXT, `plot` TEXT,
                        PRIMARY KEY(`sourceId`, `snapshotId`, `seriesId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_series_sourceId_snapshotId` ON `vod_series` (`sourceId`, `snapshotId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_series_normalizedName` ON `vod_series` (`normalizedName`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vod_episodes` (
                        `sourceId` TEXT NOT NULL, `seriesId` TEXT NOT NULL,
                        `episodeId` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL, `name` TEXT NOT NULL,
                        `encryptedStreamUrl` TEXT NOT NULL, `plot` TEXT,
                        `durationSeconds` INTEGER,
                        PRIMARY KEY(`sourceId`, `seriesId`, `episodeId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_episodes_sourceId_seriesId_seasonNumber_episodeNumber` ON `vod_episodes` (`sourceId`, `seriesId`, `seasonNumber`, `episodeNumber`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_progress` (
                        `contentKey` TEXT NOT NULL, `sourceId` TEXT NOT NULL,
                        `contentType` TEXT NOT NULL, `itemId` TEXT NOT NULL,
                        `positionMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL, `lastWatchedEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`contentKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_progress_sourceId` ON `playback_progress` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_progress_lastWatchedEpochMillis` ON `playback_progress` (`lastWatchedEpochMillis`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `metadata_cache` (
                        `lookupKey` TEXT NOT NULL, `provider` TEXT NOT NULL,
                        `status` TEXT NOT NULL, `externalId` TEXT,
                        `mediaType` TEXT NOT NULL, `matchedTitle` TEXT,
                        `displayTitle` TEXT, `overview` TEXT, `posterUrl` TEXT,
                        `backdropUrl` TEXT, `year` INTEGER, `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER, `attributionName` TEXT NOT NULL,
                        `attributionUrl` TEXT NOT NULL, `confidence` REAL NOT NULL,
                        `cachedAtEpochMillis` INTEGER NOT NULL,
                        `expiresAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`lookupKey`, `provider`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_metadata_cache_expiresAtEpochMillis` " +
                        "ON `metadata_cache` (`expiresAtEpochMillis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_metadata_cache_externalId` " +
                        "ON `metadata_cache` (`externalId`)",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sports_api_cache` (
                        `cacheKey` TEXT NOT NULL, `sport` TEXT NOT NULL,
                        `kind` TEXT NOT NULL, `payload` TEXT NOT NULL,
                        `source` TEXT NOT NULL, `quotaRemaining` INTEGER,
                        `fetchedAtEpochMillis` INTEGER NOT NULL,
                        `expiresAtEpochMillis` INTEGER NOT NULL,
                        `staleUntilEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sports_api_cache_staleUntilEpochMillis` " +
                        "ON `sports_api_cache` (`staleUntilEpochMillis`)",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_channels` ADD COLUMN `playlistOrder` " +
                        "INTEGER NOT NULL DEFAULT 2147483647",
                )
                db.execSQL("UPDATE `iptv_channels` SET `playlistOrder` = rowid")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_source_state` ADD COLUMN `epgOffsetMinutes` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vod_episodes` ADD COLUMN `thumbnailUrl` TEXT")
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `runtimeMinutes` INTEGER")
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `rating` TEXT")
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `castJson` TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalogue_metadata_overrides` (
                        `contentKey` TEXT NOT NULL,
                        `providerPosterUrl` TEXT,
                        `replacementPosterUrl` TEXT,
                        `replaceProviderPoster` INTEGER NOT NULL,
                        `replacementTitle` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`contentKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `metadata_cache` ADD COLUMN `detailsLoaded` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `similarMoviesJson` TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_channelId` " +
                        "ON `iptv_channels` (`channelId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vod_movies_sourceId_movieId` " +
                        "ON `vod_movies` (`sourceId`, `movieId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vod_episodes_sourceId_episodeId` " +
                        "ON `vod_episodes` (`sourceId`, `episodeId`)",
                )
            }
        }

        /**
         * Genres, so the library can be grouped by what a title is rather than
         * by the bucket its playlist happened to use.
         *
         * Nothing is backfilled here. The rows arrive as the metadata worker
         * makes its usual pass, so an upgrade costs a schema change and no
         * requests; until that pass reaches a title it simply has no genres.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `genresJson` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalogue_genres` (
                        `contentKey` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        PRIMARY KEY(`contentKey`, `genre`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_catalogue_genres_genre` " +
                        "ON `catalogue_genres` (`genre`)",
                )
            }
        }

        /**
         * Stamps each matched title, and each cached match, with the genre
         * vocabulary it was sorted under. Everything already stored predates
         * genres entirely, so zero is the right default on both: the metadata
         * pass treats those titles as still to do, and will not answer from a
         * cached match that has no genres in it.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `catalogue_metadata_overrides` " +
                        "ADD COLUMN `genresVersion` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `metadata_cache` " +
                        "ADD COLUMN `genresVersion` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Lets a match be chosen by hand. Nothing already stored was, so every
         * existing row stays exactly as the matcher left it.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `metadata_cache` " +
                        "ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Records which TMDB entry a title turned out to be, so that two copies
         * of one film can be recognised as the same film. Nothing already
         * stored knows, and each row learns it the next time the metadata pass
         * reaches it.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `catalogue_metadata_overrides` ADD COLUMN `externalId` TEXT",
                )
            }
        }

        /**
         * Records which film a saved position belongs to, so that a position
         * can be found from either copy of a duplicated film. Positions already
         * saved carry no work key and stand for themselves until next written.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playback_progress` ADD COLUMN `workKey` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playback_progress_workKey` " +
                        "ON `playback_progress` (`workKey`)",
                )
            }
        }

        /**
         * A compact, indexed queue for metadata enrichment. It is intentionally
         * empty after migration: the first background run seeds it from the
         * active snapshots while preserving already-settled overrides.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalogue_metadata_work` (
                        `contentKey` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `year` INTEGER,
                        `providerPosterUrl` TEXT,
                        `targetGenresVersion` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextAttemptAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`contentKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_catalogue_metadata_work_state_nextAttemptAtEpochMillis_contentKey` " +
                        "ON `catalogue_metadata_work` " +
                        "(`state`, `nextAttemptAtEpochMillis`, `contentKey`)",
                )
            }
        }

        /**
         * Makes playlist-category navigation an indexed lookup and lets the
         * poster wall read a narrow projection instead of detail-sized rows.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vod_movies` ADD COLUMN `categoryKey` TEXT")
                db.execSQL("ALTER TABLE `vod_series` ADD COLUMN `categoryKey` TEXT")
                db.execSQL(
                    "UPDATE `vod_movies` SET `categoryKey` = LOWER(TRIM(`categoryName`)) " +
                        "WHERE `categoryName` IS NOT NULL AND TRIM(`categoryName`) != ''",
                )
                db.execSQL(
                    "UPDATE `vod_series` SET `categoryKey` = LOWER(TRIM(`categoryName`)) " +
                        "WHERE `categoryName` IS NOT NULL AND TRIM(`categoryName`) != ''",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vod_movies_categoryKey` " +
                        "ON `vod_movies` (`categoryKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vod_series_categoryKey` " +
                        "ON `vod_series` (`categoryKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playback_progress_contentType` " +
                        "ON `playback_progress` (`contentType`)",
                )
            }
        }

        /**
         * A title now belongs to one primary genre instead of every genre TMDB
         * returned. Existing rows were written in the enum's stable order, and
         * their wire values sort in that same order, so MIN is the best primary
         * available without refetching thousands of already-enriched titles.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM `catalogue_genres` " +
                        "WHERE `genre` != (" +
                        "SELECT MIN(`candidate`.`genre`) FROM `catalogue_genres` AS `candidate` " +
                        "WHERE `candidate`.`contentKey` = `catalogue_genres`.`contentKey`" +
                        ")",
                )
                // Version one rows were fully enriched; this migration itself
                // applies version two's single-primary policy, so they do not
                // need to hit TMDB again. Version zero still means unfinished.
                db.execSQL(
                    "UPDATE `catalogue_metadata_overrides` SET `genresVersion` = 2 " +
                        "WHERE `genresVersion` = 1",
                )
                db.execSQL(
                    "UPDATE `metadata_cache` SET `genresVersion` = 2 " +
                        "WHERE `genresVersion` = 1",
                )
                db.execSQL(
                    "UPDATE `catalogue_metadata_work` SET `targetGenresVersion` = 2 " +
                        "WHERE `targetGenresVersion` = 1",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `iptv_channels_new` (
                        `sourceId` TEXT NOT NULL,
                        `snapshotId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `tvgId` TEXT,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `groupTitle` TEXT,
                        `logoUrl` TEXT,
                        `encryptedStreamUrl` TEXT NOT NULL,
                        `userAgent` TEXT,
                        `referrer` TEXT,
                        `lastSeenEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `snapshotId`, `channelId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `iptv_channels_new` (
                        `sourceId`, `snapshotId`, `channelId`, `tvgId`, `name`,
                        `normalizedName`, `groupTitle`, `logoUrl`, `encryptedStreamUrl`,
                        `userAgent`, `referrer`, `lastSeenEpochMillis`
                    )
                    SELECT ?, `snapshotId`, ? || ':' || `channelId`, `tvgId`, `name`,
                        `normalizedName`, `groupTitle`, `logoUrl`, `encryptedStreamUrl`,
                        `userAgent`, `referrer`, `lastSeenEpochMillis`
                    FROM `iptv_channels`
                    """.trimIndent(),
                    arrayOf(LEGACY_SOURCE_ID, LEGACY_SOURCE_ID),
                )
                db.execSQL("DROP TABLE `iptv_channels`")
                db.execSQL("ALTER TABLE `iptv_channels_new` RENAME TO `iptv_channels`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_snapshotId` " +
                        "ON `iptv_channels` (`sourceId`, `snapshotId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_tvgId` " +
                        "ON `iptv_channels` (`sourceId`, `tvgId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `xmltv_channels_new` (
                        `sourceId` TEXT NOT NULL,
                        `snapshotId` TEXT NOT NULL,
                        `xmltvChannelId` TEXT NOT NULL,
                        `displayName` TEXT,
                        `iconUrl` TEXT,
                        PRIMARY KEY(`sourceId`, `snapshotId`, `xmltvChannelId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `xmltv_channels_new` (
                        `sourceId`, `snapshotId`, `xmltvChannelId`, `displayName`, `iconUrl`
                    )
                    SELECT ?, `snapshotId`, `xmltvChannelId`, `displayName`, `iconUrl`
                    FROM `xmltv_channels`
                    """.trimIndent(),
                    arrayOf(LEGACY_SOURCE_ID),
                )
                db.execSQL("DROP TABLE `xmltv_channels`")
                db.execSQL("ALTER TABLE `xmltv_channels_new` RENAME TO `xmltv_channels`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_xmltv_channels_sourceId_snapshotId` " +
                        "ON `xmltv_channels` (`sourceId`, `snapshotId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tv_programmes_new` (
                        `sourceId` TEXT NOT NULL,
                        `snapshotId` TEXT NOT NULL,
                        `programmeId` TEXT NOT NULL,
                        `xmltvChannelId` TEXT NOT NULL,
                        `startEpochMillis` INTEGER NOT NULL,
                        `stopEpochMillis` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT,
                        `description` TEXT,
                        `categories` TEXT NOT NULL,
                        PRIMARY KEY(`sourceId`, `snapshotId`, `programmeId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `tv_programmes_new` (
                        `sourceId`, `snapshotId`, `programmeId`, `xmltvChannelId`,
                        `startEpochMillis`, `stopEpochMillis`, `title`, `subtitle`,
                        `description`, `categories`
                    )
                    SELECT ?, `snapshotId`, `programmeId`, `xmltvChannelId`,
                        `startEpochMillis`, `stopEpochMillis`, `title`, `subtitle`,
                        `description`, `categories`
                    FROM `tv_programmes`
                    """.trimIndent(),
                    arrayOf(LEGACY_SOURCE_ID),
                )
                db.execSQL("DROP TABLE `tv_programmes`")
                db.execSQL("ALTER TABLE `tv_programmes_new` RENAME TO `tv_programmes`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tv_programmes_sourceId_snapshotId` " +
                        "ON `tv_programmes` (`sourceId`, `snapshotId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_tv_programmes_sourceId_xmltvChannelId_startEpochMillis_stopEpochMillis` " +
                        "ON `tv_programmes` (`sourceId`, `xmltvChannelId`, `startEpochMillis`, " +
                        "`stopEpochMillis`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_state_new` (
                        `sourceId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `activeSnapshotId` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `kind`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `import_state_new` (
                        `sourceId`, `kind`, `activeSnapshotId`, `updatedAtEpochMillis`, `itemCount`
                    )
                    SELECT ?, `kind`, `activeSnapshotId`, `updatedAtEpochMillis`, `itemCount`
                    FROM `import_state`
                    """.trimIndent(),
                    arrayOf(LEGACY_SOURCE_ID),
                )
                db.execSQL("DROP TABLE `import_state`")
                db.execSQL("ALTER TABLE `import_state_new` RENAME TO `import_state`")
                db.execSQL(
                    "UPDATE `event_channel_decisions` SET `channelId` = ? || ':' || `channelId`",
                    arrayOf(LEGACY_SOURCE_ID),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `iptv_source_state` (
                        `sourceId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `connectionLimit` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `iptv_source_state` (
                        `sourceId`, `name`, `type`, `enabled`, `connectionLimit`,
                        `priority`, `updatedAtEpochMillis`
                    )
                    SELECT ?, 'IPTV', 'M3U', 1, 1, 0, `updatedAtEpochMillis`
                    FROM `import_state`
                    ORDER BY `updatedAtEpochMillis` DESC
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(LEGACY_SOURCE_ID),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_refresh_state` (
                        `sourceId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastAttemptAtEpochMillis` INTEGER NOT NULL,
                        `lastSuccessAtEpochMillis` INTEGER,
                        `lastFailureAtEpochMillis` INTEGER,
                        `lastError` TEXT,
                        `itemCount` INTEGER NOT NULL,
                        `consecutiveFailures` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `kind`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private const val LEGACY_SOURCE_ID = IptvSourceConfiguration.LEGACY_SOURCE_ID
    }
}
