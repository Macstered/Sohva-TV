package com.streammate.tv.feature.guide

import com.streammate.tv.iptv.repository.GuideRailGroup
import com.streammate.tv.iptv.repository.SourceRefreshHealth
import com.streammate.tv.iptv.repository.GuideSource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources
import com.streammate.tv.iptv.repository.organizationItem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.app.CategoryRoom
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.tickerFlow
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.EnrichedMetadata
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which slice of the line-up the rail is showing. */
enum class ChannelFilter {
    ALL,
    FAVOURITES,
    RECENT,
}

enum class GuideSortMode {
    PLAYLIST,
    NAME,
}

data class GuideSelection(
    val channel: GuideTimelineChannel,
    val programme: GuideTimelineProgramme?,
)

@Composable
fun GuideScreen(
    guideRepository: GuideRepository,
    preferencesRepository: AppPreferencesRepository,
    metadataRepository: MetadataRepository,
    initialChannelId: String? = null,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChannels: () -> Unit,
    onPlay: (String) -> Unit,
    onPlayCatchup: (String, Long, Long) -> Unit,
    onManageGroups: ((String?, String?) -> Unit)? = null,
    startInOptions: Boolean = false,
    initialManagedGroup: String? = null,
    onManagementReturnHandled: () -> Unit = {},
    /** Starts a full background sync of every source. */
    onSyncNow: () -> Unit = {},
) {
    val palette = StreamMateThemeTokens.palette
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val managementReturn = remember { startInOptions }
    var optionsVisible by remember { mutableStateOf(startInOptions) }
    LaunchedEffect(Unit) { if (managementReturn) onManagementReturnHandled() }
    // The window someone has paged to must stay where they left it, so this is
    // an absolute start rather than something derived from the clock. Null means
    // "showing now", which is the only state that follows the clock forward.
    var pinnedWindowStart by rememberSaveable { mutableStateOf<Long?>(null) }
    val windowStart = pinnedWindowStart ?: GuideTimeWindow.nowStart(now)
    val windowEnd = windowStart + TIMELINE_WINDOW_MILLIS

    // Paging destroys the cell that had focus - the window it belonged to is
    // gone - and Compose then falls back to the first focusable on the screen.
    // Focus has to be put back deliberately, on the row it came from.
    val pagedFocus = remember { FocusRequester() }
    var pendingPageDirection by remember { mutableStateOf(0) }

    fun moveWindow(byMillis: Long) {
        val moved = GuideTimeWindow.shifted(windowStart, byMillis, now)
        if (moved == windowStart) return
        pendingPageDirection = if (byMillis > 0) 1 else -1
        pinnedWindowStart = moved.takeUnless { GuideTimeWindow.isAtNow(it, now) }
    }
    // The rail is the cheap read: every source and group with a count, from
    // the channel table alone. The timeline below is read for one group, one
    // list or a handful of ids at a time, never for the whole library.
    val loadedRail by remember(guideRepository) { guideRepository.observeRail() }
        .collectAsStateWithLifecycle(initialValue = null)
    val railLoaded = loadedRail != null
    val railRows = loadedRail ?: emptyList()
    val libraryEmpty = railLoaded && railRows.isEmpty()
    val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val organizationState by remember(guideRepository) { guideRepository.organization?.state ?: kotlinx.coroutines.flow.flowOf(com.streammate.tv.iptv.repository.OrganizationReadState()) }.collectAsStateWithLifecycle(initialValue = com.streammate.tv.iptv.repository.OrganizationReadState())
    val organization = organizationState.organization
    val liveRoom = com.streammate.tv.core.model.LibraryRoom.LIVE
    val showFavourites = organization.shortcutEnabled(liveRoom, com.streammate.tv.core.model.ORGANIZATION_FAVOURITES)
    val showRecent = organization.shortcutEnabled(liveRoom, com.streammate.tv.core.model.ORGANIZATION_RECENT)
    val customLists by guideRepository.observeCustomChannelLists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listMemberships by guideRepository.observeChannelListMemberships()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val selectedSidebarFocus = remember { FocusRequester() }
    val guideReturnFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    var channelFilter by remember { mutableStateOf(ChannelFilter.ALL) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var sourceSelectionInitialized by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf(initialManagedGroup) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(GuideSortMode.PLAYLIST) }
    LaunchedEffect(showFavourites, showRecent, selectedListId, organizationState) {
        if ((channelFilter == ChannelFilter.FAVOURITES && !showFavourites) || (channelFilter == ChannelFilter.RECENT && !showRecent)) channelFilter = ChannelFilter.ALL
        if (selectedListId != null && !organization.shortcutEnabled(liveRoom, "@list:$selectedListId")) selectedListId = null
    }
    var selection by remember { mutableStateOf<GuideSelection?>(null) }
    var selectedMetadata by remember { mutableStateOf<EnrichedMetadata?>(null) }
    var categoryEditMode by remember { mutableStateOf(false) }
    var groupRailVisible by rememberSaveable { mutableStateOf(false) }
    var groupRailFocusRequest by remember { mutableIntStateOf(0) }
    var restoreGuideFocus by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // This pipeline sorts and filters every channel the user owns. Un-remembered
    // it ran on every recomposition, which on this screen means every D-pad
    // press and every clock tick. Each stage is now keyed on what it actually
    // reads.
    val hiddenLiveCategories = if (guideRepository.organization == null) preferences.hiddenLiveCategories else emptySet()
    val favouriteChannelIds = preferences.favouriteChannelIds
    val recentChannelIds = preferences.recentChannelIds

    val sources = remember(railRows) {
        railRows.distinctBy(GuideRailGroup::sourceId).map { GuideSourceOption(it.sourceId, it.sourceName) }
    }
    // Groups a rule has switched off are not on the rail; the organisation
    // view drops their channels the same way.
    val sourceRail = remember(railRows, selectedSourceId, organizationState) {
        railRows.filter { selectedSourceId == null || it.sourceId == selectedSourceId }
            .filter { row ->
                row.groupTitle == null || organization.groupRule(
                    liveRoom,
                    com.streammate.tv.core.model.OrganizationItem("", row.sourceId, row.groupTitle, row.groupTitle, row.organizationGroupKey),
                ).enabled != false
            }
    }
    val allGroups = remember(sourceRail) { sourceRail.mapNotNull(GuideRailGroup::groupTitle).distinct() }
    val groups = remember(allGroups, hiddenLiveCategories) {
        allGroups.filterNot { group ->
            hiddenLiveCategories.any { it.equals(group, ignoreCase = true) }
        }
    }
    // Counted off the rail, so a group's number says how many are in it, not
    // how many survived the filter that is currently applied.
    val groupCounts = remember(sourceRail) {
        sourceRail.filter { it.groupTitle != null }
            .groupBy { it.groupTitle!! }
            .mapValues { (_, rows) -> rows.sumOf(GuideRailGroup::channelCount) }
    }
    val sourceChannelCount = remember(sourceRail, hiddenLiveCategories) {
        sourceRail.filterNot { row ->
            row.groupTitle != null && hiddenLiveCategories.any { it.equals(row.groupTitle, ignoreCase = true) }
        }.sumOf(GuideRailGroup::channelCount)
    }
    // Which channels the timeline is read for: named ids for favourites,
    // recent and lists; otherwise the selected source and group.
    val timelineChannelIds: List<String>? = remember(channelFilter, selectedListId, favouriteChannelIds, recentChannelIds, listMemberships) {
        when {
            channelFilter == ChannelFilter.FAVOURITES -> favouriteChannelIds.toList()
            channelFilter == ChannelFilter.RECENT -> recentChannelIds
            selectedListId != null -> listMemberships.filter { it.listId == selectedListId }.map { it.channelId }
            else -> null
        }
    }
    val loadedTimeline by remember(windowStart, selectedSourceId, selectedGroup, timelineChannelIds) {
        val ids = timelineChannelIds
        val sourceId = selectedSourceId
        when {
            ids != null -> guideRepository.observeTimelineForChannels(ids, windowStart, windowEnd)
            sourceId != null -> guideRepository.observeTimeline(windowStart, windowEnd, sourceId, selectedGroup)
            else -> kotlinx.coroutines.flow.flowOf<List<GuideTimelineChannel>?>(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    // The last timeline stays on screen, its own rows and all, while the next
    // one is read: the rows, the info box above them and the rail keep their
    // places, and the new group's rows replace them when they arrive, normally
    // within a few frames. Clearing them at once flashed an empty list and,
    // with the info box gone, a rail stretched to the full height. A read that
    // drags on gets a reading notice above the stale rows, never in their
    // place: on the Shield a large group's read can pass the threshold, and
    // swapping the rows out for the notice collapsed the layout all the same.
    var shownTimeline by remember { mutableStateOf<List<GuideTimelineChannel>?>(null) }
    LaunchedEffect(loadedTimeline) { if (loadedTimeline != null) shownTimeline = loadedTimeline }
    var readingForLong by remember { mutableStateOf(false) }
    LaunchedEffect(loadedTimeline == null) {
        readingForLong = false
        if (loadedTimeline == null) {
            delay(TIMELINE_READING_NOTICE_MILLIS)
            readingForLong = true
        }
    }
    val timelineStale = loadedTimeline == null && shownTimeline != null
    val guide = loadedTimeline ?: shownTimeline ?: emptyList()
    val timelineLoading = loadedTimeline == null && shownTimeline == null
    val showReadingNotice = timelineStale && readingForLong
    val guideLoaded = railLoaded && (libraryEmpty || shownTimeline != null)
    // A stale timeline was read for the previous selection; it is shown as it
    // is rather than filtered to a selection none of its rows belong to.
    val sourceChannels = remember(guide, selectedSourceId, timelineStale) {
        if (timelineStale) guide else guide.filter { selectedSourceId == null || it.sourceId == selectedSourceId }
    }
    val categoryChannels = remember(sourceChannels, channelFilter, selectedListId, hiddenLiveCategories) {
        if (channelFilter != ChannelFilter.ALL || selectedListId != null) {
            sourceChannels
        } else {
            sourceChannels.filterNot { channel ->
                channel.groupTitle?.let { group ->
                    hiddenLiveCategories.any { it.equals(group, ignoreCase = true) }
                } == true
            }
        }
    }
    val filteredGuide = remember(
        categoryChannels,
        selectedGroup,
        selectedListId,
        listMemberships,
        channelFilter,
        sortMode,
        organizationState,
        searchQuery,
        favouriteChannelIds,
        recentChannelIds,
        timelineStale,
    ) {
        categoryChannels
            .filter { timelineStale || selectedGroup == null || it.groupTitle == selectedGroup }
            .filter { channel ->
                selectedListId == null || listMemberships.any {
                    it.listId == selectedListId && it.channelId == channel.id
                }
            }
            .let { channels ->
                when {
                    channelFilter == ChannelFilter.RECENT -> channels
                    selectedListId != null -> {
                        val positions = listMemberships
                            .filter { it.listId == selectedListId }
                            .associate { it.channelId to it.sortOrder }
                        if (guideRepository.organization == null) channels.sortedWith(compareBy({ positions[it.id] ?: Int.MAX_VALUE }, { it.name.lowercase() }))
                        else {
                            val byId = channels.associateBy { it.id }
                            organization.orderedItems(liveRoom, channels.map { channel ->
                                com.streammate.tv.core.model.OrganizationItem(channel.id, channel.sourceId, channel.name, channel.groupTitle, channel.organizationGroupKey,
                                    providerOrder = channel.playlistOrder, legacyPosition = positions[channel.id]?.toLong())
                            }, viewKey = "@list:$selectedListId").mapNotNull { byId[it.id] }
                        }
                    }
                    sortMode == GuideSortMode.NAME -> channels.sortedBy { it.name.lowercase() }
                    guideRepository.organization != null -> channels
                    else -> channels.sortedWith(
                        compareByDescending<GuideTimelineChannel> { it.sourcePriority }
                            .thenBy { it.playlistOrder }
                            .thenBy { it.name.lowercase() },
                    )
                }
            }
            .filter { channel ->
                searchQuery.isBlank() ||
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.programmes.any { it.title.contains(searchQuery, ignoreCase = true) }
            }
            .let { channels ->
                when (channelFilter) {
                    ChannelFilter.ALL -> channels
                    ChannelFilter.FAVOURITES -> channels.filter { it.id in favouriteChannelIds }
                    ChannelFilter.RECENT -> {
                        val positions = recentChannelIds.withIndex().associate { it.value to it.index }
                        channels.filter { it.id in positions }.sortedBy { positions[it.id] }
                    }
                }
            }
    }
    val initialFocusIndex = remember(filteredGuide, initialChannelId) {
        filteredGuide
            .indexOfFirst { it.id == initialChannelId }
            .takeIf { it >= 0 }
            ?: 0
    }

    LaunchedEffect(Unit) {
        tickerFlow(periodMillis = 60_000, emitImmediately = false).collect {
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(railLoaded, initialChannelId) {
        if (managementReturn || !railLoaded || initialChannelId == null) return@LaunchedEffect
        val restored = guideRepository.channelPlacement(initialChannelId) ?: return@LaunchedEffect
        selectedSourceId = restored.sourceId
        selectedGroup = restored.groupTitle
        selectedListId = null
        channelFilter = ChannelFilter.ALL
    }
    LaunchedEffect(sources.map(GuideSourceOption::sourceId), initialChannelId) {
        val sourceIds = sources.map(GuideSourceOption::sourceId)
        if (sourceIds.isEmpty()) {
            selectedSourceId = null
            sourceSelectionInitialized = false
            return@LaunchedEffect
        }
        if (!sourceSelectionInitialized || selectedSourceId !in sourceIds) {
            val storedPreferences = preferencesRepository.preferences.first()
            selectedSourceId = preferredGuideSourceId(
                sourceIds = sourceIds,
                initialChannelSourceId = initialChannelId
                    ?.takeUnless { managementReturn }
                    ?.let { guideRepository.channelPlacement(it)?.sourceId },
                savedSourceId = storedPreferences.lastGuideSourceId,
                lastChannelSourceId = storedPreferences.lastChannelId?.let { guideRepository.channelPlacement(it)?.sourceId },
            )
            sourceSelectionInitialized = true
        }
    }
    LaunchedEffect(selectedSourceId, sourceSelectionInitialized) {
        if (sourceSelectionInitialized) {
            preferencesRepository.setLastGuideSourceId(selectedSourceId)
        }
    }
    // A group that has gone - hidden, or belonging to a source no longer
    // selected - drops back to All channels. It does not pick a different
    // group: "everything" is a state someone can be in, and the guide used to
    // have no way of being in it.
    LaunchedEffect(groups, selectedSourceId) {
        if (sourceSelectionInitialized && selectedGroup != null && selectedGroup !in groups) selectedGroup = null
    }
    LaunchedEffect(customLists.map { it.id }) {
        if (selectedListId != null && customLists.none { it.id == selectedListId }) selectedListId = null
    }
    LaunchedEffect(filteredGuide.map(GuideTimelineChannel::id), initialChannelId) {
        val selectedChannel = selection?.channel
        val restoredChannel = filteredGuide.firstOrNull { it.id == initialChannelId }
        if (restoredChannel != null && selectedChannel?.id != restoredChannel.id) {
            selection = GuideSelection(restoredChannel, restoredChannel.preferredProgramme(now))
        } else if (selectedChannel == null || filteredGuide.none { it.id == selectedChannel.id }) {
            selection = filteredGuide.firstOrNull()?.let { GuideSelection(it, it.preferredProgramme(now)) }
        }
    }
    LaunchedEffect(windowStart, filteredGuide) {
        if (pendingPageDirection == 0 || filteredGuide.isEmpty()) return@LaunchedEffect
        // Adjacent to where focus was: the earliest programme of the new window
        // when moving forward, the latest when moving back.
        pagedFocus.requestFocusWhenAttached()
        pendingPageDirection = 0
    }
    // Paging time leaves the selection on a programme that is no longer on
    // screen, and the hero above the grid would go on describing it.
    LaunchedEffect(windowStart) {
        val channel = selection?.channel ?: return@LaunchedEffect
        val current = filteredGuide.firstOrNull { it.id == channel.id } ?: return@LaunchedEffect
        val stillVisible = selection?.programme?.let { programme ->
            programme.stopEpochMillis > windowStart && programme.startEpochMillis < windowEnd
        } ?: false
        if (!stillVisible) {
            selection = GuideSelection(current, current.programmeAt(windowStart, windowEnd))
        }
    }
    // guideLoaded is a key so the empty state, which only appears after the
    // first read, still gets its focus once it is there.
    LaunchedEffect(filteredGuide.map(GuideTimelineChannel::id), initialFocusIndex, guideLoaded) {
        if (optionsVisible || !guideLoaded) return@LaunchedEffect
        if (filteredGuide.isNotEmpty()) {
            channelListState.scrollToItem(initialFocusIndex)
            firstFocus.requestFocusWhenAttached()
        } else if (libraryEmpty) {
            firstFocus.requestFocusWhenAttached()
        }
    }
    LaunchedEffect(selection?.programme?.id) {
        selectedMetadata = null
        val programme = selection?.programme ?: return@LaunchedEffect
        if (!metadataRepository.isEnabled()) return@LaunchedEffect
        delay(METADATA_SELECTION_DELAY_MILLIS)
        selectedMetadata = metadataRepository.enrich(
            MetadataLookup(
                mediaType = MetadataMediaType.PROGRAMME,
                title = programme.title,
            ),
        )
    }
    LaunchedEffect(optionsVisible) {
        if (optionsVisible) optionsFocus.requestFocusWhenAttached()
    }
    LaunchedEffect(groupRailVisible, groupRailFocusRequest, restoreGuideFocus) {
        if (groupRailVisible) {
            selectedSidebarFocus.requestFocusWhenAttached()
        } else if (restoreGuideFocus) {
            guideReturnFocus.requestFocusWhenAttached()
            restoreGuideFocus = false
        }
    }
    // Back peels one layer at a time: the options sheet, then category editing,
    // then out of the guide the way any other screen leaves.
    BackHandler(enabled = optionsVisible) { optionsVisible = false }
    BackHandler(enabled = !optionsVisible && categoryEditMode) { categoryEditMode = false }

    StreamMateScreenBackground { contentModifier ->
        Box(modifier = contentModifier) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!guideLoaded) {
                    LoadingGuide()
                    return@Column
                }
                if (libraryEmpty) {
                    val sourceStates by guideRepository.observeSourceStates()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val health by guideRepository.observeSourceRefreshHealth()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    EmptyGuide(
                        sources = sourceStates.filter(GuideSource::enabled),
                        health = health,
                        onSettings = onSettings,
                        onSyncNow = onSyncNow,
                        focusRequester = firstFocus,
                    )
                    return@Column
                }
                selection?.let { selected ->
                    val channelNumber = filteredGuide
                        .indexOfFirst { it.id == selected.channel.id }
                        .takeIf { it >= 0 }
                        ?.plus(1)
                    GuideHero(
                        selection = selected,
                        channelNumber = channelNumber,
                        now = now,
                        timeZoneId = preferences.timeZoneId,
                        favourite = selected.channel.id in favouriteChannelIds,
                        metadata = selectedMetadata,
                        onWatch = { onPlay(selected.channel.id) },
                        onToggleFavourite = {
                            coroutineScope.launch {
                                preferencesRepository.setFavouriteChannel(
                                    selected.channel.id,
                                    selected.channel.id !in favouriteChannelIds,
                                )
                            }
                        },
                        onPlayCatchup = selected.programme
                            ?.takeIf { selected.channel.canCatchup(it, now) }
                            ?.let { programme ->
                                {
                                    onPlayCatchup(
                                        selected.channel.id,
                                        programme.startEpochMillis,
                                        programme.stopEpochMillis,
                                    )
                                }
                            },
                        onSearch = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                        searchVisible = searchVisible,
                        onOpenMetadata = selectedMetadata?.let { metadata ->
                            { runCatching { uriHandler.openUri(metadata.attributionUrl) } }
                        },
                    )
                    Spacer(Modifier.height(GUIDE_HERO_GAP))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(GUIDE_CONTENT_GAP),
                ) {
                    if (groupRailVisible) GuideGroupRail(
                        modifier = Modifier.width(GUIDE_RAIL_WIDTH).fillMaxHeight(),
                        channelFilter = channelFilter,
                        selectedGroup = selectedGroup,
                        selectedListId = selectedListId,
                        groups = groups,
                        manualPositions = if (organization.groupSort(liveRoom) == com.streammate.tv.core.model.LibrarySort.MANUAL) buildMap {
                            organization.rules.filter { it.key.room == liveRoom && it.key.itemKey.isEmpty() && it.position != null }.forEach {
                                val key = when (it.key.groupKey) {
                                    com.streammate.tv.core.model.ORGANIZATION_FAVOURITES -> "favourites"
                                    com.streammate.tv.core.model.ORGANIZATION_RECENT -> "recent"
                                    else -> it.key.groupKey.removePrefix("@")
                                }
                                put(key, it.position!!)
                            }
                            sourceRail.filter { it.groupTitle != null }.groupBy { it.groupTitle!! }.forEach { (name, rows) ->
                                rows.mapNotNull { row ->
                                    organization.groupRule(
                                        liveRoom,
                                        com.streammate.tv.core.model.OrganizationItem("", row.sourceId, name, name, row.organizationGroupKey),
                                    ).position
                                }.minOrNull()?.let { put("group:$name", it) }
                            }
                        } else emptyMap(),
                        allGroups = allGroups,
                        groupCounts = groupCounts,
                        customLists = customLists.filter { organization.shortcutEnabled(liveRoom, "@list:${it.id}") }.map { it.id to it.name },
                        showFavourites = showFavourites,
                        showRecent = showRecent,
                        favouriteCount = favouriteChannelIds.size,
                        allChannelsCount = sourceChannelCount,
                        hiddenGroups = hiddenLiveCategories,
                        categoryEditMode = categoryEditMode,
                        searchVisible = searchVisible,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it.take(MAX_SEARCH_LENGTH) },
                        onShowFavourites = {
                            channelFilter = ChannelFilter.FAVOURITES
                            selectedGroup = null
                            selectedListId = null
                        },
                        onShowRecent = {
                            channelFilter = ChannelFilter.RECENT
                            selectedGroup = null
                            selectedListId = null
                        },
                        onShowAllChannels = {
                            channelFilter = ChannelFilter.ALL
                            selectedGroup = null
                            selectedListId = null
                        },
                        onGroup = { group ->
                            channelFilter = ChannelFilter.ALL
                            selectedGroup = group
                            selectedListId = null
                        },
                        onCustomList = { listId ->
                            channelFilter = ChannelFilter.ALL
                            selectedListId = listId
                            selectedGroup = null
                        },
                        onToggleGroupHidden = { group ->
                            coroutineScope.launch {
                                preferencesRepository.setCategoryHidden(
                                    CategoryRoom.LIVE_TV,
                                    group,
                                    hiddenLiveCategories.none { it.equals(group, ignoreCase = true) },
                                )
                            }
                        },
                        onOpenOptions = { optionsVisible = true },
                        selectedItemFocusRequester = selectedSidebarFocus,
                        onExitToGuide = {
                            groupRailVisible = false
                            restoreGuideFocus = true
                        },
                    )
                    if (filteredGuide.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when {
                                    timelineLoading -> stringResource(R.string.guide_loading)
                                    channelFilter == ChannelFilter.FAVOURITES -> stringResource(R.string.guide_empty_favourites)
                                    channelFilter == ChannelFilter.RECENT -> stringResource(R.string.guide_empty_recent)
                                    else -> stringResource(R.string.guide_empty_filtered)
                                },
                                color = palette.textMuted,
                                modifier = Modifier.testTag(if (timelineLoading) "guide-timeline-loading" else "guide-timeline-empty"),
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                            if (showReadingNotice) {
                                Text(
                                    text = stringResource(R.string.guide_loading),
                                    color = palette.textMuted,
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 4.dp)
                                        .testTag("guide-timeline-loading"),
                                )
                            }
                            GuideGrid(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                channels = filteredGuide,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                now = now,
                                timeZoneId = preferences.timeZoneId,
                                selection = selection,
                                listState = channelListState,
                                initialFocusIndex = initialFocusIndex,
                                firstFocusRequester = firstFocus,
                                returnFocusRequester = guideReturnFocus,
                                onOpenGroupRail = {
                                    restoreGuideFocus = false
                                    groupRailVisible = true
                                    // Left must move into the rail even if a
                                    // previous focus transition left its
                                    // visibility state stale.
                                    groupRailFocusRequest += 1
                                },
                                pagedFocusRequester = pagedFocus,
                                pagedFocusAtEnd = pendingPageDirection < 0,
                                onSelection = { channel, programme ->
                                    // Focus is back in the EPG. The rail is a
                                    // temporary drawer, so remove it regardless
                                    // of whether focus arrived via Right or via
                                    // a list refresh after choosing a group.
                                    if (groupRailVisible) groupRailVisible = false
                                    selection = GuideSelection(channel, programme)
                                },
                                onPlay = { channel, programme ->
                                    if (programme != null && channel.canCatchup(programme, now)) {
                                        onPlayCatchup(
                                            channel.id,
                                            programme.startEpochMillis,
                                            programme.stopEpochMillis,
                                        )
                                    } else {
                                        onPlay(channel.id)
                                    }
                                },
                                onPageForward = { moveWindow(GuideTimeWindow.PAGE_MILLIS) },
                                // Only once there is something to go back to.
                                // At now, left still reaches the rail, which is
                                // where it has always gone and the common case.
                                onPageBack = if (GuideTimeWindow.isAtNow(windowStart, now)) {
                                    null
                                } else {
                                    { moveWindow(-GuideTimeWindow.PAGE_MILLIS) }
                                },
                                onTransportKey = { key ->
                                    when (key) {
                                        Key.MediaFastForward -> {
                                            moveWindow(GuideTimeWindow.PAGE_MILLIS)
                                            true
                                        }
                                        Key.MediaRewind -> {
                                            moveWindow(-GuideTimeWindow.PAGE_MILLIS)
                                            true
                                        }
                                        Key.MediaNext -> {
                                            moveWindow(GuideTimeWindow.DAY_MILLIS)
                                            true
                                        }
                                        Key.MediaPrevious -> {
                                            moveWindow(-GuideTimeWindow.DAY_MILLIS)
                                            true
                                        }
                                        Key.Menu -> {
                                            optionsVisible = true
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            )
                            GuideKeyHints()
                        }
                    }
                }
            }
            if (optionsVisible) {
                GuideOptionsSheet(
                    modifier = Modifier.zIndex(1f),
                    sourceLabel = selectedSourceId
                        ?.let { id -> sources.firstOrNull { it.sourceId == id }?.sourceName }
                        ?: sources.firstOrNull()?.sourceName.orEmpty(),
                    sortMode = sortMode,
                    categoryEditMode = categoryEditMode,
                    firstFocusRequester = optionsFocus,
                    onCycleSource = {
                        selectedSourceId = nextValue(sources.map(GuideSourceOption::sourceId), selectedSourceId)
                        channelFilter = ChannelFilter.ALL
                        selectedGroup = null
                        selectedListId = null
                    },
                    onCycleSort = {
                        if (onManageGroups != null) {
                            optionsVisible = false
                            onManageGroups(selectedGroup, selectedSourceId)
                        } else sortMode = if (sortMode == GuideSortMode.PLAYLIST) {
                            GuideSortMode.NAME
                        } else {
                            GuideSortMode.PLAYLIST
                        }
                    },
                    onToggleCategoryEdit = {
                        if (onManageGroups != null) onManageGroups(selectedGroup, selectedSourceId) else categoryEditMode = !categoryEditMode
                        optionsVisible = false
                    },
                    onChannels = onChannels,
                    onSettings = onSettings,
                    onBack = onBack,
                    onDismiss = { optionsVisible = false },
                )
            }
        }
    }
}

/**
 * Everything that is not a group.
 *
 * The guide used to carry a title, a subtitle and a three-button toolbar across
 * the top, all of it on screen the whole time for the sake of controls someone
 * touches once a month. It lives behind one button now, and the grid has the
 * room back.
 */
@Composable
private fun GuideOptionsSheet(
    sourceLabel: String,
    sortMode: GuideSortMode,
    categoryEditMode: Boolean,
    firstFocusRequester: FocusRequester,
    onCycleSource: () -> Unit,
    onCycleSort: () -> Unit,
    onToggleCategoryEdit: () -> Unit,
    onChannels: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 420.dp)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel)
                .padding(spacing.xl)
                .focusGroup()
                .testTag("guide-options-sheet"),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.guide_options_title),
                color = palette.textPrimary,
                fontSize = typography.headline.fontSize,
                lineHeight = typography.headline.lineHeight,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.guide_subtitle),
                color = palette.textMuted,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
            TvActionButton(
                label = stringResource(R.string.guide_filter_source, sourceLabel),
                onClick = onCycleSource,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocusRequester),
                testTag = "guide-filter-source",
            )
            TvActionButton(
                label = stringResource(
                    R.string.guide_sort,
                    stringResource(
                        if (sortMode == GuideSortMode.PLAYLIST) {
                            R.string.guide_sort_playlist
                        } else {
                            R.string.guide_sort_name
                        },
                    ),
                ),
                onClick = onCycleSort,
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-sort",
            )
            TvActionButton(
                label = stringResource(
                    if (categoryEditMode) R.string.category_edit_done else R.string.category_edit,
                ),
                icon = TvIcons.Check,
                onClick = onToggleCategoryEdit,
                modifier = Modifier.fillMaxWidth(),
                selected = categoryEditMode,
                testTag = "guide-category-edit",
            )
            TvActionButton(
                label = stringResource(R.string.guide_channels),
                icon = TvIcons.Channels,
                onClick = onChannels,
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-channels",
            )
            TvActionButton(
                label = stringResource(R.string.guide_settings),
                icon = TvIcons.Settings,
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-settings",
            )
            TvActionButton(
                label = stringResource(R.string.action_back),
                icon = TvIcons.Back,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-back",
            )
            TvActionButton(
                label = stringResource(R.string.guide_close_options),
                icon = TvIcons.Close,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-options-close",
            )
        }
    }
}

