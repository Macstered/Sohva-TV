package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMetaQueryPlanTest {
    @Test
    fun metaLookupUsesThePrimaryKey() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            val sql = "SELECT value FROM app_meta WHERE key = :key"
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                val plan = db.plan(sql).joinToString(" | ")
                assertTrue(plan, plan.contains("SEARCH") && !plan.contains("TEMP B-TREE"))
            }
        }
    }
}
