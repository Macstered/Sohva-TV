package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 51 §9: the sync's keyset walk and diff writes use the primary key, never a sort. */
class TraktQueryPlanTest {
    private fun assertKeyed(sql: String) {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                val plan = db.plan(sql).joinToString(" | ")
                assertTrue(plan, plan.contains("SEARCH") && !plan.contains("TEMP B-TREE") && !plan.contains("SCAN"))
            }
        }
    }

    @Test
    fun statePageWalksThePrimaryKey() =
        assertKeyed("SELECT * FROM trakt_state WHERE profile_id = :profile AND kind = :kind AND key > :after ORDER BY key LIMIT :limit")

    @Test
    fun stateDeleteUsesThePrimaryKey() =
        assertKeyed("DELETE FROM trakt_state WHERE profile_id = :profile AND key IN (:a, :b)")
}
