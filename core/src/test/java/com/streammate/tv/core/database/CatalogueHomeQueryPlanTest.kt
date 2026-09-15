package com.streammate.tv.core.database

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Puts the home-row SQL in front of SQLite's planner on the exported schema.
 *
 * The organisation views run a stack of rule sub-queries per row, so the
 * only acceptable way to reach `vod_movies` or `vod_series` through them is a
 * full primary-key lookup driven from `playback_progress`. A plan that walks
 * either table, or reaches `vod_series` on its `(sourceId, snapshotId)` prefix
 * alone, is the shape that took seconds per emission on the Shield.
 */
class CatalogueHomeQueryPlanTest {
    private lateinit var connection: Connection

    @Before
    fun createDatabase() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val schema = Json.parseToJsonElement(latestSchemaFile().readText()).jsonObject["database"]!!.jsonObject
        connection.createStatement().use { statement ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                statement.execute(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    statement.execute(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]!!.jsonArray.forEach { view ->
                val name = view.jsonObject["viewName"]!!.jsonPrimitive.content
                statement.execute(view.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", name))
            }
        }
        seedLibrary()
    }

    @After
    fun closeDatabase() = connection.close()

    @Test fun guideSearchKeepsContainsMatchingAndAllDatesWithoutBroadProgrammeJoins() {
        connection.createStatement().use { sql ->
            sql.execute("INSERT INTO import_state VALUES('$SOURCE','playlist','$SNAPSHOT',1,5000)")
            sql.execute("INSERT INTO import_state VALUES('$SOURCE','epg','$SNAPSHOT',1,300000)")
            sql.execute("WITH RECURSIVE n(x) AS (VALUES(0) UNION ALL SELECT x+1 FROM n WHERE x<4999) INSERT INTO iptv_channels(sourceId,snapshotId,channelId,name,normalizedName,lastSeenEpochMillis,tvgId,playlistOrder,encryptedStreamUrl,organizationGroupKey,organizationNameKey) SELECT '$SOURCE','$SNAPSHOT','c'||x,'Channel '||x,'channel '||x,1,'id'||x,x,'synthetic','g','g' FROM n")
            sql.execute("WITH RECURSIVE n(x) AS (VALUES(0) UNION ALL SELECT x+1 FROM n WHERE x<299999) INSERT INTO tv_programmes(sourceId,snapshotId,programmeId,xmltvChannelId,title,categories,startEpochMillis,stopEpochMillis) SELECT '$SOURCE','$SNAPSHOT','p'||x,'id'||(x%5000),CASE WHEN x%97=0 THEN 'Test Match' ELSE 'Programme '||x END,'',x*1000,x*1000+1000 FROM n")
            sql.execute("ANALYZE")
            val query = GUIDE_SEARCH_SQL.replace(":query", "'at'").replace(":limit", "80")
            val plan = sql.executeQuery("EXPLAIN QUERY PLAN $query").use { result -> buildList { while (result.next()) add(result.getString("detail")) } }
            assertTrue(plan.joinToString("\n"), plan.any { it.contains("index_tv_programmes_sourceId_xmltvChannelId_startEpochMillis_stopEpochMillis") && it.contains("xmltvChannelId=?") })
            val started = System.nanoTime()
            val hits = sql.executeQuery(query).use { result -> buildList { while (result.next()) add(result.getString("title") to result.getLong("startEpochMillis")) } }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            assertEquals(80, hits.size)
            assertTrue(hits.all { it.first == "Test Match" })
            assertTrue("Historical programmes are still searchable", hits.any { it.second == 0L })
            assertTrue("guide query took $elapsed ms", elapsed < 1000)
            println("Guide search: 5000 channels / 300000 programmes, substring 'at': $elapsed ms")
        }
    }

    @Test
    fun continueWatchingReachesTitlesByPrimaryKeyOnly() {
        assertKeyedLookups(CONTINUE_WATCHING_SQL)
        assertEquals(20, rowCount(CONTINUE_WATCHING_SQL))
    }

    @Test
    fun movieHistoryReachesTitlesByPrimaryKeyOnly() {
        assertKeyedLookups(MOVIE_HISTORY_CARDS_SQL)
        assertTrue(rowCount(MOVIE_HISTORY_CARDS_SQL) > 0)
    }

    @Test
    fun seriesHistoryReachesTitlesByPrimaryKeyOnly() {
        assertKeyedLookups(SERIES_HISTORY_CARDS_SQL)
        assertTrue(rowCount(SERIES_HISTORY_CARDS_SQL) > 0)
    }

    @Test
    fun homeRowsStayFastOnALargeLibrary() {
        // Generous: the fixed queries run in single-digit milliseconds here,
        // the regression took several seconds on the same data.
        listOf(CONTINUE_WATCHING_SQL, MOVIE_HISTORY_CARDS_SQL, SERIES_HISTORY_CARDS_SQL).forEach { sql ->
            val started = System.nanoTime()
            rowCount(sql)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue("query took $elapsedMillis ms:\n$sql", elapsedMillis < 1_000)
        }
    }

    @Test
    fun traktHomeStartsFromPausedItemsAndReachesOnlyExactLibraryKeys() {
        seedTrakt()
        listOf(false, true).forEach { analyzed ->
            if (analyzed) connection.createStatement().use { it.execute("ANALYZE") }
            assertKeyedLookups(TRAKT_CONTINUE_WATCHING_SQL)
            val started = System.nanoTime()
            assertEquals(20, rowCount(TRAKT_CONTINUE_WATCHING_SQL))
            assertTrue("Trakt Home exceeded 100 ms (analyzed=$analyzed)", (System.nanoTime() - started) / 1_000_000 < 100)
        }
        val actual = traktKeys()
        assertEquals((9 downTo 0).map { "vod:episode:$SOURCE:e${it * EPISODES_PER_SERIES}" } + (9 downTo 0).map { "vod:movie:$SOURCE:$it" }, actual)
    }

    @Test
    fun traktHomePreservesProfileVisibilitySnapshotAndOpaqueItemKeys() {
        seedTrakt()
        connection.createStatement().use { sql ->
            sql.execute("UPDATE vod_movies SET movieId = 'opaque:7' WHERE movieId = '7'")
            sql.execute("UPDATE catalogue_metadata_overrides SET contentKey = 'vod:movie:$SOURCE:opaque:7' WHERE contentKey = 'vod:movie:$SOURCE:7'")
            sql.execute("INSERT INTO organization_rules(room, sourceId, groupKey, itemKey, enabled, sortMode) VALUES ('MOVIES', '$SOURCE', '', 'vod:movie:$SOURCE:6', 0, '')")
        }
        assertTrue("Opaque IDs must keep their colon suffix", "vod:movie:$SOURCE:opaque:7" in traktKeys())
        assertTrue("Hidden movies must stay out of Trakt Home", "vod:movie:$SOURCE:6" !in traktKeys())
        assertEquals(0, rowCount(TRAKT_CONTINUE_WATCHING_SQL.replace(":profileId", "'other-viewer'")))
        connection.createStatement().use { it.execute("UPDATE import_state SET activeSnapshotId = 'other-snapshot'") }
        assertEquals(0, rowCount(TRAKT_CONTINUE_WATCHING_SQL))
        connection.createStatement().use { it.execute("UPDATE import_state SET activeSnapshotId = '$SNAPSHOT'"); it.execute("UPDATE iptv_source_state SET enabled = 0") }
        assertEquals(0, rowCount(TRAKT_CONTINUE_WATCHING_SQL))
    }

    @Test
    fun duplicateProviderCopiesAreCollapsedBeforeTheTraktRowLimit() {
        seedTrakt()
        connection.createStatement().use { sql ->
            repeat(2) { copy ->
                val provider = "new-provider-$copy"
                sql.execute("INSERT INTO iptv_source_state(sourceId,name,type,enabled,connectionLimit,priority,updatedAtEpochMillis,epgOffsetMinutes) VALUES('$provider','Synthetic','xtream',1,1,0,1,0)")
                sql.execute("INSERT INTO import_state VALUES('$provider','catalogue','$SNAPSHOT',1,10)")
                sql.execute("INSERT INTO vod_movies(sourceId,snapshotId,movieId,name,normalizedName,categoryName,categoryKey,organizationGroupKey,organizationNameKey,encryptedStreamUrl) SELECT '$provider',snapshotId,movieId,name,normalizedName,categoryName,categoryKey,organizationGroupKey,organizationNameKey,'synthetic' FROM vod_movies WHERE sourceId='$SOURCE' AND CAST(movieId AS INTEGER)<10")
                sql.execute("INSERT INTO catalogue_metadata_overrides(contentKey,replaceProviderPoster,replacementTitle,externalId,updatedAtEpochMillis) SELECT 'vod:movie:$provider:'||movieId,0,name,100000+movieId,1 FROM vod_movies WHERE sourceId='$provider'")
            }
            sql.execute("UPDATE trakt_state SET imdb='tt1234567' WHERE tmdb=100001 AND kind='movie'")
        }
        assertKeyedLookups(TRAKT_CONTINUE_WATCHING_SQL)
        assertEquals(20, rowCount(TRAKT_CONTINUE_WATCHING_SQL))
        connection.createStatement().use { sql ->
            sql.executeQuery(bound(TRAKT_CONTINUE_WATCHING_SQL)).use { rows ->
                val movies = mutableListOf<Long>()
                while (rows.next()) if (rows.getString("contentType") == "movie") {
                    movies += rows.getLong("tmdbId")
                    if (rows.getLong("tmdbId") == 100001L) assertEquals("tt1234567", rows.getString("imdbId"))
                }
                assertEquals((100009L downTo 100000L).toList(), movies)
            }
        }
    }

    @Test
    fun localResumeExposesConfirmedMovieIdentityUsingOnlyItsBoundedRows() {
        seedTrakt()
        connection.createStatement().use { sql ->
            val movie = sql.executeQuery("SELECT itemId FROM playback_progress WHERE contentType='movie' LIMIT 1").use { it.next(); it.getString(1) }
            sql.execute("UPDATE playback_progress SET lastWatchedEpochMillis=3000000 WHERE contentType='movie' AND itemId='$movie'")
            sql.execute("INSERT INTO metadata_cache(lookupKey,provider,status,externalId,mediaType,attributionName,attributionUrl,confidence,cachedAtEpochMillis,expiresAtEpochMillis) VALUES('movie-$movie','tmdb','positive','${100000 + movie.toInt()}','movie','Synthetic','https://example.invalid',1,1,9999999999999)")
            sql.executeQuery(bound(CONTINUE_WATCHING_SQL)).use { rows ->
                assertTrue(rows.next())
                assertEquals("vod:movie:$SOURCE:$movie", rows.getString("contentKey"))
                assertEquals(100000L + movie.toLong(), rows.getLong("tmdbId"))
            }
        }
        assertKeyedLookups(CONTINUE_WATCHING_SQL)
    }

    @Test
    fun overlaysAndScopedDetailsUseExactKeysOnALargeLibrary() {
        seedTrakt()
        val queries = listOf(TRAKT_MOVIE_OVERLAY_SQL, TRAKT_EPISODE_OVERLAY_SQL,
            TRAKT_SELECTED_MOVIES_SQL.replace(":contentKeys", "'vod:movie:$SOURCE:1'"),
            TRAKT_SELECTED_SERIES_SQL.replace(":sourceId", "'$SOURCE'").replace(":seriesId", "'1'"))
        queries.forEach { sql ->
            assertKeyedLookups(sql)
            val started = System.nanoTime()
            rowCount(sql)
            assertTrue("overlay exceeded 500 ms", (System.nanoTime() - started) / 1_000_000 < 500)
        }
        assertEquals(1000, rowCount(queries[0]))
        assertEquals(100, rowCount(queries[1]))
        assertEquals(1, rowCount(queries[2]))
        assertEquals(1, rowCount(queries[3]))
    }

    private fun traktKeys(): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(bound(TRAKT_CONTINUE_WATCHING_SQL)).use { rows ->
            generateSequence { if (rows.next()) rows.getString("contentKey") else null }.toList()
        }
    }

