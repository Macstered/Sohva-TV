package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.PairingSql
import com.sohva.tv.core.data.database.SohvaDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 60 §9 "Pairing": the channel page is a primary-key range read with the channel table outer;
 * the programme page reads the (source, snapshot, guide id, start) index, never the table.
 */
class PairingQueryPlanTest {
    @Test
    fun theChannelPageWalksThePrimaryKey() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                val plan = db.plan(PairingSql.CHANNELS)
                assertTrue(plan.toString(), plan.first().contains("SEARCH c USING INTEGER PRIMARY KEY"))
                assertTrue(plan.toString(), plan.none { it.contains("TEMP B-TREE") || it.startsWith("SCAN") })
            }
        }
    }

    @Test
    fun theProgrammePageUsesTheGuideIndex() {
        QueryPlanHarness.open(SohvaDatabase.VERSION).use { db ->
            for (analyzed in listOf(false, true)) {
                if (analyzed) db.analyze()
                val plan = db.plan(PairingSql.PROGRAMMES).joinToString(" | ")
                assertTrue(plan, plan.contains("USING INDEX index_programme_source_id_snapshot_epg_id_start_at"))
                assertTrue(plan, !plan.contains("SCAN p"))
            }
        }
    }
}
