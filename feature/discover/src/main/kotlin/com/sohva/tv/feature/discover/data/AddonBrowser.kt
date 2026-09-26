package com.sohva.tv.feature.discover.data

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.cache.CachedPreview
import com.sohva.tv.feature.discover.cache.ResponseCache
import com.sohva.tv.feature.discover.net.AddonClient
import com.sohva.tv.feature.discover.net.CacheHints
import com.sohva.tv.feature.discover.protocol.AddonCatalog
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonMediaParser
import com.sohva.tv.feature.discover.protocol.CatalogPage
import com.sohva.tv.feature.discover.protocol.MetaDetails
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.InstallationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer

/** An answer, and whether it is the saved copy standing in for an unavailable provider. */
data class Fetched<T>(val value: T, val stale: Boolean)

/**
 * Catalogs and details through the response cache (spec 50 §4.9, §4.10, §4.18). Every request is
 * bound to the profile's access and the installation's revision, checked again after the network
 * and after cache I/O, so a late answer is never shown or cached for a profile or configuration
 * that changed (ADDON-FR-03, -04). Streams and subtitles never pass through here (never cached).
 */
class AddonBrowser(
    private val access: DiscoverAccess,
    private val store: InstallationStore,
    private val client: AddonClient,
    private val cache: ResponseCache,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    /**
     * A catalog page (FR-16): the manifest must declare it and its extras (FR-14, -15); [itemLimit]
     * models are built (20 shelves, 100 Search, 1,000 grids). A landing shelf also saves its preview.
     */
    suspend fun catalog(
        profile: String,
        installation: Installation,
        catalog: AddonCatalog,
        extras: Map<String, String>,
        itemLimit: Int,
        refresh: Boolean = false,
    ): Fetched<CatalogPage> {
        if (!installation.manifest.supports("catalog", catalog.type, catalog.id)) throw AddonException(AddonFailure.UNSUPPORTED_RESOURCE)
        installation.manifest.checkExtras(catalog, extras)
        val shelf = extras.isEmpty() && itemLimit <= SHELF_ITEMS
        return fetch(profile, installation, "catalog", catalog.type, catalog.id, extras, refresh, { AddonMediaParser.catalog(it, itemLimit) }) { key, page, hints ->
            if (shelf) cache.writePreview(key, CatalogPage(page.items.take(SHELF_ITEMS), page.receivedCount), hints)
        }
    }

    /**
     * FR-60 step 1: a shelf's saved titles without network, usable only when stored within a day,
     * not in the future, and fresh or allowed stale.
     */
    suspend fun shelfPreview(profile: String, installation: Installation, catalog: AddonCatalog): CachedPreview? = withContext(io) {
        current(profile, installation)
        val key = ResponseCache.key(profile, installation.id, installation.revision, "catalog", catalog.type, catalog.id, emptyMap())
        val preview = cache.readPreview(key) ?: return@withContext null
        val age = clock.wallMillis() - preview.storedAt
        current(profile, installation)
        preview.takeIf { age in 0..DAY_MS && (it.fresh || it.staleAllowed) }
    }

    /** One addon's details for (type, id) (FR-18); a different type is rejected (FR-24). */
    suspend fun meta(profile: String, installation: Installation, type: String, id: String, refresh: Boolean = false): Fetched<MetaDetails> {
        if (!installation.manifest.supports("meta", type, id)) throw AddonException(AddonFailure.UNSUPPORTED_RESOURCE)
        val fetched = fetch(profile, installation, "meta", type, id, emptyMap(), refresh, AddonMediaParser::meta, null)
        if (fetched.value.preview.type != type) throw AddonException(AddonFailure.INVALID_RESPONSE)
        return fetched
    }

    /**
     * The details route (FR-72): enabled installations serving `meta` for the title, the owner
     * first then by priority, at most three; 15 s each, 20 s in all. Revocations stop the route;
     * other failures try the next; the last failure is reported.
     */
    suspend fun details(profile: String, owner: String?, type: String, id: String, refresh: Boolean = false): Fetched<MetaDetails> {
        val candidates = candidates(profile, owner, type, id, MAX_CANDIDATES)
        if (candidates.isEmpty()) throw AddonException(AddonFailure.UNSUPPORTED_RESOURCE)
        var last: AddonException? = null
        try {
            withTimeout(ROUTE_MS) {
                for (inst in candidates) {
                    try {
                        return@withTimeout withTimeout(CANDIDATE_MS) { meta(profile, inst, type, id, refresh) }
                    } catch (e: TimeoutCancellationException) {
                        last = AddonException(AddonFailure.TIMEOUT)
                    } catch (e: AddonException) {
                        if (e.failure.revocation) throw e
                        last = e
                    }
                }
                null
            }?.let { return it }
        } catch (e: TimeoutCancellationException) {
            throw AddonException(AddonFailure.TIMEOUT)
        }
        throw last ?: AddonException(AddonFailure.UNSUPPORTED_RESOURCE)
    }

    /**
     * FR-73: saved details only (no network): the owner first, up to three candidates (one when
     * [freshOnly]); fresh data for the landing hero, stale-allowed data for history and Home.
     */
    suspend fun cachedDetails(profile: String, owner: String?, type: String, id: String, freshOnly: Boolean): MetaDetails? = withContext(io) {
        for (inst in candidates(profile, owner, type, id, if (freshOnly) 1 else MAX_CANDIDATES)) {
            val key = ResponseCache.key(profile, inst.id, inst.revision, "meta", type, id, emptyMap())
            val saved = cache.read(key) ?: continue
            if (!(saved.fresh || (!freshOnly && saved.staleAllowed))) continue
            val details = runCatching { AddonMediaParser.meta(saved.body) }.getOrNull() ?: continue
            current(profile, inst)
            if (details.preview.type == type) return@withContext details
        }
        null
    }

    private suspend fun candidates(profile: String, owner: String?, type: String, id: String, max: Int): List<Installation> {
        if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
        val all = store.list(profile).filter { it.enabled && it.manifest.supports("meta", type, id) }
        return (all.filter { it.id == owner } + all.filter { it.id != owner }).take(max)
    }

    private suspend fun <T> fetch(
        profile: String,
        installation: Installation,
        resource: String,
        type: String,
        id: String,
        extras: Map<String, String>,
        refresh: Boolean,
        parse: (Buffer) -> T,
        also: ((String, T, CacheHints) -> Unit)?,
    ): Fetched<T> = withContext(io) {
        current(profile, installation)
        val key = ResponseCache.key(profile, installation.id, installation.revision, resource, type, id, extras)
        var saved = cache.read(key)
        current(profile, installation)
        if (saved != null && saved.fresh && !refresh) {
            try {
                return@withContext Fetched(parse(saved.body), stale = false)
            } catch (e: AddonException) {
                // A saved body that no longer parses is dropped and fetched again (FR-123).
                cache.delete(key)
                saved = null
            }
        }
        val url = installation.endpoint.resource(resource, type, id, extras)
        val answer = try {
            client.get(url)
        } catch (e: AddonException) {
            current(profile, installation)
            val fallback = saved
            if (fallback != null && fallback.staleAllowed && e.failure.temporary(e.status)) {
                val value = runCatching { parse(fallback.body) }.getOrNull()
                if (value != null) return@withContext Fetched(value, stale = true)
            }
            // 401, 403, 404, 410…: an expired configuration is not disguised as offline (FR-123).
            if (e.failure == AddonFailure.HTTP_ERROR && !e.failure.temporary(e.status)) cache.delete(key)
            throw e
        }
        val value = parse(answer.body)
        current(profile, installation)
        cache.write(key, answer.body, answer.hints)
        also?.invoke(key, value, answer.hints)
        // A revocation during the cache write still wins (lesson 3).
        current(profile, installation)
        Fetched(value, stale = false)
    }

    /** The profile may still use Discover and the installation is unchanged and enabled (FR-03, -04). */
    private suspend fun current(profile: String, installation: Installation) {
        if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
        val now = store.find(profile, installation.id) ?: throw AddonException(AddonFailure.NOT_FOUND)
        if (now.revision != installation.revision || !now.enabled) throw AddonException(AddonFailure.CONFLICT)
    }

    companion object {
        const val SHELF_ITEMS: Int = 20
        const val SEARCH_ITEMS: Int = 100
        const val GRID_ITEMS: Int = 1_000
        private const val MAX_CANDIDATES = 3
        private const val CANDIDATE_MS = 15_000L
        private const val ROUTE_MS = 20_000L
        private const val DAY_MS = 24 * 60 * 60_000L
    }
}
