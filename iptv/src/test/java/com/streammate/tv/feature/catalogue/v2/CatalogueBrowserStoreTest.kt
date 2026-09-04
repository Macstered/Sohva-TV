package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.catalogue.CatalogueGrouping
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueCategory
import com.streammate.tv.iptv.repository.CatalogueGeneration
import com.streammate.tv.iptv.repository.CatalogueSourceGeneration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueBrowserStoreTest {
    @Test
    fun historyCanBeTheInitialSliceWithoutBeingReplacedByProviderGroups() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 2)),
        )

        val store = CatalogueBrowserStore(
            mode = CatalogueMode.MOVIES,
            dataSource = dataSource,
            scope = backgroundScope,
            initialPartition = CatalogueBrowsePartition.History,
            initialState = CatalogueBrowserState(CatalogueMode.MOVIES),
        )
        runCurrent()

        assertEquals(CatalogueBrowsePartition.History, store.state.value.selectedPartition)
        assertEquals(
            CatalogueBrowsePartition.History,
            dataSource.observedRequests.single().partition,
        )
    }

    @Test
    fun allHiddenGroupsRemainEditableWhileBrowseFallsBackToTheWholeLibrary() = runTest {
        val action = CatalogueCategory("Action", 2)
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = emptyList(),
            initialGroupSnapshot = CataloguePlaylistGroupSnapshot(
                all = listOf(action),
                visible = emptyList(),
            ),
        )

        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        assertEquals(listOf(action), store.state.value.allPlaylistGroups)
        assertTrue(store.state.value.playlistGroups.isEmpty())
        assertEquals(CatalogueBrowsePartition.PlaylistGroup(null), store.state.value.selectedPartition)
    }

    @Test
    fun hidingTheLastVisibleSelectedGroupMovesTheExistingStoreToTheWholeLibrary() = runTest {
        val action = CatalogueCategory("Action", 2)
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(action),
            initialGroupSnapshot = CataloguePlaylistGroupSnapshot(
                all = listOf(action),
                visible = listOf(action),
            ),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()
        assertEquals(CatalogueBrowsePartition.PlaylistGroup("Action"), store.state.value.selectedPartition)

        dataSource.emitGroupSnapshot(
            CataloguePlaylistGroupSnapshot(all = listOf(action), visible = emptyList()),
        )
        runCurrent()

        assertEquals(listOf(action), store.state.value.allPlaylistGroups)
        assertEquals(CatalogueBrowsePartition.PlaylistGroup(null), store.state.value.selectedPartition)
        assertEquals(CatalogueBrowsePartition.PlaylistGroup(null), dataSource.observedRequests.last().partition)
    }

    @Test
    fun firstPlaylistGroupBecomesTheInitialDatabaseSlice() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 2), CatalogueCategory("Drama", 1)),
        )
        val request = movieGroup("Action")
        dataSource.wall(request).tryEmit(listOf(entry("a"), entry("b")))

        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        assertEquals(request.partition, store.state.value.selectedPartition)
        assertEquals(listOf("a", "b"), store.state.value.wall?.entries?.map { it.contentKey })
        assertTrue(store.state.value.wallIsCurrent)
        assertEquals(listOf(request), dataSource.observedRequests)
        assertEquals(0, dataSource.genreFacetObservationCount)
    }

    @Test
    fun changingGroupKeepsTheOldWallUntilTheNewRequestEmits() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 1), CatalogueCategory("Drama", 1)),
        )
        val action = movieGroup("Action")
        val drama = movieGroup("Drama")
        dataSource.wall(action).tryEmit(listOf(entry("action")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.selectPartition(drama.partition)
        runCurrent()

        assertEquals("action", store.state.value.wall?.entries?.single()?.contentKey)
        assertEquals(CatalogueWallLoadState.LOADING, store.state.value.wallLoadState)
        assertTrue(store.state.value.isShowingStaleWall)

        // A cancelled request cannot replace the selected wall.
        dataSource.wall(action).tryEmit(listOf(entry("late-action")))
        runCurrent()
        assertEquals("action", store.state.value.wall?.entries?.single()?.contentKey)

        dataSource.wall(drama).tryEmit(listOf(entry("drama")))
        runCurrent()
        assertEquals("drama", store.state.value.wall?.entries?.single()?.contentKey)
        assertTrue(store.state.value.wallIsCurrent)
    }

    @Test
    fun shortEmptyInvalidationsDoNotClearRailSelectionOrWall() = runTest {
        val actionRow = CatalogueCategory("Action", 1)
        val dataSource = FakeCatalogueBrowseDataSource(initialGroups = listOf(actionRow))
        val action = movieGroup("Action")
        dataSource.wall(action).tryEmit(listOf(entry("action")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        dataSource.groups.value = emptyList()
        dataSource.wall(action).tryEmit(emptyList())
        runCurrent()
        advanceTimeBy(TRANSIENT_EMPTY_GRACE_MILLIS - 1)
        runCurrent()

        assertEquals(listOf(actionRow), store.state.value.playlistGroups)
        assertEquals(action.partition, store.state.value.selectedPartition)
        assertEquals("action", store.state.value.wall?.entries?.single()?.contentKey)
        assertTrue(store.state.value.wallIsCurrent)

        dataSource.groups.value = listOf(actionRow)
        dataSource.wall(action).tryEmit(listOf(entry("action-2")))
        runCurrent()
        advanceTimeBy(TRANSIENT_EMPTY_GRACE_MILLIS)
        runCurrent()

        assertEquals("action-2", store.state.value.wall?.entries?.single()?.contentKey)
        assertFalse(store.state.value.playlistGroups.isEmpty())
    }

    @Test
    fun unexplainedPersistentEmptyCannotClearAStableWall() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 1)),
        )
        val action = movieGroup("Action")
        dataSource.wall(action).tryEmit(listOf(entry("action")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        dataSource.wall(action).tryEmit(emptyList())
        runCurrent()
        advanceTimeBy(TRANSIENT_EMPTY_GRACE_MILLIS * 2)
        runCurrent()

        assertEquals("action", store.state.value.wall?.entries?.single()?.contentKey)
        assertEquals(CatalogueWallLoadState.READY, store.state.value.wallLoadState)
        assertTrue(store.state.value.wallIsCurrent)
    }

    @Test
    fun newDatabaseGenerationCanConfirmARealEmptySliceAfterGrace() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 1)),
        )
        val action = movieGroup("Action")
        dataSource.wall(action).tryEmit(listOf(entry("action")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        dataSource.wall(action).tryEmit(emptyList())
        dataSource.generation.value = CatalogueGeneration(
            listOf(CatalogueSourceGeneration("source", "snapshot-2", 2, 0)),
        )
        runCurrent()
        advanceTimeBy(TRANSIENT_EMPTY_GRACE_MILLIS)
        runCurrent()

        assertTrue(store.state.value.wall?.entries?.isEmpty() == true)
        assertEquals(CatalogueWallLoadState.READY, store.state.value.wallLoadState)
        assertTrue(store.state.value.wallIsCurrent)
    }

    @Test
    fun authoritativeNonEmptyGroupListFallsBackWhenSelectionWasRemoved() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Removed", 1)),
        )
        val removed = movieGroup("Removed")
        val remaining = movieGroup("Remaining")
        dataSource.wall(removed).tryEmit(listOf(entry("old")))
        dataSource.wall(remaining).tryEmit(listOf(entry("new")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        dataSource.groups.value = listOf(CatalogueCategory("Remaining", 1))
        runCurrent()

        assertEquals(remaining.partition, store.state.value.selectedPartition)
        assertEquals("new", store.state.value.wall?.entries?.single()?.contentKey)
        assertTrue(store.state.value.wallIsCurrent)
    }

    @Test
    fun groupingToggleUsesIndexedGenrePartitionsAndRestoresEachRailSelection() = runTest {
        val actionGroup = CatalogueBrowsePartition.PlaylistGroup("Action group")
        val dramaGroup = CatalogueBrowsePartition.PlaylistGroup("Drama group")
        val actionGenre = CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION)
        val dramaGenre = CatalogueBrowsePartition.Genre(CatalogueGenre.DRAMA)
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(
                CatalogueCategory("Action group", 2),
                CatalogueCategory("Drama group", 1),
            ),
            initialFacets = listOf(
                CatalogueBrowseFacet(actionGenre, 4),
                CatalogueBrowseFacet(dramaGenre, 9),
            ),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.selectGrouping(CatalogueGrouping.GENRE)
        runCurrent()
        assertEquals(CatalogueGrouping.GENRE, store.state.value.grouping)
        assertEquals(actionGenre, store.state.value.selectedPartition)
        assertEquals(1, dataSource.genreFacetObservationCount)

        store.selectPartition(dramaGenre)
        runCurrent()
        store.selectGrouping(CatalogueGrouping.PLAYLIST)
        store.selectPartition(dramaGroup)
        runCurrent()
        store.selectGrouping(CatalogueGrouping.GENRE)
        assertEquals(dramaGenre, store.state.value.selectedPartition)

        store.selectGrouping(CatalogueGrouping.PLAYLIST)
        runCurrent()
        assertEquals(dramaGroup, store.state.value.selectedPartition)
        assertTrue(dataSource.observedRequests.any { it.partition == dramaGenre })
        assertTrue(dataSource.observedRequests.any { it.partition == dramaGroup })
        assertTrue(dataSource.observedRequests.any { it.partition == actionGroup })
    }

    @Test
    fun initialSavedFilterStartsItsWallAndObservesTheGenreRail() = runTest {
        val custom = CatalogueBrowsePartition.CustomGroup("eighties")
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Provider", 3)),
            initialFacets = listOf(CatalogueBrowseFacet(custom, null, "Eighties")),
        )
        val request = CatalogueBrowseRequest(CatalogueMode.MOVIES, custom)
        dataSource.wall(request).tryEmit(listOf(entry("custom")))

        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope, custom)
        runCurrent()

        assertEquals(CatalogueGrouping.GENRE, store.state.value.grouping)
        assertEquals(custom, store.state.value.selectedPartition)
        assertEquals("custom", store.state.value.wall?.entries?.single()?.contentKey)
        assertEquals(1, dataSource.genreFacetObservationCount)
        assertEquals(listOf(request), dataSource.observedRequests)
    }

    @Test
    fun savedFilterSelectionSurvivesRailSwitchesAndFallsBackWhenDeleted() = runTest {
        val custom = CatalogueBrowsePartition.CustomGroup("eighties")
        val genre = CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION)
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Provider", 3)),
            initialFacets = listOf(
                CatalogueBrowseFacet(custom, null, "Eighties"),
                CatalogueBrowseFacet(genre, 3),
            ),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()
        store.selectPartition(custom)
        runCurrent()
        store.selectGrouping(CatalogueGrouping.PLAYLIST)
        runCurrent()
        store.selectGrouping(CatalogueGrouping.GENRE)
        runCurrent()
        assertEquals(custom, store.state.value.selectedPartition)

        dataSource.facets.value = listOf(CatalogueBrowseFacet(genre, 3))
        runCurrent()
        assertEquals(genre, store.state.value.selectedPartition)
        assertEquals(CatalogueGrouping.GENRE, store.state.value.grouping)
    }

    @Test
    fun deletingTheOnlySavedFilterReturnsToAnExistingProviderGroup() = runTest {
        val custom = CatalogueBrowsePartition.CustomGroup("eighties")
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Provider", 3)),
            initialFacets = listOf(CatalogueBrowseFacet(custom, null, "Eighties")),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope, custom)
        runCurrent()

        dataSource.facets.value = emptyList()
        runCurrent()

        assertEquals(CatalogueBrowsePartition.PlaylistGroup("Provider"), store.state.value.selectedPartition)
        assertEquals(CatalogueGrouping.PLAYLIST, store.state.value.grouping)
    }

    @Test
    fun firstGenreWallDoesNotWaitForFacetCounts() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Provider group", 1)),
        )
        val action = CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION)
        dataSource.wall(CatalogueBrowseRequest(CatalogueMode.MOVIES, action))
            .tryEmit(listOf(entry("action")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.selectGrouping(CatalogueGrouping.GENRE)
        runCurrent()

        assertEquals(action, store.state.value.selectedPartition)
        assertEquals("action", store.state.value.wall?.entries?.single()?.contentKey)
        assertTrue(store.state.value.wallIsCurrent)
        assertTrue(store.state.value.genreFacets.isEmpty())
        assertEquals(1, dataSource.genreFacetObservationCount)
    }

    @Test
    fun searchDebouncesDatabaseRequestsAndKeepsTheLastWallUntilTheResultArrives() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Provider group", 2)),
        )
        val partition = CatalogueBrowsePartition.PlaylistGroup("Provider group")
        val unfiltered = CatalogueBrowseRequest(CatalogueMode.MOVIES, partition)
        dataSource.wall(unfiltered).tryEmit(listOf(entry("old-a"), entry("old-b")))
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.setSearch("12")
        runCurrent()

        assertEquals("12", store.state.value.search)
        assertEquals(CatalogueWallLoadState.LOADING, store.state.value.wallLoadState)
        assertEquals(listOf("old-a", "old-b"), store.state.value.wall?.entries?.map { it.contentKey })
        assertTrue(store.state.value.isShowingStaleWall)
        assertEquals(listOf(unfiltered), dataSource.observedRequests)

        advanceTimeBy(CATALOGUE_V2_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val filtered = CatalogueBrowseRequest(CatalogueMode.MOVIES, partition, "12")
        assertEquals(filtered, dataSource.observedRequests.last())

        dataSource.wall(filtered).tryEmit(listOf(entry("12 Strong")))
        runCurrent()
        assertEquals(listOf("12 Strong"), store.state.value.wall?.entries?.map { it.contentKey })
        assertTrue(store.state.value.wallIsCurrent)
    }

    @Test
    fun clearingSearchUpdatesRetainedStateBeforeTheReplacementWallLoads() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(CatalogueCategory("Action", 2)),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.setSearch("SpongeBob")
        assertEquals("SpongeBob", store.state.value.search)

        store.setSearch("")
        assertEquals("", store.state.value.search)
        assertEquals(CatalogueWallLoadState.LOADING, store.state.value.wallLoadState)
    }

    @Test
    fun changingPartitionCancelsAQueuedSearchForTheOldPartition() = runTest {
        val dataSource = FakeCatalogueBrowseDataSource(
            initialGroups = listOf(
                CatalogueCategory("Action", 2),
                CatalogueCategory("Drama", 2),
            ),
        )
        val store = CatalogueBrowserStore(CatalogueMode.MOVIES, dataSource, backgroundScope)
        runCurrent()

        store.setSearch("one")
        store.selectPartition(CatalogueBrowsePartition.PlaylistGroup("Drama"))
        runCurrent()
        advanceTimeBy(CATALOGUE_V2_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(
            CatalogueBrowseRequest(
                CatalogueMode.MOVIES,
                CatalogueBrowsePartition.PlaylistGroup("Drama"),
                "one",
            ),
            dataSource.observedRequests.last(),
        )
        assertFalse(
            dataSource.observedRequests.any {
                it.partition == CatalogueBrowsePartition.PlaylistGroup("Action") &&
                    it.search == "one"
            },
        )
    }

    private fun movieGroup(name: String) = CatalogueBrowseRequest(
        CatalogueMode.MOVIES,
        CatalogueBrowsePartition.PlaylistGroup(name),
    )

    private fun entry(key: String) = CatalogueBrowseEntry(
        contentKey = key,
        target = CatalogueBrowseTarget.Movie("source", key),
        providerTitle = key,
        playlistGroup = "Action",
        providerPosterUrl = null,
        year = null,
        rating = null,
        genres = emptySet(),
        metadataOverride = null,
    )
}

