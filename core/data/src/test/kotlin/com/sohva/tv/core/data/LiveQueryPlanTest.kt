package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.LiveSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guide's and player's hot reads (spec 20 §11 "Query plans", plan/04 §15.11): each reaches its
 * rows through an index, pages walk the key without a sorter, and no row read joins programmes.
 */
class LiveQueryPlanTest {
    private val statements = mapOf(
        "rail" to LiveSql.RAIL,
        "group keys" to LiveSql.GROUP_KEYS,
        "source keys" to LiveSql.SOURCE_KEYS,
        "ungrouped keys" to LiveSql.UNGROUPED_KEYS,
        "group rows" to LiveSql.GROUP_ROWS,
        "source rows" to LiveSql.SOURCE_ROWS,
        "ungrouped rows" to LiveSql.UNGROUPED_ROWS,
        "rows by id" to LiveSql.ROWS_BY_ID,
        "keys by channel key" to LiveSql.KEYS_BY_CHANNEL_KEY,
        "by key" to LiveSql.BY_KEY,
        "group by number" to LiveSql.GROUP_BY_NUMBER,
        "source by number" to LiveSql.SOURCE_BY_NUMBER,
        "ids by number" to LiveSql.IDS_BY_NUMBER,
        "playable" to LiveSql.PLAYABLE,
        "epg state" to LiveSql.EPG_STATE,
        "window" to LiveSql.WINDOW,
        "description" to LiveSql.DESCRIPTION,
        "group matches" to LiveSql.GROUP_MATCHES,
        "source matches" to LiveSql.SOURCE_MATCHES,
    )

    /** Small tables whose full read is the point (a source list of at most a hundred rows). */
    private val smallScans = setOf("source")

    @Test
    fun everyLiveStatementUsesAnIndexWithoutASorter() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in statements) {
                    val plan = db.plan(sql).joinToString(" | ")
                    assertTrue("$name ($analyzed): $plan", plan.contains("SEARCH"))
                    val scans = Regex("""\bSCAN (?:TABLE )?(\w+)""").findAll(plan).map { it.groupValues[1] }.toList()
                    assertTrue("$name ($analyzed): $plan", scans.all { it in smallScans })
                    // The rail is a handful of groups per source; everything else must not sort.
                    if (name != "rail") assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE FOR ORDER BY"))
                }
            }
        }
    }

    @Test
    fun rowReadsNeverJoinProgrammes() {
        for (sql in listOf(LiveSql.GROUP_ROWS, LiveSql.SOURCE_ROWS, LiveSql.ROWS_BY_ID, LiveSql.GROUP_KEYS, LiveSql.SOURCE_KEYS)) {
            assertFalse(sql, sql.contains("programme"))
        }
    }

    @Test
    fun sourcesScanOnlyTheSmallSourceTable() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            val plan = db.plan(LiveSql.SOURCES).joinToString(" | ")
            assertFalse(plan, Regex("""\bSCAN (?:TABLE )?channel\b""").containsMatchIn(plan))
        }
    }
}
