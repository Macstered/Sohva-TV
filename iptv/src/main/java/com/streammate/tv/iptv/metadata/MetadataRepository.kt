package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.database.CatalogueGenreEntity
import com.streammate.tv.core.database.CatalogueMetadataOverrideEntity
import com.streammate.tv.core.database.CatalogueMetadataWorkEntity
import com.streammate.tv.core.database.MetadataCacheEntity
import com.streammate.tv.core.database.MetadataDao
import com.streammate.tv.core.security.SecretSettingsStore
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.security.MetadataSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

class MetadataRepository(
    private val dao: MetadataDao,
    private val settingsStore: SecretSettingsStore,
    httpClient: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class MemoryCacheEntry(
        val metadata: EnrichedMetadata,
        val expiresAtEpochMillis: Long,
    )

    private val tmdbProvider = TmdbMetadataProvider(httpClient)
    private val providers: List<MetadataProvider> = listOf(
        tmdbProvider,
        TvmazeMetadataProvider(httpClient),
    )
    private val memoryCacheLock = Any()
    private val memoryCache = object : LinkedHashMap<String, MemoryCacheEntry>(
        MAX_MEMORY_CACHE_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MemoryCacheEntry>?,
        ): Boolean = size > MAX_MEMORY_CACHE_ENTRIES
    }

    fun isEnabled(): Boolean {
        val settings = settingsStore.loadMetadataSettings()
        return providers.any { it.enabled(settings) }
    }

    fun cached(lookup: MetadataLookup): EnrichedMetadata? {
        val sanitized = sanitize(lookup) ?: return null
        return memoryCached(lookupKey(sanitized), clock())
    }

    /**
     * What every sorted title is, keyed by content key.
     *
     * The whole table at once rather than a query per genre: the rail counts
     * are taken over the titles on the page in front of the viewer, not over
     * the library as a whole, so the film page never offers a row that only
     * television is in. It is two short strings per genre per title, which is
     * small beside the catalogue it describes.
     */
    fun observeCatalogueGenres(
        mediaType: MetadataMediaType? = null,
    ): Flow<Map<String, Set<CatalogueGenre>>> =
        when (mediaType) {
            MetadataMediaType.MOVIE -> dao.observeMovieGenres()
            MetadataMediaType.SERIES -> dao.observeSeriesGenres()
            else -> dao.observeGenres()
        }.map { rows ->
            rows.groupBy(CatalogueGenreEntity::contentKey)
                .mapValues { (_, entries) ->
                    entries.mapNotNull { CatalogueGenre.fromWireValue(it.genre) }.toSet()
                }
        }

    fun observeCatalogueMetadataOverrides(
        mediaType: MetadataMediaType? = null,
    ): Flow<Map<String, CatalogueMetadataOverride>> =
        when (mediaType) {
            MetadataMediaType.MOVIE -> dao.observeMovieCatalogueMetadataOverrides()
            MetadataMediaType.SERIES -> dao.observeSeriesCatalogueMetadataOverrides()
            else -> dao.observeCatalogueMetadataOverrides()
        }.map { entries ->
            entries.associate { entry ->
                entry.contentKey to CatalogueMetadataOverride(
                    contentKey = entry.contentKey,
                    providerPosterUrl = entry.providerPosterUrl,
                    replacementPosterUrl = entry.replacementPosterUrl,
                    replaceProviderPoster = entry.replaceProviderPoster,
                    replacementTitle = entry.replacementTitle,
                    externalId = entry.externalId,
                    genresVersion = entry.genresVersion,
                )
            }
        }

    suspend fun catalogueMetadataWorkNeedsSync(
        targetGenresVersion: Int = CatalogueGenre.VERSION,
    ): Boolean = dao.catalogueMetadataWorkCount() == 0 ||
        dao.outdatedCatalogueMetadataWorkCount(targetGenresVersion) > 0

    /**
     * Reconciles the compact durable queue with the current active snapshots.
     * This is the one intentional full-catalogue pass, run after an import or
     * schema/vocabulary change rather than before every sixty-title batch.
     */
    suspend fun synchronizeCatalogueMetadataWork(
        candidates: List<CatalogueMetadataCandidate>,
        targetGenresVersion: Int = CatalogueGenre.VERSION,
    ): Int {
        val rows = synchronizedCatalogueMetadataWork(
            candidates = candidates,
            existing = dao.catalogueMetadataWork(),
            targetGenresVersion = targetGenresVersion,
            nowEpochMillis = clock(),
        )
        dao.replaceCatalogueMetadataWork(rows)
        return rows.count {
            it.state == CatalogueMetadataWorkEntity.STATE_PENDING ||
                it.state == CatalogueMetadataWorkEntity.STATE_RETRY
        }
    }

    suspend fun nextCatalogueMetadataWork(
        limit: Int,
        nowEpochMillis: Long = clock(),
    ): List<CatalogueMetadataWork> = dao.nextCatalogueMetadataWork(
        nowEpochMillis = nowEpochMillis,
        limit = limit.coerceIn(1, MAX_METADATA_WORK_PAGE_SIZE),
    ).mapNotNull(CatalogueMetadataWorkEntity::toDomain)

    /**
     * A catalogue lookup distinguishes a real miss from a transient provider
     * failure. The ordinary UI API intentionally returns null for both; the
     * durable worker needs the distinction so an outage does not permanently
     * mark thousands of titles as unmatched.
     */
    suspend fun enrichCatalogue(lookup: MetadataLookup): CatalogueEnrichmentResult {
        val sanitized = sanitize(lookup) ?: return CatalogueEnrichmentResult.NoMatch
        val key = lookupKey(sanitized)
        // A memory entry has no genre-version stamp. Force this background pass
        // through the durable cache, where stale vocabulary is detectable.
        synchronized(memoryCacheLock) { memoryCache.remove(key) }
        enrich(sanitized)?.let { return CatalogueEnrichmentResult.Matched(it) }

        val now = clock()
        val settings = settingsStore.loadMetadataSettings()
        val eligibleProviders = providers.filter {
            it.enabled(settings) && it.supports(sanitized.mediaType)
        }
        if (eligibleProviders.isEmpty()) return CatalogueEnrichmentResult.NoMatch
        val everyProviderHasFreshMiss = eligibleProviders.all { provider ->
            val cached = dao.cached(key, provider.id)
            cached?.status == CACHE_STATUS_NEGATIVE && cached.expiresAtEpochMillis > now
        }
        return if (everyProviderHasFreshMiss) {
            CatalogueEnrichmentResult.NoMatch
        } else {
            CatalogueEnrichmentResult.Retry
        }
    }

    /** Applies a whole worker page with one Room transaction. */
    suspend fun applyCatalogueEnrichmentBatch(updates: List<CatalogueEnrichmentUpdate>) {
        if (updates.isEmpty()) return
        val now = clock()
        val replacedGenreContentKeys = ArrayList<String>(updates.size)
        val genreRows = ArrayList<CatalogueGenreEntity>()
        val overrides = ArrayList<CatalogueMetadataOverrideEntity>(updates.size)
        val workRows = ArrayList<CatalogueMetadataWorkEntity>(updates.size)

        updates.forEach { update ->
            val work = update.work
            val contentKey = work.contentKey.trim().take(MAX_CONTENT_KEY_LENGTH)
            if (contentKey.isBlank()) return@forEach
            val providerPosterUrl = work.providerPosterUrl
                ?.trim()
                ?.take(MAX_PROVIDER_POSTER_URL_LENGTH)
                ?.takeIf(String::isNotBlank)
            when (val result = update.result) {
                is CatalogueEnrichmentResult.Matched -> {
                    val metadata = result.metadata
                    val replacementTitle = metadata.title.trim().take(MAX_TITLE_LENGTH)
                    if (replacementTitle.isBlank()) return@forEach
                    replacedGenreContentKeys += contentKey
                    metadata.genres.firstOrNull()?.let { genre ->
                        genreRows += CatalogueGenreEntity(contentKey, genre.wireValue)
                    }
                    val replacementPosterUrl = metadata.posterUrl.httpsUrlOrNull()
                    overrides += CatalogueMetadataOverrideEntity(
                        contentKey = contentKey,
                        providerPosterUrl = providerPosterUrl,
                        replacementPosterUrl = replacementPosterUrl,
                        replaceProviderPoster = providerPosterUrl == null && replacementPosterUrl != null,
                        replacementTitle = replacementTitle,
                        externalId = metadata.externalId.takeIf(String::isNotBlank),
                        genresVersion = CatalogueGenre.VERSION,
                        updatedAtEpochMillis = now,
                    )
                    workRows += work.toEntity(
                        state = CatalogueMetadataWorkEntity.STATE_COMPLETE,
                        attemptCount = 0,
                        nextAttemptAtEpochMillis = 0,
                        nowEpochMillis = now,
                    )
                }
                CatalogueEnrichmentResult.NoMatch -> {
                    val replacementTitle = work.title.trim().take(MAX_TITLE_LENGTH)
                    if (replacementTitle.isBlank()) return@forEach
                    replacedGenreContentKeys += contentKey
                    overrides += CatalogueMetadataOverrideEntity(
                        contentKey = contentKey,
                        providerPosterUrl = providerPosterUrl,
                        replacementPosterUrl = null,
                        replaceProviderPoster = false,
                        replacementTitle = replacementTitle,
                        externalId = null,
                        genresVersion = CatalogueGenre.VERSION,
                        updatedAtEpochMillis = now,
                    )
                    workRows += work.toEntity(
                        state = CatalogueMetadataWorkEntity.STATE_NO_MATCH,
                        attemptCount = 0,
                        nextAttemptAtEpochMillis = 0,
                        nowEpochMillis = now,
                    )
                }
                CatalogueEnrichmentResult.Retry -> {
                    val attempts = (work.attemptCount + 1).coerceAtMost(MAX_METADATA_ATTEMPTS)
                    workRows += work.toEntity(
                        state = CatalogueMetadataWorkEntity.STATE_RETRY,
                        attemptCount = attempts,
                        nextAttemptAtEpochMillis = now + catalogueMetadataRetryDelayMillis(attempts),
                        nowEpochMillis = now,
                    )
                }
            }
        }
        dao.applyCatalogueMetadataBatch(
            replacedGenreContentKeys = replacedGenreContentKeys,
            genreRows = genreRows,
            overrides = overrides,
            workRows = workRows,
        )
    }

    /**
     * Records what a content key turned out to be.
     *
     * The genres are written here rather than by the caller because this is the
     * moment a title's identity is settled - by the metadata pass today, and by
     * a match chosen by hand later. Anywhere that learns what something is
     * should leave the library able to group it.
     */
    suspend fun rememberCatalogueMetadataOverride(
        contentKey: String,
        providerPosterUrl: String?,
        metadata: EnrichedMetadata,
        replaceProviderPoster: Boolean = false,
    ) {
        val safeContentKey = contentKey.trim().take(MAX_CONTENT_KEY_LENGTH)
        if (safeContentKey.isBlank()) return
        val replacementTitle = metadata.title.trim().take(MAX_TITLE_LENGTH)
        if (replacementTitle.isBlank()) return
        val replacementPosterUrl = metadata.posterUrl.httpsUrlOrNull()
        val now = clock()
        dao.applyCatalogueMetadata(
            contentKey = safeContentKey,
            genreRows = metadata.genres.firstOrNull()?.let { genre ->
                listOf(CatalogueGenreEntity(contentKey = safeContentKey, genre = genre.wireValue))
            }.orEmpty(),
            override = CatalogueMetadataOverrideEntity(
                contentKey = safeContentKey,
                providerPosterUrl = providerPosterUrl
                    ?.trim()
                    ?.take(MAX_PROVIDER_POSTER_URL_LENGTH)
                    ?.takeIf(String::isNotBlank),
                replacementPosterUrl = replacementPosterUrl,
                replaceProviderPoster = replaceProviderPoster && replacementPosterUrl != null,
                replacementTitle = replacementTitle,
                externalId = metadata.externalId.takeIf(String::isNotBlank),
                genresVersion = CatalogueGenre.VERSION,
                updatedAtEpochMillis = now,
            ),
            genresVersion = CatalogueGenre.VERSION,
            nowEpochMillis = now,
        )
    }

    /**
     * Repairs older catalogue rows that already point at the right metadata
     * record but were persisted before its poster was available. Details have
     * the complete cached record, so this needs neither a new search nor a
     * library-wide pass. Complete rows are deliberately left untouched to
     * avoid invalidating an otherwise stable browse wall on every visit.
     */
    suspend fun backfillMissingCataloguePoster(
        contentKey: String,
        providerPosterUrl: String?,
        metadata: EnrichedMetadata,
    ) {
        val safeContentKey = contentKey.trim().take(MAX_CONTENT_KEY_LENGTH)
        if (safeContentKey.isBlank() || metadata.posterUrl.httpsUrlOrNull() == null) return
        val existing = dao.catalogueMetadataOverride(safeContentKey)
        if (!existing?.replacementPosterUrl.isNullOrBlank()) return
        rememberCatalogueMetadataOverride(
            contentKey = safeContentKey,
            providerPosterUrl = providerPosterUrl,
            metadata = metadata,
            replaceProviderPoster = providerPosterUrl.isNullOrBlank() ||
                existing?.replaceProviderPoster == true,
        )
    }

    /**
     * What the provider offers for a query somebody typed.
     *
     * Deliberately unscored and unfiltered. The matcher refuses anything it is
     * not sure of, which is the whole reason this exists: a person looking at
     * the posters and the years can settle in a moment what a confidence
     * threshold never will.
     */
    suspend fun searchCandidates(lookup: MetadataLookup): List<MetadataSearchResult> {
        val sanitized = lookup.copy(title = lookup.title.trim().take(MAX_TITLE_LENGTH))
        if (sanitized.title.isBlank()) return emptyList()
        val settings = settingsStore.loadMetadataSettings()
        providers.forEach { provider ->
            if (!provider.enabled(settings) || !provider.supports(sanitized.mediaType)) return@forEach
            val candidates = runCatching { provider.search(sanitized, settings) }
                .getOrDefault(emptyList())
            if (candidates.isNotEmpty()) {
                return candidates.map { candidate ->
                    MetadataSearchResult(
                        externalId = candidate.externalId,
                        mediaType = candidate.mediaType,
                        title = candidate.displayTitle,
                        year = candidate.year,
                        overview = candidate.overview,
                        posterUrl = candidate.posterUrl.httpsUrlOrNull(),
                    )
                }
            }
        }
        return emptyList()
    }

    /**
     * Settles what a title is, because somebody said so.
     *
     * Writes the record fetched by id over whatever was there - including the
     * "nothing matched" that sent them here - and marks it so that no later
     * search replaces it. The genres and the poster follow from the same call
     * every automatic match goes through, so the title leaves the unsorted row
     * and joins its own.
     */
    suspend fun pinMatch(
        contentKey: String,
        lookup: MetadataLookup,
        result: MetadataSearchResult,
        providerPosterUrl: String?,
    ): EnrichedMetadata? {
        val sanitized = sanitize(lookup) ?: return null
        val settings = settingsStore.loadMetadataSettings()
        val provider = providers.firstOrNull {
            it.enabled(settings) && it.supports(result.mediaType)
        } ?: return null
        val candidate = runCatching {
            tmdbProvider.detailsById(
                externalId = result.externalId,
                mediaType = result.mediaType,
                language = sanitized.language,
                credential = settings.tmdbReadAccessToken,
            )
        }.getOrNull() ?: return null
        val now = clock()
        val entry = positiveCacheEntry(
            lookupKey = lookupKey(sanitized),
            provider = provider,
            match = MetadataMatch(candidate, CHOSEN_BY_HAND_CONFIDENCE),
            now = now,
        ).copy(pinned = true)
        dao.upsert(entry)
        val metadata = entry.toDomain() ?: return null
        memoryPut(lookupKey(sanitized), metadata, now + provider.positiveCacheMillis)
        rememberCatalogueMetadataOverride(
            contentKey = contentKey,
            providerPosterUrl = providerPosterUrl,
            metadata = metadata,
            replaceProviderPoster = true,
        )
        return metadata
    }

    /** Hands a title back to the matcher, as though it had never been chosen. */
    suspend fun clearPinnedMatch(contentKey: String, lookup: MetadataLookup) {
        val sanitized = sanitize(lookup) ?: return
        val lookupKey = lookupKey(sanitized)
        dao.deleteCached(lookupKey)
        memoryCache.remove(lookupKey)
        dao.replaceGenres(contentKey, emptyList())
    }

    /** Whether this title's match was chosen rather than found. */
    suspend fun isMatchPinned(lookup: MetadataLookup): Boolean {
        val sanitized = sanitize(lookup) ?: return false
        val lookupKey = lookupKey(sanitized)
        return providers.any { dao.cached(lookupKey, it.id)?.pinned == true }
    }

    private suspend fun refreshPinnedGenres(
        entry: MetadataCacheEntity,
        lookup: MetadataLookup,
        settings: MetadataSettings,
    ): MetadataCacheEntity {
        val externalId = entry.externalId ?: return entry
        val mediaType = MetadataMediaType.entries.firstOrNull { it.wireValue == entry.mediaType }
            ?: return entry
        val candidate = runCatching {
            tmdbProvider.detailsById(externalId, mediaType, lookup.language, settings.tmdbReadAccessToken)
        }.getOrNull() ?: return entry
        val updated = entry.copy(
            genresJson = encodeGenres(candidate.genres),
            genresVersion = CatalogueGenre.VERSION,
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun enrich(lookup: MetadataLookup): EnrichedMetadata? {
        val sanitized = sanitize(lookup) ?: return null
        val now = clock()
        val lookupKey = lookupKey(sanitized)
        memoryCached(lookupKey, now)?.let { return it }
        val settings = settingsStore.loadMetadataSettings()
        dao.deleteExpired(now)
        providers.forEach { provider ->
            if (!provider.enabled(settings) || !provider.supports(sanitized.mediaType)) return@forEach
            // A match found before this vocabulary existed has no genres in
            // it, and answering from it would leave the title sorted nowhere
            // while looking finished. Those are searched again; a miss is a
            // miss whatever the genres, so negative entries stand.
            val stored = dao.cached(lookupKey, provider.id)
            // A match somebody chose answers straight away, however old it is.
            // Only its genres can go out of date, and those are refreshed from
            // the record they already point at rather than by searching again,
            // which could only disagree with the person who chose it.
            if (stored != null && stored.pinned) {
                val refreshed = if (stored.genresVersion < CatalogueGenre.VERSION) {
                    refreshPinnedGenres(stored, sanitized, settings)
                } else {
                    stored
                }
                val metadata = refreshed.toDomain() ?: return@forEach
                memoryPut(lookupKey, metadata, now + provider.positiveCacheMillis)
                return metadata
            }
            val cached = stored?.takeUnless {
                it.status == CACHE_STATUS_POSITIVE && it.genresVersion < CatalogueGenre.VERSION
            }
            if (cached != null && cached.expiresAtEpochMillis > now) {
                if (cached.status == CACHE_STATUS_POSITIVE) {
                    val metadata = cached.toDomain() ?: return@forEach
                    memoryPut(lookupKey, metadata, cached.expiresAtEpochMillis)
                    return metadata
                }
                return@forEach
            }
            val candidates = try {
                provider.search(sanitized, settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@forEach
            }
            val match = MetadataMatcher.choose(sanitized, candidates)
            if (match == null) {
                dao.upsert(negativeCacheEntry(lookupKey, provider, sanitized, now))
                return@forEach
            }
            val entry = positiveCacheEntry(lookupKey, provider, match, now)
            dao.upsert(entry)
            val metadata = entry.toDomain() ?: return@forEach
            memoryPut(lookupKey, metadata, entry.expiresAtEpochMillis)
            return metadata
        }
        return null
    }

    suspend fun enrichMovieDetails(lookup: MetadataLookup): EnrichedMetadata? {
        val sanitized = sanitize(lookup) ?: return null
        if (sanitized.mediaType != MetadataMediaType.MOVIE) return enrich(sanitized)
        val base = enrich(sanitized) ?: return null
        if (base.provider != tmdbProvider.id) return base

        val now = clock()
        val lookupKey = lookupKey(sanitized)
        val cached = dao.cached(lookupKey, tmdbProvider.id)
        if (cached?.status == CACHE_STATUS_POSITIVE &&
            cached.expiresAtEpochMillis > now &&
            cached.detailsLoaded
        ) {
            return cached.toDomain()?.also { memoryPut(lookupKey, it, cached.expiresAtEpochMillis) }
                ?: base
        }
        val settings = settingsStore.loadMetadataSettings()
        if (!tmdbProvider.enabled(settings)) return base
        val details = try {
            tmdbProvider.movieDetails(
                externalId = base.externalId,
                language = sanitized.language,
                credential = settings.tmdbReadAccessToken,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return base
        }
        val entry = cached?.takeIf { it.status == CACHE_STATUS_POSITIVE } ?: return base
        val updated = entry.copy(
            externalId = details.externalId,
            matchedTitle = details.matchingTitle,
            displayTitle = details.displayTitle,
            overview = details.overview ?: entry.overview,
            posterUrl = details.posterUrl.httpsUrlOrNull() ?: entry.posterUrl,
            backdropUrl = details.backdropUrl.httpsUrlOrNull() ?: entry.backdropUrl,
            year = details.year ?: entry.year,
            runtimeMinutes = details.runtimeMinutes ?: entry.runtimeMinutes,
            rating = details.rating ?: entry.rating,
            castJson = encodeCast(details.cast),
            genresJson = encodeGenres(details.genres).takeIf { it != null }
                ?: entry.genresJson,
            detailsLoaded = true,
            similarMoviesJson = encodeSimilarMovies(details.similarMovies),
            attributionUrl = details.attributionUrl.httpsUrlOrNull() ?: entry.attributionUrl,
        )
        dao.upsert(updated)
        return updated.toDomain()?.also { memoryPut(lookupKey, it, updated.expiresAtEpochMillis) }
            ?: base
    }

    private fun sanitize(lookup: MetadataLookup): MetadataLookup? {
        val sanitized = lookup.copy(
            title = MetadataMatcher.searchTitle(lookup.title).take(MAX_TITLE_LENGTH),
            year = (lookup.year ?: MetadataMatcher.yearFromTitle(lookup.title))
                ?.takeIf { it in MIN_YEAR..MAX_YEAR },
            seasonNumber = lookup.seasonNumber?.takeIf { it in 0..MAX_SEASON_NUMBER },
            episodeNumber = lookup.episodeNumber?.takeIf { it in 0..MAX_EPISODE_NUMBER },
            language = lookup.language.takeIf(LANGUAGE_PATTERN::matches) ?: DEFAULT_LANGUAGE,
        )
        return sanitized.takeIf {
            MetadataMatcher.normalizeTitle(it.title).length >= MIN_TITLE_LENGTH
        }
    }

    suspend fun clearCache() {
        synchronized(memoryCacheLock) { memoryCache.clear() }
        dao.clearAllMetadata()
    }

    suspend fun verifyTmdbCredential(credential: String) = tmdbProvider.verifyCredential(credential.trim())

    private fun memoryCached(lookupKey: String, now: Long): EnrichedMetadata? =
        synchronized(memoryCacheLock) {
            val entry = memoryCache[lookupKey] ?: return@synchronized null
            if (entry.expiresAtEpochMillis <= now) {
                memoryCache.remove(lookupKey)
                null
            } else {
                entry.metadata
            }
        }

    private fun memoryPut(
        lookupKey: String,
        metadata: EnrichedMetadata,
        expiresAtEpochMillis: Long,
    ) {
        synchronized(memoryCacheLock) {
            memoryCache[lookupKey] = MemoryCacheEntry(metadata, expiresAtEpochMillis)
        }
    }

    private fun positiveCacheEntry(
        lookupKey: String,
        provider: MetadataProvider,
        match: MetadataMatch,
        now: Long,
    ): MetadataCacheEntity {
        val candidate = match.candidate
        return MetadataCacheEntity(
            lookupKey = lookupKey,
            provider = provider.id,
            status = CACHE_STATUS_POSITIVE,
            externalId = candidate.externalId,
            mediaType = candidate.mediaType.wireValue,
            matchedTitle = candidate.matchingTitle,
            displayTitle = candidate.displayTitle,
            overview = candidate.overview,
            posterUrl = candidate.posterUrl.httpsUrlOrNull(),
            backdropUrl = candidate.backdropUrl.httpsUrlOrNull(),
            year = candidate.year,
            seasonNumber = candidate.seasonNumber,
            episodeNumber = candidate.episodeNumber,
            runtimeMinutes = candidate.runtimeMinutes,
            rating = candidate.rating,
            castJson = encodeCast(candidate.cast),
            genresJson = encodeGenres(candidate.genres),
            genresVersion = CatalogueGenre.VERSION,
            detailsLoaded = candidate.detailsLoaded,
            similarMoviesJson = encodeSimilarMovies(candidate.similarMovies),
            attributionName = provider.attributionName,
            attributionUrl = candidate.attributionUrl.httpsUrlOrNull() ?: providerHome(provider.id),
            confidence = match.confidence,
            cachedAtEpochMillis = now,
            expiresAtEpochMillis = now + provider.positiveCacheMillis,
        )
    }

    private fun negativeCacheEntry(
        lookupKey: String,
        provider: MetadataProvider,
        lookup: MetadataLookup,
        now: Long,
    ) = MetadataCacheEntity(
        lookupKey = lookupKey,
        provider = provider.id,
        status = CACHE_STATUS_NEGATIVE,
        externalId = null,
        mediaType = lookup.mediaType.wireValue,
        matchedTitle = null,
        displayTitle = null,
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        year = null,
        seasonNumber = lookup.seasonNumber,
        episodeNumber = lookup.episodeNumber,
        runtimeMinutes = null,
        rating = null,
        castJson = null,
        detailsLoaded = false,
        similarMoviesJson = null,
        attributionName = provider.attributionName,
        attributionUrl = providerHome(provider.id),
        confidence = 0.0,
        cachedAtEpochMillis = now,
        expiresAtEpochMillis = now + NEGATIVE_CACHE_MILLIS,
    )

    private fun MetadataCacheEntity.toDomain(): EnrichedMetadata? {
        val type = MetadataMediaType.entries.firstOrNull { it.wireValue == mediaType } ?: return null
        return EnrichedMetadata(
            provider = provider,
            externalId = externalId ?: return null,
            mediaType = type,
            title = displayTitle ?: matchedTitle ?: return null,
            overview = overview,
            posterUrl = posterUrl.httpsUrlOrNull(),
            backdropUrl = backdropUrl.httpsUrlOrNull(),
            year = year,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            runtimeMinutes = runtimeMinutes,
            rating = rating,
            cast = decodeCast(castJson),
            genres = decodeGenres(genresJson),
            detailsLoaded = detailsLoaded,
            similarMovies = decodeSimilarMovies(similarMoviesJson),
            attributionName = attributionName,
            attributionUrl = attributionUrl.httpsUrlOrNull() ?: providerHome(provider),
            confidence = confidence,
        )
    }

    private fun lookupKey(lookup: MetadataLookup): String {
        val canonical = listOf(
            LOOKUP_KEY_VERSION,
            lookup.mediaType.wireValue,
            MetadataMatcher.normalizeTitle(lookup.title),
            lookup.year?.toString().orEmpty(),
            lookup.seasonNumber?.toString().orEmpty(),
            lookup.episodeNumber?.toString().orEmpty(),
            lookup.language.lowercase(),
        ).joinToString("\u001F")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun encodeCast(cast: List<MetadataCastMember>): String? = cast
        .takeIf(List<MetadataCastMember>::isNotEmpty)
        ?.let { members ->
            JsonArray(
                members.map { member ->
                    buildJsonObject {
                        put("name", member.name)
                        member.character?.let { put("character", it) }
                        member.profileUrl?.let { put("profileUrl", it) }
                    }
                },
            ).toString()
        }

    /**
     * Stored by wire value rather than by the provider's own id, so the rows
     * stay readable whichever provider found the title and survive a provider
     * renumbering its genres.
     */
    private fun encodeGenres(genres: List<CatalogueGenre>): String? = genres
        .takeIf(List<CatalogueGenre>::isNotEmpty)
        ?.let { list -> JsonArray(list.map { JsonPrimitive(it.wireValue) }).toString() }

    private fun decodeGenres(value: String?): List<CatalogueGenre> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            CACHE_JSON.parseToJsonElement(value).jsonArray.mapNotNull { element ->
                CatalogueGenre.fromWireValue((element as? JsonPrimitive)?.content)
            }.take(1)
        }.getOrDefault(emptyList())
    }

    private fun decodeCast(value: String?): List<MetadataCastMember> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            CACHE_JSON.parseToJsonElement(value).jsonArray.mapNotNull { element ->
                val member = element as? JsonObject ?: return@mapNotNull null
                val name = member.string("name") ?: return@mapNotNull null
                MetadataCastMember(
                    name = name,
                    character = member.string("character"),
                    profileUrl = member.string("profileUrl")?.httpsUrlOrNull(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSimilarMovies(movies: List<MetadataMovieReference>): String? = movies
        .takeIf(List<MetadataMovieReference>::isNotEmpty)
        ?.let { references ->
            JsonArray(
                references.map { reference ->
                    buildJsonObject {
                        put("externalId", reference.externalId)
                        put("title", reference.title)
                        put(
                            "alternativeTitles",
                            JsonArray(reference.alternativeTitles.map(::JsonPrimitive)),
                        )
                        reference.year?.let { put("year", it) }
                        reference.posterUrl?.let { put("posterUrl", it) }
                    }
                },
            ).toString()
        }

    private fun decodeSimilarMovies(value: String?): List<MetadataMovieReference> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            CACHE_JSON.parseToJsonElement(value).jsonArray.mapNotNull { element ->
                val movie = element as? JsonObject ?: return@mapNotNull null
                val externalId = movie.string("externalId") ?: return@mapNotNull null
                val title = movie.string("title") ?: return@mapNotNull null
                MetadataMovieReference(
                    externalId = externalId,
                    title = title,
                    alternativeTitles = (movie["alternativeTitles"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.content },
                    year = movie.string("year")?.toIntOrNull(),
                    posterUrl = movie.string("posterUrl")?.httpsUrlOrNull(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun providerHome(provider: String): String = when (provider) {
        "tmdb" -> "https://www.themoviedb.org"
        "tvmaze" -> "https://www.tvmaze.com"
        else -> "https://streammate.invalid"
    }

    private companion object {
        /** A person chose it, so there is nothing to be unsure about. */
        const val CHOSEN_BY_HAND_CONFIDENCE = 1.0

        const val CACHE_STATUS_POSITIVE = "positive"
        const val CACHE_STATUS_NEGATIVE = "negative"
        const val NEGATIVE_CACHE_MILLIS = 7L * 24 * 60 * 60 * 1_000
        const val MIN_TITLE_LENGTH = 2
        const val MAX_TITLE_LENGTH = 160
        const val MAX_CONTENT_KEY_LENGTH = 512
        const val MAX_PROVIDER_POSTER_URL_LENGTH = 2_048
        const val MAX_METADATA_WORK_PAGE_SIZE = 200
        const val MAX_METADATA_ATTEMPTS = 16
        const val MIN_YEAR = 1870
        const val MAX_YEAR = 2200
        const val MAX_SEASON_NUMBER = 10_000
        const val MAX_EPISODE_NUMBER = 100_000
        const val DEFAULT_LANGUAGE = "fi-FI"
        const val LOOKUP_KEY_VERSION = "4"
        const val MAX_MEMORY_CACHE_ENTRIES = 256
        val LANGUAGE_PATTERN = Regex("[a-z]{2}(?:-[A-Z]{2})?")
        val CACHE_JSON = Json { ignoreUnknownKeys = true }
    }
}

/** Pure reconciliation kept outside Room so queue behavior is unit-testable. */
internal fun synchronizedCatalogueMetadataWork(
    candidates: List<CatalogueMetadataCandidate>,
    existing: List<CatalogueMetadataWorkEntity>,
    targetGenresVersion: Int,
    nowEpochMillis: Long,
): List<CatalogueMetadataWorkEntity> {
    val existingByKey = existing.associateBy(CatalogueMetadataWorkEntity::contentKey)
    return candidates.map { candidate ->
        val previous = existingByKey[candidate.contentKey]
        val sameLookup = previous != null &&
            previous.mediaType == candidate.mediaType.wireValue &&
            previous.title == candidate.title &&
            previous.year == candidate.year &&
            previous.targetGenresVersion == targetGenresVersion
        val settled = candidate.genresVersion >= targetGenresVersion
        val state = when {
            settled && sameLookup && previous.state == CatalogueMetadataWorkEntity.STATE_NO_MATCH ->
                CatalogueMetadataWorkEntity.STATE_NO_MATCH
            settled -> CatalogueMetadataWorkEntity.STATE_COMPLETE
            sameLookup && previous.state == CatalogueMetadataWorkEntity.STATE_RETRY ->
                CatalogueMetadataWorkEntity.STATE_RETRY
            else -> CatalogueMetadataWorkEntity.STATE_PENDING
        }
        CatalogueMetadataWorkEntity(
            contentKey = candidate.contentKey,
            mediaType = candidate.mediaType.wireValue,
            title = candidate.title,
            year = candidate.year,
            providerPosterUrl = candidate.providerPosterUrl,
            targetGenresVersion = targetGenresVersion,
            state = state,
            attemptCount = if (state == CatalogueMetadataWorkEntity.STATE_RETRY) {
                previous?.attemptCount ?: 0
            } else {
                0
            },
            nextAttemptAtEpochMillis = if (state == CatalogueMetadataWorkEntity.STATE_RETRY) {
                previous?.nextAttemptAtEpochMillis ?: 0
            } else {
                0
            },
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}

internal fun catalogueMetadataRetryDelayMillis(attemptCount: Int): Long {
    val exponent = (attemptCount - 1).coerceIn(0, MAX_RETRY_EXPONENT)
    return (INITIAL_METADATA_RETRY_MILLIS * (1L shl exponent))
        .coerceAtMost(MAX_METADATA_RETRY_MILLIS)
}

private fun CatalogueMetadataWorkEntity.toDomain(): CatalogueMetadataWork? {
    val type = MetadataMediaType.entries.firstOrNull { it.wireValue == mediaType } ?: return null
    return CatalogueMetadataWork(
        contentKey = contentKey,
        mediaType = type,
        title = title,
        year = year,
        providerPosterUrl = providerPosterUrl,
        targetGenresVersion = targetGenresVersion,
        state = state,
        attemptCount = attemptCount,
        nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    )
}

private fun CatalogueMetadataWork.toEntity(
    state: String,
    attemptCount: Int,
    nextAttemptAtEpochMillis: Long,
    nowEpochMillis: Long,
): CatalogueMetadataWorkEntity = CatalogueMetadataWorkEntity(
    contentKey = contentKey,
    mediaType = mediaType.wireValue,
    title = title,
    year = year,
    providerPosterUrl = providerPosterUrl,
    targetGenresVersion = targetGenresVersion,
    state = state,
    attemptCount = attemptCount,
    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
)

private const val INITIAL_METADATA_RETRY_MILLIS = 15L * 60 * 1_000
private const val MAX_METADATA_RETRY_MILLIS = 24L * 60 * 60 * 1_000
private const val MAX_RETRY_EXPONENT = 7
