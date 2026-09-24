package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.ImportSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every statement of the import (AGENTS.md §4 rule 3): each reaches its rows through an index —
 * no scan of a large table and no temporary sort — with and without statistics.
 */
class ImportQueryPlanTest {
    private val statements = mapOf(
        "channel hashes" to ImportSql.CHANNEL_HASHES,
        "channel keys" to ImportSql.CHANNEL_KEYS_PAGE,
        "channel delete" to ImportSql.CHANNEL_DELETE,
        "channel count" to ImportSql.CHANNEL_COUNT,
        "channel epg ids" to ImportSql.CHANNEL_EPG_PAGE,
        "movie hashes" to ImportSql.MOVIE_HASHES,
        "movie keys" to ImportSql.MOVIE_KEYS_PAGE,
        "movie delete" to ImportSql.MOVIE_DELETE,
        "movie count" to ImportSql.MOVIE_COUNT,
        "series hashes" to ImportSql.SERIES_HASHES,
        "series keys" to ImportSql.SERIES_KEYS_PAGE,
        "series delete" to ImportSql.SERIES_DELETE,
        "series count" to ImportSql.SERIES_COUNT,
        "episode hashes" to ImportSql.EPISODE_HASHES,
        "episode keys" to ImportSql.EPISODE_KEYS_PAGE,
        "episode delete" to ImportSql.EPISODE_DELETE,
        "episode orphans" to ImportSql.EPISODE_ORPHANS_DELETE,
        "groups of a source" to ImportSql.GROUPS_OF_SOURCE,
        "group delete" to ImportSql.GROUP_DELETE,
        "programme sweep" to ImportSql.PROGRAMME_SWEEP,
        "epg channel sweep" to ImportSql.EPG_CHANNEL_SWEEP,
    )

    @Test
    fun theHarnessSeesAScan() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            val plan = db.plan("SELECT id FROM channel WHERE name = :name ORDER BY sort_name").joinToString(" | ")
            assertTrue(plan, Regex("""\bSCAN\b""").containsMatchIn(plan) && plan.contains("TEMP B-TREE"))
        }
    }

    @Test
    fun everyImportStatementUsesAnIndex() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in statements) {
                    val plan = db.plan(sql).joinToString(" | ")
                    assertTrue("$name ($analyzed): $plan", plan.contains("SEARCH"))
                    assertFalse("$name ($analyzed): $plan", Regex("""\bSCAN\b""").containsMatchIn(plan))
                    assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE"))
                }
            }
        }
    }
}
