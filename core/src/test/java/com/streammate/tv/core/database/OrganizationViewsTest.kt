package com.streammate.tv.core.database

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.random.Random

class OrganizationViewsTest {
    @Test
    fun `the inlined live predicate is the view's predicate with the timeline's aliases`() {
        val viewPredicate = ORGANIZATION_VISIBLE_LIVE_SQL.substringAfter("WHERE ").trim()
        val expected = viewPredicate
            .replace("m.", "c.")
            .replace("p.hidden", "preference.hidden")
            .replace("p.customOrganizationGroupKey", "preference.customOrganizationGroupKey")
        assertEquals(expected, ORGANIZATION_VISIBLE_LIVE_PREDICATE.trim())
    }

    /**
     * The shortened predicate decides what a whole source shows, hidden
     * channels and groups included, so it is held to the one it shortens by
     * SQLite itself: both run over the same channels under several hundred
     * rule sets made of every shape a rule can take - about a channel, a
     * group, or a channel within a group; for one source or all; by group key
     * or name key; shown, hidden, or with no opinion - and must agree on each.
     */
    @Test
    fun `the source predicate shows exactly what the live predicate shows`() {
        assertNotEquals(ORGANIZATION_VISIBLE_LIVE_PREDICATE.trim(), ORGANIZATION_VISIBLE_LIVE_SOURCE_PREDICATE.trim())
        var hiddenSomewhere = 0
        var shortCircuited = 0
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            repeat(ROUNDS) { round ->
                val random = Random(round)
                seed(connection, random, round)
                val canonical = visible(connection, ORGANIZATION_VISIBLE_LIVE_PREDICATE)
                val shortened = visible(connection, ORGANIZATION_VISIBLE_LIVE_SOURCE_PREDICATE)
                assertEquals("round $round: ${rules(connection)}", canonical, shortened)
                if (canonical.size < CHANNELS.size) hiddenSomewhere++
                if (!anyRuleNamesAChannel(connection)) shortCircuited++
            }
        }
        // Both halves of each CASE were exercised, and rules did hide things.
        assertTrue("rules hid something in only $hiddenSomewhere rounds", hiddenSomewhere > ROUNDS / 2)
        assertTrue("the lookups were skipped in only $shortCircuited rounds", shortCircuited > ROUNDS / 20)
        assertTrue("the lookups were made in only ${ROUNDS - shortCircuited} rounds", ROUNDS - shortCircuited > ROUNDS / 3)
    }

    private class Channel(val id: String, val source: String, val groupKey: String, val nameKey: String)

    private fun seed(connection: Connection, random: Random, round: Int) {
        connection.createStatement().use { statement ->
            statement.execute("DELETE FROM organization_rules")
            statement.execute("DELETE FROM channel_preferences")
            // What every migrated install carries, and a shortcut switched off:
            // group-shaped rows that name no real group.
            statement.execute("INSERT INTO organization_rules VALUES ('LIVE', '', '@legacy-v1', '', 1, NULL, NULL)")
            statement.execute("INSERT INTO organization_rules VALUES ('LIVE', '', '@favourites', '', 0, NULL, NULL)")
        }
        // A third of the rounds have no rule that names a channel, as most installs do not.
        val channelRules = round % 3 != 0
        val sources = listOf("a", "b", "")
        val groupKeys = CHANNELS.flatMap { listOf(it.groupKey, it.nameKey) }.distinct() + listOf("name:unused", "name:mine")
        val opinions = listOf("0", "1", "NULL")
        val seen = HashSet<String>()
        connection.prepareStatement("INSERT INTO organization_rules VALUES (?, ?, ?, ?, ?, NULL, ?)").use { insert ->
            repeat(random.nextInt(0, 14)) {
                val channel = CHANNELS.random(random)
                val (groupKey, itemKey) = when (if (channelRules) random.nextInt(3) else 1) {
                    0 -> "" to channel.id
                    1 -> groupKeys.random(random) to ""
                    else -> groupKeys.random(random) to channel.id
                }
                val room = if (random.nextInt(8) == 0) "MOVIES" else "LIVE"
                val source = sources.random(random)
                if (!seen.add("$room|$source|$groupKey|$itemKey")) return@repeat
                insert.setString(1, room)
                insert.setString(2, source)
                insert.setString(3, groupKey)
                insert.setString(4, itemKey)
                when (val enabled = if (channelRules) opinions.random(random) else opinions.take(2).random(random)) {
                    "NULL" -> insert.setNull(5, java.sql.Types.INTEGER)
                    else -> insert.setInt(5, enabled.toInt())
                }
                insert.setLong(6, random.nextLong(100))
                insert.executeUpdate()
            }
        }
        connection.prepareStatement(
            "INSERT INTO channel_preferences (channelId, sourceId, customName, customGroupTitle, hidden, sortOrder," +
                " manualXmltvChannelId, updatedAtEpochMillis, customOrganizationGroupKey, customLogoUrl, channelNumber)" +
                " VALUES (?, ?, NULL, ?, ?, NULL, NULL, 1, ?, NULL, NULL)",
        ).use { insert ->
            CHANNELS.filter { random.nextInt(3) == 0 }.forEach { channel ->
                val regrouped = random.nextInt(3) == 0
                insert.setString(1, channel.id)
                insert.setString(2, channel.source)
                insert.setString(3, if (regrouped) "Mine" else null)
                insert.setInt(4, random.nextInt(2))
                // A regrouped channel written before its key was stored has the title without the key.
                insert.setString(5, if (regrouped && random.nextBoolean()) "name:mine" else null)
                insert.executeUpdate()
            }
        }
    }

    private fun visible(connection: Connection, predicate: String): Set<String> = connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT c.sourceId || '/' || c.channelId FROM iptv_channels c" +
                " LEFT JOIN channel_preferences preference ON preference.channelId = c.channelId WHERE $predicate",
        ).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toSet() }
    }

    private fun anyRuleNamesAChannel(connection: Connection): Boolean = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT EXISTS (SELECT 1 FROM organization_rules WHERE room = 'LIVE' AND enabled IS NOT NULL AND itemKey <> '')")
            .use { rows -> rows.next() && rows.getInt(1) == 1 }
    }

    private fun rules(connection: Connection): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT room || ':' || sourceId || ':' || groupKey || ':' || itemKey || '=' || COALESCE(enabled, 'null') FROM organization_rules")
            .use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
    }

    private fun createSchema(connection: Connection) {
        val directory = File("schemas/com.streammate.tv.core.database.StreamMateDatabase")
        val latest = directory.listFiles { file -> file.extension == "json" }!!.maxByOrNull { it.nameWithoutExtension.toInt() }!!
        val schema = Json.parseToJsonElement(latest.readText()).jsonObject["database"]!!.jsonObject
        connection.createStatement().use { statement ->
            schema["entities"]!!.jsonArray
                .filter { it.jsonObject["tableName"]!!.jsonPrimitive.content in setOf("iptv_channels", "channel_preferences", "organization_rules") }
                .forEach { entity ->
                    val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                    statement.execute(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
        }
        connection.prepareStatement(
            "INSERT INTO iptv_channels (sourceId, snapshotId, channelId, tvgId, name, normalizedName, groupTitle, logoUrl," +
                " encryptedStreamUrl, userAgent, referrer, lastSeenEpochMillis, playlistOrder, catchupType, catchupSource," +
                " catchupDays, xtreamStreamId, catchupTimeZone, organizationGroupKey, organizationNameKey)" +
                " VALUES (?, 's', ?, NULL, 'n', 'n', 'g', NULL, 'enc', NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, ?, ?)",
        ).use { insert ->
            CHANNELS.forEach { channel ->
                insert.setString(1, channel.source)
                insert.setString(2, channel.id)
                insert.setString(3, channel.groupKey)
                insert.setString(4, channel.nameKey)
                insert.executeUpdate()
            }
        }
    }

    private companion object {
        const val ROUNDS = 400

        /**
         * Two sources; groups known by a provider id and by a name, by a name
         * alone, and by neither, as a channel imported before the keys were
         * stored is; and a channel with no id, which a rule naming no channel
         * would match.
         */
        val CHANNELS: List<Channel> = buildList {
            for (source in listOf("a", "b")) {
                for (index in 1..6) {
                    val (groupKey, nameKey) = when (index % 3) {
                        0 -> "id:${index % 2}" to "name:sport"
                        1 -> "name:news" to "name:news"
                        else -> "" to ""
                    }
                    add(Channel("$source:$index", source, groupKey, nameKey))
                }
            }
            add(Channel("", "a", "name:news", "name:news"))
        }
    }
}
