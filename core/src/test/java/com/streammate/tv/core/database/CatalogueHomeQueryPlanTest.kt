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

    private fun assertKeyedLookups(sql: String) {
        val plan = connection.createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN $sql").use { rows ->
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
        statement.executeQuery(sql).use { rows -> generateSequence { rows.next().takeIf { it } }.count() }
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
