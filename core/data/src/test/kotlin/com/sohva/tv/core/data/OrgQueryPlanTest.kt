package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.OrgSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Spec 42 §9.1 "Query-plan tests": the organisation pass reaches titles and channels through
 * indexes or a primary-key walk, never a scan that sorts, with and without statistics.
 */
class OrgQueryPlanTest {
    private val queries = mapOf(
        "films page" to OrgSql.FILMS_PAGE,
        "series page" to OrgSql.SERIES_PAGE,
        "films of work" to OrgSql.FILMS_OF_WORK,
        "series of keys" to OrgSql.SERIES_OF_KEYS,
        "channels page" to OrgSql.CHANNELS_PAGE,
        "channels of keys" to OrgSql.CHANNELS_OF_KEYS,
        // A sorted group's channels for their places (GUIDE-13): the group's index, sorted in Kotlin.
        "group channels" to OrgSql.GROUP_CHANNELS,
    )

    @Test
    fun thePassReadsThroughIndexes() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in queries) {
                    val plan = db.plan(sql).joinToString(" | ")
                    println("$name ($analyzed): $plan")
                    assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE"))
                    // A bare SCAN of a title or channel table would walk the catalogue.
                    assertFalse("$name ($analyzed): $plan", Regex("""\bSCAN (m|s|c)\b(?! USING)""").containsMatchIn(plan))
                }
            }
        }
    }

    /** The manager reads one group's rows (a film group's range is sorted by SQLite, bounded by the group). */
    @Test
    fun theManagerReadsOneGroupOrList() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in mapOf(
                    "films" to OrgSql.MANAGER_FILMS, "series" to OrgSql.MANAGER_SERIES,
                    "channels" to OrgSql.MANAGER_CHANNELS, "list" to OrgSql.MANAGER_LIST_MEMBERS,
                )) {
                    val plan = db.plan(sql).joinToString(" | ")
                    println("$name ($analyzed): $plan")
                    assertFalse("$name ($analyzed): $plan", Regex("""SCAN (t|c)(?! USING)""").containsMatchIn(plan))
                    if (name == "channels" || name == "list") assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE"))
                }
            }
        }
    }
}
