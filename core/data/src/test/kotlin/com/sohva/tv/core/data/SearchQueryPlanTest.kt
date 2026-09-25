package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.SearchSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 03 §11 "Query plans": every search group starts from its full-text index and reaches the
 * content tables by id or through an index, with and without statistics. A title table is never
 * scanned; the only sort is over the matches.
 */
class SearchQueryPlanTest {
    private val queries = mapOf(
        "films" to SearchSql.FILMS,
        "series" to SearchSql.SERIES,
        "episodes" to SearchSql.EPISODES,
        "channels" to SearchSql.CHANNELS,
        "programmes" to SearchSql.PROGRAMMES,
    )

    @Test
    fun everyGroupStartsFromItsIndex() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in queries) {
                    val plan = db.plan(sql)
                    val text = plan.joinToString(" | ")
                    println("$name ($analyzed): $text")
                    assertTrue("$name ($analyzed): $text", plan.any { it.contains("VIRTUAL TABLE") })
                    // The full-text table is walked through its MATCH index; everything else is a lookup.
                    val scans = plan.filter { Regex("""\bSCAN\b""").containsMatchIn(it) && !it.contains("VIRTUAL TABLE") }
                    assertTrue("$name ($analyzed): $text", scans.isEmpty())
                    if (name == "programmes") assertTrue("$name ($analyzed): $text", text.contains("index_channel_source_id_epg_id"))
                    assertFalse("$name ($analyzed): $text", Regex("""\bSCAN (m|s|e|c|p|movie|series|episode|channel|programme)\b""").containsMatchIn(text))
                }
            }
        }
    }
}