private class FakeCatalogueBrowseDataSource(
    initialGroups: List<CatalogueCategory>,
    initialFacets: List<CatalogueBrowseFacet> = emptyList(),
    initialGroupSnapshot: CataloguePlaylistGroupSnapshot? = null,
) : CatalogueBrowseDataSource {
    val generation = MutableStateFlow(CatalogueGeneration(emptyList()))
    val groups = MutableStateFlow(initialGroups)
    private val groupSnapshots = initialGroupSnapshot?.let(::MutableStateFlow)
    val facets = MutableStateFlow(initialFacets)
    val observedRequests = mutableListOf<CatalogueBrowseRequest>()
    var genreFacetObservationCount = 0
    private val walls = mutableMapOf<CatalogueBrowseRequest, MutableSharedFlow<List<CatalogueBrowseEntry>>>()

    override fun observeGeneration(): Flow<CatalogueGeneration> = generation

    override fun observePlaylistGroups(mode: CatalogueMode): Flow<List<CatalogueCategory>> = groups

    override fun observePlaylistGroupSnapshot(
        mode: CatalogueMode,
    ): Flow<CataloguePlaylistGroupSnapshot> = groupSnapshots ?: groups.map { current ->
        CataloguePlaylistGroupSnapshot(all = current, visible = current)
    }

    override fun observeGenreFacets(mode: CatalogueMode): Flow<List<CatalogueBrowseFacet>> {
        genreFacetObservationCount += 1
        return facets
    }

    override fun observeEntries(request: CatalogueBrowseRequest): Flow<List<CatalogueBrowseEntry>> {
        observedRequests += request
        return wall(request)
    }

    fun wall(request: CatalogueBrowseRequest): MutableSharedFlow<List<CatalogueBrowseEntry>> =
        walls.getOrPut(request) { MutableSharedFlow(replay = 1) }

    fun emitGroupSnapshot(snapshot: CataloguePlaylistGroupSnapshot) {
        checkNotNull(groupSnapshots) { "This fake was not configured with explicit group snapshots" }
            .value = snapshot
    }
}
