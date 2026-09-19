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
 * The rows-only read the guide uses for every selection, in front of SQLite's
 * planner on the exported schema with fifty thousand channels. It must never
 * touch the programme table; a page must walk the primary key without a
 * sorter, since Android re-runs a statement for every cursor window it fills;
 * and the pages put back in display order must be the rows, in the order, the
 * one ordered statement they replaced gave.
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
        val plan = plan(pageSql())
        assertTrue(plan.toString(), plan.isNotEmpty())
        plan.forEach { line -> assertFalse("plan touches programmes:\n$line", "tv_programmes" in line) }
    }

    @Test
    fun sportsChannelPagesSeekTheRowIdWithoutSortingTheWholeLibrary() {
        val sql = SPORTS_CHANNEL_CANDIDATES_PAGE_SQL.replace(":afterRowId", "40000").replace(":limit", "256")
        val queryPlan = plan(sql)
        assertTrue(queryPlan.toString(), queryPlan.any { "SEARCH c USING INTEGER PRIMARY KEY (rowid>?)" in it })
        assertFalse(queryPlan.toString(), queryPlan.any { "TEMP B-TREE" in it })
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                var count = 0
                while (rows.next()) {
                    assertTrue(rows.getLong("channelRowId") > 40000)
                    count++
                }
                assertEquals(256, count)
            }
        }
    }

    @Test
    fun sportsProgrammePagesSeekOnlySelectedChannelsAndTheirIndexedTimeRange() {
        val sql = SPORTS_PROGRAMME_CANDIDATES_PAGE_SQL
            .replace(":channelRowIds", "40001,40002")
            .replace(":fromEpochMillis", "1000000").replace(":toEpochMillis", "2000000")
            .replace(":afterProgrammeRowId", "-1").replace(":afterChannelRowId", "-1")
            .replace(":limit", "64")
        val queryPlan = plan(sql)
        assertTrue(queryPlan.toString(), queryPlan.any { "SEARCH c USING INTEGER PRIMARY KEY (rowid=?)" in it })
        assertTrue(queryPlan.toString(), queryPlan.any {
            "SEARCH p USING INDEX index_tv_programmes_sourceId_xmltvChannelId_startEpochMillis_stopEpochMillis" in it &&
                "sourceId=? AND xmltvChannelId=? AND startEpochMillis>? AND startEpochMillis<?" in it
        })
        assertFalse(queryPlan.toString(), queryPlan.any { it.startsWith("SCAN p") })
    }

    @Test
    fun aPageWalksThePrimaryKeyFromWhereTheLastEndedWithoutASorter() {
        listOf(pageSql(), pageSql(group = "Group 07"), pageSql(after = "$SOURCE:30000")).forEach { sql ->
            val plan = plan(sql)
            // A sorter would make every page, and every cursor window a page
            // overflowed into, pay for every channel of the source again.
            plan.forEach { line -> assertFalse("plan sorts:\n$plan", "TEMP B-TREE" in line) }
            val channels = plan.filter { "sqlite_autoindex_iptv_channels_1" in it }
            assertEquals("channels are not read along their primary key:\n$plan", 1, channels.size)
            assertTrue("a page does not start where the last ended:\n$plan", "channelId>?" in channels.single())
        }
    }

    @Test
    fun everyChannelOfALargeSourceComesBackQuickly() {
        val started = System.nanoTime()
        val rows = allPages()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(CHANNELS, rows.size)
        assertEquals(CHANNELS, rows.map { it.channelId }.toSet().size)
        assertTrue("rows read took $elapsedMillis ms", elapsedMillis < 2_000)
        println("$CHANNELS channels in pages of $PAGE: $elapsedMillis ms")
    }

    @Test
    fun aGroupComesBackAlone() {
        val rows = allPages(group = "Group 07")
        assertEquals(CHANNELS / GROUPS, rows.size)
        assertTrue(rows.all { it.groupTitle == "Group 07" })
    }

    @Test
    fun aPageCostsItsOwnRowsWhereverInTheSourceItStarts() {
        // The statement this replaced took as long for its last rows as for
        // all of them; a page deep in the source must not read its way there.
        fun millis(after: String): Long {
            val started = System.nanoTime()
            assertEquals(PAGE, page(after = after).size)
            return (System.nanoTime() - started) / 1_000_000
        }
        millis("") // Warm the page cache before comparing.
        val first = millis("")
        // Ids sort as text, so the last page's worth begins a little before "source:9".
        val deep = millis("$SOURCE:8")
        assertTrue("first page $first ms, a page near the end $deep ms", deep <= first * 3 + 50)
        println("a page of $PAGE: $first ms at the start, $deep ms near the end")
    }

    @Test
    fun pagesPutBackInDisplayOrderAreTheOrderedStatementTheyReplaced() {
        // Names that decide the last sort key: one high in the BMP and one
        // past it, which UTF-16 and SQLite's UTF-8 order the opposite way.
        val fullwidthTilde = String(Character.toChars(0xFF5E))
        val television = String(Character.toChars(0x1F4FA))
        connection.createStatement().use { statement ->
            // Ties on every key but the name or the id, the viewer's own
            // positions, a custom group, and rows the rules remove or restore.
            statement.execute("UPDATE iptv_channels SET playlistOrder = 7 WHERE channelId IN ('$SOURCE:100', '$SOURCE:200', '$SOURCE:300', '$SOURCE:400')")
            statement.execute("UPDATE iptv_channels SET name = 'Tie' WHERE channelId IN ('$SOURCE:300', '$SOURCE:400')")
            statement.execute(
                "INSERT INTO channel_preferences (channelId, sourceId, customName, customGroupTitle, hidden, sortOrder," +
                    " manualXmltvChannelId, updatedAtEpochMillis, customOrganizationGroupKey, customLogoUrl, channelNumber) VALUES" +
                    " ('$SOURCE:49000', '$SOURCE', NULL, NULL, 0, 2, NULL, 1, NULL, NULL, NULL)," +
                    " ('$SOURCE:12', '$SOURCE', NULL, NULL, 0, 1, NULL, 1, NULL, NULL, NULL)," +
                    " ('$SOURCE:100', '$SOURCE', '${fullwidthTilde}high', NULL, 0, NULL, NULL, 1, NULL, NULL, 501)," +
                    " ('$SOURCE:200', '$SOURCE', '${television}beyond', NULL, 0, NULL, NULL, 1, NULL, NULL, NULL)," +
                    " ('$SOURCE:500', '$SOURCE', '', 'Mine', 0, NULL, NULL, 1, 'name:mine', 'logo', NULL)," +
                    " ('$SOURCE:600', '$SOURCE', NULL, NULL, 1, NULL, NULL, 1, NULL, NULL, NULL)," +
                    " ('$SOURCE:601', '$SOURCE', NULL, NULL, 1, NULL, NULL, 1, NULL, NULL, NULL)",
            )
            statement.execute(
                "INSERT INTO organization_rules (room, sourceId, groupKey, itemKey, enabled, sortMode, position) VALUES" +
                    " ('LIVE', '$SOURCE', 'name:group 03', '', 0, NULL, NULL)," +
                    " ('LIVE', '', '', '$SOURCE:700', 0, NULL, NULL)," +
                    " ('LIVE', '$SOURCE', '', '$SOURCE:600', 1, NULL, NULL)," +
                    " ('LIVE', '$SOURCE', 'name:group 08', '$SOURCE:808', 0, NULL, NULL)," +
                    " ('MOVIES', '', '', '$SOURCE:1', 0, NULL, NULL)",
            )
        }
        val expected = rosterRows(orderedStatementSql())
        val paged = allPages().sortedWith(GuideRosterRow.DISPLAY_ORDER)
        assertEquals(expected.map { it.channelId }, paged.map { it.channelId })
        assertEquals(expected, paged)

        // And the seeding did what it was meant to, so the comparison above
        // is not of two lists that are alike because nothing happened.
        assertEquals(listOf("$SOURCE:12", "$SOURCE:49000"), paged.take(2).map { it.channelId })
        val tied = paged.filter { it.playlistOrder == 7 && it.legacyPosition == null }.map { it.channelId }
        assertEquals(listOf("$SOURCE:7", "$SOURCE:300", "$SOURCE:400", "$SOURCE:100", "$SOURCE:200"), tied)
        val hidden = setOf("$SOURCE:601", "$SOURCE:700", "$SOURCE:808")
        assertTrue(paged.none { it.channelId in hidden || it.groupTitle == "Group 03" })
        assertTrue("a rule that enables a channel outranks its legacy hidden flag", paged.any { it.channelId == "$SOURCE:600" })
        assertEquals(CHANNELS - CHANNELS / GROUPS - hidden.size, paged.size)
        assertEquals("Mine" to "name:mine", paged.single { it.channelId == "$SOURCE:500" }.let { it.groupTitle to it.organizationGroupKey })
        assertEquals(501, paged.single { it.channelId == "$SOURCE:100" }.channelNumber)
    }

    @Test
    fun sharingKeepsARowEqualAndItsRepeatedValuesOnce() {
        val pool = HashMap<String, String>()
        val rows = page().map { it.sharing(pool) }
        assertEquals(page(), rows)
        assertTrue(rows.all { it.sourceId === rows.first().sourceId && it.snapshotId === rows.first().snapshotId })
        assertTrue("pooled ${pool.size} values for ${rows.size} rows", pool.size < 2 * GROUPS + 10)
    }

    @Test
    fun namedChannelsAlsoLoadWithoutReadingProgrammes() {
        val sql = GUIDE_CHANNELS_FOR_IDS_SQL.replace(":channelIds", "'$SOURCE:1', '$SOURCE:40001'")
        assertEquals(2, rowCount(sql))
        plan(sql).forEach { line -> assertFalse(line, "tv_programmes" in line) }
    }

    @Test
    fun programmeSearchUsesTheTimeIndexAndReturnsOnlyMatchingChannels() {
        connection.createStatement().use { statement ->
            statement.execute("UPDATE iptv_source_state SET epgOffsetMinutes = 60")
            statement.execute("INSERT INTO import_state VALUES ('$SOURCE', 'epg', 'epg', 1, 3)")
            statement.execute("""
                INSERT INTO tv_programmes VALUES
                ('$SOURCE', 'epg', 'current', 'ch1.example', 1000, 2000, 'News', NULL, NULL, ''),
                ('$SOURCE', 'epg', 'future', 'ch2.example', 3000, 4000, 'News', NULL, NULL, ''),
                ('$SOURCE', 'old', 'stale', 'ch3.example', 1000, 2000, 'News', NULL, NULL, '')
            """.trimIndent())
        }
        val sql = GUIDE_PROGRAMME_MATCHES_SQL
            .replace(":sourceId", "'$SOURCE'").replace(":groupTitle", "NULL")
            .replace(":pattern", "'%News%'")
            .replace(":fromEpochMillis", "3601500").replace(":toEpochMillis", "3602500")
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                assertTrue(rows.next())
                assertEquals("$SOURCE:1", rows.getString(1))
                assertFalse(rows.next())
            }
        }
        val plan = plan(sql)
        assertTrue(plan.toString(), plan.any { "startEpochMillis<?" in it })
    }

    private fun pageSql(group: String? = null, after: String = "", limit: Int = PAGE): String = GUIDE_CHANNEL_PAGE_SQL
        .replace(":sourceId", "'$SOURCE'")
        .replace(":groupTitle", group?.let { "'$it'" } ?: "NULL")
        .replace(":afterChannelId", "'$after'")
        .replace(":limit", limit.toString())

    /** What the guide used to run: the same rows from one statement, in the order it shows them. */
    private fun orderedStatementSql(): String {
        val ordered = pageSql(limit = -1).replace(
            "ORDER BY c.channelId",
            "ORDER BY source_state.priority DESC, source_state.name, COALESCE(preference.sortOrder, 2147483647)," +
                " c.playlistOrder, COALESCE(NULLIF(preference.customName, ''), c.name), c.channelId",
        )
        check("c.playlistOrder," in ordered) { "the page statement no longer ends in the order this test replaces" }
        return ordered
    }

    private fun page(group: String? = null, after: String = ""): List<GuideRosterRow> = rosterRows(pageSql(group, after))

    /** The repository's loop: a page from where the last ended, until one comes back short. */
    private fun allPages(group: String? = null): List<GuideRosterRow> = buildList {
        var after = ""
        while (true) {
            val page = page(group, after)
            addAll(page)
            if (page.size < PAGE) break
            after = page.last().channelId
        }
    }

    private fun rosterRows(sql: String): List<GuideRosterRow> = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            generateSequence {
                if (!rows.next()) null else GuideRosterRow(
                    sourceId = rows.getString("sourceId"),
                    snapshotId = rows.getString("snapshotId"),
                    sourceName = rows.getString("sourceName"),
                    sourcePriority = rows.getInt("sourcePriority"),
                    channelId = rows.getString("channelId"),
                    channelName = rows.getString("channelName"),
                    groupTitle = rows.getString("groupTitle"),
                    logoUrl = rows.getString("logoUrl"),
                    channelNumber = rows.getInt("channelNumber").takeUnless { rows.wasNull() },
                    playlistOrder = rows.getInt("playlistOrder"),
                    legacyPosition = rows.getLong("legacyPosition").takeUnless { rows.wasNull() },
                    organizationGroupKey = rows.getString("organizationGroupKey"),
                    catchupType = rows.getString("catchupType"),
                    catchupSource = rows.getString("catchupSource"),
                    catchupDays = rows.getInt("catchupDays").takeUnless { rows.wasNull() },
                )
            }.toList()
        }
    }

    private fun plan(sql: String): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery("EXPLAIN QUERY PLAN $sql").use { rows ->
            generateSequence { if (rows.next()) rows.getString("detail") else null }.toList()
        }
    }

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
        const val PAGE = 2_000
    }
}