/**
 * What the remote does here.
 *
 * Time paging lives on the transport keys, which no amount of pressing an arrow
 * would reveal. A guide that can reach tomorrow but never says so is a guide
 * that cannot.
 */
@Composable
private fun GuideKeyHints() {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Only bindings that actually do something. A hint bar listing keys the
        // app ignores, or that this remote does not have, is worse than none -
        // it sends people pressing buttons and concluding the app is broken.
        listOf(
            "◀ ▶" to stringResource(R.string.guide_hint_time),
            "▲ ▼" to stringResource(R.string.guide_hint_channel),
            "OK" to stringResource(R.string.guide_hint_watch),
            "⏮ ⏭" to stringResource(R.string.guide_hint_day),
            "MENU" to stringResource(R.string.guide_hint_options),
        ).forEach { (keys, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = keys,
                    modifier = Modifier
                        .clip(StreamMateThemeTokens.shapes.small)
                        .background(palette.surface)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    color = palette.textMuted,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = palette.textDim,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                )
            }
        }
    }
}

/**
 * The guide with nothing in it. With no source saved it points at Settings;
 * with sources saved it says, per source, what each import last did, so an
 * empty guide always comes with its reason and a way to sync from here.
 */
@Composable
private fun EmptyGuide(
    sources: List<GuideSource>,
    health: List<SourceRefreshHealth>,
    onSettings: () -> Unit,
    onSyncNow: () -> Unit,
    focusRequester: FocusRequester,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val resources = LocalResources.current
    val hasSources = sources.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StreamMateThemeTokens.shapes.large)
            .background(palette.surface)
            .padding(28.dp),
    ) {
        Text(
            text = stringResource(R.string.guide_empty_title),
            fontSize = typography.headline.fontSize,
            lineHeight = typography.headline.lineHeight,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                if (hasSources) R.string.guide_empty_sources_description else R.string.guide_empty_description,
            ),
            color = palette.textMuted,
        )
        if (hasSources) {
            Spacer(Modifier.height(12.dp))
            sources.forEach { source ->
                val byKind = health.filter { it.sourceId == source.id }.associateBy(SourceRefreshHealth::kind)
                Text(text = source.name, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                listOf(
                    "playlist" to R.string.health_playlist,
                    "epg" to R.string.health_epg,
                ).forEach { (kind, label) ->
                    Text(
                        text = sourceHealthLine(resources, byKind[kind], resources.getString(label)),
                        color = if (byKind[kind]?.status == "failed") palette.danger else palette.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("guide-empty-health-${source.id}-$kind"),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasSources) {
                TvActionButton(
                    label = stringResource(R.string.guide_empty_sync),
                    icon = TvIcons.Refresh,
                    onClick = onSyncNow,
                    modifier = Modifier.focusRequester(focusRequester),
                    testTag = "guide-empty-sync",
                )
            }
            TvActionButton(
                label = stringResource(R.string.guide_open_settings),
                onClick = onSettings,
                modifier = if (hasSources) Modifier else Modifier.focusRequester(focusRequester),
                testTag = "guide-empty-settings",
            )
        }
        if (hasSources) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guide_empty_sync_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}

/** One line of import status, in the words the settings health rows use. */
internal fun sourceHealthLine(resources: Resources, health: SourceRefreshHealth?, kind: String): String =
    when (health?.status) {
        null -> resources.getString(R.string.health_never, kind)
        "success" -> resources.getQuantityString(R.plurals.health_success, health.itemCount, kind, health.itemCount)
        "failed" -> com.streammate.tv.feature.settings.readableImportError(resources, health.lastError)
            ?.let { resources.getString(R.string.health_failed_detail, kind, it) }
            ?: resources.getString(R.string.health_failed, kind, health.consecutiveFailures)
        else -> resources.getString(R.string.health_updating, kind)
    }

internal fun preferredGuideSourceId(
    sourceIds: List<String>,
    initialChannelSourceId: String?,
    savedSourceId: String?,
    lastChannelSourceId: String?,
): String? = initialChannelSourceId?.takeIf(sourceIds::contains)
    ?: savedSourceId?.takeIf(sourceIds::contains)
    ?: lastChannelSourceId?.takeIf(sourceIds::contains)
    ?: sourceIds.firstOrNull()

/** Between the hero and the grid below it. */
private val GUIDE_HERO_GAP = 12.dp

@Composable
private fun LoadingGuide() {
    val palette = StreamMateThemeTokens.palette
    Text(
        text = stringResource(R.string.guide_loading),
        color = palette.textMuted,
        modifier = Modifier.padding(28.dp).testTag("guide-loading"),
    )
}

/** A source as the guide's switcher names it. */
private data class GuideSourceOption(val sourceId: String, val sourceName: String)
