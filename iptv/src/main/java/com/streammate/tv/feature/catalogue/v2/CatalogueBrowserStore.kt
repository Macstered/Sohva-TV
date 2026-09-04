package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.feature.catalogue.CatalogueGrouping
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.iptv.repository.CatalogueCategory
import com.streammate.tv.iptv.repository.CatalogueGeneration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogueWallLoadState {
    IDLE,
    LOADING,
    READY,
    FAILED,
}

enum class CatalogueBrowserFailureStage {
    GENERATION,
    PLAYLIST_GROUPS,
    GENRE_FACETS,
    WALL,
}

data class CatalogueBrowserFailure(
    val stage: CatalogueBrowserFailureStage,
    val message: String?,
)

data class CatalogueBrowserWall(
    val request: CatalogueBrowseRequest,
    val generation: CatalogueGeneration?,
    val entries: List<CatalogueBrowseEntry>,
    val primaryContentKeyByCopy: Map<String, String> = emptyMap(),
)

data class CatalogueBrowserState(
    val mode: CatalogueMode,
    val grouping: CatalogueGrouping = CatalogueGrouping.PLAYLIST,
    val allPlaylistGroups: List<CatalogueCategory> = emptyList(),
    val playlistGroups: List<CatalogueCategory> = emptyList(),
    val playlistGroupsReady: Boolean = false,
    val genreFacets: List<CatalogueBrowseFacet> = emptyList(),
    val genreFacetsReady: Boolean = false,
    val search: String = "",
    val selectedPartition: CatalogueBrowsePartition? = null,
    val wall: CatalogueBrowserWall? = null,
    val wallLoadState: CatalogueWallLoadState = CatalogueWallLoadState.IDLE,
    val latestGeneration: CatalogueGeneration? = null,
    val failure: CatalogueBrowserFailure? = null,
) {
    val wallIsCurrent: Boolean
        get() = wallLoadState == CatalogueWallLoadState.READY &&
            wall?.request == selectedPartition?.let { CatalogueBrowseRequest(mode, it, search) }

    val isShowingStaleWall: Boolean
        get() = wall != null && !wallIsCurrent
}

/**
 * Lifecycle-agnostic V2 state machine. Its owner supplies and cancels [scope].
 * Request identity is explicit, so a cancelled group's late result can never
 * replace the currently selected wall.
 */
