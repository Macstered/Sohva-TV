package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueCategory
import com.streammate.tv.iptv.repository.CatalogueGeneration
import com.streammate.tv.iptv.repository.CatalogueSourceGeneration
import com.streammate.tv.iptv.repository.VodMovieCard
import com.streammate.tv.iptv.repository.VodSeriesCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueBrowseDataSourceTest {
    private val queries = FakeCatalogueBrowseQueries()
    private val subject = RepositoryCatalogueBrowseDataSource(queries)

    @Test
    fun playlistGroupsAreSelectedByMode() = runTest {
        queries.movieCategories.value = listOf(CatalogueCategory("Movies", 12))
        queries.seriesCategories.value = listOf(CatalogueCategory("Shows", 7))

        assertEquals("Movies", subject.observePlaylistGroups(CatalogueMode.MOVIES).first().single().name)
        assertEquals("Shows", subject.observePlaylistGroups(CatalogueMode.SERIES).first().single().name)
    }

    @Test
    fun hiddenPlaylistGroupsAreFilteredBeforeTheStoreSelectsItsFirstGroup() = runTest {
        queries.movieCategories.value = listOf(
            CatalogueCategory("Hidden Group", 12),
            CatalogueCategory("Visible", 7),
        )
        val filtered = RepositoryCatalogueBrowseDataSource(
            queries = queries,
            hiddenPlaylistGroups = setOf("  HIDDEN GROUP "),
        )

        val snapshot = filtered.observePlaylistGroupSnapshot(CatalogueMode.MOVIES).first()

        assertEquals(listOf("Hidden Group", "Visible"), snapshot.all.map { it.name })
        assertEquals(listOf("Visible"), snapshot.visible.map { it.name })
        assertEquals(listOf("Visible"), filtered.observePlaylistGroups(CatalogueMode.MOVIES).first().map { it.name })
    }

    @Test
    fun genreFacetsKeepStableEnumOrderAndPutUnsortedLast() = runTest {
        queries.movieGenreCounts.value = mapOf(
            CatalogueGenre.DRAMA to 9,
            CatalogueGenre.ACTION to 4,
        )
        queries.unsortedMovieCount.value = 3

        assertEquals(
            listOf(
                CatalogueBrowseFacet(CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION), 4),
                CatalogueBrowseFacet(CatalogueBrowsePartition.Genre(CatalogueGenre.DRAMA), 9),
                CatalogueBrowseFacet(CatalogueBrowsePartition.Unsorted, 3),
            ),
            subject.observeGenreFacets(CatalogueMode.MOVIES).first(),
        )
    }

    @Test
    fun moviePlaylistRequestUsesIndexedCategorySliceAndMapsOnlyWallFields() = runTest {
        val card = VodMovieCard(
            contentKey = "vod:movie:one:42",
            sourceId = "one",
            movieId = "42",
            name = "Provider title",
            categoryName = "Action",
            posterUrl = "https://poster",
            year = 2024,
            rating = "7.2",
            genres = setOf(CatalogueGenre.ACTION),
            metadataOverride = null,
        )
        queries.movies.value = listOf(card)

        val result = subject.observeEntries(
            CatalogueBrowseRequest(
                mode = CatalogueMode.MOVIES,
                partition = CatalogueBrowsePartition.PlaylistGroup("Action"),
            ),
        ).first().single()

        assertEquals(QuerySelection(category = "Action"), queries.lastMovieSelection)
        assertEquals(card.contentKey, result.contentKey)
        assertEquals(CatalogueBrowseTarget.Movie("one", "42"), result.target)
        assertEquals(card.name, result.providerTitle)
        assertSame(card.genres, result.genres)
    }

    @Test
    fun stockGenreAndUnsortedSelectionsStayDatabaseAddressable() = runTest {
        subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.Genre(CatalogueGenre.DRAMA)),
        ).first()
        assertEquals(QuerySelection(genre = CatalogueGenre.DRAMA), queries.lastMovieSelection)

        subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.SERIES, CatalogueBrowsePartition.Unsorted),
        ).first()
        assertEquals(QuerySelection(unsorted = true), queries.lastSeriesSelection)
    }

    @Test
    fun searchStaysInsideTheSelectedDatabasePartition() = runTest {
        subject.observeEntries(
            CatalogueBrowseRequest(
                CatalogueMode.MOVIES,
                CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION),
                search = "Strong",
            ),
        ).first()

        assertEquals(
            QuerySelection(genre = CatalogueGenre.ACTION, search = "Strong"),
            queries.lastMovieSelection,
        )
    }

    @Test
    fun seriesGetsStableContentKeyAndTarget() = runTest {
        queries.series.value = listOf(
            VodSeriesCard(
                sourceId = "two",
                seriesId = "9",
                name = "Series",
                categoryName = "Drama",
                posterUrl = null,
                year = null,
                rating = null,
                genres = emptySet(),
                metadataOverride = null,
            ),
        )

        val result = subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.SERIES, CatalogueBrowsePartition.PlaylistGroup("Drama")),
        ).first().single()

        assertEquals("series:two:9", result.contentKey)
        assertEquals(CatalogueBrowseTarget.Series("two", "9"), result.target)
    }

    @Test
    fun historyUsesDedicatedRecentQueriesForBothLibraries() = runTest {
        queries.movieHistory.value = listOf(movieCard("Recent movie"))
        queries.seriesHistory.value = listOf(seriesCard("Recent series"))

        val movies = subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.History),
        ).first()
        val series = subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.SERIES, CatalogueBrowsePartition.History),
        ).first()

        assertEquals(listOf("Recent movie"), movies.map(CatalogueBrowseEntry::providerTitle))
        assertEquals(listOf("Recent series"), series.map(CatalogueBrowseEntry::providerTitle))
        assertEquals(null, queries.lastMovieSelection)
        assertEquals(null, queries.lastSeriesSelection)
    }

    @Test
    fun historySearchFiltersWithoutChangingRecencyOrder() = runTest {
        queries.movieHistory.value = listOf(movieCard("Newest"), movieCard("Older match"))

        val result = subject.observeEntries(
            CatalogueBrowseRequest(
                CatalogueMode.MOVIES,
                CatalogueBrowsePartition.History,
                search = "match",
            ),
        ).first()

        assertEquals(listOf("Older match"), result.map(CatalogueBrowseEntry::providerTitle))
    }

    @Test
    fun generationFlowIsNotSynthesizedFromWallEmissions() = runTest {
        val generation = CatalogueGeneration(
            listOf(CatalogueSourceGeneration("one", "snapshot-2", 123L, 99)),
        )
        queries.generation.value = generation

        assertSame(generation, subject.observeGeneration().first())
    }

    @Test
    fun savedFiltersAppearWithoutQueryingEveryCustomWall() = runTest {
        val group = CatalogueCustomGroup("eighties", "Eighties", fromYear = 1980, toYear = 1989)
        subject.setCustomGroups(listOf(group))
        queries.movieGenreCounts.value = mapOf(CatalogueGenre.ACTION to 4)

        assertEquals(
            listOf(
                CatalogueBrowseFacet(CatalogueBrowsePartition.CustomGroup(group.id), null, group.name),
                CatalogueBrowseFacet(CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION), 4),
            ),
            subject.observeGenreFacets(CatalogueMode.MOVIES).first(),
        )
        assertEquals(null, queries.lastCustomMovieSelection)
        assertEquals(null, queries.lastCustomSeriesSelection)
    }

    @Test
    fun savedFilterUsesADedicatedQueryAndKeepsTheSearchForBothModes() = runTest {
        val group = CatalogueCustomGroup("eighties", "Eighties", setOf(CatalogueGenre.ACTION), 1980, 1989, 7.0)
        subject.setCustomGroups(listOf(group))
        val partition = CatalogueBrowsePartition.CustomGroup(group.id)

        subject.observeEntries(CatalogueBrowseRequest(CatalogueMode.MOVIES, partition, "title")).first()
        subject.observeEntries(CatalogueBrowseRequest(CatalogueMode.SERIES, partition, "show")).first()

        assertEquals(group to "title", queries.lastCustomMovieSelection)
        assertEquals(group to "show", queries.lastCustomSeriesSelection)
        assertEquals(null, queries.lastMovieSelection)
        assertEquals(null, queries.lastSeriesSelection)
    }

    @Test
    fun removedSavedFilterNeverFallsThroughToTheWholeLibrary() = runTest {
        queries.movies.value = listOf(movieCard("Unrelated"))
        val result = subject.observeEntries(
            CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.CustomGroup("deleted")),
        ).first()

        assertEquals(emptyList<CatalogueBrowseEntry>(), result)
        assertEquals(null, queries.lastMovieSelection)
        assertEquals(null, queries.lastCustomMovieSelection)
    }

    @Test
    fun editingTheSelectedFilterReplacesItsQueryAndDeletingItCancelsCollection() = runTest {
        val subject = RepositoryCatalogueBrowseDataSource(queries, dispatcher = StandardTestDispatcher(testScheduler))
        val group = CatalogueCustomGroup("eighties", "Eighties", fromYear = 1980, toYear = 1989)
        subject.setCustomGroups(listOf(group))
        queries.movies.value = listOf(movieCard("Before edit"))
        val emissions = mutableListOf<List<CatalogueBrowseEntry>>()
        backgroundScope.launch {
            subject.observeEntries(
                CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.CustomGroup(group.id)),
            ).collect { emissions += it }
        }
        runCurrent()
        assertEquals(1, queries.movies.subscriptionCount.value)

        val edited = group.copy(fromYear = 1985)
        subject.setCustomGroups(listOf(edited))
        runCurrent()
        assertEquals(edited to "", queries.lastCustomMovieSelection)
        assertEquals(listOf(group, edited), queries.customMovieQueries.map { it.first })
        assertEquals(1, queries.movies.subscriptionCount.value)

        subject.setCustomGroups(listOf(edited, group.copy(id = "other", name = "Other")))
        runCurrent()
        assertEquals(2, queries.customMovieQueries.size)

        subject.setCustomGroups(emptyList())
        runCurrent()
        assertEquals(0, queries.movies.subscriptionCount.value)
        assertEquals(emptyList<CatalogueBrowseEntry>(), emissions.last())
        val emissionCount = emissions.size
        queries.movies.value = listOf(movieCard("Late result"))
        runCurrent()
        assertEquals(emissionCount, emissions.size)
        assertEquals(null, queries.lastMovieSelection)
    }
}

