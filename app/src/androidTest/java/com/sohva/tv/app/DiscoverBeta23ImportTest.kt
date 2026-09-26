package com.sohva.tv.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.discover.Beta23DiscoverImport
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.feature.discover.protocol.Hashes
import com.sohva.tv.feature.discover.store.WatchIdentity
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decision A1 for Discover: beta 23's three databases and `sohva_addon_ui`, written here the way
 * beta 23 wrote them (its envelope, its tables, its hashed keys), come across once with the same
 * installation id, progress, Library title, catalog order and hidden set, and "Show all languages".
 * A lost old key imports nothing. Fictional data only.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverBeta23ImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val graph get() = (context.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val spec = EnvelopeSpec.BETA23_DISCOVER
    private val profile = "default"
    private val installation = "3f7c1a2e-0000-4000-8000-000000000001"

    @get:Rule
    val clear = ClearStateRule()

    private val manifest = """{"id":"org.example.old","version":"1.0.0","name":"Old provider","types":["movie"],"resources":["catalog","meta","stream"],
        "catalogs":[{"type":"movie","id":"c1","name":"One"},{"type":"movie","id":"c2","name":"Two"}]}"""

    @Before
    fun seed() {
        forget()
        val cipher = EnvelopeCipher(spec, AndroidKeystoreKeyProvider(spec.keystoreAlias), PrefsWrappedKeyStore(context, spec))
        fun enc(json: JSONObject) = cipher.encrypt(json.toString())
        db("sohva-addons.db") { d ->
            d.execSQL(
                "CREATE TABLE addon_installations (installationId TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, endpointFingerprint TEXT NOT NULL, " +
                    "encryptedPayload TEXT NOT NULL, enabled INTEGER NOT NULL, position INTEGER NOT NULL, revision INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)",
            )
            val url = "https://provider.example/old/manifest.json"
            d.execSQL(
                "INSERT INTO addon_installations VALUES (?, ?, ?, ?, 0, 0, 3, 1000)",
                arrayOf(installation, profile, Hashes.sha256Hex(url), enc(JSONObject().put("url", url).put("manifest", manifest))),
            )
        }
        db("sohva-addon-progress.db") { d ->
            d.execSQL("CREATE TABLE addon_progress (key TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, encryptedPayload TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL)")
            val payload = JSONObject().put("version", 1).put("installation", installation).put("mediaType", "movie").put("mediaId", "tt0000001")
                .put("videoType", "movie").put("videoId", "tt0000001").put("title", "Fictional Film").put("position", 600_000L).put("duration", 5_400_000L)
                .put("updated", 2_000L).put("completed", false).put("artwork", JSONObject().put("name", "Fictional Film"))
            d.execSQL("INSERT INTO addon_progress VALUES ('k1', ?, ?, 2000)", arrayOf(profile, enc(payload)))
        }
        db("sohva-addon-library.db") { d ->
            d.execSQL("CREATE TABLE addon_library (key TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, encryptedPayload TEXT NOT NULL, addedAtMillis INTEGER NOT NULL)")
            val payload = JSONObject().put("version", 1).put("installation", installation).put("type", "movie").put("id", "tt0000001")
                .put("name", "Fictional Film").put("year", "2024").put("added", 3_000L)
            d.execSQL("INSERT INTO addon_library VALUES ('l1', ?, ?, 3000)", arrayOf(profile, enc(payload)))
        }
        val c1 = Hashes.parts(installation, "movie", "c1")
        val c2 = Hashes.parts(installation, "movie", "c2")
        context.getSharedPreferences("sohva_addon_ui", Context.MODE_PRIVATE).edit()
            .putString("catalog_order_" + Hashes.parts("catalog-order", profile), JSONArray(listOf(c2, c1)).toString())
            .putString("catalog_hidden_" + Hashes.parts("catalog-visibility", profile), JSONArray(listOf(c1)).toString())
            .putBoolean("all_subtitle_languages", true)
            .commit()
    }

    @After
    fun forget() {
        runBlocking { graph.data.appMeta.delete("import.beta23.discover") }
        listOf("sohva-addons.db", "sohva-addon-progress.db", "sohva-addon-library.db").forEach { context.deleteDatabase(it) }
        listOf("sohva_addon_ui", spec.prefsFile).forEach { context.deleteSharedPreferences(it) }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
    }

    private fun db(name: String, block: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).also { it.parentFile?.mkdirs() }, null).use(block)
    }

    @Test
    fun beta23sDiscoverDataComesAcrossOnceWithItsKeys() = runBlocking {
        Beta23DiscoverImport(graph).run()
        val installed = host.installations.list(profile).single()
        assertEquals(installation, installed.id)
        assertEquals("Old provider", installed.manifest.name)
        assertEquals("disabled stays disabled", false, installed.enabled)
        val entry = host.progress.get(profile, WatchIdentity(installation, "movie", "tt0000001", "movie", "tt0000001"))
        assertNotNull(entry)
        assertEquals(600_000L, entry!!.positionMs)
        assertEquals(2_000L, entry.updatedAt)
        assertTrue(host.library.contains(profile, installation, "movie", "tt0000001"))
        val catalogs = host.catalogs.ordered(profile, listOf(installed))
        assertEquals(listOf("c2", "c1"), catalogs.map { it.catalog.id })
        assertEquals(listOf(false, true), catalogs.map { it.hidden })
        assertTrue(host.settings.allLanguages.value)
        // Once: a second run changes nothing.
        host.library.remove(profile, installation, "movie", "tt0000001")
        Beta23DiscoverImport(graph).run()
        assertTrue(!host.library.contains(profile, installation, "movie", "tt0000001"))
    }

    @Test
    fun aLostOldKeyImportsNothing() = runBlocking {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        Beta23DiscoverImport(graph).run()
        assertTrue(host.installations.list(profile).isEmpty())
        assertEquals("failed", graph.data.appMeta.value("import.beta23.discover"))
    }
}
