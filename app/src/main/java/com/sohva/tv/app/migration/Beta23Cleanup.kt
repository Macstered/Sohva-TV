package com.sohva.tv.app.migration

import android.content.Context
import androidx.core.content.edit
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.discover.Beta23DiscoverImport
import com.sohva.tv.app.trakt.Beta23TraktImport
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.migration.Beta23Database
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.security.EnvelopeSpec
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.withContext

/**
 * Beta 23's own files go once everything in them has come across (plan/04 §17, decision "Beta 23
 * files after the import"): its main and Discover databases, its settings file, its secure stores,
 * Trakt records, Discover UI state, and its two Keystore keys. Only when every importer finished
 * with "done" or "nothing"; after any failure all of them stay, so a later build can try again.
 * Files the rebuild keeps using under beta 23's names (locale, artwork cache, update memory) and
 * the shared `channel-logos` folder are never touched.
 */
class Beta23Cleanup(private val graph: AppGraph) {
    private val app: Context get() = graph.app

    suspend fun run() = withContext(graph.dispatchers.io) {
        val meta = graph.data.appMeta
        if (meta.value(MARKER) != null) return@withContext
        if (!anything()) {
            meta.put(AppMetaEntity(MARKER, "nothing"))
            return@withContext
        }
        val markers = listOf(Beta23SourceImport.MARKER, Beta23DiscoverImport.MARKER, Beta23TraktImport.MARKER, Beta23Upgrade.MARKER)
        val states = markers.map { meta.value(it) }
        // Every part has run, and none failed.
        if (states.any { it == null || it.startsWith("failed") }) return@withContext
        DATABASES.forEach { app.deleteDatabase(it) }
        Beta23Upgrade.oldPreferences(app).delete()
        PREFERENCES.forEach(::deletePreferences)
        runCatching {
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            listOf(EnvelopeSpec.BETA23_MAIN, EnvelopeSpec.BETA23_DISCOVER).forEach { keystore.deleteEntry(it.keystoreAlias) }
        }
        meta.put(AppMetaEntity(MARKER, "done"))
        graph.diagnostics.info("upgrade", "beta 23 files removed")
    }

    /**
     * `deleteSharedPreferences` is API 24; on 23 the file is emptied and then removed by hand. The
     * emptying is a synchronous commit on purpose: the file is deleted right after, off the main thread.
     */
    private fun deletePreferences(name: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            app.deleteSharedPreferences(name)
            return
        }
        val file = File(app.applicationInfo.dataDir, "shared_prefs/$name.xml")
        if (!file.exists()) return
        app.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        file.delete()
    }

    private fun anything(): Boolean =
        DATABASES.any { app.getDatabasePath(it).exists() } || Beta23Upgrade.oldPreferences(app).exists() ||
            PREFERENCES.any { File(app.applicationInfo.dataDir, "shared_prefs/$it.xml").exists() }

    companion object {
        const val MARKER: String = "import.beta23.cleanup"
        private val DATABASES = listOf(Beta23Database.FILE) + Beta23DiscoverImport.DATABASES
        private val PREFERENCES = listOf(
            Beta23SourceImport.FILE, Beta23SourceImport.LEGACY_FILE, Beta23TraktImport.OLD_FILE, Beta23DiscoverImport.UI_PREFS,
            EnvelopeSpec.BETA23_MAIN.prefsFile, EnvelopeSpec.BETA23_DISCOVER.prefsFile,
        )
    }
}
