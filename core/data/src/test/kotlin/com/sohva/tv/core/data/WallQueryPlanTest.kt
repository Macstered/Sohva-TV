package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.LibrarySql
import com.sohva.tv.core.data.database.ProgressSql
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TitleSql
import com.sohva.tv.core.data.database.WallSql
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walls' pages (spec 40 §9.3 and §11 "Query plans", plan/04 §15.11): every page shape reads
 * its index in order (no sorter), with and without statistics, and History reaches titles by key
 * from the progress table.
 */
class WallQueryPlanTest {
    private val pages = mapOf(
        "film group after" to WallSql.FILM_GROUP_AFTER,
        "film group before" to WallSql.FILM_GROUP_BEFORE,
        "film all after" to WallSql.FILM_ALL_AFTER,
        "film all before" to WallSql.FILM_ALL_BEFORE,
        "film genre after" to WallSql.FILM_GENRE_AFTER,
        "film genre before" to WallSql.FILM_GENRE_BEFORE,
        "film unsorted after" to WallSql.FILM_UNSORTED_AFTER,
        "film unsorted before" to WallSql.FILM_UNSORTED_BEFORE,
        "series group after" to WallSql.SERIES_GROUP_AFTER,
        "series group before" to WallSql.SERIES_GROUP_BEFORE,
        "series all after" to WallSql.SERIES_ALL_AFTER,
        "series all before" to WallSql.SERIES_ALL_BEFORE,
        "series genre after" to WallSql.SERIES_GENRE_AFTER,
        "series genre before" to WallSql.SERIES_GENRE_BEFORE,
        "series unsorted after" to WallSql.SERIES_UNSORTED_AFTER,
        "series unsorted before" to WallSql.SERIES_UNSORTED_BEFORE,
        "film history older" to WallSql.FILM_HISTORY_OLDER,
        "film history newer" to WallSql.FILM_HISTORY_NEWER,
        "series history older" to WallSql.SERIES_HISTORY_OLDER,
        "series history newer" to WallSql.SERIES_HISTORY_NEWER,
    )

    @Test
    fun everyWallPageReadsItsIndexInOrder() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                for ((name, sql) in pages) {
                    val plan = db.plan(sql).joinToString(" | ")
                    if ("unsorted" in name) assertTrue("$name ($analyzed): $plan", plan.contains("genre=?"))
                    assertFalse("$name ($analyzed): $plan", plan.contains("TEMP B-TREE"))
                    val scans = Regex("""\bSCAN (?:TABLE )?(\w+)""").findAll(plan).map { it.groupValues[1] }.toList()
                    // "All groups" walks its index in order ("SCAN movie USING INDEX …"); a scan of the bare table would sort or visit every row.
                    assertTrue("$name ($analyzed): $plan", scans.isEmpty() || plan.contains("USING INDEX") || plan.contains("USING COVERING INDEX"))
                    assertFalse("$name ($analyzed): $plan", Regex("""\bSCAN (?:TABLE )?\w+(?! USING)""").containsMatchIn(plan.replace(Regex("""SCAN (?:TABLE )?\w+ USING (?:COVERING )?INDEX \w+"""), "")))
                }
            }
        }
    }

    @Test
    fun copiesAndGroupsAreLookups() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            val lookups = mapOf(
                "copies" to WallSql.FILM_COPIES,
                "groups" to WallSql.GROUPS,
                "progress" to ProgressSql.GET,
                "newest of work" to ProgressSql.NEWEST_OF_WORK,
                "of series" to ProgressSql.OF_SERIES,
                "film facts" to ProgressSql.FILM,
                "episode facts" to ProgressSql.EPISODE,
                "season" to ProgressSql.SEASON,
                "ticks own" to ProgressSql.TICKS_OWN,
                "ticks work" to ProgressSql.TICKS_WORK,
                "matches page" to LibrarySql.MATCHES_PAGE,
                "film keys page" to LibrarySql.FILM_KEYS_PAGE,
                "copies" to LibrarySql.COPIES,
                "film genre count" to LibrarySql.FILM_GENRE_COUNT,
                "film unsorted count" to LibrarySql.FILM_UNSORTED_COUNT,
                "series genre count" to LibrarySql.SERIES_GENRE_COUNT,
                "series unsorted count" to LibrarySql.SERIES_UNSORTED_COUNT,
                "versions" to TitleSql.VERSIONS,
                "similar" to TitleSql.SIMILAR,
                "visible" to TitleSql.VISIBLE,
                "repair film poster" to TitleSql.REPAIR_FILM_POSTER,
                "repair series poster" to TitleSql.REPAIR_SERIES_POSTER,
            )
            for ((name, sql) in lookups) {
                val plan = db.plan(sql).joinToString(" | ")
                assertTrue("$name: $plan", plan.contains("SEARCH"))
                assertFalse("$name: $plan", Regex("""\bSCAN\b""").containsMatchIn(plan))
            }
        }
    }
}
