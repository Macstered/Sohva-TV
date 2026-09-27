package com.sohva.tv.app.discover

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonEndpoint
import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.sohva.tv.feature.discover.protocol.Hashes
import com.sohva.tv.feature.discover.store.Artwork
import com.sohva.tv.feature.discover.store.LibraryTitle
import com.sohva.tv.feature.discover.store.WatchEntry
import com.sohva.tv.feature.discover.store.WatchIdentity
import java.io.File
import kotlinx.coroutines.withContext
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Beta 23's Discover data, once (decision A1 option B, plan/04 §17): its three databases are read
 * without Room and its payloads decrypted with its own envelope, opened read only (no key created,
 * nothing written back); installations keep their ids, so progress, Library and catalog keys, which
 * hash those ids the same way, stay valid. The rows are re-encrypted with the main cipher. A row
 * that cannot be read is skipped; an unreadable key imports nothing. The old files are left in place.
 */
class Beta23DiscoverImport(private val graph: AppGraph) {
    private val app: Context get() = graph.app

    /** Counts of what came across, for the diagnostics log (never titles or URLs). */
    private data class Counts(var installations: Int = 0, var progress: Int = 0, var library: Int = 0)

    suspend fun run() = withContext(graph.dispatchers.io) {
        val meta = graph.data.appMeta
        if (meta.value(MARKER) != null) return@withContext
        val files = DATABASES.map(app::getDatabasePath).filter(File::exists)
        val prefs = File(app.applicationInfo.dataDir, "shared_prefs/$UI_PREFS.xml")
        // Discover is built only when there is something to import (spec 50 §9 "Start-up").
        val host = if (files.isEmpty() && !prefs.exists()) null else graph.discover
        if (host == null) {
            meta.put(AppMetaEntity(MARKER, "nothing"))
            return@withContext
        }
        val marker = try {
            val cipher = Beta23SourceImport.readOnlyCipher(app, EnvelopeSpec.BETA23_DISCOVER)
            probe(cipher)
            val counts = Counts()
            val profiles = installations(host, cipher, counts)
            progress(host, cipher, counts)
            library(host, cipher, counts)
            if (prefs.exists()) preferences(host, profiles)
            graph.diagnostics.info("discover", "beta 23 import: ${counts.installations} addons, ${counts.progress} progress, ${counts.library} library")
            "done:${counts.installations}/${counts.progress}/${counts.library}"
        } catch (e: Exception) {
            // Typically the old Keystore key is gone; nothing half-written stays readable as new data.
            graph.diagnostics.info("discover", "beta 23 import failed: ${e.javaClass.simpleName}")
            "failed"
        }
        meta.put(AppMetaEntity(MARKER, marker))
        if (marker.startsWith("done")) host.noteSetupChanged()
    }

    /** One payload decrypted outside any row guard: a lost key fails the whole import, not each row. */
    private fun probe(cipher: EnvelopeCipher) {
        val sample = listOf("sohva-addons.db" to "addon_installations", "sohva-addon-progress.db" to "addon_progress", "sohva-addon-library.db" to "addon_library")
            .firstNotNullOfOrNull { (file, table) ->
                open(file) { db -> db.rawQuery("SELECT encryptedPayload FROM $table LIMIT 1", null).use { c -> if (c.moveToFirst()) c.getString(0) else null } }
            } ?: return
        cipher.decrypt(sample)
    }

    private inline fun <T> open(name: String, block: (SQLiteDatabase) -> T): T? {
        val file = app.getDatabasePath(name).takeIf(File::exists) ?: return null
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(block)
    }

