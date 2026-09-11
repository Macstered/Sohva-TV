package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Separate from ordering: only hidden catalog hashes, scoped to a hashed profile. */
interface AddonCatalogVisibilityPersistence {
    suspend fun read(profileKey: String): Set<String>
    suspend fun write(profileKey: String, hidden: Set<String>)
}

class AddonCatalogVisibilityRepository(
    private val store: AddonStore, private val persistence: AddonCatalogVisibilityPersistence,
    private val access: AddonManagementAccess,
) {
    private val mutex = Mutex()
    private val hash = Regex("[a-f0-9]{64}")
    suspend fun loadHidden(profile: String): Set<String> = storage {
        authorize(profile)
        val hidden = persistence.read(profileKey(profile)).filterTo(mutableSetOf()) { hash.matches(it) }
        authorize(profile)
        hidden
    }

    /** Update one catalog, preserving other choices and rejecting removed/stale identities. */
    suspend fun setVisible(profile: String, key: String, visible: Boolean): Set<String> = storage {
        mutex.withLock {
            authorize(profile)
            val current = AddonCatalogOrdering.ordered(store.list(profile), emptyList()).map { it.key }.toSet()
            if (!hash.matches(key) || key !in current) fail(AddonFailure.CONFLICT)
            val hidden = persistence.read(profileKey(profile)).intersect(current).toMutableSet()
            if (visible) hidden.remove(key) else hidden.add(key)
            authorize(profile)
            persistence.write(profileKey(profile), hidden.toSet())
            authorize(profile)
            hidden.toSet()
        }
    }

    private fun profileKey(profile: String) = addonCacheKey(listOf("catalog-visibility", profile))
    private suspend fun authorize(profile: String) {
        if (profile.isBlank() || profile.length > 256 || !access.allowed(profile)) fail(AddonFailure.ACCESS_DENIED)
    }
    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AddonException) { throw error }
        catch (_: Exception) { fail(AddonFailure.STORAGE) }
    }
}
