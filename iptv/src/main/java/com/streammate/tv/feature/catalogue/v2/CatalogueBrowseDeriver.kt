package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.feature.catalogue.CatalogueCopyDerivationCache
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.feature.catalogue.catalogueCopyScore
import com.streammate.tv.feature.catalogue.catalogueFilms
import com.streammate.tv.feature.catalogue.catalogueQualityTags
import com.streammate.tv.iptv.metadata.CatalogueMetadataOverride
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Off-main, cancellable copy folding for the V2 movie wall. */
class CatalogueBrowseDeriver(
    private val preferredCopy: CataloguePreferredCopy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val cache = CatalogueCopyDerivationCache()

    suspend fun derive(
        request: CatalogueBrowseRequest,
        entries: List<CatalogueBrowseEntry>,
    ): CatalogueDerivedEntries = withContext(dispatcher) {
        if (request.mode == CatalogueMode.SERIES || entries.size < 2) {
            return@withContext CatalogueDerivedEntries(entries)
        }

        val externalIds = buildMap(entries.size) {
            entries.forEach { entry ->
                entry.metadataOverride
                    ?.externalId
                    ?.takeIf(String::isNotBlank)
                    ?.let { put(entry.contentKey, it) }
            }
        }
        val derivationContext = coroutineContext
        val films = catalogueFilms(
            copies = entries,
            externalIds = externalIds,
            workKey = { entry ->
                derivationContext.ensureActive()
                cache.workKey(entry, externalIds[entry.contentKey])
            },
            rank = { entry ->
                derivationContext.ensureActive()
                catalogueCopyScore(cache.claims(entry), preferredCopy)
            },
        ) { primary, copies -> primary.filledInFrom(copies) }
        CatalogueDerivedEntries(
            entries = films.films,
            primaryContentKeyByCopy = films.filmKeyByCopy,
        )
    }
}

private fun CatalogueBrowseEntry.filledInFrom(copies: List<CatalogueBrowseEntry>) = copy(
    providerPosterUrl = providerPosterUrl?.takeIf(String::isNotBlank)
        ?: copies.firstNotNullOfOrNull { it.providerPosterUrl?.takeIf(String::isNotBlank) },
    year = year ?: copies.firstNotNullOfOrNull(CatalogueBrowseEntry::year),
    rating = rating?.takeIf(String::isNotBlank)
        ?: copies.firstNotNullOfOrNull { it.rating?.takeIf(String::isNotBlank) },
    genres = copies.flatMapTo(linkedSetOf()) { it.genres },
    metadataOverride = copies.mapNotNull(CatalogueBrowseEntry::metadataOverride)
        .reduceOrNull(::preferredOverride),
    copyQualityTags = copies.flatMap { catalogueQualityTags(it.providerTitle) }.distinct(),
    copyCount = copies.size,
)

private fun preferredOverride(
    first: CatalogueMetadataOverride,
    second: CatalogueMetadataOverride,
): CatalogueMetadataOverride = when {
    second.genresVersion > first.genresVersion -> second
    first.replacementPosterUrl.isNullOrBlank() && !second.replacementPosterUrl.isNullOrBlank() -> second
    else -> first
}
