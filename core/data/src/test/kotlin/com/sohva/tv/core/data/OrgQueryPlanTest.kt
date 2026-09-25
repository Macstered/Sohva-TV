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
}