    private fun seedTrakt() {
        connection.createStatement().use { sql ->
            sql.execute("DELETE FROM organization_rules")
            sql.execute("INSERT INTO catalogue_metadata_overrides(contentKey, replaceProviderPoster, replacementTitle, externalId, updatedAtEpochMillis) SELECT 'vod:movie:' || sourceId || ':' || movieId, 0, name, CAST(movieId + 100000 AS TEXT), 1 FROM vod_movies")
            sql.execute("INSERT INTO catalogue_metadata_overrides(contentKey, replaceProviderPoster, replacementTitle, externalId, updatedAtEpochMillis) SELECT 'series:' || sourceId || ':' || seriesId, 0, name, CAST(seriesId + 200000 AS TEXT), 1 FROM vod_series")
            sql.execute("INSERT INTO metadata_cache(lookupKey, provider, status, externalId, mediaType, attributionName, attributionUrl, confidence, cachedAtEpochMillis, expiresAtEpochMillis) SELECT 'series-' || seriesId, 'tmdb', 'positive', CAST(seriesId + 200000 AS TEXT), 'series', 'Synthetic', 'https://example.invalid', 1, 1, 9999999999999 FROM vod_series")
            repeat(1000) { i -> sql.execute("INSERT INTO trakt_state VALUES('default', 'movie:$i', 'movie', ${100000 + i}, NULL, NULL, NULL, ${if (i < 10) 35 else 0}, 1, 1, ${10000 + i})") }
            repeat(100) { i -> sql.execute("INSERT INTO trakt_state VALUES('default', 'episode:$i', 'episode', ${200000 + i}, NULL, 1, 1, ${if (i < 10) 45 else 0}, 1, 1, ${20000 + i})") }
        }
    }

