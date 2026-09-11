package com.sohva.tv.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Only hashed catalog identities are persisted; names, IDs and configured URLs are not. */
interface AddonCatalogOrderPersistence {
    suspend fun read(profileKey: String): List<String>
    suspend fun write(profileKey: String, catalogKeys: List<String>)
}

class AddonCatalogEntry(val installation: InstalledAddon, val catalog: AddonCatalog) {
    val key = addonCacheKey(listOf(installation.installationId, catalog.type, catalog.id))
    override fun toString() = "AddonCatalogEntry([redacted])"
}

object AddonCatalogOrdering {
    /** Visibility hides browsing catalogs, never disables their addon capabilities. */
    fun visible(installations: List<InstalledAddon>, order: List<String>, hidden: Set<String>): List<AddonCatalogEntry> =
        ordered(installations, order).filter { it.installation.enabled && it.key !in hidden }

    fun ordered(installations: List<InstalledAddon>, order: List<String>): List<AddonCatalogEntry> {
        val rank = order.withIndex().associate { it.value to it.index }
        return installations.flatMap { owner -> owner.manifest.catalogs.map { AddonCatalogEntry(owner, it) } }
            .sortedBy { rank[it.key] ?: Int.MAX_VALUE } // Stable: new catalogs append in provider order.
    }
}

class AddonCatalogOrderRepository(private val store: AddonStore, private val persistence: AddonCatalogOrderPersistence,
    private val access: AddonManagementAccess) {
    private val mutex = Mutex()
    suspend fun load(profile: String): List<String> = withContext(Dispatchers.IO) {
        authorize(profile)
        persistence.read(addonCacheKey(listOf("catalog-order", profile))).filter { it.matches(Regex("[a-f0-9]{64}")) }.distinct()
    }
    suspend fun save(profile: String, order: List<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            authorize(profile)
            val current = AddonCatalogOrdering.ordered(store.list(profile), emptyList()).map { it.key }.toSet()
            if (order.size != current.size || order.toSet() != current) throw AddonException(AddonFailure.CONFLICT)
            authorize(profile)
            persistence.write(addonCacheKey(listOf("catalog-order", profile)), order)
        }
    }
    private suspend fun authorize(profile: String) {
        if (profile.isBlank() || profile.length > 256 || !access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
    }
}
