package com.sohva.tv.core.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.database.DatabaseFactory
import com.sohva.tv.core.data.database.SohvaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "device-test.db"

    /** The migration harness: every future version adds a step test on top of this helper. */
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SohvaDatabase::class.java)

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun opensInWalWithNormalSync() = runBlocking {
        val db = DatabaseFactory.create(context, name)
        try {
            db.appMeta().put(AppMetaEntity("probe", "1"))
            assertEquals("1", db.appMeta().value("probe"))
            val sql = db.openHelper.writableDatabase
            sql.query("PRAGMA journal_mode").use { it.moveToFirst(); assertEquals("wal", it.getString(0).lowercase()) }
            // 1 = NORMAL
            sql.query("PRAGMA synchronous").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFrom1To2KeepingTheInstallFacts() {
        helper.createDatabase(name, 1).use { it.execSQL("INSERT INTO app_meta (key, value) VALUES ('probe', 'kept')") }
        helper.runMigrationsAndValidate(name, 2, true).use { db ->
            db.query("SELECT value FROM app_meta WHERE key = 'probe'").use { it.moveToFirst(); assertEquals("kept", it.getString(0)) }
            for (table in listOf("source", "source_status", "content_group", "channel", "movie", "series", "episode", "epg_channel", "programme")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 0, it.getInt(0)) }
            }
        }
    }

    @Test
    fun exportedSchemaOfTheCurrentVersionMatchesTheCode() {
        helper.createDatabase(name, SohvaDatabase.VERSION).close()
        // Opening with Room validates the identity hash against the exported schema.
        val db = DatabaseFactory.create(context, name)
        db.openHelper.writableDatabase
        db.close()
    }
}
