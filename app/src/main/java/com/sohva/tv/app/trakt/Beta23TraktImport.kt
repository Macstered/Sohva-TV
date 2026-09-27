package com.sohva.tv.app.trakt

import android.content.Context
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.store.TraktAccount
import com.sohva.tv.feature.trakt.store.TraktAccountStore
import java.io.File
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Beta 23's Trakt accounts, once (decision A1 option B, plan/04 §17): `trakt_accounts` read with
 * beta 23's main envelope opened read only, and each profile's account, scrobble queue, in-flight
 * start, activity stamp and Home lists re-encrypted into the rebuild's store. Profile ids are the
 * same (they stayed in DataStore). Beta 23 never saved the account's id, so it is left empty until
 * the next sign-in. The history cache is not carried over: the first sync after the import walks
 * every list (the sync format was never stored). The old file stays until the last importer.
 */
class Beta23TraktImport(private val graph: AppGraph) {
    private val app: Context get() = graph.app

    suspend fun run() = withContext(graph.dispatchers.io) {
        val meta = graph.data.appMeta
        if (meta.value(MARKER) != null) return@withContext
        // Only a file that exists is opened: getSharedPreferences would create an empty one.
        if (!File(app.applicationInfo.dataDir, "shared_prefs/$OLD_FILE.xml").exists()) {
            meta.put(AppMetaEntity(MARKER, "nothing"))
            return@withContext
        }
        val host = graph.trakt ?: return@withContext meta.put(AppMetaEntity(MARKER, "nothing"))
        val marker = try {
            val cipher = Beta23SourceImport.readOnlyCipher(app)
            val profiles = copy(host.store, cipher)
            graph.diagnostics.info("trakt", "beta 23 import: ${profiles.size} accounts")
            host.accountsImported(profiles)
            "done:${profiles.size}"
        } catch (e: Exception) {
            // Typically the old Keystore key is gone: nothing is imported and nothing is half-written.
            graph.diagnostics.info("trakt", "beta 23 import failed: ${e.javaClass.simpleName}")
            "failed"
        }
        meta.put(AppMetaEntity(MARKER, marker))
    }

    private fun copy(store: TraktAccountStore, cipher: EnvelopeCipher): List<String> {
        val old = app.getSharedPreferences(OLD_FILE, Context.MODE_PRIVATE)
        val all = old.all
        val profiles = all.keys.filter { it.startsWith("account:") }.map { it.removePrefix("account:") }
        // Read everything first: a key that cannot open the first record imports nothing.
        val accounts = profiles.associateWith { p -> JSONObject(cipher.decrypt(all["account:$p"] as String)) }
        for ((profile, json) in accounts) {
            val tokens = json.optString("access").takeIf { it.isNotEmpty() }?.let { access ->
                TraktTokens(access, json.getString("refresh"), json.getLong("expires"), TraktAccountStore.DEFAULT_LIFETIME)
            }
            store.saveAccount(profile, TraktAccount(json.getString("username"), uuid = "", tokens = tokens, reauthorize = json.optBoolean("reauthorize")))
            secret(all, cipher, "pending:$profile")?.let { store.putSecret("pending:$profile", pending(JSONArray(it)).toString()) }
            secret(all, cipher, "active:$profile")?.let { store.putSecret("active:$profile", entry(JSONObject(it)).toString()) }
            (all["activity:$profile"] as? Long)?.takeIf { it > 0 }?.let { store.putLong("activity:$profile", it) }
            for (list in LISTS) secret(all, cipher, "$list:$profile")?.let { store.putSecret("$list:$profile", titles(JSONObject(it)).toString()) }
        }
        return profiles
    }

    /** One record, or null when it is missing or cannot be read (a single damaged record is skipped). */
    private fun secret(all: Map<String, *>, cipher: EnvelopeCipher, key: String): String? =
        (all[key] as? String)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    /** Beta 23 wrote actions as enum names (`START`); the rebuild writes Trakt's paths (`start`). */
    private fun entry(o: JSONObject): JSONObject = o.put("action", o.optString("action").lowercase())

    private fun pending(a: JSONArray): JSONArray = JSONArray().also { out -> for (i in 0 until a.length()) out.put(entry(a.getJSONObject(i))) }

    /** Beta 23 kept a card's ids beside its other fields; the rebuild keeps them under `ids`. */
    private fun titles(o: JSONObject): JSONObject {
        val items = o.optJSONArray("items") ?: JSONArray()
        val out = JSONArray()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val ids = JSONObject()
            for (name in ID_FIELDS) if (item.has(name)) ids.put(name, item.remove(name))
            out.put(item.put("ids", ids))
        }
        return JSONObject().put("at", o.optLong("at")).put("items", out)
    }

    companion object {
        const val MARKER: String = "import.beta23.trakt"
        const val OLD_FILE: String = "trakt_accounts"
        private val LISTS = listOf("recommendations", "nextup")
        private val ID_FIELDS = listOf("trakt", "tmdb", "imdb", "tvdb")
    }
}