private data class QuerySelection(
    val category: String? = null,
    val genre: CatalogueGenre? = null,
    val unsorted: Boolean = false,
    val search: String = "",
)

private class FakeCatalogueBrowseQueries : CatalogueBrowseQueries {
    val generation = MutableStateFlow(CatalogueGeneration(emptyList()))
    val movieCategories = MutableStateFlow<List<CatalogueCategory>>(emptyList())
    val seriesCategories = MutableStateFlow<List<CatalogueCategory>>(emptyList())
    val movies = MutableStateFlow<List<VodMovieCard>>(emptyList())
    val series = MutableStateFlow<List<VodSeriesCard>>(emptyList())
    val movieHistory = MutableStateFlow<List<VodMovieCard>>(emptyList())
    val seriesHistory = MutableStateFlow<List<VodSeriesCard>>(emptyList())
    val movieGenreCounts = MutableStateFlow<Map<CatalogueGenre, Int>>(emptyMap())
    val seriesGenreCounts = MutableStateFlow<Map<CatalogueGenre, Int>>(emptyMap())
    val unsortedMovieCount = MutableStateFlow(0)
    val unsortedSeriesCount = MutableStateFlow(0)
    var lastMovieSelection: QuerySelection? = null
    var lastSeriesSelection: QuerySelection? = null
    var lastCustomMovieSelection: Pair<CatalogueCustomGroup, String>? = null
    var lastCustomSeriesSelection: Pair<CatalogueCustomGroup, String>? = null
    val customMovieQueries = mutableListOf<Pair<CatalogueCustomGroup, String>>()