    /** The one bound parameter: the planner is asked about the first viewer. */
    private fun bound(sql: String): String = sql.replace(":profileId", "'default'")

    private fun assertKeyedLookups(sql: String) {
        val plan = connection.createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN ${bound(sql)}").use { rows ->
                generateSequence { if (rows.next()) rows.getString("detail") else null }.toList()
            }
        }
        val titleTables = listOf("vod_movies", "vod_series", "vod_episodes")
        plan.forEach { line ->
            val touchesTitles = titleTables.any { it in line } ||
                Regex("""\b(SCAN|SEARCH) (movie|item|episode|m)\b""").containsMatchIn(line)
            if (!touchesTitles) return@forEach
            assertTrue("title table walked:\n${plan.joinToString("\n")}", !line.startsWith("SCAN"))
            // A lookup keyed on the source alone, or on the source and snapshot,
            // walks every title in the provider: the shape that took seconds.
            assertTrue(
                "title table reached on a source-wide prefix:\n${plan.joinToString("\n")}",
                "index_vod_series_sourceId_snapshotId" !in line &&
                    "index_vod_movies_sourceId_snapshotId" !in line &&
                    "(sourceId=?)" !in line &&
                    "(sourceId=? AND snapshotId=?)" !in line,
            )
        }
    }

    private fun rowCount(sql: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery(bound(sql)).use { rows -> generateSequence { rows.next().takeIf { it } }.count() }
    }

    private fun seedLibrary() {
        connection.autoCommit = false
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO iptv_source_state (sourceId, name, type, enabled, connectionLimit, priority, updatedAtEpochMillis, epgOffsetMinutes)" +
                    " VALUES ('$SOURCE', 'Provider', 'xtream', 1, 1, 0, 1, 0)",
            )
            statement.execute(
                "INSERT INTO import_state (sourceId, kind, activeSnapshotId, updatedAtEpochMillis, itemCount)" +
                    " VALUES ('$SOURCE', 'catalogue', '$SNAPSHOT', 1, $MOVIES)",
            )
        }
        connection.prepareStatement(
            "INSERT INTO vod_movies (sourceId, snapshotId, movieId, name, normalizedName, categoryName, categoryKey," +
                " organizationGroupKey, organizationNameKey, posterUrl, encryptedStreamUrl, year, rating)" +
                " VALUES ('$SOURCE', '$SNAPSHOT', ?, ?, ?, ?, ?, ?, ?, ?, 'e', 2000, '7')",
        ).use { insert ->
            repeat(MOVIES) { index ->
                insert.setString(1, index.toString())
                insert.setString(2, "Movie $index")
                insert.setString(3, "movie $index")
                insert.setString(4, "Cat ${index % 200}")
                insert.setString(5, "cat${index % 200}")
                insert.setString(6, "cat${index % 200}")
                insert.setString(7, "cat ${index % 200}")
                insert.setString(8, "https://example.invalid/$index.jpg")
                insert.addBatch()
            }
            insert.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO vod_series (sourceId, snapshotId, seriesId, name, normalizedName, categoryName, categoryKey," +
                " organizationGroupKey, organizationNameKey, posterUrl, year, rating)" +
                " VALUES ('$SOURCE', '$SNAPSHOT', ?, ?, ?, ?, ?, ?, ?, ?, 2000, '7')",
        ).use { insert ->
            repeat(SERIES) { index ->
                insert.setString(1, index.toString())
                insert.setString(2, "Series $index")
                insert.setString(3, "series $index")
                insert.setString(4, "SCat ${index % 100}")
                insert.setString(5, "scat${index % 100}")
                insert.setString(6, "scat${index % 100}")
                insert.setString(7, "scat ${index % 100}")
                insert.setString(8, "https://example.invalid/s$index.jpg")
                insert.addBatch()
            }
            insert.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO vod_episodes (sourceId, seriesId, episodeId, name, seasonNumber, episodeNumber, encryptedStreamUrl)" +
                " VALUES ('$SOURCE', ?, ?, ?, ?, ?, 'e')",
        ).use { insert ->
            repeat(SERIES * EPISODES_PER_SERIES) { index ->
                insert.setString(1, (index / EPISODES_PER_SERIES).toString())
                insert.setString(2, "e$index")
                insert.setString(3, "Episode $index")
                insert.setInt(4, 1 + (index % EPISODES_PER_SERIES) / 10)
                insert.setInt(5, 1 + index % 10)
                insert.addBatch()
            }
            insert.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO playback_progress (contentKey, contentType, sourceId, itemId, positionMillis, durationMillis," +
                " completed, lastWatchedEpochMillis, workKey) VALUES (?, ?, '$SOURCE', ?, ?, 5000, 0, ?, ?)",
        ).use { insert ->
            val random = java.util.Random(1)
            repeat(WATCHED_MOVIES) { index ->
                val movieId = random.nextInt(MOVIES).toString()
                insert.setString(1, "vod:movie:$SOURCE:$movieId")
                insert.setString(2, "movie")
                insert.setString(3, movieId)
                insert.setLong(4, 1000L + index)
                insert.setLong(5, 1_000_000L + index)
                insert.setString(6, "work$index")
                insert.addBatch()
            }
            repeat(WATCHED_EPISODES) { index ->
                val episodeId = "e${random.nextInt(SERIES * EPISODES_PER_SERIES)}"
                insert.setString(1, "vod:episode:$SOURCE:$episodeId")
                insert.setString(2, "episode")
                insert.setString(3, episodeId)
                insert.setLong(4, 1000L + index)
                insert.setLong(5, 2_000_000L + index)
                insert.setNull(6, java.sql.Types.VARCHAR)
                insert.addBatch()
            }
            insert.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO organization_rules (room, sourceId, groupKey, itemKey, enabled, sortMode) VALUES (?, ?, ?, '', ?, '')",
        ).use { insert ->
            repeat(200) { index ->
                insert.setString(1, if (index % 2 == 0) "MOVIES" else "SERIES")
                insert.setString(2, if (index % 3 == 0) "" else SOURCE)
                insert.setString(3, if (index % 2 == 0) "cat$index" else "scat$index")
                insert.setInt(4, if (index % 4 == 0) 0 else 1)
                insert.addBatch()
            }
            insert.executeBatch()
        }
        connection.commit()
        connection.autoCommit = true
    }

    private fun latestSchemaFile(): File {
        val directory = File("schemas/com.streammate.tv.core.database.StreamMateDatabase")
        return directory.listFiles { file -> file.extension == "json" }!!
            .maxByOrNull { file -> file.nameWithoutExtension.toInt() }!!
    }

    private companion object {
        const val SOURCE = "source"
        const val SNAPSHOT = "snapshot"
        const val MOVIES = 40_000
        const val SERIES = 4_000
        const val EPISODES_PER_SERIES = 20
        const val WATCHED_MOVIES = 150
        const val WATCHED_EPISODES = 150
    }
}
