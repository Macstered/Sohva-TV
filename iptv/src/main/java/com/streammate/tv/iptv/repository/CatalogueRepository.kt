package com.streammate.tv.iptv.repository

import com.streammate.tv.core.error.localizedTransportFailure
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.database.CatalogueDao
import com.streammate.tv.core.database.CatalogueCopyRow
import com.streammate.tv.core.database.CatalogueGenerationRow
import com.streammate.tv.core.database.CatalogueGroupFacetRow
import com.streammate.tv.core.database.CatalogueMetadataCandidateRow
import com.streammate.tv.core.database.PlaybackProgressEntity
import com.streammate.tv.core.database.VodEpisodeEntity
import com.streammate.tv.core.database.VodMovieCardRow
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.database.VodSeriesCardRow
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.core.database.catalogueCategoryKey
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.iptv.m3u.ChannelNameNormalizer
import com.streammate.tv.iptv.metadata.MetadataMatcher
import com.streammate.tv.iptv.metadata.CatalogueMetadataCandidate
import com.streammate.tv.iptv.metadata.CatalogueMetadataOverride
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.catalogueWorkKey
import com.streammate.tv.iptv.metadata.MetadataMovieReference
import com.streammate.tv.iptv.xtream.XtreamCatalogueSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class VodContentType(val wireValue: String) {
    MOVIE("movie"),
    EPISODE("episode"),
}

data class VodMovie(
    val contentKey: String,
    val sourceId: String,
    val movieId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(categoryName),
)

/** Lightweight movie payload used by large poster walls. */
data class VodMovieCard(
    val contentKey: String,
    val sourceId: String,
    val movieId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val genres: Set<CatalogueGenre>,
    val metadataOverride: CatalogueMetadataOverride?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(categoryName),
)

/**
 * One copy of a film: the same work under another playlist's name.
 *
 * Carries the provider's own name rather than a cleaned-up one, because the
 * name is the only thing that says how this copy differs from the one beside
 * it - stripping it would leave two identical rows.
 */
data class CatalogueFilmCopy(
    val contentKey: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    /** The copy the film is currently being shown as. */
    val current: Boolean,
)

data class AvailableSimilarMovie(
    val movie: VodMovie,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
)

data class VodSeries(
    val sourceId: String,
    val seriesId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(categoryName),
)

/** Lightweight series payload used by large poster walls. */
data class VodSeriesCard(
    val sourceId: String,
    val seriesId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val genres: Set<CatalogueGenre>,
    val metadataOverride: CatalogueMetadataOverride?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(categoryName),
)

data class CatalogueGroupFacet(
    val contentKey: String,
    val year: Int?,
    val rating: String?,
    val genres: Set<CatalogueGenre>,
)

data class VodEpisode(
    val contentKey: String,
    val sourceId: String,
    val seriesId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val plot: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
)

