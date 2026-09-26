package com.sohva.tv.feature.discover.store

import com.sohva.tv.feature.discover.protocol.AddonCatalog
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.Hashes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A catalog in the viewer's order (ADDON-FR-51, -52): identity survives manifest refresh and priority changes. */
class CatalogEntry(val key: String, val installation: Installation, val catalog: AddonCatalog, val hidden: Boolean) {
    /** Browsable: its addon is enabled and the viewer has not hidden it (FR-54). */
    val visible: Boolean get() = installation.enabled && !hidden

    override fun toString(): String = "CatalogEntry($key)"

    companion object {
        fun key(installationId: String, type: String, id: String): String = Hashes.parts(installationId, type, id)
    }
}

/**
 * Per-profile catalog order and visibility (spec 50 §4.8), one row per catalog key in
 * `discover.db`. Order is independent of provider priority (lesson 20). Writes are serialised and
 * each is one transaction.
 */
class CatalogPrefs(private val dao: CatalogPrefDao) {
    private val lock = Mutex()

    /**
     * FR-52: every catalog of every installation in priority then manifest order, stably sorted by
     * the saved order; unsaved catalogs keep their natural order after the saved ones.
     */
    suspend fun ordered(profile: String, installations: List<Installation>): List<CatalogEntry> {
        val prefs = dao.of(profile).filter { HEX64.matches(it.catalogKey) }.associateBy { it.catalogKey }
        val natural = installations.flatMap { inst -> inst.manifest.catalogs.map { c -> inst to c } }
        val entries = natural.map { (inst, c) ->
            val key = CatalogEntry.key(inst.id, c.type, c.id)
            CatalogEntry(key, inst, c, prefs[key]?.hidden == true)
        }
        val position = entries.withIndex().associate { (i, e) -> e.key to (prefs[e.key]?.position?.let { it.toLong() } ?: (Int.MAX_VALUE.toLong() + i)) }
        return entries.sortedBy { position.getValue(it.key) }
    }

    /** FR-54: the enabled, shown catalogs in order. */
    suspend fun visible(profile: String, installations: List<Installation>): List<CatalogEntry> = ordered(profile, installations).filter { it.visible }

    /** FR-53: [keys] must be exactly the current catalogs, else CONFLICT; one write. */
    suspend fun saveOrder(profile: String, installations: List<Installation>, keys: List<String>) = lock.withLock {
        val current = ordered(profile, installations)
        if (keys.toSet() != current.map { it.key }.toSet() || keys.size != current.size) throw AddonException(AddonFailure.CONFLICT)
        val hidden = current.filter { it.hidden }.map { it.key }.toSet()
        dao.replace(profile, keys.mapIndexed { i, k -> CatalogPrefEntity(profile, k, i, k in hidden) })
    }

    /**
     * FR-54: one catalog shown or hidden, other choices kept, keys of catalogs that no longer exist
     * dropped, an unknown key refused (CONFLICT). Showing never enables its addon.
     */
    suspend fun setHidden(profile: String, installations: List<Installation>, key: String, hidden: Boolean) = lock.withLock {
        val current = ordered(profile, installations)
        if (current.none { it.key == key }) throw AddonException(AddonFailure.CONFLICT)
        val saved = dao.of(profile).associateBy { it.catalogKey }
        val rows = current.map { e ->
            CatalogPrefEntity(profile, e.key, saved[e.key]?.position, if (e.key == key) hidden else e.hidden)
        }.filter { it.position != null || it.hidden }
        dao.replace(profile, rows)
    }

    suspend fun forget(profile: String) = lock.withLock { dao.clear(profile) }

    private companion object {
        val HEX64 = Regex("[0-9a-f]{64}")
    }
}
