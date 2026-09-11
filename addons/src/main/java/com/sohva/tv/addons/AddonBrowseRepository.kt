package com.sohva.tv.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AddonBrowseResult<T>(val value: T, val stale: Boolean, val warning: AddonFailure? = null, val fromCache: Boolean = false) {
    override fun toString() = "AddonBrowseResult([redacted])"
}

/** No startup, background catalog scans or stream requests. Each call is user-driven. */
class AddonBrowseRepository(
    private val client: AddonClient,
    private val store: AddonStore,
    private val cache: AddonResponseCache,
    private val access: AddonManagementAccess = AddonManagementAccess { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun catalog(
        profileId: String, installationId: String, type: String, id: String,
        extras: Map<String, String> = emptyMap(), refresh: Boolean = false, itemLimit: Int = 1000,
    ): AddonBrowseResult<AddonCatalogPage> = request(profileId, installationId, "catalog", type, id, extras, refresh) { AddonMediaParser.catalog(it, itemLimit) }

    /** Immediate Home preview while revalidating. Never serves no-cache/must-revalidate
     * stale responses, future-dated data, or snapshots older than one day. No network. */
    suspend fun cachedCatalog(profileId: String, installationId: String, type: String, id: String): AddonBrowseResult<AddonCatalogPage>? = withContext(Dispatchers.IO) {
        val owner = installation(profileId, installationId)
        if (!owner.manifest.supports("catalog", type, id)) fail(AddonFailure.UNSUPPORTED_RESOURCE)
        owner.manifest.catalogs.first { it.type == type && it.id == id }.validateExtras(emptyMap())
        val key = addonCacheKey(listOf(profileId, installationId, owner.revision.toString(), "catalog", type, id))
        val saved = cache.get(key) ?: return@withContext null
        val now = clock()
        if (now < saved.storedAtMillis || now - saved.storedAtMillis > 86_400_000 || (!saved.fresh(now) && !saved.staleAllowed)) return@withContext null
        val page = try { AddonMediaParser.catalog(saved.body, 20) } catch (_: AddonException) { cache.remove(key); return@withContext null }
        if (installation(profileId, installationId).revision != owner.revision) fail(AddonFailure.CONFLICT)
        AddonBrowseResult(page, stale = !saved.fresh(now), fromCache = true)
    }

    suspend fun metadata(
        profileId: String, installationId: String, key: AddonMediaKey, refresh: Boolean = false,
    ): AddonBrowseResult<AddonMedia> = request(profileId, installationId, "meta", key.type, key.id, emptyMap(), refresh, AddonMediaParser::metadata)
    // AIOMetadata can return an IMDb canonical ID for a TMDB/TVDB request. Keep
    // that provider response intact, but key the cache and UI selection by the
    // ORIGINAL request. Never globally merge identities or redirect requests.

    /** Cache-only history/hero lookup. Focused heroes require fresh text; passive history
     * artwork can retain permitted stale data without triggering a metadata queue. */
    suspend fun cachedDetails(profileId: String, installationId: String, key: AddonMediaKey,
        freshOnly: Boolean = false): AddonMedia? = withContext(Dispatchers.IO) {
        val owner = installation(profileId, installationId)
        val candidates = store.list(profileId).filter { it.enabled && it.manifest.supports("meta", key.type, key.id) }
            // A hero must not prefer a lower-priority cached provider over the one
            // details would request. History's best-effort artwork lookup stays broader.
            .sortedBy { if (it.installationId == installationId) 0 else 1 }.take(if (freshOnly) 1 else 3)
        for (candidate in candidates) {
            val cached = cache.get(addonCacheKey(listOf(profileId, candidate.installationId, candidate.revision.toString(), "meta", key.type, key.id))) ?: continue
            if (!cached.fresh(clock()) && (freshOnly || !cached.staleAllowed)) continue
            val media = try { AddonMediaParser.metadata(cached.body) } catch (_: AddonException) { continue }
            if (media.key.type != key.type) continue
            if (installation(profileId, installationId).revision != owner.revision || installation(profileId, candidate.installationId).revision != candidate.revision) fail(AddonFailure.CONFLICT)
            return@withContext media
        }
        null
    }

    /** Title details or a settled foreground hero: owner first, then compatible metadata services.
     * Catalog-only services (e.g. custom lists) need not implement meta. Never invent IDs/types,
     * contact an uninstalled service, or let fallback bypass profile/installation revocation.
     */
    suspend fun details(profileId: String, installationId: String, key: AddonMediaKey,
        refresh: Boolean = false): AddonBrowseResult<AddonMedia> {
        val owner = installation(profileId, installationId)
        val candidates = store.list(profileId).filter { it.enabled && it.manifest.supports("meta", key.type, key.id) }
            .sortedBy { if (it.installationId == installationId) 0 else 1 }.take(3)
        var lastFailure = AddonFailure.UNSUPPORTED_RESOURCE
        val result = withTimeoutOrNull(20_000) {
            for (candidate in candidates) {
                try {
                    val resolved = withTimeoutOrNull(15_000) {
                        metadata(profileId, candidate.installationId, key, refresh)
                    } ?: run { lastFailure = AddonFailure.TIMEOUT; continue }
                    // Canonical IDs may differ, but a metadata response cannot turn a movie into a series.
                    if (resolved.value.key.type != key.type) { lastFailure = AddonFailure.INVALID_RESPONSE; continue }
                    return@withTimeoutOrNull resolved
                } catch (error: AddonException) {
                    if (error.failure in setOf(AddonFailure.ACCESS_DENIED, AddonFailure.CONFLICT, AddonFailure.NOT_FOUND)) throw error
                    lastFailure = error.failure
                }
            }
            null
        }
        if (installation(profileId, installationId).revision != owner.revision) fail(AddonFailure.CONFLICT)
        return result ?: fail(lastFailure)
    }

    private suspend fun installation(profileId: String, installationId: String): InstalledAddon {
        if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
        return store.list(profileId).firstOrNull { it.installationId == installationId && it.enabled } ?: fail(AddonFailure.NOT_FOUND)
    }
    private suspend fun <T> request(
        profileId: String, installationId: String, resource: String, type: String, id: String,
        extras: Map<String, String>, refresh: Boolean, parse: (String) -> T,
    ): AddonBrowseResult<T> = withContext(Dispatchers.IO) {
        val original = installation(profileId, installationId)
        if (!original.manifest.supports(resource, type, id)) fail(AddonFailure.UNSUPPORTED_RESOURCE)
        if (resource == "catalog") original.manifest.catalogs.first { it.id == id && it.type == type }.validateExtras(extras)
        val key = addonCacheKey(listOf(profileId, installationId, original.revision.toString(), resource, type, id) +
            extras.toSortedMap().flatMap { listOf(it.key, it.value) })
        val saved = cache.get(key)
        suspend fun checked(value: T, stale: Boolean, warning: AddonFailure? = null, fromCache: Boolean = false): AddonBrowseResult<T> {
            if (installation(profileId, installationId).revision != original.revision) fail(AddonFailure.CONFLICT)
            return AddonBrowseResult(value, stale, warning, fromCache)
        }
        if (!refresh && saved != null && saved.fresh(clock())) {
            try { return@withContext checked(parse(saved.body), false, fromCache = true) }
            catch (error: AddonException) {
                if (error.failure != AddonFailure.INVALID_RESPONSE && error.failure != AddonFailure.RESPONSE_TOO_LARGE) throw error
                cache.remove(key)
            }
        }
        try {
            val document = client.resource(original.endpoint, original.manifest, resource, type, id, extras)
            val value = parse(document.body)
            checked(value, false)
            if (document.noStore) cache.remove(key) else {
                val now = clock()
                val ttl = (document.maxAgeSeconds ?: 300).coerceIn(0, 86_400) * 1000L
                cache.put(key, CachedAddonResponse(document.body, now, now + ttl, document.staleAllowed))
            }
            // Cache I/O can suspend. Recheck after it, not just before writing a response.
            checked(value, false)
        } catch (error: AddonException) {
            val temporary = error.failure in setOf(AddonFailure.NETWORK, AddonFailure.TIMEOUT) ||
                (error.failure == AddonFailure.HTTP_ERROR &&
                    (error.httpStatus in setOf(408, 425, 429) || error.httpStatus in 500..599))
            // Expired credentials or removed resources are not offline outages. Invalidate
            // the old response so the next visit cannot serve it as a still-fresh cache hit.
            if (error.failure == AddonFailure.HTTP_ERROR && !temporary) cache.remove(key)
            if (saved == null || !saved.staleAllowed || !temporary) throw error
            checked(parse(saved.body), true, error.failure, fromCache = true)
        }
    }
}
