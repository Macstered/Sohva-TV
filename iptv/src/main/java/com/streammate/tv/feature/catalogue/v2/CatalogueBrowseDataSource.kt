package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueCategory
import com.streammate.tv.iptv.repository.CatalogueGeneration
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.VodMovieCard
import com.streammate.tv.iptv.repository.VodSeriesCard
import com.streammate.tv.iptv.repository.seriesContentKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale

interface CatalogueBrowseDataSource {
    fun observeGeneration(): Flow<CatalogueGeneration>

    fun observePlaylistGroups(mode: CatalogueMode): Flow<List<CatalogueCategory>>

    /**
     * The store needs the browse-visible rows while category editing needs the
     * provider's complete list. Keeping both in one emission lets the real
     * repository answer both needs with one Room query.
     */
    fun observePlaylistGroupSnapshot(mode: CatalogueMode): Flow<CataloguePlaylistGroupSnapshot> =
        observePlaylistGroups(mode).map { groups ->
            CataloguePlaylistGroupSnapshot(all = groups, visible = groups)
        }

    fun observeGenreFacets(mode: CatalogueMode): Flow<List<CatalogueBrowseFacet>>

    fun observeEntries(request: CatalogueBrowseRequest): Flow<List<CatalogueBrowseEntry>>
}

data class CataloguePlaylistGroupSnapshot(
    val all: List<CatalogueCategory>,
    val visible: List<CatalogueCategory>,
)

/**
 * Test seam around the existing repository. It also makes the exact set of
 * database flows V2 is allowed to consume visible in one place.
 */
internal interface CatalogueBrowseQueries {
    fun observeGeneration(): Flow<CatalogueGeneration>

    fun observeMovieCategories(): Flow<List<CatalogueCategory>>

    fun observeSeriesCategories(): Flow<List<CatalogueCategory>>

    fun observeMovieGenreCounts(): Flow<Map<CatalogueGenre, Int>>

    fun observeSeriesGenreCounts(): Flow<Map<CatalogueGenre, Int>>

    fun observeUnsortedMovieCount(): Flow<Int>

    fun observeUnsortedSeriesCount(): Flow<Int>

    fun observeMovieCards(
        category: String? = null,
        genre: CatalogueGenre? = null,
        unsorted: Boolean = false,
        search: String = "",
    ): Flow<List<VodMovieCard>>

    fun observeMovieHistoryCards(): Flow<List<VodMovieCard>>

    fun observeMovieCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodMovieCard>>

    fun observeSeriesCards(
        category: String? = null,
        genre: CatalogueGenre? = null,
        unsorted: Boolean = false,
        search: String = "",
    ): Flow<List<VodSeriesCard>>

    fun observeSeriesHistoryCards(): Flow<List<VodSeriesCard>>

    fun observeSeriesCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodSeriesCard>>
}

