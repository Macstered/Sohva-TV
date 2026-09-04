package com.streammate.tv.feature.catalogue.v2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.error.userMessage
import com.streammate.tv.feature.catalogue.CatalogueGrouping
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.feature.catalogue.CataloguePoster
import com.streammate.tv.feature.catalogue.catalogueGenreLabel
import com.streammate.tv.feature.catalogue.catalogueQualityTags
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.TvTagChip
import com.streammate.tv.feature.common.TvTagTone
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.repository.CatalogueRepository
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * Production catalogue browser. The repository boundary keeps database work
 * and copy folding out of Compose; a session retains only cheap browsing state.
 */
@Composable
fun CatalogueBrowserV2(
    mode: CatalogueMode,
    repository: CatalogueRepository,
    preferredCopy: CataloguePreferredCopy,
    onOpenEntry: (CatalogueBrowseEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPartition: CatalogueBrowsePartition? = CatalogueBrowsePartition.History,
    session: CatalogueBrowserSession? = null,
    hiddenPlaylistGroups: Set<String> = emptySet(),
    onRefresh: suspend () -> Result<String> = { Result.success("") },
    onSetPlaylistGroupHidden: suspend (String, Boolean) -> Unit = { _, _ -> },
    onManageGroups: ((String?) -> Unit)? = null,
    customGroups: List<CatalogueCustomGroup> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val localSession = remember(mode) { CatalogueBrowserSession(mode) }
    val activeSession = session ?: localSession
    val dataSource = remember(repository) {
        RepositoryCatalogueBrowseDataSource(repository, hiddenPlaylistGroups, customGroups)
    }
    LaunchedEffect(dataSource, hiddenPlaylistGroups) {
        dataSource.setHiddenPlaylistGroups(hiddenPlaylistGroups)
    }
    LaunchedEffect(dataSource, customGroups) {
        dataSource.setCustomGroups(customGroups)
    }
    val deriver = remember(preferredCopy) { CatalogueBrowseDeriver(preferredCopy) }
    val store = remember(mode, dataSource, scope, initialPartition, deriver) {
        CatalogueBrowserStore(
            mode = mode,
            dataSource = dataSource,
            scope = scope,
            initialPartition = initialPartition,
            initialState = activeSession.snapshot,
            deriveEntries = deriver::derive,
        )
    }
    DisposableEffect(store, activeSession) {
        onDispose { activeSession.snapshot = store.state.value }
    }
    val state by store.state.collectAsStateWithLifecycle()
    val organizationState by remember(repository) { repository.organization?.state ?: kotlinx.coroutines.flow.flowOf(com.streammate.tv.iptv.repository.OrganizationReadState()) }.collectAsStateWithLifecycle(initialValue = com.streammate.tv.iptv.repository.OrganizationReadState())
    val room = if (mode == CatalogueMode.MOVIES) com.streammate.tv.core.model.LibraryRoom.MOVIES else com.streammate.tv.core.model.LibraryRoom.SERIES
    val historyEnabled = organizationState.organization.shortcutEnabled(room, com.streammate.tv.core.model.ORGANIZATION_HISTORY)
    LaunchedEffect(historyEnabled, state.playlistGroupsReady) {
        if (!historyEnabled && state.selectedPartition == CatalogueBrowsePartition.History && state.playlistGroupsReady) store.selectPartition(CatalogueBrowsePartition.PlaylistGroup(state.playlistGroups.firstOrNull()?.name))
    }
    fun clearCompletedSearch() {
        if (state.search.isNotEmpty()) store.setSearch("")
    }
    CatalogueBrowserV2Screen(
        state = state,
        historyEnabled = historyEnabled,
        organization = organizationState.organization,
        onManageGroups = onManageGroups?.let { manage -> { group -> activeSession.restoreFocusToOptions = true; manage(group) } },
        onSearchChange = store::setSearch,
        onSelectGrouping = store::selectGrouping,
        onSelectPartition = store::selectPartition,
        onOpenEntry = { entry ->
            clearCompletedSearch()
            onOpenEntry(entry)
        },
        onBack = {
            clearCompletedSearch()
            onBack()
        },
        hiddenPlaylistGroups = hiddenPlaylistGroups,
        onRefresh = onRefresh,
        onSetPlaylistGroupHidden = onSetPlaylistGroupHidden,
        modifier = modifier,
        session = activeSession,
    )
}

/** Cheap retained state; it owns no jobs, database flows, or Android lifecycle. */
class CatalogueBrowserSession(mode: CatalogueMode) {
    internal var snapshot: CatalogueBrowserState = CatalogueBrowserState(mode)
    internal var groupFirstVisibleItemIndex: Int = 0
    internal var groupFirstVisibleItemScrollOffset: Int = 0
    internal var wallFirstVisibleItemIndex: Int = 0
    internal var wallFirstVisibleItemScrollOffset: Int = 0
    internal var focusedEntryContentKey: String? = null
    internal var restoreFocusToWall: Boolean = false
    internal var initialHistoryFocusPending: Boolean = true
    internal var restoreFocusToOptions: Boolean = false
}

@Composable
fun CatalogueBrowserV2Screen(
    state: CatalogueBrowserState,
    historyEnabled: Boolean = true,
    organization: com.streammate.tv.core.model.LibraryOrganization = com.streammate.tv.core.model.LibraryOrganization(),
    onSearchChange: (String) -> Unit = {},
    onSelectGrouping: (CatalogueGrouping) -> Unit = {},
    onSelectPartition: (CatalogueBrowsePartition) -> Unit,
    onOpenEntry: (CatalogueBrowseEntry) -> Unit,
    onBack: () -> Unit,
    hiddenPlaylistGroups: Set<String> = emptySet(),
    onRefresh: suspend () -> Result<String> = { Result.success("") },
    onSetPlaylistGroupHidden: suspend (String, Boolean) -> Unit = { _, _ -> },
    onManageGroups: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    session: CatalogueBrowserSession? = null,
) {
    val fallbackSession = remember(state.mode) { CatalogueBrowserSession(state.mode) }
    val activeSession = session ?: fallbackSession
    val groupListState = rememberLazyListState(
        initialFirstVisibleItemIndex = activeSession.groupFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = activeSession.groupFirstVisibleItemScrollOffset,
    )
    val wallGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = activeSession.wallFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = activeSession.wallFirstVisibleItemScrollOffset,
    )
    var pendingWallFocusKey by remember(activeSession) {
        mutableStateOf(
            activeSession.focusedEntryContentKey.takeIf { activeSession.restoreFocusToWall },
        )
    }
    val restoredWallFocusRequester = remember(activeSession) { FocusRequester() }
    val historyFocusRequester = remember(activeSession) { FocusRequester() }
    val wallReturnFocusRequester = remember { FocusRequester() }
    val optionsButtonFocusRequester = remember { FocusRequester() }
    val optionsFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var optionsVisible by remember { mutableStateOf(false) }
    var categoryEditMode by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    val selectedGroup = (state.selectedPartition as? CatalogueBrowsePartition.PlaylistGroup)?.name
    val selectedFacet = state.genreFacets.firstOrNull { it.partition == state.selectedPartition }
    val selectedLabel = when (val partition = state.selectedPartition) {
        CatalogueBrowsePartition.History -> stringResource(R.string.catalogue_history)
        is CatalogueBrowsePartition.PlaylistGroup -> partition.name
        is CatalogueBrowsePartition.Genre -> catalogueGenreLabel(partition.genre)
        is CatalogueBrowsePartition.CustomGroup -> selectedFacet?.name
        CatalogueBrowsePartition.Unsorted -> stringResource(R.string.catalogue_genre_unsorted)
        null -> null
    }
    val selectedCount = if (state.selectedPartition == CatalogueBrowsePartition.History) {
        state.wall?.takeIf { state.wallIsCurrent }?.entries?.size
    } else when (state.grouping) {
        CatalogueGrouping.PLAYLIST -> state.playlistGroups.firstOrNull {
            it.name.equals(selectedGroup, ignoreCase = true)
        }?.count
        CatalogueGrouping.GENRE -> selectedFacet?.count
    }.takeIf { state.search.isBlank() } ?: state.wall
        ?.takeIf { state.wallIsCurrent }
        ?.entries
        ?.size
    val wallEntries = state.wall?.entries.orEmpty()
    val wallInteractive = state.wallIsCurrent
    var lastPresentedPartition by remember(activeSession) { mutableStateOf(state.selectedPartition) }
    var lastPresentedGrouping by remember(activeSession) { mutableStateOf(state.grouping) }
    var lastPresentedSearch by remember(activeSession) { mutableStateOf(state.search) }
    val editableGroups = state.allPlaylistGroups.ifEmpty { state.playlistGroups }

    BackHandler(enabled = !optionsVisible && !categoryEditMode, onBack = onBack)
    BackHandler(enabled = !optionsVisible && categoryEditMode) { categoryEditMode = false }
    BackHandler(enabled = optionsVisible) { optionsVisible = false }

    LaunchedEffect(optionsVisible) {
        if (optionsVisible) optionsFocusRequester.requestFocusWhenAttached()
    }

    LaunchedEffect(categoryEditMode) {
        if (categoryEditMode) groupListState.scrollToItem(0)
    }

    val firstGroupFocus = remember { FocusRequester() }
    val room = if (state.mode == CatalogueMode.MOVIES) com.streammate.tv.core.model.LibraryRoom.MOVIES else com.streammate.tv.core.model.LibraryRoom.SERIES
    val railPartitions = buildList<CatalogueBrowsePartition> {
        if (historyEnabled) add(CatalogueBrowsePartition.History)
        if (state.grouping == CatalogueGrouping.PLAYLIST) addAll(state.playlistGroups.map { CatalogueBrowsePartition.PlaylistGroup(it.name) })
        else addAll(state.genreFacets.map { it.partition })
    }.let { rows ->
        if (state.grouping == CatalogueGrouping.PLAYLIST && organization.groupSort(room) == com.streammate.tv.core.model.LibrarySort.MANUAL) rows.sortedBy { partition ->
            if (partition == CatalogueBrowsePartition.History) organization.rule(com.streammate.tv.core.model.OrganizationKey(room, groupKey = com.streammate.tv.core.model.ORGANIZATION_HISTORY))?.position ?: Long.MAX_VALUE
            else state.playlistGroups.firstOrNull { it.name == (partition as? CatalogueBrowsePartition.PlaylistGroup)?.name }?.manualPosition ?: Long.MAX_VALUE
        } else rows
    }
    val wallReturnPartition = state.selectedPartition?.takeIf { it in railPartitions }
        ?: CatalogueBrowsePartition.History.takeIf { it in railPartitions }
        ?: railPartitions.firstOrNull()
    var previousHistoryEnabled by remember { mutableStateOf(historyEnabled) }
    LaunchedEffect(historyEnabled) {
        if (previousHistoryEnabled && !historyEnabled) activeSession.initialHistoryFocusPending = true
        previousHistoryEnabled = historyEnabled
    }
    LaunchedEffect(activeSession) {
        if (activeSession.restoreFocusToOptions && optionsButtonFocusRequester.requestFocusWhenAttached()) activeSession.restoreFocusToOptions = false
    }
    LaunchedEffect(activeSession, historyEnabled, state.playlistGroupsReady, railPartitions) {
        if (activeSession.initialHistoryFocusPending && !activeSession.restoreFocusToOptions) {
            val target = if (historyEnabled) railPartitions.indexOf(CatalogueBrowsePartition.History) else 0
            if (target >= 0 && railPartitions.isNotEmpty()) groupListState.scrollToItem(target)
        }
        if (
            activeSession.initialHistoryFocusPending && (historyEnabled || state.playlistGroupsReady) &&
            (if (historyEnabled) historyFocusRequester else if (state.playlistGroups.isNotEmpty()) firstGroupFocus else optionsButtonFocusRequester).requestFocusWhenAttached()
        ) {
            activeSession.initialHistoryFocusPending = false
        }
    }

    RetainCatalogueBrowserUiState(
        session = activeSession,
        groupListState = groupListState,
        wallGridState = wallGridState,
    )

    LaunchedEffect(state.selectedPartition) {
        val previousPartition = lastPresentedPartition
        if (previousPartition != null && previousPartition != state.selectedPartition) {
            wallGridState.scrollToItem(0)
            activeSession.wallFirstVisibleItemIndex = 0
            activeSession.wallFirstVisibleItemScrollOffset = 0
            activeSession.focusedEntryContentKey = null
            activeSession.restoreFocusToWall = false
        }
        lastPresentedPartition = state.selectedPartition
    }

    LaunchedEffect(state.grouping) {
        if (lastPresentedGrouping != state.grouping) {
            groupListState.scrollToItem(0)
        }
        lastPresentedGrouping = state.grouping
    }

    LaunchedEffect(state.search) {
        if (lastPresentedSearch != state.search) {
            wallGridState.scrollToItem(0)
            activeSession.wallFirstVisibleItemIndex = 0
            activeSession.wallFirstVisibleItemScrollOffset = 0
            activeSession.focusedEntryContentKey = null
            activeSession.restoreFocusToWall = false
        }
        lastPresentedSearch = state.search
    }

    LaunchedEffect(pendingWallFocusKey, wallEntries, wallInteractive) {
        val contentKey = pendingWallFocusKey ?: return@LaunchedEffect
        if (!wallInteractive) return@LaunchedEffect
        val itemIndex = wallEntries.indexOfFirst { it.contentKey == contentKey }
        if (itemIndex < 0) {
            val replacementKey = state.wall
                ?.primaryContentKeyByCopy
                ?.get(contentKey)
                ?.takeIf { replacement -> wallEntries.any { it.contentKey == replacement } }
                ?: wallEntries.firstOrNull()?.contentKey
            pendingWallFocusKey = replacementKey
            activeSession.focusedEntryContentKey = replacementKey
            activeSession.restoreFocusToWall = replacementKey != null
            return@LaunchedEffect
        }
        if (wallGridState.layoutInfo.visibleItemsInfo.none { it.key == contentKey }) {
            wallGridState.scrollToItem(itemIndex)
        }
        withFrameNanos { }
        restoredWallFocusRequester.requestFocus()
        pendingWallFocusKey = null
    }

    StreamMateScreenBackground(modifier) { safeModifier ->
        Box(safeModifier) {
            Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.lg),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(
                        if (state.mode == CatalogueMode.MOVIES) {
                            R.string.catalogue_movies
                        } else {
                            R.string.catalogue_series
                        },
                    ),
                    color = palette.textPrimary,
                    fontSize = typography.display.fontSize,
                    lineHeight = typography.display.lineHeight,
                    fontWeight = FontWeight.Black,
                )
                selectedLabel?.let { label ->
                    Text(
                        text = buildString {
                            append(label)
                            selectedCount?.let { append("  ·  ").append(it) }
                        },
                        modifier = Modifier.padding(start = spacing.lg, bottom = 5.dp),
                        color = palette.textDim,
                        fontSize = typography.bodyLarge.fontSize,
                        lineHeight = typography.bodyLarge.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isShowingStaleWall) {
                    Text(
                        text = if (state.wallLoadState == CatalogueWallLoadState.FAILED) {
                            state.failure?.message ?: stringResource(R.string.catalogue_v2_failed)
                        } else {
                            stringResource(R.string.catalogue_v2_loading)
                        },
                        modifier = Modifier.padding(start = spacing.lg, bottom = 5.dp),
                        color = palette.textDim,
                        fontSize = typography.caption.fontSize,
                    )
                }
                status?.takeIf(String::isNotBlank)?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(start = spacing.lg, bottom = 5.dp),
                        color = palette.textDim,
                        fontSize = typography.caption.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(V2_CONTENT_GAP),
            ) {
                Column(
                    modifier = Modifier
                        .width(V2_RAIL_WIDTH)
                        .fillMaxHeight()
                        .testTag("catalogue-v2-groups"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvUrlField(
                        value = state.search,
                        onValueChange = onSearchChange,
                        label = stringResource(
                            if (state.mode == CatalogueMode.MOVIES) {
                                R.string.catalogue_search_movie
                            } else {
                                R.string.catalogue_search_series
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "catalogue-v2-search",
                        leadingIconRes = TvIcons.Search,
                        keyboardType = KeyboardType.Text,
                        editOnClickOnly = true,
                        compact = true,
                    )
                    if (!categoryEditMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TvActionButton(
                                label = stringResource(R.string.catalogue_grouping_groups),
                                onClick = { onSelectGrouping(CatalogueGrouping.PLAYLIST) },
                                modifier = Modifier.weight(1f),
                                compact = true,
                                selected = state.grouping == CatalogueGrouping.PLAYLIST,
                                testTag = "catalogue-v2-grouping-playlist",
                            )
                            TvActionButton(
                                label = stringResource(R.string.catalogue_grouping_genres),
                                onClick = { onSelectGrouping(CatalogueGrouping.GENRE) },
                                modifier = Modifier.weight(1f),
                                compact = true,
                                selected = state.grouping == CatalogueGrouping.GENRE,
                                testTag = "catalogue-v2-grouping-genre",
                            )
                        }
                    }
                    TvActionButton(
                        label = stringResource(R.string.catalogue_options),
                        icon = TvIcons.Info,
                        onClick = { optionsVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                        compact = true,
                        focusRequester = optionsButtonFocusRequester,
                        testTag = "catalogue-v2-options",
                    )
                    LazyColumn(
                        state = groupListState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        if (categoryEditMode) {
                            items(
                                items = editableGroups,
                                key = { "edit:${it.name.lowercase()}" },
                                contentType = { "playlist-group-edit" },
                            ) { group ->
                                val hidden = hiddenPlaylistGroups.any {
                                    it.equals(group.name, ignoreCase = true)
                                }
                                TvListRow(
                                    label = group.name,
                                    icon = if (hidden) TvIcons.Close else TvIcons.Check,
                                    trailing = stringResource(
                                        if (hidden) {
                                            R.string.catalogue_category_hidden
                                        } else {
                                            R.string.catalogue_category_visible
                                        },
                                    ),
                                    dense = true,
                                    divider = true,
                                    onClick = {
                                        scope.launch {
                                            onSetPlaylistGroupHidden(group.name, !hidden)
                                        }
                                    },
                                    testTag = "catalogue-v2-category-${group.name.hashCode()}",
                                )
                            }
                        } else {
                            items(railPartitions, key = { it.stableKey() }, contentType = { "library-destination" }) { partition ->
                                val history = partition == CatalogueBrowsePartition.History
                                val group = (partition as? CatalogueBrowsePartition.PlaylistGroup)?.let { selected -> state.playlistGroups.firstOrNull { it.name == selected.name } }
                                val facet = state.genreFacets.firstOrNull { it.partition == partition }
                                val focusModifier = when {
                                    history -> Modifier.focusRequester(historyFocusRequester)
                                    partition == railPartitions.firstOrNull { it != CatalogueBrowsePartition.History } -> Modifier.focusRequester(firstGroupFocus)
                                    else -> Modifier
                                }
                                TvListRow(
                                    label = when { history -> stringResource(R.string.catalogue_history); group != null -> group.name; else -> facet?.label().orEmpty() },
                                    icon = if (history) TvIcons.Replay else null,
                                    trailing = when {
                                        group != null -> group.count.toString()
                                        facet?.count != null -> facet.count.toString()
                                        state.selectedPartition == partition && state.wallIsCurrent -> wallEntries.size.toString()
                                        else -> null
                                    },
                                    selected = state.selectedPartition == partition,
                                    dense = true, divider = true,
                                    onClick = { activeSession.restoreFocusToWall = false; onSelectPartition(partition) },
                                    modifier = focusModifier
                                        .then(if (partition == wallReturnPartition) Modifier.focusRequester(wallReturnFocusRequester) else Modifier)
                                        .onFocusChanged { if (it.hasFocus) activeSession.restoreFocusToWall = false },
                                    testTag = when { history -> "catalogue-v2-history"; group != null -> "catalogue-v2-group-${group.name.hashCode()}"; else -> "catalogue-v2-genre-${partition.stableKey()}" },
                                )
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        wallEntries.isNotEmpty() -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(V2_POSTER_MIN_WIDTH),
                            state = wallGridState,
                            modifier = Modifier.fillMaxSize()
                                .testTag(if (wallInteractive) "catalogue-v2-wall-current" else "catalogue-v2-wall-stale")
                                .onPreviewKeyEvent { event ->
                                    val firstColumn = wallGridState.layoutInfo.visibleItemsInfo.any {
                                        it.key == activeSession.focusedEntryContentKey && it.column == 0
                                    }
                                    if (
                                        event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown &&
                                        firstColumn && wallInteractive && !categoryEditMode
                                    ) {
                                        // Geometric focus can hit Search or Options from the
                                        // top row. Only the first column exits the wall;
                                        // every other column keeps normal card traversal.
                                        scope.launch {
                                            val target = railPartitions.indexOf(wallReturnPartition)
                                            if (target >= 0) {
                                                groupListState.scrollToItem(target)
                                                wallReturnFocusRequester.requestFocusWhenAttached()
                                            } else {
                                                optionsButtonFocusRequester.requestFocusWhenAttached()
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(V2_GRID_GAP_X),
                            verticalArrangement = Arrangement.spacedBy(V2_GRID_GAP_Y),
                        ) {
                            items(
                                items = wallEntries,
                                key = CatalogueBrowseEntry::contentKey,
                                contentType = { "poster" },
                            ) { entry ->
                                CatalogueBrowserV2Card(
                                    entry = entry,
                                    enabled = wallInteractive,
                                    focusRequester = restoredWallFocusRequester.takeIf {
                                        pendingWallFocusKey == entry.contentKey
                                    },
                                    onFocused = {
                                        activeSession.focusedEntryContentKey = entry.contentKey
                                        activeSession.restoreFocusToWall = true
                                    },
                                    onClick = {
                                        activeSession.focusedEntryContentKey = entry.contentKey
                                        activeSession.restoreFocusToWall = true
                                        onOpenEntry(entry)
                                    },
                                )
                            }
                        }
                        state.wallLoadState == CatalogueWallLoadState.LOADING -> V2Message(
                            stringResource(R.string.catalogue_v2_loading),
                        )
                        state.wallLoadState == CatalogueWallLoadState.FAILED -> V2Message(
                            state.failure?.message ?: stringResource(R.string.catalogue_v2_failed),
                        )
                        state.wallIsCurrent -> V2Message(
                            stringResource(
                                if (state.search.isBlank()) {
                                    R.string.catalogue_v2_empty_group
                                } else {
                                    R.string.catalogue_no_results
                                },
                            ),
                        )
                    }
                }
            }
            }
            if (optionsVisible) {
                CatalogueBrowserV2OptionsSheet(
                    modifier = Modifier.zIndex(1f),
                    refreshing = refreshing,
                    categoryEditMode = categoryEditMode,
                    firstFocusRequester = optionsFocusRequester,
                    onRefresh = {
                        if (!refreshing) {
                            scope.launch {
                                refreshing = true
                                status = onRefresh().fold(
                                    onSuccess = { it },
                                    onFailure = { it.userMessage(context) },
                                )
                                refreshing = false
                            }
                        }
                    },
                    onToggleCategoryEdit = {
                        if (onManageGroups != null) onManageGroups(if (state.selectedPartition == CatalogueBrowsePartition.History) com.streammate.tv.core.model.ORGANIZATION_HISTORY else selectedGroup) else categoryEditMode = !categoryEditMode
                        optionsVisible = false
                    },
                    onBack = onBack,
                    onDismiss = { optionsVisible = false },
                )
            }
        }
    }
}

@Composable
private fun CatalogueBrowserV2OptionsSheet(
    refreshing: Boolean,
    categoryEditMode: Boolean,
    firstFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onToggleCategoryEdit: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    Box(
        modifier = modifier.fillMaxSize().background(palette.background.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 420.dp)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel)
                .padding(spacing.xl)
                .focusGroup()
                .testTag("catalogue-v2-options-sheet"),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.catalogue_options_title),
                color = palette.textPrimary,
                fontSize = typography.headline.fontSize,
                lineHeight = typography.headline.lineHeight,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.catalogue_subtitle),
                color = palette.textMuted,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
            TvActionButton(
                label = stringResource(
                    if (refreshing) R.string.action_refreshing else R.string.action_refresh,
                ),
                icon = TvIcons.Refresh,
                enabled = !refreshing,
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = firstFocusRequester,
                testTag = "catalogue-v2-refresh",
            )
            TvActionButton(
                label = stringResource(
                    if (categoryEditMode) R.string.category_edit_done else R.string.category_edit,
                ),
                icon = TvIcons.Check,
                onClick = onToggleCategoryEdit,
                modifier = Modifier.fillMaxWidth(),
                selected = categoryEditMode,
                testTag = "catalogue-v2-category-edit",
            )
            TvActionButton(
                label = stringResource(R.string.action_back),
                icon = TvIcons.Back,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                testTag = "catalogue-v2-back",
            )
            TvActionButton(
                label = stringResource(R.string.catalogue_close_options),
                icon = TvIcons.Close,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                testTag = "catalogue-v2-options-close",
            )
        }
    }
}

@Composable
private fun CatalogueBrowserV2Card(
    entry: CatalogueBrowseEntry,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val title = entry.displayTitle()
    val qualityTags = remember(entry.providerTitle, entry.copyQualityTags) {
        entry.copyQualityTags.ifEmpty { catalogueQualityTags(entry.providerTitle) }
    }
    var focused by remember(entry.contentKey) { mutableStateOf(false) }
    val providerPosterUrl = entry.providerPosterUrl?.takeIf(String::isNotBlank)
    var providerPosterFailed by remember(entry.contentKey, providerPosterUrl) {
        mutableStateOf(false)
    }
    val posterUrl = entry.displayPosterUrl(providerPosterFailed)
    val onPosterError: (() -> Unit)? = if (
        !providerPosterFailed && providerPosterUrl != null && posterUrl == providerPosterUrl
    ) {
        { providerPosterFailed = true }
    } else {
        null
    }
    val shape = StreamMateThemeTokens.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .semantics { text = AnnotatedString(title) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(V2_POSTER_ASPECT_RATIO)
                .clip(shape)
                .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier),
        ) {
            CataloguePoster(
                url = posterUrl,
                title = title,
                modifier = Modifier.fillMaxSize(),
                onError = onPosterError,
            )
            if (entry.copyCount > 1 || qualityTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (entry.copyCount > 1) {
                        TvTagChip(
                            label = stringResource(R.string.catalogue_copy_count, entry.copyCount),
                            tone = TvTagTone.ACCENT,
                        )
                    }
                    qualityTags.forEach { tag ->
                        TvTagChip(label = tag, tone = TvTagTone.PRIMARY)
                    }
                }
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 9.dp),
            color = if (focused) palette.textPrimary else palette.textMuted,
            fontSize = typography.label.fontSize,
            lineHeight = typography.label.lineHeight,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val facts = listOfNotNull(
            entry.year?.toString(),
            entry.rating?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        Text(
            text = facts,
            color = palette.textDim,
            fontSize = typography.caption.fontSize,
            lineHeight = typography.caption.lineHeight,
            maxLines = 1,
        )
    }
}

