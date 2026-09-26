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
import org.junit.Assert.assertTrue
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
    fun migratesFrom2To3KeepingSourcesAndChannels() {
        helper.createDatabase(name, 2).use {
            it.execSQL(
                "INSERT INTO source (id, name, type, enabled, priority, connection_limit, import_scope, epg_offset_minutes, created_at, updated_at) " +
                    "VALUES ('s1', 'Fixture', 'M3U', 1, 0, 1, 'BOTH', 0, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(name, 3, true).use { db ->
            db.query("SELECT name FROM source WHERE id = 's1'").use { it.moveToFirst(); assertEquals("Fixture", it.getString(0)) }
            for (table in listOf("favourite_channel", "recent_channel")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 0, it.getInt(0)) }
            }
        }
    }

    @Test
    fun migratesFrom3To4CopyingTheProviderValues() {
        helper.createDatabase(name, 3).use {
            it.execSQL(
                "INSERT INTO channel (id, key, source_id, group_id, name, sort_name, tvg_id, epg_id, logo_url, stream_url_enc, " +
                    "user_agent, referrer, playlist_order, provider_number, number, display_rank, visible, catchup_type, " +
                    "catchup_source, catchup_days, catchup_tz, xtream_stream_id, content_hash, generation) VALUES " +
                    "(1, 's1:a', 's1', 7, 'Northstar 1', 'northstar 1', 'n1', 'n1', 'https://provider.example/n1.png', 'sealed', " +
                    "NULL, NULL, 0, 1, 1, 1024, 1, NULL, NULL, NULL, NULL, NULL, 42, 1)",
            )
            it.execSQL("INSERT INTO favourite_channel (profile_id, channel_key, added_at) VALUES ('default', 's1:a', 5)")
        }
        helper.runMigrationsAndValidate(name, 4, true).use { db ->
            db.query("SELECT display_rank FROM channel WHERE id = 1").use { it.moveToFirst(); assertEquals(1L shl 40, it.getLong(0)) }
            db.query("SELECT provider_name, provider_group_id, provider_logo_url, name FROM channel WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("Northstar 1", it.getString(0))
                assertEquals(7L, it.getLong(1))
                assertEquals("https://provider.example/n1.png", it.getString(2))
                assertEquals("Northstar 1", it.getString(3))
            }
            db.query("SELECT COUNT(*) FROM favourite_channel").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            for (table in listOf("channel_custom", "channel_list", "channel_list_member", "locked_channel", "reminder")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 0, it.getInt(0)) }
            }
        }
    }

    @Test
    fun migratesFrom4To5KeepingFilmsWithEmptyClaims() {
        helper.createDatabase(name, 4).use {
            it.execSQL(
                "INSERT INTO movie (id, key, source_id, provider_id, group_id, name, sort_name, year, rating, rating_x10, poster_url, " +
                    "stream_url_enc, plot, provider_order, genre, work_key, primary_copy, visible, item_position, content_hash, generation) " +
                    "VALUES (1, 'vod:movie:s1:9', 's1', '9', 3, 'FIN | Quiet Harbour 4K', 'fin quiet harbour 4k', 2020, '7.5', 75, NULL, " +
                    "'sealed', NULL, 0, NULL, NULL, 1, 1, NULL, 42, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, 5, true).use { db ->
            db.query("SELECT name, quality_mask, claim_mask, picture_rank, content_hash FROM movie WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("FIN | Quiet Harbour 4K", it.getString(0))
                // The next import fills the claims: its hash carries a keys version, so 42 no longer matches.
                assertEquals(0, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals(0, it.getInt(3))
                assertEquals(42L, it.getLong(4))
            }
            db.query("SELECT COUNT(*) FROM watch_progress").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migratesFrom5To6AddingTheMetadataTables() {
        helper.createDatabase(name, 5).use {
            it.execSQL(
                "INSERT INTO movie (id, key, source_id, provider_id, group_id, name, sort_name, year, rating, rating_x10, poster_url, " +
                    "stream_url_enc, plot, provider_order, quality_mask, claim_mask, picture_rank, genre, work_key, primary_copy, group_primary, " +
                    "visible, item_position, content_hash, generation) VALUES (1, 'vod:movie:s1:9', 's1', '9', 3, 'Quiet Harbour', " +
                    "'quiet harbour', 2020, NULL, NULL, NULL, 'sealed', NULL, 0, 0, 0, 0, NULL, NULL, 1, 1, 1, NULL, 42, 1)",
            )
            it.execSQL(
                "INSERT INTO watch_progress (profile_id, content_key, source_id, content_type, work_key, series_key, position_ms, " +
                    "duration_ms, completed, updated_at) VALUES ('default', 'vod:movie:s1:9', 's1', 'MOVIE', NULL, NULL, 60000, 600000, 0, 5)",
            )
        }
        helper.runMigrationsAndValidate(name, 6, true).use { db ->
            db.query("SELECT name, replacement_title, replace_poster, similar_key FROM movie WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("Quiet Harbour", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals(0, it.getInt(2))
                assertTrue(it.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM watch_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            for (table in listOf("metadata_match", "metadata_cache", "metadata_pin", "metadata_queue", "genre_count")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 0, it.getInt(0)) }
            }
        }
    }

    @Test
    fun migratesFrom6To7AddingTheOrganisationRules() {
        helper.createDatabase(name, 6).use {
            it.execSQL(
                "INSERT INTO movie (id, key, source_id, provider_id, group_id, name, sort_name, year, rating, rating_x10, poster_url, " +
                    "stream_url_enc, plot, provider_order, quality_mask, claim_mask, picture_rank, genre, work_key, primary_copy, group_primary, " +
                    "visible, item_position, content_hash, generation) VALUES (1, 'vod:movie:s1:9', 's1', '9', 3, 'Quiet Harbour', " +
                    "'quiet harbour', 2020, NULL, NULL, NULL, 'sealed', NULL, 0, 0, 0, 0, NULL, NULL, 1, 1, 1, NULL, 42, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, 7, true).use { db ->
            db.query("SELECT name FROM movie WHERE id = 1").use { it.moveToFirst(); assertEquals("Quiet Harbour", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM organization_rule").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migratesFrom7To8IndexingExistingTitlesForSearch() {
        helper.createDatabase(name, 7).use {
            it.execSQL(
                "INSERT INTO movie (id, key, source_id, provider_id, group_id, name, sort_name, year, rating, rating_x10, poster_url, " +
                    "stream_url_enc, plot, provider_order, quality_mask, claim_mask, picture_rank, genre, work_key, primary_copy, group_primary, " +
                    "visible, item_position, content_hash, generation) VALUES (1, 'vod:movie:s1:9', 's1', '9', 3, 'Quiet Harbour', " +
                    "'quiet harbour', 2020, NULL, NULL, NULL, 'sealed', NULL, 0, 0, 0, 0, NULL, NULL, 1, 1, 1, NULL, 42, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, 8, true).use { db ->
            val found = { term: String -> db.query("SELECT rowid FROM movie_search WHERE movie_search MATCH ?", arrayOf(term)).use { it.count } }
            assertEquals(1, found("\"harb*\""))
            // The triggers came with the tables: a rename follows.
            db.execSQL("UPDATE movie SET name = 'Loud Pier' WHERE id = 1")
            assertEquals(0, found("\"harb*\""))
            assertEquals(1, found("\"pier*\""))
        }
    }

    @Test
    fun migratesFrom8To9KeepingViewerDataAndAddingAllowedGroups() {
        helper.createDatabase(name, 8).use {
            it.execSQL("INSERT INTO favourite_channel (profile_id, channel_key, added_at) VALUES ('default', 's1:c1', 1)")
            it.execSQL("INSERT INTO locked_channel (profile_id, channel_key) VALUES ('default', 's1:c2')")
        }
        helper.runMigrationsAndValidate(name, 9, true).use { db ->
            db.query("SELECT channel_key FROM favourite_channel").use { assertEquals(1, it.count) }
            db.query("SELECT channel_key FROM locked_channel").use { assertEquals(1, it.count) }
            db.execSQL("INSERT INTO profile_allowed_group (profile_id, room, group_key) VALUES ('p1', 'LIVE', 'name:news')")
            db.query("SELECT group_key FROM profile_allowed_group WHERE profile_id = 'p1' AND room = 'LIVE'").use { assertEquals(1, it.count) }
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