class CatalogueBrowserStore(
    private val mode: CatalogueMode,
    private val dataSource: CatalogueBrowseDataSource,
    private val scope: CoroutineScope,
    initialPartition: CatalogueBrowsePartition? = null,
    initialState: CatalogueBrowserState? = null,
    private val deriveEntries: suspend (
        request: CatalogueBrowseRequest,
        entries: List<CatalogueBrowseEntry>,
    ) -> CatalogueDerivedEntries = { _, entries -> CatalogueDerivedEntries(entries) },
) {
    private val mutableState = MutableStateFlow(
        initialState
            ?.takeIf { it.mode == mode }
            ?.let { state ->
                if (state.selectedPartition == null && initialPartition != null) {
                    state.copy(selectedPartition = initialPartition)
                } else {
                    state
                }
            }
            ?: CatalogueBrowserState(
                mode = mode,
                selectedPartition = initialPartition,
            ),
    )
    val state: StateFlow<CatalogueBrowserState> = mutableState.asStateFlow()

    private val requestSerial = AtomicLong(0L)
    private var wallJob: Job? = null
    private var genreFacetsJob: Job? = null
    private var searchJob: Job? = null
    private var pendingEmptyWallJob: Job? = null
    private var pendingEmptyGroupsJob: Job? = null
    private var pendingEmptyWall: PendingEmptyWall? = null
    private var pendingEmptyGroups = false
    private var stableGroupsGeneration: CatalogueGeneration? = null
    private var automaticallySelectedAll = false
    private var lastPlaylistPartition = mutableState.value.selectedPartition
        as? CatalogueBrowsePartition.PlaylistGroup
    private var lastGenrePartition = mutableState.value.selectedPartition
        ?.takeIf { it.grouping() == CatalogueGrouping.GENRE }

    init {
        observeGeneration()
        observePlaylistGroups()
        mutableState.value.selectedPartition?.let(::startWallRequest)
        if (mutableState.value.grouping == CatalogueGrouping.GENRE) {
            ensureGenreFacetsObserved()
        }
    }

    fun selectPartition(partition: CatalogueBrowsePartition) {
        searchJob?.cancel()
        searchJob = null
        automaticallySelectedAll = false
        if (partition.grouping() == CatalogueGrouping.GENRE) {
            ensureGenreFacetsObserved()
        }
        rememberPartition(partition)
        startWallRequest(partition)
    }

    fun setSearch(search: String) {
        val boundedSearch = search.take(MAX_CATALOGUE_V2_SEARCH_LENGTH)
        if (mutableState.value.search == boundedSearch) return

        searchJob?.cancel()
        wallJob?.cancel()
        requestSerial.incrementAndGet()
        mutableState.update {
            it.copy(
                search = boundedSearch,
                wallLoadState = if (it.selectedPartition == null) {
                    CatalogueWallLoadState.IDLE
                } else {
                    CatalogueWallLoadState.LOADING
                },
            )
        }
        val selected = mutableState.value.selectedPartition ?: return
        searchJob = scope.launch {
            if (boundedSearch.isNotBlank()) delay(CATALOGUE_V2_SEARCH_DEBOUNCE_MILLIS)
            startWallRequest(selected)
        }
    }

    fun selectGrouping(grouping: CatalogueGrouping) {
        if (mutableState.value.grouping == grouping) return
        searchJob?.cancel()
        searchJob = null
        if (grouping == CatalogueGrouping.PLAYLIST) {
            genreFacetsJob?.cancel()
            genreFacetsJob = null
        }
        val target = when (grouping) {
            CatalogueGrouping.PLAYLIST -> lastPlaylistPartition
                ?.takeIf(::playlistPartitionStillExists)
                ?: mutableState.value.playlistGroups.firstOrNull()
                    ?.let { CatalogueBrowsePartition.PlaylistGroup(it.name) }
                ?: CatalogueBrowsePartition.PlaylistGroup(null)
                    .takeIf { mutableState.value.playlistGroupsReady }

            CatalogueGrouping.GENRE -> lastGenrePartition
                ?.takeIf(::genrePartitionStillExists)
                ?: mutableState.value.genreFacets.firstOrNull()?.partition
                // Facet counts are deliberately lazy. Start the first useful
                // wall immediately instead of making it wait behind two
                // whole-library count queries on Room's query executor.
                ?: CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION)
        }
        if (target == null) {
            wallJob?.cancel()
            pendingEmptyWallJob?.cancel()
            pendingEmptyWall = null
            mutableState.update {
                it.copy(
                    grouping = grouping,
                    selectedPartition = null,
                    wallLoadState = CatalogueWallLoadState.IDLE,
                )
            }
        } else {
            rememberPartition(target)
            startWallRequest(target)
        }
        if (grouping == CatalogueGrouping.GENRE) {
            // Queue counts after the selected wall. They decorate the rail;
            // they are not a prerequisite for browsing it.
            ensureGenreFacetsObserved()
        }
    }

    private fun observeGeneration() {
        scope.launch {
            dataSource.observeGeneration()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    recordFailure(CatalogueBrowserFailureStage.GENERATION, throwable)
                }
                .collect { generation ->
                    mutableState.update { current ->
                        val firstKnownGeneration = current.latestGeneration == null
                        current.copy(
                            latestGeneration = generation,
                            // The first generation and first non-empty wall are
                            // two observations of the same already-open DB.
                            // Later generations are not applied until the card
                            // query itself emits.
                            wall = current.wall?.let { wall ->
                                if (
                                    firstKnownGeneration &&
                                    wall.generation == null &&
                                    wall.entries.isNotEmpty()
                                ) {
                                    wall.copy(generation = generation)
                                } else {
                                    wall
                                }
                            },
                        )
                    }
                    if (stableGroupsGeneration == null && mutableState.value.playlistGroups.isNotEmpty()) {
                        stableGroupsGeneration = generation
                    }
                    scheduleConfirmedEmptyGroups()
                    scheduleConfirmedEmptyWall()
                }
        }
    }

    private fun observePlaylistGroups() {
        scope.launch {
            dataSource.observePlaylistGroupSnapshot(mode)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    recordFailure(CatalogueBrowserFailureStage.PLAYLIST_GROUPS, throwable)
                }
                .collect(::onPlaylistGroups)
        }
    }

    private fun ensureGenreFacetsObserved() {
        if (genreFacetsJob?.isActive == true) return
        genreFacetsJob = scope.launch {
            dataSource.observeGenreFacets(mode)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    recordFailure(CatalogueBrowserFailureStage.GENRE_FACETS, throwable)
                }
                .collect(::acceptGenreFacets)
        }
    }

    private fun onPlaylistGroups(snapshot: CataloguePlaylistGroupSnapshot) {
        val groups = snapshot.visible
        // A non-empty complete list with no visible rows means the viewer hid
        // every group. That is authoritative even though the catalogue
        // generation did not change; only a wholly empty Room emission needs
        // the import invalidation grace period below.
        if (
            snapshot.all.isNotEmpty() ||
            groups.isNotEmpty() ||
            mutableState.value.allPlaylistGroups.isEmpty()
        ) {
            pendingEmptyGroupsJob?.cancel()
            pendingEmptyGroups = false
            acceptPlaylistGroups(snapshot)
            return
        }

        // Room invalidates the category query together with import-state and
        // card queries. A short-lived empty result must not tear down the rail
        // or reset its selection in the middle of that hand-off.
        pendingEmptyGroups = true
        scheduleConfirmedEmptyGroups()
    }

    private fun scheduleConfirmedEmptyGroups() {
        if (!pendingEmptyGroups) return
        val generation = mutableState.value.latestGeneration ?: return
        // Category membership is derived only from active catalogue rows. If
        // their generation did not change, an empty list is not authoritative.
        if (generation == stableGroupsGeneration) return
        pendingEmptyGroupsJob?.cancel()
        pendingEmptyGroupsJob = scope.launch {
            delay(TRANSIENT_EMPTY_GRACE_MILLIS)
            if (
                pendingEmptyGroups &&
                mutableState.value.latestGeneration != stableGroupsGeneration
            ) {
                pendingEmptyGroups = false
                acceptPlaylistGroups(CataloguePlaylistGroupSnapshot(emptyList(), emptyList()))
            }
        }
    }

    private fun acceptPlaylistGroups(snapshot: CataloguePlaylistGroupSnapshot) {
        val groups = snapshot.visible
        stableGroupsGeneration = mutableState.value.latestGeneration
        mutableState.update { current ->
            current.copy(
                allPlaylistGroups = snapshot.all,
                playlistGroups = groups,
                playlistGroupsReady = true,
            )
        }

        val selected = mutableState.value.selectedPartition
        when {
            selected == null && mutableState.value.grouping == CatalogueGrouping.PLAYLIST ->
                selectFirstGroupOrAll(groups)
            selected is CatalogueBrowsePartition.PlaylistGroup && selected.name == null &&
                automaticallySelectedAll && groups.isNotEmpty() -> {
                automaticallySelectedAll = false
                startWallRequest(CatalogueBrowsePartition.PlaylistGroup(groups.first().name))
            }
            selected is CatalogueBrowsePartition.PlaylistGroup && selected.name != null &&
                groups.isEmpty() && snapshot.all.isNotEmpty() -> {
                automaticallySelectedAll = true
                startWallRequest(CatalogueBrowsePartition.PlaylistGroup(null))
            }
            selected is CatalogueBrowsePartition.PlaylistGroup && selected.name != null &&
                groups.isNotEmpty() && groups.none { it.name.equals(selected.name, ignoreCase = true) } -> {
                startWallRequest(CatalogueBrowsePartition.PlaylistGroup(groups.first().name))
            }
        }
    }

    private fun selectFirstGroupOrAll(groups: List<CatalogueCategory>) {
        automaticallySelectedAll = groups.isEmpty()
        val partition = CatalogueBrowsePartition.PlaylistGroup(groups.firstOrNull()?.name)
        rememberPartition(partition)
        startWallRequest(partition)
    }

    private fun acceptGenreFacets(facets: List<CatalogueBrowseFacet>) {
        mutableState.update {
            it.copy(
                genreFacets = facets,
                genreFacetsReady = true,
            )
        }
        if (mutableState.value.grouping != CatalogueGrouping.GENRE) return
        val selected = mutableState.value.selectedPartition
        if (selected == null || !genrePartitionStillExists(selected)) {
            val partition = facets.firstOrNull()?.partition
            if (partition == null) {
                // A deleted saved filter must not remain selected forever.
                // Empty genre counts alone must not switch rails: they can
                // arrive before the first indexed genre wall is ready.
                if (selected is CatalogueBrowsePartition.CustomGroup) {
                    selectFirstGroupOrAll(mutableState.value.playlistGroups)
                }
            } else {
                rememberPartition(partition)
                startWallRequest(partition)
            }
        }
    }

    private fun startWallRequest(partition: CatalogueBrowsePartition) {
        val request = CatalogueBrowseRequest(mode, partition, mutableState.value.search)
        val current = mutableState.value
        if (current.selectedPartition == partition && wallJob?.isActive == true) return

        val serial = requestSerial.incrementAndGet()
        wallJob?.cancel()
        pendingEmptyWallJob?.cancel()
        pendingEmptyWall = null
        mutableState.update {
            it.copy(
                grouping = if (partition == CatalogueBrowsePartition.History) {
                    it.grouping
                } else {
                    partition.grouping()
                },
                selectedPartition = partition,
                wallLoadState = CatalogueWallLoadState.LOADING,
                failure = it.failure?.takeUnless { failure ->
                    failure.stage == CatalogueBrowserFailureStage.WALL
                },
            )
        }
        wallJob = scope.launch {
            dataSource.observeEntries(request)
                .map { entries -> deriveEntries(request, entries) }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (isCurrent(serial, request)) {
                        mutableState.update {
                            it.copy(
                                wallLoadState = CatalogueWallLoadState.FAILED,
                                failure = CatalogueBrowserFailure(
                                    CatalogueBrowserFailureStage.WALL,
                                    throwable.message,
                                ),
                            )
                        }
                    }
                }
                .collect { entries -> publishOrHoldEmpty(serial, request, entries) }
        }
    }

    private fun publishOrHoldEmpty(
        serial: Long,
        request: CatalogueBrowseRequest,
        derived: CatalogueDerivedEntries,
    ) {
        if (!isCurrent(serial, request)) return
        pendingEmptyWallJob?.cancel()
        pendingEmptyWall = null
        val entries = derived.entries

        val expectedCount = request.search.takeIf(String::isBlank)
            ?.let { request.partition as? CatalogueBrowsePartition.PlaylistGroup }
            ?.name
            ?.let { selectedName ->
                mutableState.value.playlistGroups.firstOrNull {
                    it.name.equals(selectedName, ignoreCase = true)
                }?.count
            }
        if (entries.isEmpty() && expectedCount != null && expectedCount > 0) {
            pendingEmptyWall = PendingEmptyWall(serial, request)
            scheduleConfirmedEmptyWall()
            return
        }
        publishWall(serial, request, derived)
    }

    private fun scheduleConfirmedEmptyWall() {
        val pending = pendingEmptyWall ?: return
        if (!isCurrent(pending.serial, pending.request)) {
            pendingEmptyWall = null
            return
        }
        val currentWall = mutableState.value.wall
        val retainingCurrentWall = currentWall?.request == pending.request &&
            currentWall.entries.isNotEmpty()
        val generationChanged = mutableState.value.latestGeneration != null &&
            currentWall?.generation != mutableState.value.latestGeneration
        if (retainingCurrentWall && !generationChanged) return

        pendingEmptyWallJob?.cancel()
        pendingEmptyWallJob = scope.launch {
            delay(TRANSIENT_EMPTY_GRACE_MILLIS)
            if (pendingEmptyWall == pending) {
                pendingEmptyWall = null
                publishWall(pending.serial, pending.request, CatalogueDerivedEntries(emptyList()))
            }
        }
    }

    private fun publishWall(
        serial: Long,
        request: CatalogueBrowseRequest,
        derived: CatalogueDerivedEntries,
    ) {
        if (!isCurrent(serial, request)) return
        mutableState.update {
            it.copy(
                wall = CatalogueBrowserWall(
                    request = request,
                    generation = it.latestGeneration,
                    entries = derived.entries,
                    primaryContentKeyByCopy = derived.primaryContentKeyByCopy,
                ),
                wallLoadState = CatalogueWallLoadState.READY,
                failure = it.failure?.takeUnless { failure ->
                    failure.stage == CatalogueBrowserFailureStage.WALL
                },
            )
        }
    }

    private fun isCurrent(serial: Long, request: CatalogueBrowseRequest): Boolean =
        serial == requestSerial.get() &&
            mutableState.value.selectedPartition == request.partition

    private fun recordFailure(stage: CatalogueBrowserFailureStage, throwable: Throwable) {
        mutableState.update {
            it.copy(failure = CatalogueBrowserFailure(stage, throwable.message))
        }
    }

    private fun rememberPartition(partition: CatalogueBrowsePartition) {
        when (partition) {
            CatalogueBrowsePartition.History -> Unit
            is CatalogueBrowsePartition.PlaylistGroup -> lastPlaylistPartition = partition
            is CatalogueBrowsePartition.Genre, is CatalogueBrowsePartition.CustomGroup, CatalogueBrowsePartition.Unsorted ->
                lastGenrePartition = partition
        }
    }

    private fun playlistPartitionStillExists(partition: CatalogueBrowsePartition.PlaylistGroup): Boolean =
        partition.name == null || mutableState.value.playlistGroups.any {
            it.name.equals(partition.name, ignoreCase = true)
        }

    private fun genrePartitionStillExists(partition: CatalogueBrowsePartition): Boolean =
        mutableState.value.genreFacets.any { it.partition == partition }
}

private fun CatalogueBrowsePartition.grouping(): CatalogueGrouping = when (this) {
    CatalogueBrowsePartition.History -> CatalogueGrouping.PLAYLIST
    is CatalogueBrowsePartition.PlaylistGroup -> CatalogueGrouping.PLAYLIST
    is CatalogueBrowsePartition.Genre, is CatalogueBrowsePartition.CustomGroup, CatalogueBrowsePartition.Unsorted -> CatalogueGrouping.GENRE
}

internal const val TRANSIENT_EMPTY_GRACE_MILLIS = 500L
internal const val CATALOGUE_V2_SEARCH_DEBOUNCE_MILLIS = 250L
internal const val MAX_CATALOGUE_V2_SEARCH_LENGTH = 80

private data class PendingEmptyWall(
    val serial: Long,
    val request: CatalogueBrowseRequest,
)
