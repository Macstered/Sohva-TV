package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AddonSearchHit(val installation: InstalledAddon, val media: AddonMedia) {
    // IDs are provider-owned. Deduplicate overlapping catalogs from the same
    // installation, never conflate unrelated providers or movie/series IDs.
    val key = addonCacheKey(listOf(installation.installationId, media.key.type, media.key.id))
    override fun toString() = "AddonSearchHit([redacted])"
}

class AddonSearchBatch(val catalogKey: String, val hits: List<AddonSearchHit> = emptyList(),
    val failure: AddonFailure? = null, val stale: Boolean = false) {
    override fun toString() = "AddonSearchBatch([redacted])"
}

object AddonSearch {
    const val MAX_QUERY_LENGTH = 256
    const val MAX_CATALOGS = 32
    const val MAX_ROW_ITEMS = 100
    fun catalogs(installations: List<InstalledAddon>, order: List<String>, hidden: Set<String>) =
        AddonCatalogOrdering.visible(installations, order, hidden).filter { entry ->
            entry.installation.manifest.supports("catalog", entry.catalog.type, entry.catalog.id) &&
                entry.catalog.extras.any { it.name == "search" } &&
                entry.catalog.extras.none { it.required && it.name !in setOf("search", "skip") }
        }

    fun merge(previous: List<AddonSearchHit>, incoming: List<AddonSearchHit>): List<AddonSearchHit> {
        val unique = (previous + incoming).distinctBy { it.key }
        return listOf("movie", "series").flatMap { type -> unique.filter { it.media.key.type == type }.take(MAX_ROW_ITEMS) }
    }
}

/** Explicit search only. Three requests at a time, capped fan-out/deadlines and
 * no metadata/source/subtitle prefetch. Query text never enters diagnostics. */
class AddonSearchRepository(
    private val access: AddonManagementAccess,
    private val load: suspend (String, AddonCatalogEntry, Map<String, String>) -> AddonBrowseResult<AddonCatalogPage>,
    private val requestTimeoutMillis: Long = 12_000,
    private val totalTimeoutMillis: Long = 30_000,
) {
    fun search(profile: String, catalogs: List<AddonCatalogEntry>, query: String): Flow<AddonSearchBatch> = channelFlow {
        val text = query.trim()
        if (text.isEmpty()) return@channelFlow
        if (text.length > AddonSearch.MAX_QUERY_LENGTH) throw AddonException(AddonFailure.INVALID_REQUEST)
        suspend fun authorize() { if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED) }
        authorize()
        val targets = catalogs.distinctBy { it.key }.take(AddonSearch.MAX_CATALOGS)
        val pending = ConcurrentHashMap.newKeySet<String>().apply { addAll(targets.map { it.key }) }
        val next = AtomicInteger()
        withTimeoutOrNull(totalTimeoutMillis) {
            coroutineScope { repeat(minOf(3, targets.size)) { launch {
                while (true) {
                    val entry = targets.getOrNull(next.getAndIncrement()) ?: break
                    authorize()
                    val batch = try {
                        val extras = buildMap {
                            put("search", text)
                            if (entry.catalog.extras.any { it.name == "skip" }) put("skip", "0")
                        }
                        entry.catalog.validateExtras(extras)
                        val result = withTimeoutOrNull(requestTimeoutMillis) { load(profile, entry, extras) }
                        if (result == null) AddonSearchBatch(entry.key, failure = AddonFailure.TIMEOUT)
                        else AddonSearchBatch(entry.key, result.value.items.filter { it.key.type in setOf("movie", "series") }
                            .map { AddonSearchHit(entry.installation, it) }, stale = result.stale)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: AddonException) {
                        if (error.failure == AddonFailure.ACCESS_DENIED) throw error
                        AddonSearchBatch(entry.key, failure = error.failure)
                    }
                    authorize()
                    send(batch)
                    pending.remove(entry.key)
                }
            } } }
        }
        authorize()
        targets.filter { it.key in pending }.forEach { send(AddonSearchBatch(it.key, failure = AddonFailure.TIMEOUT)) }
    }
}