    /** Installations in their order; returns the profiles that had any. */
    private suspend fun installations(host: DiscoverHost, cipher: EnvelopeCipher, counts: Counts): Set<String> {
        val rows = open("sohva-addons.db") { db ->
            db.rawQuery("SELECT installationId, profileId, encryptedPayload, enabled, position FROM addon_installations ORDER BY profileId, position", null).use { c ->
                buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3).toString(), c.getInt(4).toString())) }
            }
        }.orEmpty()
        val profiles = HashSet<String>()
        for ((id, profile, payload, enabled, position) in rows) {
            val json = runCatching { JSONObject(cipher.decrypt(payload)) }.getOrNull() ?: continue
            val endpoint = runCatching { AddonEndpoint.parse(json.getString("url"), baseAllowed = true, allowHttp = true) }.getOrNull() ?: continue
            val manifest = runCatching { AddonManifest.parse(Buffer().writeUtf8(json.getString("manifest"))) }.getOrNull() ?: continue
            if (host.installations.restore(profile, id, endpoint, manifest, enabled == "1", position.toInt())) counts.installations++
            profiles += profile
        }
        return profiles
    }

    private suspend fun progress(host: DiscoverHost, cipher: EnvelopeCipher, counts: Counts) {
        val rows = open("sohva-addon-progress.db") { db ->
            db.rawQuery("SELECT profileId, encryptedPayload FROM addon_progress ORDER BY updatedAtMillis", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
        }.orEmpty()
        for ((profile, payload) in rows) {
            val j = runCatching { JSONObject(cipher.decrypt(payload)) }.getOrNull()?.takeIf { it.optLong("version") == 1L } ?: continue
            val entry = runCatching {
                val art = j.optJSONObject("artwork")
                WatchEntry(
                    WatchIdentity(j.getString("installation"), j.getString("mediaType"), j.getString("mediaId"), j.getString("videoType"), j.getString("videoId")),
                    j.getString("title"), j.getLong("position"), j.optLong("duration").takeIf { j.has("duration") && it > 0 }, j.getLong("updated"),
                    j.optBoolean("completed"),
                    Artwork(art?.optString("name").orEmpty(), art?.text("poster"), art?.text("background")),
                )
            }.getOrNull() ?: continue
            host.progress.restore(profile, entry)
            counts.progress++
        }
    }

    private suspend fun library(host: DiscoverHost, cipher: EnvelopeCipher, counts: Counts) {
        val rows = open("sohva-addon-library.db") { db ->
            db.rawQuery("SELECT profileId, encryptedPayload FROM addon_library ORDER BY addedAtMillis", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
        }.orEmpty()
        for ((profile, payload) in rows) {
            val j = runCatching { JSONObject(cipher.decrypt(payload)) }.getOrNull()?.takeIf { it.optInt("version") == 1 } ?: continue
            val title = runCatching {
                LibraryTitle(j.getString("installation"), j.getString("type"), j.getString("id"), j.getString("name"), j.text("poster"), j.text("background"), j.text("year"), j.getLong("added"))
            }.getOrNull()?.takeIf { it.type == "movie" || it.type == "series" } ?: continue
            if (host.library.restore(profile, title)) counts.library++
        }
    }

    /** `sohva_addon_ui`: each profile's catalog order and hidden set, and "Show all languages". */
    private suspend fun preferences(host: DiscoverHost, profiles: Set<String>) {
        val prefs = app.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        for (profile in profiles) {
            val order = keys(prefs.getString("catalog_order_" + Hashes.parts("catalog-order", profile), null))
            val hidden = keys(prefs.getString("catalog_hidden_" + Hashes.parts("catalog-visibility", profile), null)).toSet()
            if (order.isEmpty() && hidden.isEmpty()) continue
            host.catalogs.restore(profile, host.installations.list(profile), order, hidden)
        }
        if (prefs.getBoolean("all_subtitle_languages", false)) host.settings.setAllLanguages(true)
    }

    private fun keys(json: String?): List<String> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun JSONObject.text(name: String): String? = if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotEmpty() } else null

    companion object {
        const val MARKER: String = "import.beta23.discover"
        const val UI_PREFS: String = "sohva_addon_ui"
        val DATABASES: List<String> = listOf("sohva-addons.db", "sohva-addon-progress.db", "sohva-addon-library.db")
    }
}
