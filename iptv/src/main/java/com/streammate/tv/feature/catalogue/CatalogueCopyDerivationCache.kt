package com.streammate.tv.feature.catalogue

import com.streammate.tv.iptv.metadata.catalogueWorkKey
import java.util.LinkedHashMap

/**
 * Reuses the expensive, immutable conclusions drawn from a provider title.
 *
 * A category change gives Compose new card objects even when the same film was
 * already present in the previous category. Normalising every title again made
 * ICU regular expressions the dominant CPU cost of warm group switching on the
 * Shield. Content keys are stable across those queries, so retain the derived
 * identity and copy claims while their inputs remain unchanged.
 *
 * The cache is deliberately bounded. Entries retain provider title strings and
 * a few small derived values; twelve thousand covers the large real-world wall
 * used for profiling without turning an app-lifetime speed-up into an
 * unbounded second catalogue.
 */
internal class CatalogueCopyDerivationCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private data class Entry(
        val title: String,
        val year: Int?,
        val externalId: String?,
        val workKey: String?,
        val claims: CatalogueCopyClaims?,
    )

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    fun workKey(copy: CatalogueCopy, externalId: String?): String {
        val normalizedExternalId = externalId?.trim()?.takeIf(String::isNotBlank)
        synchronized(lock) {
            entries[copy.contentKey]
                ?.takeIf { entry ->
                    entry.title == copy.title &&
                        entry.year == copy.year &&
                        entry.externalId == normalizedExternalId
                }
                ?.workKey
                ?.let { return it }
        }

        val derived = catalogueWorkKey(copy.title, copy.year, normalizedExternalId)
        synchronized(lock) {
            val previous = entries[copy.contentKey]
            entries[copy.contentKey] = Entry(
                title = copy.title,
                year = copy.year,
                externalId = normalizedExternalId,
                workKey = derived,
                claims = previous?.claims?.takeIf { previous.title == copy.title },
            )
        }
        return derived
    }

    fun claims(copy: CatalogueCopy): CatalogueCopyClaims {
        synchronized(lock) {
            entries[copy.contentKey]
                ?.takeIf { it.title == copy.title }
                ?.claims
                ?.let { return it }
        }

        val derived = catalogueCopyClaims(copy.title)
        synchronized(lock) {
            val previous = entries[copy.contentKey]
            entries[copy.contentKey] = if (previous?.title == copy.title) {
                previous.copy(claims = derived)
            } else {
                Entry(
                    title = copy.title,
                    year = copy.year,
                    externalId = null,
                    workKey = null,
                    claims = derived,
                )
            }
        }
        return derived
    }

    internal fun entryCount(): Int = synchronized(lock) { entries.size }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 12_000
    }
}
