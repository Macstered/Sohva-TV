package com.streammate.tv.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamMateDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StreamMateDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFromOneToEightPreservesGuideAndAddsMatchingTables() {
        helper.createDatabase(TEST_DATABASE_FROM_ONE, 1).apply {
            execSQL(
                """
                INSERT INTO import_state(kind, activeSnapshotId, updatedAtEpochMillis, itemCount)
                VALUES ('playlist', 'snapshot-1', 1, 2)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_ONE,
            8,
            true,
            StreamMateDatabase.MIGRATION_1_2,
            StreamMateDatabase.MIGRATION_2_3,
            StreamMateDatabase.MIGRATION_3_4,
            StreamMateDatabase.MIGRATION_4_5,
            StreamMateDatabase.MIGRATION_5_6,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.query(
                "SELECT sourceId, itemCount FROM import_state WHERE kind = 'playlist'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("m3u-primary", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
            }
            database.query("SELECT COUNT(*) FROM team_aliases").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM event_channel_decisions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM source_refresh_state").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT sourceId, connectionLimit FROM iptv_source_state").use { cursor ->
                cursor.moveToFirst()
                assertEquals("m3u-primary", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            database.query("SELECT COUNT(*) FROM channel_preferences").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFromTwoToEightAddsSourceOwnershipAndPreservesDecisions() {
        helper.createDatabase(TEST_DATABASE_FROM_TWO, 2).apply {
            execSQL(
                """
                INSERT INTO iptv_channels(
                    snapshotId, channelId, tvgId, name, normalizedName, groupTitle,
                    logoUrl, encryptedStreamUrl, userAgent, referrer, lastSeenEpochMillis
                ) VALUES (
                    'snapshot-1', 'channel-1', 'guide-1', 'Channel One', 'channel one',
                    'News', NULL, 'encrypted', NULL, NULL, 5
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO import_state(kind, activeSnapshotId, updatedAtEpochMillis, itemCount)
                VALUES ('playlist', 'snapshot-1', 5, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO event_channel_decisions(eventId, channelId, decision, updatedAtEpochMillis)
                VALUES ('event-1', 'channel-1', 'confirmed', 5)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_TWO,
            8,
            true,
            StreamMateDatabase.MIGRATION_2_3,
            StreamMateDatabase.MIGRATION_3_4,
            StreamMateDatabase.MIGRATION_4_5,
            StreamMateDatabase.MIGRATION_5_6,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.query("SELECT sourceId, channelId FROM iptv_channels").use { cursor ->
                cursor.moveToFirst()
                assertEquals("m3u-primary", cursor.getString(0))
                assertEquals("m3u-primary:channel-1", cursor.getString(1))
            }
            database.query("SELECT channelId FROM event_channel_decisions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("m3u-primary:channel-1", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrationFromThreeToEightAddsPersistentChannelPreferences() {
        helper.createDatabase(TEST_DATABASE_FROM_THREE, 3).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_THREE,
            8,
            true,
            StreamMateDatabase.MIGRATION_3_4,
            StreamMateDatabase.MIGRATION_4_5,
            StreamMateDatabase.MIGRATION_5_6,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO channel_preferences(
                    channelId, sourceId, customName, customGroupTitle, hidden,
                    sortOrder, manualXmltvChannelId, updatedAtEpochMillis
                ) VALUES ('source:one', 'source', 'One HD', 'News', 1, 4, 'guide.one', 10)
                """.trimIndent(),
            )
            database.query(
                "SELECT customName, hidden, manualXmltvChannelId FROM channel_preferences",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("One HD", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("guide.one", cursor.getString(2))
            }
            database.execSQL(
                "INSERT INTO channel_lists(listId, name, sortOrder, updatedAtEpochMillis) " +
                    "VALUES ('favourites', 'Favourites', 0, 10)",
            )
            database.execSQL(
                "INSERT INTO channel_list_members(listId, channelId, sortOrder) " +
                    "VALUES ('favourites', 'source:one', 0)",
            )
            database.query("SELECT COUNT(*) FROM channel_list_members").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFromFourToEightAddsCatchupCapabilities() {
        helper.createDatabase(TEST_DATABASE_FROM_FOUR, 4).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_FOUR,
            8,
            true,
            StreamMateDatabase.MIGRATION_4_5,
            StreamMateDatabase.MIGRATION_5_6,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO iptv_channels(
                    sourceId, snapshotId, channelId, tvgId, name, normalizedName,
                    groupTitle, logoUrl, encryptedStreamUrl, userAgent, referrer,
                    lastSeenEpochMillis, catchupType, catchupSource, catchupDays, xtreamStreamId
                ) VALUES (
                    'source', 'snapshot', 'source:one', NULL, 'One', 'one', NULL,
                    NULL, 'encrypted', NULL, NULL, 1, 'xtream', NULL, 7, '42'
                )
                """.trimIndent(),
            )
            database.query(
                "SELECT catchupType, catchupDays, xtreamStreamId FROM iptv_channels",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("xtream", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals("42", cursor.getString(2))
            }
        }
    }

    @Test
    fun migrationFromFiveToEightAddsVodCatalogueAndProgress() {
        helper.createDatabase(TEST_DATABASE_FROM_FIVE, 5).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_FIVE,
            8,
            true,
            StreamMateDatabase.MIGRATION_5_6,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO playback_progress(
                    contentKey, sourceId, contentType, itemId, positionMillis,
                    durationMillis, completed, lastWatchedEpochMillis
                ) VALUES ('vod:movie:source:42', 'source', 'movie', '42', 1000, 10000, 0, 5)
                """.trimIndent(),
            )
            database.query("SELECT positionMillis, completed FROM playback_progress").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1000, cursor.getLong(0))
                assertEquals(0, cursor.getInt(1))
            }
            database.query("SELECT COUNT(*) FROM vod_movies").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFromSixToEightAddsMetadataCacheWithProvenance() {
        helper.createDatabase(TEST_DATABASE_FROM_SIX, 6).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_SIX,
            8,
            true,
            StreamMateDatabase.MIGRATION_6_7,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO metadata_cache(
                    lookupKey, provider, status, externalId, mediaType,
                    matchedTitle, displayTitle, overview, posterUrl, backdropUrl,
                    year, seasonNumber, episodeNumber, attributionName,
                    attributionUrl, confidence, cachedAtEpochMillis, expiresAtEpochMillis
                ) VALUES (
                    'lookup', 'tmdb', 'positive', '42', 'movie', 'Test', 'Test',
                    'Overview', 'https://image.example/poster.jpg', NULL, 2026,
                    NULL, NULL, 'TMDB', 'https://www.themoviedb.org/movie/42',
                    0.99, 10, 20
                )
                """.trimIndent(),
            )
            database.query(
                "SELECT provider, attributionName, confidence FROM metadata_cache",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("tmdb", cursor.getString(0))
                assertEquals("TMDB", cursor.getString(1))
                assertEquals(0.99, cursor.getDouble(2), 0.001)
            }
        }
    }

    @Test
    fun migrationFromSevenToEightAddsPersistentSportsCache() {
        helper.createDatabase(TEST_DATABASE_FROM_SEVEN, 7).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_SEVEN,
            8,
            true,
            StreamMateDatabase.MIGRATION_7_8,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO sports_api_cache(
                    cacheKey, sport, kind, payload, source, quotaRemaining,
                    fetchedAtEpochMillis, expiresAtEpochMillis, staleUntilEpochMillis
                ) VALUES (
                    'football|events|2026-08-25|Europe/Helsinki', 'football',
                    'events', '{}', 'api-sports-football', 88, 10, 20, 30
                )
                """.trimIndent(),
            )
            database.query(
                "SELECT source, quotaRemaining, staleUntilEpochMillis FROM sports_api_cache",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("api-sports-football", cursor.getString(0))
                assertEquals(88, cursor.getInt(1))
                assertEquals(30, cursor.getLong(2))
            }
        }
    }

    @Test
    fun migrationFromEightToNineAddsPlaylistOrder() {
        helper.createDatabase(TEST_DATABASE_FROM_EIGHT, 8).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_EIGHT,
            9,
            true,
            StreamMateDatabase.MIGRATION_8_9,
        ).use { database ->
            database.query("PRAGMA table_info(`iptv_channels`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "playlistOrder") found = true
                }
                assertEquals(true, found)
            }
        }
    }

    @Test
    fun migrationFromNineToTenAddsZeroEpgOffset() {
        helper.createDatabase(TEST_DATABASE_FROM_NINE, 9).apply {
            execSQL(
                """
                INSERT INTO iptv_source_state(
                    sourceId, name, type, enabled, connectionLimit, priority, updatedAtEpochMillis
                ) VALUES ('source', 'Source', 'M3U', 1, 1, 0, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_NINE,
            10,
            true,
            StreamMateDatabase.MIGRATION_9_10,
        ).use { database ->
            database.query(
                "SELECT epgOffsetMinutes FROM iptv_source_state WHERE sourceId = 'source'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFromTenToElevenAddsEpisodeArtworkAndRichMetadata() {
        helper.createDatabase(TEST_DATABASE_FROM_TEN, 10).apply {
            execSQL(
                """
                INSERT INTO vod_episodes(
                    sourceId, seriesId, episodeId, seasonNumber, episodeNumber,
                    name, encryptedStreamUrl, plot, durationSeconds
                ) VALUES ('source', 'series', 'episode', 1, 2, 'Episode', 'encrypted', NULL, 1800)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_TEN,
            11,
            true,
            StreamMateDatabase.MIGRATION_10_11,
        ).use { database ->
            database.query(
                "SELECT durationSeconds, thumbnailUrl FROM vod_episodes WHERE episodeId = 'episode'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1800, cursor.getInt(0))
                assertEquals(true, cursor.isNull(1))
            }
            database.query("PRAGMA table_info(`metadata_cache`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                assertEquals(true, "runtimeMinutes" in columns)
                assertEquals(true, "rating" in columns)
                assertEquals(true, "castJson" in columns)
            }
        }
    }

    @Test
    fun migrationFromElevenToTwelveAddsCatalogueMetadataOverrides() {
        helper.createDatabase(TEST_DATABASE_FROM_ELEVEN, 11).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_ELEVEN,
            12,
            true,
            StreamMateDatabase.MIGRATION_11_12,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO catalogue_metadata_overrides(
                    contentKey, providerPosterUrl, replacementPosterUrl,
                    replaceProviderPoster, replacementTitle, updatedAtEpochMillis
                ) VALUES ('vod:movie:source:movie', 'http://broken.test/poster.jpg',
                    'https://image.tmdb.test/poster.jpg', 1, 'Korvaava nimi', 123)
                """.trimIndent(),
            )
            database.query(
                "SELECT providerPosterUrl, replacementPosterUrl, replaceProviderPoster, " +
                    "replacementTitle, " +
                    "updatedAtEpochMillis " +
                    "FROM catalogue_metadata_overrides WHERE contentKey = 'vod:movie:source:movie'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("http://broken.test/poster.jpg", cursor.getString(0))
                assertEquals("https://image.tmdb.test/poster.jpg", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("Korvaava nimi", cursor.getString(3))
                assertEquals(123L, cursor.getLong(4))
            }
        }
    }

    @Test
    fun migrationFromTwelveToThirteenAddsMovieDetailMetadata() {
        helper.createDatabase(TEST_DATABASE_FROM_TWELVE, 12).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_TWELVE,
            13,
            true,
            StreamMateDatabase.MIGRATION_12_13,
        ).use { database ->
            database.query("PRAGMA table_info(`metadata_cache`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                val columns = buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(nameIndex), cursor.getString(defaultIndex))
                    }
                }
                assertEquals("0", columns["detailsLoaded"])
                assertEquals(true, "similarMoviesJson" in columns)
            }
        }
    }

    @Test
    fun migrationFromThirteenToFourteenAddsLookupIndices() {
        helper.createDatabase(TEST_DATABASE_FROM_THIRTEEN, 13).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_THIRTEEN,
            14,
            true,
            StreamMateDatabase.MIGRATION_13_14,
        ).use { database ->
            val indices = buildSet {
                database.query("PRAGMA index_list(`iptv_channels`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                database.query("PRAGMA index_list(`vod_movies`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                database.query("PRAGMA index_list(`vod_episodes`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertEquals(true, "index_iptv_channels_channelId" in indices)
            assertEquals(true, "index_vod_movies_sourceId_movieId" in indices)
            assertEquals(true, "index_vod_episodes_sourceId_episodeId" in indices)
        }
    }

    /**
     * The genre table arrives empty and a title already in the cache keeps
     * everything it had. Nothing is re-fetched by the upgrade itself: the rows
     * fill in as the metadata worker makes its usual pass.
     */
    @Test
    fun migrationToFifteenAddsGenresWithoutDisturbingWhatWasAlreadyMatched() {
        helper.createDatabase(TEST_DATABASE_FROM_FOURTEEN, 14).apply {
            execSQL(
                """
                INSERT INTO metadata_cache(
                    lookupKey, provider, status, externalId, mediaType, matchedTitle,
                    displayTitle, overview, posterUrl, backdropUrl, year, seasonNumber,
                    episodeNumber, detailsLoaded, attributionName, attributionUrl,
                    confidence, cachedAtEpochMillis, expiresAtEpochMillis
                ) VALUES (
                    'key-1', 'tmdb', 'positive', '603', 'movie', 'The Matrix',
                    'The Matrix', NULL, NULL, NULL, 1999, NULL,
                    NULL, 1, 'TMDB', 'https://www.themoviedb.org/movie/603',
                    0.99, 1, 9999999999999
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_FOURTEEN,
            15,
            true,
            StreamMateDatabase.MIGRATION_14_15,
        ).use { database ->
            database.query("SELECT displayTitle, genresJson FROM metadata_cache").use { cursor ->
                cursor.moveToFirst()
                assertEquals("The Matrix", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
            database.query("SELECT COUNT(*) FROM catalogue_genres").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            // The rail groups by genre, so that column is read far more often
            // than it is written.
            val indices = buildList {
                database.query("PRAGMA index_list(`catalogue_genres`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertEquals(true, "index_catalogue_genres_genre" in indices)
        }
    }

    /**
     * A library matched before genres existed keeps its posters and titles, and
     * is stamped as unsorted so the metadata pass comes back to it.
     */
    @Test
    fun migrationToSixteenMarksEverythingAlreadyMatchedAsStillToSort() {
        helper.createDatabase(TEST_DATABASE_FROM_FIFTEEN, 15).apply {
            execSQL(
                """
                INSERT INTO catalogue_metadata_overrides(
                    contentKey, providerPosterUrl, replacementPosterUrl,
                    replaceProviderPoster, replacementTitle, updatedAtEpochMillis
                ) VALUES (
                    'vod:movie:source:1', NULL, 'https://image.tmdb.org/p.jpg',
                    1, 'The Matrix', 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_FIFTEEN,
            16,
            true,
            StreamMateDatabase.MIGRATION_15_16,
        ).use { database ->
            database.query(
                "SELECT replacementTitle, genresVersion FROM catalogue_metadata_overrides",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("The Matrix", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            // The same for anything sitting in the cache: it was matched without
            // genres, so it must not be answered from.
            database.query("SELECT COUNT(*) FROM metadata_cache WHERE genresVersion = 0").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    /**
     * A library that has been matched keeps everything it knew, and learns
     * which record each title is as the metadata pass next reaches it.
     */
    @Test
    fun migrationToEighteenLetsATitleRememberWhichRecordItIs() {
        helper.createDatabase(TEST_DATABASE_FROM_SEVENTEEN, 17).apply {
            execSQL(
                """
                INSERT INTO catalogue_metadata_overrides(
                    contentKey, providerPosterUrl, replacementPosterUrl,
                    replaceProviderPoster, replacementTitle, genresVersion,
                    updatedAtEpochMillis
                ) VALUES (
                    'vod:movie:source:1', NULL, 'https://image.tmdb.org/p.jpg',
                    1, 'The Matrix', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_SEVENTEEN,
            18,
            true,
            StreamMateDatabase.MIGRATION_17_18,
        ).use { database ->
            database.query(
                "SELECT replacementTitle, genresVersion, externalId " +
                    "FROM catalogue_metadata_overrides",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("The Matrix", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(true, cursor.isNull(2))
            }
        }
    }

    /**
     * Everything already being watched keeps its place, and learns which film
     * it is in the next time it is written.
     */
    @Test
    fun migrationToNineteenLetsAPositionKnowWhichFilmItIsIn() {
        helper.createDatabase(TEST_DATABASE_FROM_EIGHTEEN, 18).apply {
            execSQL(
                """
                INSERT INTO playback_progress(
                    contentKey, sourceId, contentType, itemId, positionMillis,
                    durationMillis, completed, lastWatchedEpochMillis
                ) VALUES (
                    'vod:movie:source:42', 'source', 'movie', '42', 400000,
                    1000000, 0, 1700000000000
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_EIGHTEEN,
            19,
            true,
            StreamMateDatabase.MIGRATION_18_19,
        ).use { database ->
            database.query(
                "SELECT positionMillis, lastWatchedEpochMillis, workKey FROM playback_progress",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(400_000L, cursor.getLong(0))
                assertEquals(1_700_000_000_000L, cursor.getLong(1))
                assertEquals(true, cursor.isNull(2))
            }
        }
    }

    @Test
    fun migrationToTwentyAddsAnIndexedEmptyMetadataQueue() {
        helper.createDatabase(TEST_DATABASE_FROM_NINETEEN, 19).apply {
            execSQL(
                """
                INSERT INTO catalogue_metadata_overrides(
                    contentKey, providerPosterUrl, replacementPosterUrl,
                    replaceProviderPoster, replacementTitle, externalId,
                    genresVersion, updatedAtEpochMillis
                ) VALUES (
                    'vod:movie:source:1', NULL, NULL, 0, 'The Matrix',
                    '603', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_NINETEEN,
            20,
            true,
            StreamMateDatabase.MIGRATION_19_20,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM catalogue_metadata_work").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            val indices = buildList {
                database.query("PRAGMA index_list(`catalogue_metadata_work`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertEquals(
                true,
                "index_catalogue_metadata_work_state_nextAttemptAtEpochMillis_contentKey" in indices,
            )
            database.query(
                "SELECT replacementTitle, genresVersion FROM catalogue_metadata_overrides",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("The Matrix", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migrationToTwentyOneBackfillsIndexedCatalogueCategoryKeys() {
        helper.createDatabase(TEST_DATABASE_FROM_TWENTY, 20).apply {
            execSQL(
                """
                INSERT INTO vod_movies(
                    sourceId, snapshotId, movieId, name, normalizedName,
                    categoryName, posterUrl, encryptedStreamUrl, year, rating, plot
                ) VALUES (
                    'source', 'snapshot', '1', 'Arrival', 'arrival',
                    '  Sci-Fi  ', NULL, 'encrypted', 2016, '7.9', NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vod_series(
                    sourceId, snapshotId, seriesId, name, normalizedName,
                    categoryName, posterUrl, backdropUrl, year, rating, plot
                ) VALUES (
                    'source', 'snapshot', '2', 'Dark', 'dark',
                    '  Drama  ', NULL, NULL, 2017, '8.7', NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_TWENTY,
            21,
            true,
            StreamMateDatabase.MIGRATION_20_21,
        ).use { database ->
            database.query("SELECT categoryKey FROM vod_movies").use { cursor ->
                cursor.moveToFirst()
                assertEquals("sci-fi", cursor.getString(0))
            }
            database.query("SELECT categoryKey FROM vod_series").use { cursor ->
                cursor.moveToFirst()
                assertEquals("drama", cursor.getString(0))
            }
            val movieIndices = buildList {
                database.query("PRAGMA index_list(`vod_movies`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            val seriesIndices = buildList {
                database.query("PRAGMA index_list(`vod_series`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertEquals(true, "index_vod_movies_categoryKey" in movieIndices)
            assertEquals(true, "index_vod_series_categoryKey" in seriesIndices)
            val progressIndices = buildList {
                database.query("PRAGMA index_list(`playback_progress`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertEquals(true, "index_playback_progress_contentType" in progressIndices)
        }
    }

    @Test
    fun migrationToTwentyTwoKeepsOnePrimaryGenrePerTitle() {
        helper.createDatabase(TEST_DATABASE_FROM_TWENTY_ONE, 21).apply {
            execSQL(
                "INSERT INTO catalogue_genres(contentKey, genre) VALUES " +
                    "('vod:movie:source:1', 'thriller'), " +
                    "('vod:movie:source:1', 'action'), " +
                    "('vod:movie:source:1', 'drama'), " +
                    "('vod:movie:source:2', 'science_fiction')",
            )
            execSQL(
                "INSERT INTO catalogue_metadata_overrides(" +
                    "contentKey, providerPosterUrl, replacementPosterUrl, " +
                    "replaceProviderPoster, replacementTitle, externalId, " +
                    "genresVersion, updatedAtEpochMillis" +
                    ") VALUES " +
                    "('vod:movie:source:1', NULL, NULL, 0, 'Movie 1', '1', 1, 1), " +
                    "('vod:movie:source:2', NULL, NULL, 0, 'Movie 2', NULL, 0, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_FROM_TWENTY_ONE,
            22,
            true,
            StreamMateDatabase.MIGRATION_21_22,
        ).use { database ->
            database.query(
                "SELECT contentKey, genre FROM catalogue_genres ORDER BY contentKey",
            ).use { cursor ->
                assertEquals(true, cursor.moveToNext())
                assertEquals("vod:movie:source:1", cursor.getString(0))
                assertEquals("action", cursor.getString(1))
                assertEquals(true, cursor.moveToNext())
                assertEquals("vod:movie:source:2", cursor.getString(0))
                assertEquals("science_fiction", cursor.getString(1))
                assertEquals(false, cursor.moveToNext())
            }
            database.query(
                "SELECT contentKey, genresVersion FROM catalogue_metadata_overrides " +
                    "ORDER BY contentKey",
            ).use { cursor ->
                assertEquals(true, cursor.moveToNext())
                assertEquals(2, cursor.getInt(1))
                assertEquals(true, cursor.moveToNext())
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE_FROM_TWENTY_ONE = "migration-test-from-twenty-one"
        const val TEST_DATABASE_FROM_TWENTY = "migration-test-from-twenty"
        const val TEST_DATABASE_FROM_NINETEEN = "migration-test-from-nineteen"
        const val TEST_DATABASE_FROM_EIGHTEEN = "migration-test-from-eighteen"
        const val TEST_DATABASE_FROM_SEVENTEEN = "migration-test-from-seventeen"
        const val TEST_DATABASE_FROM_FIFTEEN = "migration-test-from-fifteen"
        const val TEST_DATABASE_FROM_FOURTEEN = "migration-test-from-fourteen"
        const val TEST_DATABASE_FROM_ONE = "migration-test-from-one"
        const val TEST_DATABASE_FROM_TWO = "migration-test-from-two"
        const val TEST_DATABASE_FROM_THREE = "migration-test-from-three"
        const val TEST_DATABASE_FROM_FOUR = "migration-test-from-four"
        const val TEST_DATABASE_FROM_FIVE = "migration-test-from-five"
        const val TEST_DATABASE_FROM_SIX = "migration-test-from-six"
        const val TEST_DATABASE_FROM_SEVEN = "migration-test-from-seven"
        const val TEST_DATABASE_FROM_EIGHT = "migration-test-from-eight"
        const val TEST_DATABASE_FROM_NINE = "migration-test-from-nine"
        const val TEST_DATABASE_FROM_TEN = "migration-test-from-ten"
        const val TEST_DATABASE_FROM_ELEVEN = "migration-test-from-eleven"
        const val TEST_DATABASE_FROM_TWELVE = "migration-test-from-twelve"
        const val TEST_DATABASE_FROM_THIRTEEN = "migration-test-from-thirteen"
    }
}
