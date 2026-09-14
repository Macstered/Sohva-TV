package com.sohva.tv.addons.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Trial builds left side tables behind; progress rows must survive the move to 4. */
class AddonProgressMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AddonProgressDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun trialVersionThreeMovesToFourKeepingProgress() {
        helper.createDatabase("addon-progress-from-3", 3).apply {
            execSQL("INSERT INTO addon_progress (`key`, profileId, encryptedPayload, updatedAtMillis) VALUES ('k', 'adult', 'payload', 5)")
            execSQL("INSERT INTO addon_projection_receipts (profileId, sourceKey, workId, revision, receiptId) VALUES ('adult', 's', 'w', 1, 'r')")
            close()
        }
        val db = helper.runMigrationsAndValidate("addon-progress-from-3", 4, true, AddonProgressDatabase.MIGRATION_3_4)
        db.query("SELECT encryptedPayload FROM addon_progress WHERE `key` = 'k'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("payload", cursor.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('addon_projection_receipts', 'addon_progress_display')").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
        db.close()
    }

    @Test fun versionOneMovesToFour() {
        helper.createDatabase("addon-progress-from-1", 1).apply {
            execSQL("INSERT INTO addon_progress (`key`, profileId, encryptedPayload, updatedAtMillis) VALUES ('k', 'adult', 'payload', 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate("addon-progress-from-1", 4, true, AddonProgressDatabase.MIGRATION_1_4)
        db.query("SELECT COUNT(*) FROM addon_progress").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
        db.close()
    }
}
