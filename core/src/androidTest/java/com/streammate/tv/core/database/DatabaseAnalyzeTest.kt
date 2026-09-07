package com.streammate.tv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseAnalyzeTest {
    @Test
    fun analyzeLeavesPlannerStatisticsBehind() {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        try {
            db.analyze()
            db.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_stat1'")
                .use { cursor -> assertTrue("sqlite_stat1 should exist after ANALYZE", cursor.moveToFirst()) }
        } finally {
            db.close()
        }
    }
}