data class WatchingProgress(
    val contentKey: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val completed: Boolean,
    val lastWatchedEpochMillis: Long,
) {
    val resumePositionMillis: Long get() = if (completed) 0L else positionMillis

    val fraction: Float
        get() = if (durationMillis > 0) {
            (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class ContinueWatchingItem(
    val contentKey: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progress: WatchingProgress,
)

data class CatalogueCategory(
    val name: String,
    val count: Int,
    val manualPosition: Long? = null,
)

data class CatalogueSourceGeneration(
    val sourceId: String,
    val activeSnapshotId: String,
    val updatedAtEpochMillis: Long,
    val itemCount: Int,
)

/**
 * Ordered database identity for the enabled catalogue snapshots. This is an
 * invalidation token, not a wall-loading flag.
 */
data class CatalogueGeneration(
    val sources: List<CatalogueSourceGeneration>,
    val organizationRules: List<com.streammate.tv.core.model.OrganizationRule> = emptyList(),
    val organizationAliases: Map<String, String> = emptyMap(),
)

enum class CatalogueSearchType {
    MOVIE,
    SERIES,
    EPISODE,
}

data class CatalogueSearchResult(
    val type: CatalogueSearchType,
    val sourceId: String,
    val itemId: String,
    val contentKey: String?,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val movie: VodMovie? = null,
    val series: VodSeries? = null,
)

data class PlayableVod(
    val contentKey: String,
    val sourceId: String,
    val title: String,
    val encryptedStreamUrl: String,
)

class CatalogueRepository(
    private val dao: CatalogueDao,
    private val clock: () -> Long = System::currentTimeMillis,
    val organization: OrganizationRepository? = null,
) {
    constructor(dao: CatalogueDao, clock: () -> Long) : this(dao, clock, null)
    private val similarMoviesCache = object : LinkedHashMap<SimilarMovieCacheKey, CachedSimilarMovies>(
        MAX_SIMILAR_CACHE_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<SimilarMovieCacheKey, CachedSimilarMovies>,
        ): Boolean = size > MAX_SIMILAR_CACHE_ENTRIES
    }

    fun observeCatalogueGeneration(): Flow<CatalogueGeneration> = kotlinx.coroutines.flow.combine(
        dao.observeCatalogueGenerations(),
        organization?.state ?: kotlinx.coroutines.flow.flowOf(OrganizationReadState()),
    ) { rows, customization -> CatalogueGeneration(rows.map { it.toDomain() }, customization.organization.rules, customization.identities) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeMovies(): Flow<List<VodMovie>> = dao.observeMovies()
        .map { movies -> movies.map { it.toDomain() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.MOVIES, VodMovie::organizationItem) ?: it }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeMovies(category: String?): Flow<List<VodMovie>> =
        (category?.let(dao::observeMoviesByCategory) ?: dao.observeMovies())
            .map { movies -> movies.map { it.toDomain() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.MOVIES, VodMovie::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeMovieCards(
        category: String? = null,
        genre: CatalogueGenre? = null,
        unsorted: Boolean = false,
        search: String = "",
    ): Flow<List<VodMovieCard>> {
        val normalizedSearch = search.trim()
        return when {
            normalizedSearch.isNotEmpty() && category != null -> catalogueCategoryKey(category)
                ?.let { dao.observeMovieCardsByCategoryMatching(it, normalizedSearch) }
                ?: dao.observeMovieCardsMatching(normalizedSearch)
            normalizedSearch.isNotEmpty() && genre != null ->
                dao.observeMovieCardsInGenreMatching(genre.wireValue, normalizedSearch)
            normalizedSearch.isNotEmpty() && unsorted ->
                dao.observeUnsortedMovieCardsMatching(normalizedSearch)
            normalizedSearch.isNotEmpty() -> dao.observeMovieCardsMatching(normalizedSearch)
            category != null -> catalogueCategoryKey(category)
                ?.let(dao::observeMovieCardsByCategory)
                ?: dao.observeMovieCards()
            genre != null -> dao.observeMovieCardsInGenre(genre.wireValue)
            unsorted -> dao.observeUnsortedMovieCards()
            else -> dao.observeMovieCards()
        }
            .map { movies -> movies.map { it.toCard() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.MOVIES, VodMovieCard::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun observeMovieHistoryCards(): Flow<List<VodMovieCard>> = dao.observeMovieHistoryCards()
        .map { movies -> movies.map { it.toCard() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.MOVIES, VodMovieCard::organizationItem, chronological = true) ?: it }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeMovieCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodMovieCard>> {
        if (!group.isUsable) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observeMovieCardsInCustomGroup(
            genres = group.genres.map(CatalogueGenre::wireValue),
            anyGenre = group.genres.isEmpty(),
            fromYear = group.fromYear,
            toYear = group.toYear,
            minRating = group.minRating,
            search = search.trim(),
        )
            .map { rows -> rows.map { it.toCard() } }
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.MOVIES, VodMovieCard::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun observeMovieCategories(): Flow<List<CatalogueCategory>> = dao.observeMovieCategories().map { rows ->
        rows.map { CatalogueCategory(it.categoryName, it.itemCount) }
    }.let { organization?.orderedCategories(it, com.streammate.tv.core.model.LibraryRoom.MOVIES) ?: it }

    fun observeMovieCount(): Flow<Int> = dao.observeMovieCount()

    fun observeMovieGenreCounts(): Flow<Map<CatalogueGenre, Int>> = dao.observeMovieGenreCounts()
        .map { rows ->
            rows.mapNotNull { row -> CatalogueGenre.fromWireValue(row.genre)?.let { it to row.itemCount } }
                .toMap()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeUnsortedMovieCount(): Flow<Int> = dao.observeUnsortedMovieCount()

    fun observeSortedMovieCount(targetGenresVersion: Int): Flow<Int> =
        dao.observeSortedMovieCount(targetGenresVersion)

    fun observeMovieGroupFacets(): Flow<List<CatalogueGroupFacet>> = dao.observeMovieGroupFacets()
        .map { rows -> rows.map { it.toFacet() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeSeries(): Flow<List<VodSeries>> = dao.observeSeries()
        .map { series -> series.map { it.toDomain() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.SERIES, VodSeries::organizationItem) ?: it }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeSeries(category: String?): Flow<List<VodSeries>> =
        (category?.let(dao::observeSeriesByCategory) ?: dao.observeSeries())
            .map { series -> series.map { it.toDomain() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.SERIES, VodSeries::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeSeriesCards(
        category: String? = null,
        genre: CatalogueGenre? = null,
        unsorted: Boolean = false,
        search: String = "",
    ): Flow<List<VodSeriesCard>> {
        val normalizedSearch = search.trim()
        return when {
            normalizedSearch.isNotEmpty() && category != null -> catalogueCategoryKey(category)
                ?.let { dao.observeSeriesCardsByCategoryMatching(it, normalizedSearch) }
                ?: dao.observeSeriesCardsMatching(normalizedSearch)
            normalizedSearch.isNotEmpty() && genre != null ->
                dao.observeSeriesCardsInGenreMatching(genre.wireValue, normalizedSearch)
            normalizedSearch.isNotEmpty() && unsorted ->
                dao.observeUnsortedSeriesCardsMatching(normalizedSearch)
            normalizedSearch.isNotEmpty() -> dao.observeSeriesCardsMatching(normalizedSearch)
            category != null -> catalogueCategoryKey(category)
                ?.let(dao::observeSeriesCardsByCategory)
                ?: dao.observeSeriesCards()
            genre != null -> dao.observeSeriesCardsInGenre(genre.wireValue)
            unsorted -> dao.observeUnsortedSeriesCards()
            else -> dao.observeSeriesCards()
        }
            .map { series -> series.map { it.toCard() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.SERIES, VodSeriesCard::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun observeSeriesHistoryCards(): Flow<List<VodSeriesCard>> = dao.observeSeriesHistoryCards()
        .map { series -> series.map { it.toCard() } }
        .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.SERIES, VodSeriesCard::organizationItem, chronological = true) ?: it }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeSeriesCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodSeriesCard>> {
        if (!group.isUsable) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observeSeriesCardsInCustomGroup(
            genres = group.genres.map(CatalogueGenre::wireValue),
            anyGenre = group.genres.isEmpty(),
            fromYear = group.fromYear,
            toYear = group.toYear,
            minRating = group.minRating,
            search = search.trim(),
        )
            .map { rows -> rows.map { it.toCard() } }
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.SERIES, VodSeriesCard::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun observeSeriesCategories(): Flow<List<CatalogueCategory>> = dao.observeSeriesCategories().map { rows ->
        rows.map { CatalogueCategory(it.categoryName, it.itemCount) }
    }.let { organization?.orderedCategories(it, com.streammate.tv.core.model.LibraryRoom.SERIES) ?: it }

    fun observeSeriesCount(): Flow<Int> = dao.observeSeriesCount()

    fun observeSeriesGenreCounts(): Flow<Map<CatalogueGenre, Int>> = dao.observeSeriesGenreCounts()
        .map { rows ->
            rows.mapNotNull { row -> CatalogueGenre.fromWireValue(row.genre)?.let { it to row.itemCount } }
                .toMap()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeUnsortedSeriesCount(): Flow<Int> = dao.observeUnsortedSeriesCount()

    fun observeSortedSeriesCount(targetGenresVersion: Int): Flow<Int> =
        dao.observeSortedSeriesCount(targetGenresVersion)

    fun observeSeriesGroupFacets(): Flow<List<CatalogueGroupFacet>> = dao.observeSeriesGroupFacets()
        .map { rows -> rows.map { it.toFacet() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /**
     * The only full active-library read metadata scheduling needs. It is a
     * narrow synchronization projection used after a refresh or schema upgrade;
     * individual worker pages come from the indexed durable queue instead.
     */
    suspend fun catalogueMetadataCandidates(): List<CatalogueMetadataCandidate> =
        dao.catalogueMetadataCandidates().mapNotNull(CatalogueMetadataCandidateRow::toMetadataCandidate)

    fun observeEpisodes(sourceId: String, seriesId: String): Flow<List<VodEpisode>> =
        dao.observeEpisodes(sourceId, seriesId)
            .map { episodes -> episodes.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeProgress(): Flow<Map<String, WatchingProgress>> = dao.observeProgress()
        .map { progress -> progress.associate { it.contentKey to it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeMovieProgress(): Flow<Map<String, WatchingProgress>> = dao.observeMovieProgress()
        .map { progress -> progress.associate { it.contentKey to it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>> = dao.observeContinueWatching().map { rows ->
        rows.map { row ->
            ContinueWatchingItem(
                contentKey = row.contentKey,
                // Sanitised here rather than at each call site: the grid already
                // shows clean titles, so a raw provider filename in a resume row
                // above it is the same title rendered two different ways.
                title = MetadataMatcher.searchTitle(row.title).ifBlank { row.title },
                subtitle = if (row.contentType == VodContentType.MOVIE.wireValue) {
                    row.year?.toString()
                } else {
                    row.seriesName?.let { "$it · K${row.seasonNumber} J${row.episodeNumber}" }
                },
                posterUrl = row.posterUrl,
                progress = WatchingProgress(
                    contentKey = row.contentKey,
                    positionMillis = row.positionMillis,
                    durationMillis = row.durationMillis,
                    completed = row.completed,
                    lastWatchedEpochMillis = row.lastWatchedEpochMillis,
                ),
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun playable(contentKey: String): PlayableVod? {
        val parts = parseContentKey(contentKey) ?: return null
        return when (parts.type) {
            VodContentType.MOVIE -> dao.activeMovie(parts.sourceId, parts.itemId)?.let {
                PlayableVod(contentKey, it.sourceId, it.name, it.encryptedStreamUrl)
            }
            VodContentType.EPISODE -> dao.activeEpisode(parts.sourceId, parts.itemId)?.let {
                PlayableVod(contentKey, it.sourceId, it.name, it.encryptedStreamUrl)
            }
        }
    }

    suspend fun movie(contentKey: String): VodMovie? {
        val parts = parseContentKey(contentKey)?.takeIf { it.type == VodContentType.MOVIE } ?: return null
        return dao.activeMovie(parts.sourceId, parts.itemId)?.toDomain()
    }

    suspend fun series(sourceId: String, seriesId: String): VodSeries? =
        dao.activeSeries(sourceId, seriesId)?.toDomain()

    suspend fun seriesForEpisode(contentKey: String): VodSeries? {
        val parts = parseContentKey(contentKey)?.takeIf { it.type == VodContentType.EPISODE } ?: return null
        val episode = dao.activeEpisode(parts.sourceId, parts.itemId) ?: return null
        return dao.activeSeries(episode.sourceId, episode.seriesId)?.toDomain()
    }

    suspend fun nextEpisode(contentKey: String): VodEpisode? {
        val parts = parseContentKey(contentKey)?.takeIf { it.type == VodContentType.EPISODE } ?: return null
        val current = dao.activeEpisode(parts.sourceId, parts.itemId) ?: return null
        return dao.nextActiveEpisode(
            sourceId = current.sourceId,
            seriesId = current.seriesId,
            seasonNumber = current.seasonNumber,
            episodeNumber = current.episodeNumber,
        )?.toDomain()
    }

    suspend fun availableSimilarMovies(
        currentMovie: VodMovie,
        references: List<MetadataMovieReference>,
        limit: Int = MAX_AVAILABLE_SIMILAR_MOVIES,
    ): List<AvailableSimilarMovie> {
        if (references.isEmpty() || limit <= 0) return emptyList()
        val cacheKey = SimilarMovieCacheKey(
            contentKey = currentMovie.contentKey,
            referenceIds = references.take(MAX_SIMILAR_REFERENCES).map { it.externalId },
            limit = limit,
        )
        val now = clock()
        val cached = synchronized(similarMoviesCache) {
            similarMoviesCache[cacheKey]
                ?.takeIf { now - it.cachedAtEpochMillis < SIMILAR_CACHE_TTL_MILLIS }
        }
        if (cached != null) {
            val visible = dao.visibleMovieKeys(cached.movies.map { it.movie.contentKey }).toSet()
            return cached.movies.filter { it.movie.contentKey in visible }
        }
        val matches = mutableListOf<AvailableSimilarMovie>()
        val seenContentKeys = mutableSetOf(currentMovie.contentKey)
        references.take(MAX_SIMILAR_REFERENCES).forEach { reference ->
            if (matches.size >= limit) return@forEach
            val referenceTitles = (listOf(reference.title) + reference.alternativeTitles)
                .map(MetadataMatcher::searchTitle)
                .filter { it.length >= MIN_SIMILAR_TITLE_LENGTH }
                .distinctBy(MetadataMatcher::normalizeTitle)
            val normalizedReferences = referenceTitles
                .map(MetadataMatcher::normalizeTitle)
                .filter(String::isNotBlank)
                .toSet()
            val candidates = referenceTitles
                .flatMap { title -> dao.activeMoviesMatchingTitle(title, MAX_MATCHES_PER_SIMILAR_TITLE) }
                .distinctBy { row ->
                    contentKey(VodContentType.MOVIE, row.movie.sourceId, row.movie.movieId)
                }
                .filter { row ->
                    val candidateTitles = listOfNotNull(row.movie.name, row.replacementTitle)
                        .map(MetadataMatcher::normalizeTitle)
                    candidateTitles.any(normalizedReferences::contains) &&
                        (reference.year == null || row.movie.year == null || reference.year == row.movie.year)
                }
                .sortedWith(
                    compareByDescending<com.streammate.tv.core.database.AvailableMovieMatchRow> {
                        it.movie.sourceId == currentMovie.sourceId
                    }.thenByDescending { it.movie.year == reference.year },
                )
            val selected = candidates.firstOrNull { row ->
                contentKey(VodContentType.MOVIE, row.movie.sourceId, row.movie.movieId) !in seenContentKeys
            } ?: return@forEach
            val movie = selected.movie.toDomain()
            seenContentKeys += movie.contentKey
            matches += AvailableSimilarMovie(
                movie = movie,
                title = reference.title,
                posterUrl = reference.posterUrl ?: movie.posterUrl,
                year = reference.year ?: movie.year,
            )
        }
        return matches.also { available ->
            synchronized(similarMoviesCache) {
                similarMoviesCache[cacheKey] = CachedSimilarMovies(
                    movies = available,
                    cachedAtEpochMillis = now,
                )
            }
        }
    }

    /**
     * Where to resume this copy - which is where the *film* was left, not this
     * copy of it. Whichever copy was last played wins, so opening the other one
     * carries on rather than starting again. A copy that has never been played
     * has no row of its own and still resumes.
     */
    suspend fun progress(contentKey: String): WatchingProgress? {
        val own = dao.progress(contentKey)
        val shared = workKeyFor(contentKey)?.let { dao.progressForWork(it) }
        return listOfNotNull(own, shared)
            .maxByOrNull { it.lastWatchedEpochMillis }
            ?.toDomain()
    }

    /**
     * Every copy of this film the library holds, this one included.
     *
     * Empty rather than a list of one when nothing else was found: there is
     * nothing to choose between, and a page offering a single "version" of a
     * film reads as a fault.
     *
     * The database casts a wide net by name and this decides what really is the
     * same film, by exactly the rule the wall groups by. A one-word title pulls
     * in a lot of candidates, which is what the bound is for; the copies of a
     * film are next to each other in it, since they are named after the film.
     */
    suspend fun catalogueCopies(movie: VodMovie): List<CatalogueFilmCopy> {
        val workKey = catalogueWorkKey(
            title = movie.name,
            year = movie.year,
            externalId = dao.metadataExternalId(movie.contentKey),
        )
        val search = MetadataMatcher.searchTitle(movie.name).ifBlank { movie.name }
        val copies = dao.activeMovieCopies(search, MAX_COPY_CANDIDATES)
            .filter { row -> row.workKey() == workKey }
            .map { row ->
                CatalogueFilmCopy(
                    contentKey = contentKey(VodContentType.MOVIE, row.movie.sourceId, row.movie.movieId),
                    sourceId = row.movie.sourceId,
                    sourceName = row.sourceName,
                    title = row.movie.name,
                    current = row.movie.sourceId == movie.sourceId && row.movie.movieId == movie.movieId,
                )
            }
        return copies.takeIf { it.size > 1 }.orEmpty()
    }

    private fun CatalogueCopyRow.workKey() = catalogueWorkKey(
        title = movie.name,
        year = movie.year ?: vodYearFromTitle(movie.name),
        externalId = externalId,
    )

    /**
     * Which film a copy is of, as far as the library currently knows.
     *
     * Films only: two copies of one film are one film, while two copies of one
     * episode need their season and episode numbers matched across playlists as
     * well, which is a harder problem left for later.
     */
    private suspend fun workKeyFor(contentKey: String): String? {
        val parts = parseContentKey(contentKey) ?: return null
        if (parts.type != VodContentType.MOVIE) return null
        val movie = dao.activeMovie(parts.sourceId, parts.itemId) ?: return null
        return catalogueWorkKey(
            title = movie.name,
            year = movie.year ?: vodYearFromTitle(movie.name),
            externalId = dao.metadataExternalId(contentKey),
        )
    }

    suspend fun search(query: String, limitPerType: Int = 40): List<CatalogueSearchResult> {
        val normalized = query.trim().take(MAX_SEARCH_QUERY_LENGTH)
        if (normalized.length < MIN_SEARCH_QUERY_LENGTH) return emptyList()
        val limit = limitPerType.coerceIn(1, MAX_SEARCH_RESULTS_PER_TYPE)
        val movies = dao.searchMovies(normalized, limit).map { movie ->
            val domain = movie.toDomain()
            CatalogueSearchResult(
                type = CatalogueSearchType.MOVIE,
                sourceId = movie.sourceId,
                itemId = movie.movieId,
                contentKey = contentKey(VodContentType.MOVIE, movie.sourceId, movie.movieId),
                title = movie.name,
                subtitle = listOfNotNull(domain.categoryName, domain.year?.toString()).joinToString(" · "),
                posterUrl = movie.posterUrl,
                movie = domain,
            )
        }
        val series = dao.searchSeries(normalized, limit).map { item ->
            val domain = item.toDomain()
            CatalogueSearchResult(
                type = CatalogueSearchType.SERIES,
                sourceId = item.sourceId,
                itemId = item.seriesId,
                contentKey = null,
                title = item.name,
                subtitle = listOfNotNull(domain.categoryName, domain.year?.toString()).joinToString(" · "),
                posterUrl = item.posterUrl,
                series = domain,
            )
        }
        val episodes = dao.searchEpisodes(normalized, limit).map { episode ->
            CatalogueSearchResult(
                type = CatalogueSearchType.EPISODE,
                sourceId = episode.sourceId,
                itemId = episode.episodeId,
                contentKey = contentKey(VodContentType.EPISODE, episode.sourceId, episode.episodeId),
                title = episode.episodeName,
                subtitle = "${episode.seriesName} · K${episode.seasonNumber} J${episode.episodeNumber}",
                posterUrl = episode.seriesPosterUrl,
            )
        }
        return movies + series + episodes
    }

    suspend fun updateProgress(contentKey: String, positionMillis: Long, durationMillis: Long) {
        val parts = parseContentKey(contentKey) ?: return
        if (positionMillis < MIN_PROGRESS_MILLIS || durationMillis <= 0) return
        val boundedPosition = positionMillis.coerceAtMost(durationMillis)
        val remaining = durationMillis - boundedPosition
        val completed = boundedPosition >= (durationMillis * COMPLETION_FRACTION).toLong() ||
            (durationMillis >= LONG_FORM_CONTENT_MILLIS && remaining <= COMPLETION_REMAINING_MILLIS)
        dao.upsertProgress(
            PlaybackProgressEntity(
                contentKey = contentKey,
                sourceId = parts.sourceId,
                contentType = parts.type.wireValue,
                itemId = parts.itemId,
                positionMillis = if (completed) durationMillis else boundedPosition,
                durationMillis = durationMillis,
                completed = completed,
                lastWatchedEpochMillis = clock(),
                workKey = workKeyFor(contentKey),
            ),
        )
    }

    private fun VodMovieEntity.toDomain() = VodMovie(
        organizationGroupKey = organizationGroupKey,
        contentKey = contentKey(VodContentType.MOVIE, sourceId, movieId),
        sourceId = sourceId,
        movieId = movieId,
        name = name,
        categoryName = categoryName,
        posterUrl = posterUrl,
        year = year ?: vodYearFromTitle(name),
        rating = rating,
        plot = plot,
    )

    private fun CatalogueGenerationRow.toDomain() = CatalogueSourceGeneration(
        sourceId = sourceId,
        activeSnapshotId = activeSnapshotId,
        updatedAtEpochMillis = updatedAtEpochMillis,
        itemCount = itemCount,
    )

    private fun VodMovieCardRow.toCard(): VodMovieCard {
        val key = contentKey(VodContentType.MOVIE, sourceId, movieId)
        return VodMovieCard(
            organizationGroupKey = organizationGroupKey,
            contentKey = key,
            sourceId = sourceId,
            movieId = movieId,
            name = name,
            categoryName = categoryName,
            posterUrl = posterUrl,
            year = year ?: vodYearFromTitle(name),
            rating = rating,
            genres = genresCsv.toCatalogueGenres(),
            metadataOverride = replacementTitle?.let { title ->
                CatalogueMetadataOverride(
                    contentKey = key,
                    providerPosterUrl = posterUrl,
                    replacementPosterUrl = replacementPosterUrl,
                    replaceProviderPoster = replaceProviderPoster ?: false,
                    replacementTitle = title,
                    externalId = externalId,
                    genresVersion = metadataGenresVersion ?: 0,
                )
            },
        )
    }

    private fun VodSeriesEntity.toDomain() = VodSeries(
        organizationGroupKey = organizationGroupKey,
        sourceId = sourceId,
        seriesId = seriesId,
        name = name,
        categoryName = categoryName,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year ?: vodYearFromTitle(name),
        rating = rating,
        plot = plot,
    )

    private fun VodSeriesCardRow.toCard(): VodSeriesCard {
        val key = seriesContentKey(sourceId, seriesId)
        return VodSeriesCard(
            organizationGroupKey = organizationGroupKey,
            sourceId = sourceId,
            seriesId = seriesId,
            name = name,
            categoryName = categoryName,
            posterUrl = posterUrl,
            year = year ?: vodYearFromTitle(name),
            rating = rating,
            genres = genresCsv.toCatalogueGenres(),
            metadataOverride = replacementTitle?.let { title ->
                CatalogueMetadataOverride(
                    contentKey = key,
                    providerPosterUrl = posterUrl,
                    replacementPosterUrl = replacementPosterUrl,
                    replaceProviderPoster = replaceProviderPoster ?: false,
                    replacementTitle = title,
                    externalId = externalId,
                    genresVersion = metadataGenresVersion ?: 0,
                )
            },
        )
    }

    private fun CatalogueGroupFacetRow.toFacet() = CatalogueGroupFacet(
        contentKey = contentKey,
        year = year,
        rating = rating,
        genres = genresCsv.toCatalogueGenres(),
    )

    private fun String?.toCatalogueGenres(): Set<CatalogueGenre> =
        this?.split(',')
            ?.mapNotNull(CatalogueGenre::fromWireValue)
            ?.toSet()
            .orEmpty()

    private fun VodEpisodeEntity.toDomain() = VodEpisode(
        contentKey = contentKey(VodContentType.EPISODE, sourceId, episodeId),
        sourceId = sourceId,
        seriesId = seriesId,
        episodeId = episodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        name = name,
        plot = plot,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
    )

    private fun PlaybackProgressEntity.toDomain() = WatchingProgress(
        contentKey = contentKey,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        completed = completed,
        lastWatchedEpochMillis = lastWatchedEpochMillis,
    )

    private companion object {
        const val MIN_PROGRESS_MILLIS = 5_000L
        const val COMPLETION_REMAINING_MILLIS = 120_000L
        const val LONG_FORM_CONTENT_MILLIS = 600_000L
        const val COMPLETION_FRACTION = 0.95
        const val MIN_SEARCH_QUERY_LENGTH = 2
        const val MAX_SEARCH_QUERY_LENGTH = 80
        const val MAX_SEARCH_RESULTS_PER_TYPE = 100
        const val MAX_AVAILABLE_SIMILAR_MOVIES = 12
        const val MAX_COPY_CANDIDATES = 200
        const val MAX_SIMILAR_REFERENCES = 20
        const val MAX_MATCHES_PER_SIMILAR_TITLE = 20
        const val MIN_SIMILAR_TITLE_LENGTH = 2
        const val MAX_SIMILAR_CACHE_ENTRIES = 24
        const val SIMILAR_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
    }
}

private data class SimilarMovieCacheKey(
    val contentKey: String,
    val referenceIds: List<String>,
    val limit: Int,
)

private data class CachedSimilarMovies(
    val movies: List<AvailableSimilarMovie>,
    val cachedAtEpochMillis: Long,
)

data class CatalogueImportSummary(val movies: Int, val series: Int)

class XtreamCatalogueImportService(
    private val client: XtreamCatalogueSource,
    private val dao: CatalogueDao,
    private val secretCipher: SecretCipher,
    private val organization: OrganizationRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Both import paths fetch over the network and then run a Keystore
    // encryption per row while building the entity list. That work ran on
    // whichever dispatcher called them, and refreshEpisodes is called straight
    // from a LaunchedEffect, i.e. the main thread.
    suspend fun refresh(source: IptvSourceConfiguration): CatalogueImportSummary =
        withContext(Dispatchers.IO) { refreshInternal(source) }

    suspend fun refreshEpisodes(source: IptvSourceConfiguration, seriesId: String): Int =
        withContext(Dispatchers.IO) { refreshEpisodesInternal(source, seriesId) }

    private suspend fun refreshInternal(source: IptvSourceConfiguration): CatalogueImportSummary {
        if (source.type != IptvSourceType.XTREAM) {
            throw LocalizedException(CoreR.string.error_source_not_xtream)
        }
        if (!source.importScope.importsVod) {
            throw LocalizedException(CoreR.string.error_source_no_vod)
        }
        val snapshotId = UUID.randomUUID().toString()
        dao.markCatalogueRefreshStarted(source.id, clock())
        return try {
            val movies = client.movies(source)
            val series = client.series(source)
            movies.chunked(BATCH_SIZE).forEach { batch ->
                dao.upsertMovies(batch.map { movie ->
                    VodMovieEntity(
                        sourceId = source.id,
                        snapshotId = snapshotId,
                        movieId = movie.streamId,
                        name = movie.name,
                        normalizedName = ChannelNameNormalizer.normalize(movie.name),
                        categoryName = movie.categoryName,
                        organizationGroupKey = com.streammate.tv.core.model.organizationGroupKey(movie.categoryName, movie.categoryId),
                        posterUrl = movie.posterUrl,
                        encryptedStreamUrl = secretCipher.encrypt(movie.streamUrl),
                        year = movie.year,
                        rating = movie.rating,
                        plot = movie.plot,
                    )
                })
            }
            series.chunked(BATCH_SIZE).forEach { batch ->
                dao.upsertSeries(batch.map { item ->
                    VodSeriesEntity(
                        sourceId = source.id,
                        snapshotId = snapshotId,
                        seriesId = item.seriesId,
                        name = item.name,
                        normalizedName = ChannelNameNormalizer.normalize(item.name),
                        categoryName = item.categoryName,
                        organizationGroupKey = com.streammate.tv.core.model.organizationGroupKey(item.categoryName, item.categoryId),
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        year = item.year,
                        rating = item.rating,
                        plot = item.plot,
                    )
                })
            }
            // Register proven copy aliases before activation so a new copy cannot briefly bypass a hidden film.
            organization?.registerImportedMovies(source.id, movies)
            dao.activateCatalogueSnapshot(source.id, snapshotId, movies.size + series.size, clock())
            CatalogueImportSummary(movies.size, series.size)
        } catch (error: Throwable) {
            dao.deleteMovieSnapshot(source.id, snapshotId)
            dao.deleteSeriesSnapshot(source.id, snapshotId)
            val redacted = SecretRedactor.redact(error.message)
            runCatching { dao.markCatalogueRefreshFailed(source.id, clock(), redacted) }
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    private suspend fun refreshEpisodesInternal(source: IptvSourceConfiguration, seriesId: String): Int {
        if (source.type != IptvSourceType.XTREAM) {
            throw LocalizedException(CoreR.string.error_source_not_xtream)
        }
        if (!source.importScope.importsVod) {
            throw LocalizedException(CoreR.string.error_source_no_vod)
        }
        return try {
            val episodes = client.seriesEpisodes(source, seriesId)
            dao.replaceSeriesEpisodes(
                sourceId = source.id,
                seriesId = seriesId,
                episodes = episodes.map { episode ->
                    VodEpisodeEntity(
                        sourceId = source.id,
                        seriesId = seriesId,
                        episodeId = episode.episodeId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        name = episode.name,
                        encryptedStreamUrl = secretCipher.encrypt(episode.streamUrl),
                        plot = episode.plot,
                        durationSeconds = episode.durationSeconds,
                        thumbnailUrl = episode.thumbnailUrl,
                    )
                },
            )
            episodes.size
        } catch (error: Throwable) {
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    private companion object {
        const val BATCH_SIZE = 250
    }
}

private data class ContentKeyParts(val type: VodContentType, val sourceId: String, val itemId: String)

private fun contentKey(type: VodContentType, sourceId: String, itemId: String): String =
    "vod:${type.wireValue}:$sourceId:$itemId"

fun seriesContentKey(sourceId: String, seriesId: String): String =
    "series:$sourceId:$seriesId"

private fun CatalogueMetadataCandidateRow.toMetadataCandidate(): CatalogueMetadataCandidate? {
    val type = MetadataMediaType.entries.firstOrNull { it.wireValue == mediaType } ?: return null
    return CatalogueMetadataCandidate(
        contentKey = contentKey,
        mediaType = type,
        title = title,
        year = year,
        providerPosterUrl = providerPosterUrl,
        genresVersion = genresVersion,
    )
}

private fun parseContentKey(value: String): ContentKeyParts? {
    val parts = value.split(':', limit = 4)
    if (parts.size != 4 || parts[0] != "vod") return null
    val type = VodContentType.entries.firstOrNull { it.wireValue == parts[1] } ?: return null
    val sourceId = parts[2].takeIf { it.matches(CONTENT_ID_PATTERN) } ?: return null
    val itemId = parts[3].takeIf { it.matches(CONTENT_ID_PATTERN) } ?: return null
    return ContentKeyParts(type, sourceId, itemId)
}

private val CONTENT_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")

internal fun vodYearFromTitle(value: String): Int? = VOD_TITLE_YEAR.find(value)
    ?.groupValues
    ?.getOrNull(1)
    ?.toIntOrNull()

private val VOD_TITLE_YEAR = Regex("[\\[(]((?:19|20)\\d{2})[\\])]")