private class RepositoryCatalogueBrowseQueries(
    private val repository: CatalogueRepository,
) : CatalogueBrowseQueries {
    override fun observeGeneration(): Flow<CatalogueGeneration> = repository.observeCatalogueGeneration()

    override fun observeMovieCategories(): Flow<List<CatalogueCategory>> = repository.observeMovieCategories()

    override fun observeSeriesCategories(): Flow<List<CatalogueCategory>> = repository.observeSeriesCategories()

    override fun observeMovieGenreCounts(): Flow<Map<CatalogueGenre, Int>> =
        repository.observeMovieGenreCounts()

    override fun observeSeriesGenreCounts(): Flow<Map<CatalogueGenre, Int>> =
        repository.observeSeriesGenreCounts()

    override fun observeUnsortedMovieCount(): Flow<Int> = repository.observeUnsortedMovieCount()

    override fun observeUnsortedSeriesCount(): Flow<Int> = repository.observeUnsortedSeriesCount()

    override fun observeMovieCards(
        category: String?,
        genre: CatalogueGenre?,
        unsorted: Boolean,
        search: String,
    ): Flow<List<VodMovieCard>> = repository.observeMovieCards(category, genre, unsorted, search)

    override fun observeMovieHistoryCards(): Flow<List<VodMovieCard>> =
        repository.observeMovieHistoryCards()

    override fun observeMovieCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodMovieCard>> =
        repository.observeMovieCardsInCustomGroup(group, search)

    override fun observeSeriesCards(
        category: String?,
        genre: CatalogueGenre?,
        unsorted: Boolean,
        search: String,
    ): Flow<List<VodSeriesCard>> = repository.observeSeriesCards(category, genre, unsorted, search)

    override fun observeSeriesHistoryCards(): Flow<List<VodSeriesCard>> =
        repository.observeSeriesHistoryCards()

    override fun observeSeriesCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodSeriesCard>> =
        repository.observeSeriesCardsInCustomGroup(group, search)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryCatalogueBrowseDataSource internal constructor(
    private val queries: CatalogueBrowseQueries,
    hiddenPlaylistGroups: Set<String> = emptySet(),
    private val organization: com.streammate.tv.iptv.repository.OrganizationRepository? = null,
    customGroups: List<CatalogueCustomGroup> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CatalogueBrowseDataSource {
    constructor(
        repository: CatalogueRepository,
        hiddenPlaylistGroups: Set<String> = emptySet(),
        customGroups: List<CatalogueCustomGroup> = emptyList(),
    ) : this(RepositoryCatalogueBrowseQueries(repository), hiddenPlaylistGroups, repository.organization, customGroups)

    private val hiddenPlaylistGroupKeys = MutableStateFlow(hiddenPlaylistGroups.normalizedGroupKeys())
    private val savedCustomGroups = MutableStateFlow(customGroups.filter(CatalogueCustomGroup::isUsable))

    fun setCustomGroups(groups: List<CatalogueCustomGroup>) {
        savedCustomGroups.value = groups.filter(CatalogueCustomGroup::isUsable)
    }

    fun setHiddenPlaylistGroups(groups: Set<String>) {
        hiddenPlaylistGroupKeys.value = groups.normalizedGroupKeys()
    }

    override fun observeGeneration(): Flow<CatalogueGeneration> = queries.observeGeneration()

    override fun observePlaylistGroups(mode: CatalogueMode): Flow<List<CatalogueCategory>> =
        observePlaylistGroupSnapshot(mode).map { it.visible }

    override fun observePlaylistGroupSnapshot(
        mode: CatalogueMode,
    ): Flow<CataloguePlaylistGroupSnapshot> =
        combine(
            when (mode) {
            CatalogueMode.MOVIES -> queries.observeMovieCategories()
            CatalogueMode.SERIES -> queries.observeSeriesCategories()
            },
            hiddenPlaylistGroupKeys,
            organization?.allCategoryGroups(if (mode == CatalogueMode.MOVIES) com.streammate.tv.core.model.LibraryRoom.MOVIES else com.streammate.tv.core.model.LibraryRoom.SERIES)
                ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        ) { groups, hiddenKeys, allGroups ->
            CataloguePlaylistGroupSnapshot(
                all = if (organization != null) allGroups else groups,
                visible = groups.filterNot {
                    it.name.trim().lowercase(Locale.ROOT) in hiddenKeys
                },
            )
        }.flowOn(dispatcher)

    override fun observeGenreFacets(mode: CatalogueMode): Flow<List<CatalogueBrowseFacet>> {
        val counts = when (mode) {
            CatalogueMode.MOVIES -> queries.observeMovieGenreCounts()
            CatalogueMode.SERIES -> queries.observeSeriesGenreCounts()
        }
        val unsorted = when (mode) {
            CatalogueMode.MOVIES -> queries.observeUnsortedMovieCount()
            CatalogueMode.SERIES -> queries.observeUnsortedSeriesCount()
        }
        return combine(counts, unsorted, savedCustomGroups) { genreCounts, unsortedCount, customGroups ->
            buildList {
                customGroups.forEach { group ->
                    add(CatalogueBrowseFacet(CatalogueBrowsePartition.CustomGroup(group.id), null, group.name))
                }
                CatalogueGenre.entries.forEach { genre ->
                    genreCounts[genre]?.takeIf { it > 0 }?.let { count ->
                        add(CatalogueBrowseFacet(CatalogueBrowsePartition.Genre(genre), count))
                    }
                }
                if (unsortedCount > 0) {
                    add(CatalogueBrowseFacet(CatalogueBrowsePartition.Unsorted, unsortedCount))
                }
            }
        }.flowOn(dispatcher)
    }

    override fun observeEntries(request: CatalogueBrowseRequest): Flow<List<CatalogueBrowseEntry>> =
        when (request.mode) {
            CatalogueMode.MOVIES -> observeMovies(request.partition, request.search)
                .map { cards -> cards.map(VodMovieCard::toBrowseEntry) }

            CatalogueMode.SERIES -> observeSeries(request.partition, request.search)
                .map { cards -> cards.map(VodSeriesCard::toBrowseEntry) }
        }
            // The repository's flowOn boundary is upstream of these V2 maps;
            // without another boundary, converting a large category would run
            // in the Compose collector on the main thread.
            .flowOn(dispatcher)

    private fun observeMovies(
        partition: CatalogueBrowsePartition,
        search: String = "",
    ): Flow<List<VodMovieCard>> = when (partition) {
        CatalogueBrowsePartition.History -> queries.observeMovieHistoryCards().map { cards ->
            cards.filter { it.matchesHistorySearch(search) }
        }
        is CatalogueBrowsePartition.PlaylistGroup -> queries.observeMovieCards(
            category = partition.name,
            search = search,
        )
        is CatalogueBrowsePartition.Genre -> queries.observeMovieCards(
            genre = partition.genre,
            search = search,
        )
        is CatalogueBrowsePartition.CustomGroup -> observeCustomGroup(partition.id).flatMapLatest { group ->
            if (group == null) flowOf(emptyList()) else queries.observeMovieCardsInCustomGroup(group, search)
        }
        CatalogueBrowsePartition.Unsorted -> queries.observeMovieCards(
            unsorted = true,
            search = search,
        )
    }

    private fun observeSeries(
        partition: CatalogueBrowsePartition,
        search: String = "",
    ): Flow<List<VodSeriesCard>> = when (partition) {
        CatalogueBrowsePartition.History -> queries.observeSeriesHistoryCards().map { cards ->
            cards.filter { it.matchesHistorySearch(search) }
        }
        is CatalogueBrowsePartition.PlaylistGroup -> queries.observeSeriesCards(
            category = partition.name,
            search = search,
        )
        is CatalogueBrowsePartition.Genre -> queries.observeSeriesCards(
            genre = partition.genre,
            search = search,
        )
        is CatalogueBrowsePartition.CustomGroup -> observeCustomGroup(partition.id).flatMapLatest { group ->
            if (group == null) flowOf(emptyList()) else queries.observeSeriesCardsInCustomGroup(group, search)
        }
        CatalogueBrowsePartition.Unsorted -> queries.observeSeriesCards(
            unsorted = true,
            search = search,
        )
    }

    private fun observeCustomGroup(id: String): Flow<CatalogueCustomGroup?> = savedCustomGroups
        .map { groups -> groups.firstOrNull { it.id == id } }
        .distinctUntilChanged()
}

private fun VodMovieCard.matchesHistorySearch(search: String): Boolean =
    search.isBlank() ||
        name.contains(search, ignoreCase = true) ||
        metadataOverride?.replacementTitle?.contains(search, ignoreCase = true) == true

private fun VodSeriesCard.matchesHistorySearch(search: String): Boolean =
    search.isBlank() ||
        name.contains(search, ignoreCase = true) ||
        metadataOverride?.replacementTitle?.contains(search, ignoreCase = true) == true

private fun Set<String>.normalizedGroupKeys(): Set<String> = mapTo(hashSetOf()) {
    it.trim().lowercase(Locale.ROOT)
}

internal fun VodMovieCard.toBrowseEntry() = CatalogueBrowseEntry(
    contentKey = contentKey,
    target = CatalogueBrowseTarget.Movie(sourceId, movieId),
    providerTitle = name,
    playlistGroup = categoryName,
    providerPosterUrl = posterUrl,
    year = year,
    rating = rating,
    genres = genres,
    metadataOverride = metadataOverride,
)

internal fun VodSeriesCard.toBrowseEntry() = CatalogueBrowseEntry(
    contentKey = seriesContentKey(sourceId, seriesId),
    target = CatalogueBrowseTarget.Series(sourceId, seriesId),
    providerTitle = name,
    playlistGroup = categoryName,
    providerPosterUrl = posterUrl,
    year = year,
    rating = rating,
    genres = genres,
    metadataOverride = metadataOverride,
)
