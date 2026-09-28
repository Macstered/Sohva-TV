package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TraktSql
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 51 §9: the sync's keyset walk, its diff writes, the visible-title lookups and the library
 * overlay all start from keys or an index, never a scan or a sort, with and without statistics.
 */
class TraktQueryPlanTest {
    private fun assertKeyed(name: String, sql: String) {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                val plan = db.plan(sql)
                val text = plan.joinToString(" | ")
                assertTrue("$name ($analyzed): $text", plan.first().startsWith("SEARCH"))
                assertFalse("$name ($analyzed): $text", Regex("""\bSCAN\b""").containsMatchIn(text) || text.contains("TEMP B-TREE"))
            }
        }
    }

    @Test
    fun syncQueriesUseThePrimaryKey() {
        assertKeyed("page", "SELECT * FROM trakt_state WHERE profile_id = :profile AND kind = :kind AND key > :after ORDER BY key LIMIT :limit")
        assertKeyed("delete", "DELETE FROM trakt_state WHERE profile_id = :profile AND key IN (:a, :b)")
    }

    @Test
    fun visibleTitleLookupsUseTheirIndexes() {
        assertKeyed("imdb", "SELECT * FROM trakt_state WHERE profile_id = :profile AND imdb IN (:a, :b)")
        assertKeyed("tmdb", "SELECT * FROM trakt_state WHERE profile_id = :profile AND tmdb IN (:a, :b)")
    }

    @Test
    fun libraryOverlayStartsFromKeys() {
        val queries = mapOf(
            "by keys" to TraktSql.BY_KEYS, "paused" to TraktSql.PAUSED, "series tmdb" to TraktSql.SERIES_TMDB,
            "series episodes" to TraktSql.SERIES_EPISODES, "film copies" to TraktSql.FILM_COPIES,
            "series copies" to TraktSql.SERIES_COPIES, "episode at" to TraktSql.EPISODE_AT,
            "film route" to TraktSql.FILM_ROUTE, "series route" to TraktSql.SERIES_ROUTE,
            "films owned" to TraktSql.FILMS_OWNED.replace(":workKeys", ":a, :b"),
            "series owned" to TraktSql.SERIES_OWNED.replace(":tmdb", ":a, :b"),
        )
        for ((name, sql) in queries) assertKeyed(name, sql)
    }
}