    override fun observeGeneration(): Flow<CatalogueGeneration> = generation

    override fun observeMovieCategories(): Flow<List<CatalogueCategory>> = movieCategories

    override fun observeSeriesCategories(): Flow<List<CatalogueCategory>> = seriesCategories

    override fun observeMovieGenreCounts(): Flow<Map<CatalogueGenre, Int>> = movieGenreCounts

    override fun observeSeriesGenreCounts(): Flow<Map<CatalogueGenre, Int>> = seriesGenreCounts

    override fun observeUnsortedMovieCount(): Flow<Int> = unsortedMovieCount

    override fun observeUnsortedSeriesCount(): Flow<Int> = unsortedSeriesCount

    override fun observeMovieCards(
        category: String?,
        genre: CatalogueGenre?,
        unsorted: Boolean,
        search: String,
    ): Flow<List<VodMovieCard>> {
        lastMovieSelection = QuerySelection(category, genre, unsorted, search)
        return movies
    }

    override fun observeMovieHistoryCards(): Flow<List<VodMovieCard>> = movieHistory

    override fun observeSeriesCards(
        category: String?,
        genre: CatalogueGenre?,
        unsorted: Boolean,
        search: String,
    ): Flow<List<VodSeriesCard>> {
        lastSeriesSelection = QuerySelection(category, genre, unsorted, search)
        return series
    }

    override fun observeSeriesHistoryCards(): Flow<List<VodSeriesCard>> = seriesHistory

    override fun observeMovieCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodMovieCard>> {
        lastCustomMovieSelection = group to search
        customMovieQueries += group to search
        return movies
    }

    override fun observeSeriesCardsInCustomGroup(group: CatalogueCustomGroup, search: String): Flow<List<VodSeriesCard>> {
        lastCustomSeriesSelection = group to search
        return series
    }
}

private fun movieCard(title: String) = VodMovieCard(
    contentKey = "vod:movie:source:${title.hashCode()}",
    sourceId = "source",
    movieId = title.hashCode().toString(),
    name = title,
    categoryName = null,
    posterUrl = null,
    year = null,
    rating = null,
    genres = emptySet(),
    metadataOverride = null,
)

private fun seriesCard(title: String) = VodSeriesCard(
    sourceId = "source",
    seriesId = title.hashCode().toString(),
    name = title,
    categoryName = null,
    posterUrl = null,
    year = null,
    rating = null,
    genres = emptySet(),
    metadataOverride = null,
)
