package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Catch-up address templates are stored sealed (spec 73 SEC-01): a template usually carries the
 * same user name and password as the stream address beside it. Imports seal the rows they write;
 * this pass seals the rows an earlier build wrote plain, once, in pages by id, and remembers in
 * `app_meta` that it is done. The column keeps its name: renaming one needs SQLite 3.25, which
 * Android 6 to 9 do not have, and a migration cannot reach the Keystore.
 */
class TemplateSealing(
    private val db: SohvaDatabase,
    private val seal: (String) -> String,
    private val bulkWrite: CoroutineDispatcher,
    /** Called between pages, so the pass gives way to playback like any bulk work. */
    private val pause: suspend () -> Unit,
) {
    /** Returns how many templates were sealed; 0 once the pass has completed on this install. */
    suspend fun run(): Int = withContext(bulkWrite) {
        val meta = db.appMeta()
        if (meta.value(MARKER) != null) return@withContext 0
        val dao = db.live()
        var after = 0L
        var sealed = 0
        while (true) {
            val page = dao.plainTemplates(after, PAGE)
            if (page.isEmpty()) break
            val rows = page.map { it.id to seal(it.template) }
            db.runInTransaction { for ((id, value) in rows) dao.setTemplate(id, value) }
            sealed += page.size
            after = page.last().id
            pause()
        }
        meta.put(AppMetaEntity(MARKER, sealed.toString()))
        sealed
    }

    companion object {
        const val MARKER: String = "catchup_templates_sealed"
        const val PAGE: Int = 500

        /** The cipher's value format; no template starts like this (it is an address or a query string). */
        const val SEALED_PREFIX: String = "v2:"

        /**
         * The template to build an archive address from: opened when sealed, as it is when an
         * earlier build stored it plain; null when there is none or it can no longer be opened.
         */
        fun open(stored: String?, decrypt: (String) -> String): String? = when {
            stored.isNullOrBlank() -> null
            stored.startsWith(SEALED_PREFIX) -> runCatching { decrypt(stored) }.getOrNull()?.takeIf { it.isNotBlank() }
            else -> stored
        }
    }
}
