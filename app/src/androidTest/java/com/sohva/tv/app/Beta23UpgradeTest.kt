package com.sohva.tv.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.discover.Beta23DiscoverImport
import com.sohva.tv.app.migration.Beta23Cleanup
import com.sohva.tv.app.migration.Beta23Upgrade
import com.sohva.tv.app.trakt.Beta23TraktImport
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.migration.Beta23Database
import com.sohva.tv.core.data.migration.Beta23Rows
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.profile.ProfileStore
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.settings.StartupScreen
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decision A1 option B, plan/04 §17: an install carrying beta 23's data, written here as beta 23
 * wrote it (its schema 29 from its own exported JSON, its settings file, its secure store under its
 * own key, a phone logo), comes across once: settings, profiles and their favourites, recents,
 * locks and allowed groups, the PIN, channel edits with the logo, lists, rules, positions (linked
 * to their titles once the catalogue is back), reminders, sport decisions and metadata fixes. Then
 * beta 23's files and keys go, and the shared logo folder stays. Fictional data only.
 */
@RunWith(AndroidJUnit4::class)
class Beta23UpgradeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val graph get() = (context.applicationContext as SohvaApplication).graph
    private val spec = EnvelopeSpec.BETA23_MAIN
    private val sep = Char(0x1F).toString()
    private val kids = "p1700000000000"
    private val film = LibraryFixture.key(0)
    private val channel = "s1:c1"

    @get:Rule
    val clear = ClearStateRule()

    @Before
    fun seed() {
        forget()
        database()
        runBlocking { preferences() }
        val cipher = EnvelopeCipher(spec, AndroidKeystoreKeyProvider(spec.keystoreAlias), PrefsWrappedKeyStore(context, spec))
        context.getSharedPreferences(Beta23SourceImport.FILE, Context.MODE_PRIVATE).edit().putString("parental_pin_v1", cipher.encrypt("1234")).commit()
    }

    @After
    fun forget() {
        runBlocking {
            listOf(Beta23Upgrade.MARKER, Beta23Cleanup.MARKER).forEach { graph.data.appMeta.delete(it) }
        }
        Beta23Upgrade.doneFile(context).delete()
        context.deleteDatabase(Beta23Database.FILE)
        Beta23Upgrade.oldPreferences(context).delete()
        listOf(Beta23SourceImport.FILE, spec.prefsFile).forEach { context.deleteSharedPreferences(it) }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        logo().delete()
    }

    private fun logo() = File(context.filesDir, "channel-logos/0123456789abcdef-1.png")

    /** Beta 23's schema 29, table by table from its exported JSON, with one row of each kind we import. */
    private fun database() {
        context.deleteDatabase(Beta23Database.FILE)
        val json = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("beta23/streammate-29.json").bufferedReader().readText()).getJSONObject("database")
        val path = context.getDatabasePath(Beta23Database.FILE).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { d ->
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                // Views and FTS tables are not needed by the importer.
                if (e.has("ftsVersion")) continue
                d.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            }
            // A real picture, as the phone sent it: the logo store decodes what it keeps.
            val picture = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
            logo().apply { parentFile?.mkdirs() }.outputStream().use { picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            d.execSQL(
                "INSERT INTO channel_preferences (channelId, sourceId, customName, customGroupTitle, hidden, sortOrder, manualXmltvChannelId, updatedAtEpochMillis, customLogoUrl, channelNumber) " +
                    "VALUES (?, 's1', 'My channel', NULL, 0, 3, NULL, 10, ?, 7)",
                arrayOf(channel, logo().toURI().toString()),
            )
            d.execSQL("INSERT INTO channel_lists (listId, name, sortOrder, updatedAtEpochMillis) VALUES ('l1', 'Evening', 0, 10)")
            d.execSQL("INSERT INTO channel_list_members (listId, channelId, sortOrder) VALUES ('l1', ?, 0)", arrayOf(channel))
            d.execSQL("INSERT INTO organization_rules (room, sourceId, groupKey, itemKey, enabled, sortMode, position) VALUES ('LIVE', 's1', 'name:news', '', 0, NULL, NULL)")
            d.execSQL(
                "INSERT INTO playback_progress (contentKey, sourceId, contentType, itemId, positionMillis, durationMillis, completed, lastWatchedEpochMillis, workKey, profileId) " +
                    "VALUES (?, ?, 'MOVIE', '0', 600000, 5400000, 0, 20, 'name:x:', ?)",
                arrayOf(film, LibraryFixture.SOURCE, kids),
            )
            d.execSQL(
                "INSERT INTO reminders (id, kind, eventId, channelId, title, subtitle, startEpochMillis, createdAtEpochMillis) VALUES ('programme:s1:c1:abc', 'PROGRAMME', NULL, ?, 'News', NULL, ?, 5)",
                arrayOf<Any>(channel, System.currentTimeMillis() + 3_600_000),
            )
            d.execSQL("INSERT INTO event_channel_decisions (eventId, channelId, decision, updatedAtEpochMillis) VALUES ('e1', ?, 'ACCEPTED', 5)", arrayOf(channel))
            d.execSQL("INSERT INTO team_aliases (sport, normalizedCanonicalName, normalizedAlias) VALUES ('FOOTBALL', 'hjk', 'hjk helsinki')")
            d.execSQL(
                "INSERT INTO catalogue_metadata_overrides (contentKey, providerPosterUrl, replacementPosterUrl, replaceProviderPoster, replacementTitle, externalId, genresVersion, updatedAtEpochMillis) " +
                    "VALUES (?, NULL, NULL, 0, 'A fixed title', '603', 2, 30)",
                arrayOf(film),
            )
        }
    }

    private suspend fun preferences() {
        val file = context.preferencesDataStoreFile("streammate_preferences")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).edit { p ->
                p[stringPreferencesKey("profiles")] = "$kids${sep}Kids${sep}2"
                p[booleanPreferencesKey("ask_profile_at_start")] = false
                p[stringPreferencesKey("startup_screen")] = "GUIDE"
                p[stringPreferencesKey("color_theme")] = "nord"
                p[booleanPreferencesKey("show_channel_numbers")] = false
                p[booleanPreferencesKey("auto_frame_rate")] = false
                p[stringSetPreferencesKey("favourite_channel_ids")] = setOf(channel)
                p[stringPreferencesKey("recent_channel_ids")] = "$channel${sep}s1:c2"
                p[stringSetPreferencesKey("locked_channel_ids")] = setOf("s1:c9")
                p[stringSetPreferencesKey("allowed_groups_live:$kids")] = setOf("name:kids")
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    private fun count(sql: String, vararg args: Any?): Int =
        graph.data.database.openHelper.readableDatabase.query(sql, arrayOf<Any?>(*args)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Test
    fun beta23sDataComesAcrossOnceAndItsFilesGoAfterwards() = runBlocking {
        val upgrade = Beta23Upgrade(graph)
        assertTrue(upgrade.pending())
        assertTrue(upgrade.run() is Beta23Upgrade.Result.Done)
        assertFalse("later starts read no database", upgrade.pending())
        // Settings and profiles.
        val start = graph.data.preferences.startSnapshot()
        assertEquals(StartupScreen.GUIDE, start.startupScreen)
        assertEquals("nord", start.theme.id)
        assertEquals(listOf(kids), start.household.stored.map { it.id })
        assertFalse(start.household.askAtStart)
        assertFalse(graph.data.preferences.playback().matchFrameRate)
        assertEquals(Outcome.Ok("1234"), graph.data.secrets.read(ProfileStore.PIN_KEY))
        // Each profile's rows.
        assertEquals(1, count("SELECT COUNT(*) FROM favourite_channel WHERE profile_id = 'default' AND channel_key = ?", channel))
        assertEquals(2, count("SELECT COUNT(*) FROM recent_channel WHERE profile_id = 'default'"))
        assertEquals(1, count("SELECT COUNT(*) FROM locked_channel WHERE channel_key = 's1:c9'"))
        assertEquals(1, count("SELECT COUNT(*) FROM profile_allowed_group WHERE profile_id = ? AND group_key = 'name:kids'", kids))
        // Channel edits with the phone logo, the list, the rule.
        assertEquals(1, count("SELECT COUNT(*) FROM channel_custom WHERE channel_key = ? AND custom_name = 'My channel' AND custom_logo_url IS NOT NULL", channel))
        assertEquals(1, count("SELECT COUNT(*) FROM channel_list_member WHERE list_id = 'l1'"))
        assertEquals(1, count("SELECT COUNT(*) FROM organization_rule WHERE room = 'LIVE' AND group_key = 'name:news'"))
        // What backups never held.
        assertEquals(1, count("SELECT COUNT(*) FROM watch_progress WHERE profile_id = ? AND content_key = ? AND position_ms = 600000", kids, film))
        assertEquals(1, count("SELECT COUNT(*) FROM reminder"))
        assertEquals(1, count("SELECT COUNT(*) FROM event_channel_decision"))
        assertEquals(1, count("SELECT COUNT(*) FROM team_alias"))
        assertEquals(1, count("SELECT COUNT(*) FROM metadata_match WHERE content_key = ? AND provider = 'tmdb' AND external_id = '603'", film))
        // Once the catalogue is back, the position takes its film's identity.
        LibraryFixture.seed(graph, groups = listOf("Drama"), perGroup = 1)
        // A catalogue import gives every film its identity; the fixture leaves it to the test.
        graph.data.database.openHelper.writableDatabase.execSQL("UPDATE movie SET work_key = 'tmdb:603' WHERE key = ?", arrayOf(film))
        Beta23Rows.link(graph.data.database)
        assertEquals(1, count("SELECT COUNT(*) FROM watch_progress WHERE content_key = ? AND work_key = 'tmdb:603'", film))
        // Every part done: beta 23's files and key go; the shared logo folder stays.
        for (marker in listOf(Beta23SourceImport.MARKER, Beta23DiscoverImport.MARKER, Beta23TraktImport.MARKER)) {
            if (graph.data.appMeta.value(marker) == null) graph.data.appMeta.put(AppMetaEntity(marker, "nothing"))
        }
        Beta23Cleanup(graph).run()
        assertFalse(context.getDatabasePath(Beta23Database.FILE).exists())
        assertFalse(Beta23Upgrade.oldPreferences(context).exists())
        assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/${Beta23SourceImport.FILE}.xml").exists())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(spec.keystoreAlias))
        assertTrue(File(context.filesDir, "channel-logos").isDirectory)
    }

    @Test
    fun aFailedPartKeepsBeta23sFiles() = runBlocking {
        Beta23Upgrade(graph).run()
        graph.data.appMeta.put(AppMetaEntity(Beta23SourceImport.MARKER, "failed:secrets"))
        Beta23Cleanup(graph).run()
        assertTrue(context.getDatabasePath(Beta23Database.FILE).exists())
        assertNotNull(graph.data.appMeta.value(Beta23SourceImport.MARKER))
        graph.data.appMeta.delete(Beta23SourceImport.MARKER)
    }
}