@Composable
private fun RetainCatalogueBrowserUiState(
    session: CatalogueBrowserSession,
    groupListState: LazyListState,
    wallGridState: LazyGridState,
) {
    DisposableEffect(session, groupListState, wallGridState) {
        onDispose {
            session.groupFirstVisibleItemIndex = groupListState.firstVisibleItemIndex
            session.groupFirstVisibleItemScrollOffset = groupListState.firstVisibleItemScrollOffset
            session.wallFirstVisibleItemIndex = wallGridState.firstVisibleItemIndex
            session.wallFirstVisibleItemScrollOffset = wallGridState.firstVisibleItemScrollOffset
        }
    }
}

@Composable
private fun V2Message(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = StreamMateThemeTokens.palette.textMuted,
            fontSize = StreamMateThemeTokens.typography.bodyLarge.fontSize,
        )
    }
}

internal fun CatalogueBrowseEntry.displayTitle(): String = metadataOverride
    ?.replacementTitle
    ?.takeIf(String::isNotBlank)
    ?: providerTitle

internal fun CatalogueBrowseEntry.displayPosterUrl(providerPosterFailed: Boolean = false): String? {
    val provider = providerPosterUrl?.takeIf(String::isNotBlank)
    val replacement = metadataOverride?.replacementPosterUrl?.takeIf(String::isNotBlank)
    return when {
        // A nonblank provider URL can still be a dead image. Once
        // Coil proves that it is, use the persisted TMDB poster; if TMDB has no
        // artwork either, returning null renders the title initials rather
        // than leaving an unexplained black card.
        providerPosterFailed -> replacement
        provider == null -> replacement
        metadataOverride?.replaceProviderPoster == true -> replacement ?: provider
        else -> provider
    }
}

