package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.HomeSql
import com.sohva.tv.core.data.database.ProgressSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 02 §9.3 and §11 "Query plans": every Home query starts from its small side (the profile's
 * progress rows, the recent channel keys, one channel's guide) and reaches rows by key or index,
 * with and without statistics. No title, channel or programme table is scanned.
 */
class HomeQueryPlanTest {
    private val queries = mapOf(
        "continue films" to ProgressSql.CONTINUE_FILMS,
        "continue episodes" to ProgressSql.CONTINUE_EPISODES,
        "recent channels" to HomeSql.CHANNELS_OF_KEYS,
        "programme at" to HomeSql.PROGRAMME_AT,
    )

    @Test
    fun homeQueriesStartFromTheirSmallSide() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in queries) {
                    val plan = db.plan(sql)
                    val text = plan.joinToString(" | ")
                    println("$name ($analyzed): $text")
                    assertFalse("$name ($analyzed): $text", Regex("""\bSCAN\b""").containsMatchIn(text))
                    assertTrue("$name ($analyzed): $text", plan.first().startsWith("SEARCH"))
                }
                val programme = db.plan(HomeSql.PROGRAMME_AT).joinToString(" | ")
                assertTrue(programme, programme.contains("index_programme_source_id_snapshot_epg_id_start_at"))
                assertFalse(programme, programme.contains("TEMP B-TREE"))
            }
        }
    }
}
