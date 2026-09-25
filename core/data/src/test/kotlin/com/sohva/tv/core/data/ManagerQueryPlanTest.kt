package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.EditSql
import com.sohva.tv.core.data.database.ManagerSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Channel management's and the edit pipeline's hot reads (spec 21 CHAN-NFR-01, AGENTS §4.3): each
 * reaches its rows through an index and pages walk their key without a sorter.
 */
class ManagerQueryPlanTest {
    private val paged = mapOf(
        "playlist page" to ManagerSql.PLAYLIST_PAGE,
        "playlist before" to ManagerSql.PLAYLIST_BEFORE,
        "name page" to ManagerSql.NAME_PAGE,
        "count" to ManagerSql.COUNT,
        "edited page" to EditSql.EDITED_PAGE,
        "rank page" to EditSql.RANK_PAGE,
        "before" to EditSql.BEFORE,
        "after" to EditSql.AFTER,
        "provider row" to EditSql.PROVIDER_ROW,
        "custom" to EditSql.CUSTOM,
        "group by key" to EditSql.GROUP_BY_KEY,
        "recount group" to EditSql.RECOUNT_GROUP,
    )

    /**
     * Small or rare reads allowed to sort: a source's group names (a few thousand at most) and the
     * mapping picker, which sorts one source's XMLTV channels by name for a page of 200 when the
     * viewer opens it (CHAN-NFR-06).
     */
    private val sorted = mapOf(
        "group names" to ManagerSql.GROUP_NAMES,
        "epg options" to ManagerSql.EPG_OPTIONS,
    )

    @Test
    fun pagesAndEditsUseAnIndexWithoutASorter() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in paged + sorted) {
                    val plan = db.plan(sql).joinToString(" | ")
                    assertTrue("$name ($analyzed): $plan", plan.contains("SEARCH"))
                    val scans = Regex("""\bSCAN (?:TABLE )?(\w+)""").findAll(plan).map { it.groupValues[1] }.toList()
                    assertTrue("$name ($analyzed): $plan", scans.isEmpty())
                    if (name in paged) assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE FOR ORDER BY"))
                }
            }
        }
    }
}