@Composable
private fun CatalogueBrowseFacet.label(): String = when (val value = partition) {
    CatalogueBrowsePartition.History -> stringResource(R.string.catalogue_history)
    is CatalogueBrowsePartition.Genre -> catalogueGenreLabel(value.genre)
    is CatalogueBrowsePartition.CustomGroup -> name.orEmpty()
    CatalogueBrowsePartition.Unsorted -> stringResource(R.string.catalogue_genre_unsorted)
    is CatalogueBrowsePartition.PlaylistGroup -> value.name.orEmpty()
}

private fun CatalogueBrowsePartition.stableKey(): String = when (this) {
    CatalogueBrowsePartition.History -> "history"
    is CatalogueBrowsePartition.PlaylistGroup -> "playlist:${name.orEmpty().lowercase()}"
    is CatalogueBrowsePartition.Genre -> "genre:${genre.wireValue}"
    is CatalogueBrowsePartition.CustomGroup -> "custom:$id"
    CatalogueBrowsePartition.Unsorted -> "genre:unsorted"
}

private val V2_POSTER_MIN_WIDTH = 88.dp
private val V2_RAIL_WIDTH = 168.dp
private val V2_CONTENT_GAP = 22.dp
private val V2_GRID_GAP_X = 22.dp
private val V2_GRID_GAP_Y = 30.dp
private const val V2_POSTER_ASPECT_RATIO = 2f / 3f
