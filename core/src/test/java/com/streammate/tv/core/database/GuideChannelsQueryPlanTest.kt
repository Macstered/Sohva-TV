package com.streammate.tv.core.database

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The rows-only read the guide uses for a very large selection, in front of
 * SQLite's planner on the exported schema with fifty thousand channels.
 * It must never touch the programme table, and it must return every channel
 * of the source in well under a second without statistics.
 */
class GuideChannelsQueryPlanTest {
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
        seedChannels()
    }

    @After
    fun closeDatabase() = connection.close()

    @Test
    fun theRowsReadNeverJoinsTheProgrammes() {
        val plan = connection.createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN ${wholeSourceSql()}").use { rows ->
                generateSequence { if (rows.next()) rows.getString("detail") else null }.toList()
            }
        }
        assertTrue(plan.toString(), plan.isNotEmpty())
        plan.forEach { line -> assertFalse("plan touches programmes:\n$line", "tv_programmes" in line) }
    }

    @Test
    fun everyChannelOfALargeSourceComesBackQuickly() {
        val started = System.nanoTime()
        val count = rowCount(wholeSourceSql())
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(CHANNELS, count)
        assertTrue("rows read took $elapsedMillis ms", elapsedMillis < 2_000)
    }

    @Test
    fun aGroupComesBackAlone() {
        assertEquals(CHANNELS / GROUPS, rowCount(groupSql("Group 07")))
    }

    private fun wholeSourceSql(): String = GUIDE_CHANNELS_FOR_SOURCE_SQL
        .replace(":sourceId", "'$SOURCE'")
        .replace(":groupTitle", "NULL")

    private fun groupSql(group: String): String = GUIDE_CHANNELS_FOR_SOURCE_SQL
        .replace(":sourceId", "'$SOURCE'")
        .replace(":groupTitle", "'$group'")

    private fun rowCount(sql: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> generateSequence { rows.next().takeIf { it } }.count() }
    }

    private fun seedChannels() {
        connection.autoCommit = false
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO iptv_source_state (sourceId, name, type, enabled, connectionLimit, priority, updatedAtEpochMillis, epgOffsetMinutes)" +
                    " VALUES ('$SOURCE', 'Provider', 'xtream', 1, 1, 0, 1, 0)",
            )
            statement.execute(
                "INSERT INTO import_state (sourceId, kind, activeSnapshotId, updatedAtEpochMillis, itemCount)" +
                    " VALUES ('$SOURCE', 'playlist', '$SNAPSHOT', 1, $CHANNELS)",
            )
        }
        connection.prepareStatement(
            "INSERT INTO iptv_channels (sourceId, snapshotId, channelId, tvgId, name, normalizedName, groupTitle, logoUrl," +
                " encryptedStreamUrl, userAgent, referrer, lastSeenEpochMillis, playlistOrder, catchupType, catchupSource," +
                " catchupDays, xtreamStreamId, catchupTimeZone, organizationGroupKey, organizationNameKey)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'enc', NULL, NULL, 1, ?, NULL, NULL, NULL, NULL, NULL, ?, ?)",
        ).use { insert ->
            for (index in 1..CHANNELS) {
                val group = "Group " + (index % GROUPS).toString().padStart(2, '0')
                insert.setString(1, SOURCE)
                insert.setString(2, SNAPSHOT)
                insert.setString(3, "$SOURCE:$index")
                insert.setString(4, "ch$index.example")
                insert.setString(5, "Channel $index")
                insert.setString(6, "channel $index")
                insert.setString(7, group)
                insert.setInt(8, index)
                insert.setString(9, "name:" + group.lowercase())
                insert.setString(10, "name:" + group.lowercase())
                insert.addBatch()
                if (index % 5_000 == 0) insert.executeBatch()
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
        const val CHANNELS = 50_000
        const val GROUPS = 100
    }
}
