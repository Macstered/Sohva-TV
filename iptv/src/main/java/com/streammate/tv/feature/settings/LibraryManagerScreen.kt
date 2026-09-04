package com.streammate.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.model.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ManagerFilter { ALL, ENABLED, DISABLED }
private enum class ManagerPane { GROUPS, ITEMS }
private enum class ManagerMenu { GROUP, ITEM, GROUP_SORT, ITEM_SORT, DEFAULT_SORT, BULK, POSITION }
private data class ManagerMove(val pane: ManagerPane, val id: String, val ids: List<String>)

@Composable
fun LibraryManagerScreen(
    repository: OrganizationRepository,
    guideRepository: GuideRepository,
    initialRoom: LibraryRoom,
    initialGroup: String? = null,
    initialSource: String? = null,
    onAdvanced: () -> Unit,
    onBack: () -> Unit,
) {
    var room by remember { mutableStateOf(initialRoom) }
    var reload by remember { mutableIntStateOf(0) }
    val library by remember(room, reload) { repository.observeLibrary(room, guideRepository) }
        .collectAsStateWithLifecycle(initialValue = ManagedLibrary(loading = true))
    val location by produceState<Triple<LibraryRoom, String?, String?>?>(null, room) {
        val saved = if (room == initialRoom && initialGroup != null) initialGroup to initialSource else try { repository.managerLocation(room) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null to null }
        value = Triple(room, saved.first, saved.second)
    }
    val start = location?.takeIf { it.first == room } ?: return
    LibraryManagerContent(
        room, library, start.second, start.third,
        onRoom = { room = it }, onChange = repository::change,
        onAdvanced = onAdvanced, onBack = onBack,
        onLocation = { group, source -> repository.saveManagerLocation(room, group, source) },
        onRetry = { reload++ },
    )
}

@Composable
fun LibraryManagerContent(
    room: LibraryRoom,
    library: ManagedLibrary,
    initialGroup: String? = null,
    initialSource: String? = null,
    onRoom: (LibraryRoom) -> Unit,
    onChange: suspend (List<OrganizationChange>) -> Unit,
    onAdvanced: () -> Unit = {},
    onBack: () -> Unit,
    onLocation: suspend (String?, String?) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    val palette = StreamMateThemeTokens.palette
    val scope = rememberCoroutineScope()
    val state = library.state.organization
    var sourceId by remember(room) { mutableStateOf(initialSource) }
    var selectedGroupKey by remember(room) { mutableStateOf(initialGroup?.let { if (it.startsWith("@") || it.startsWith("name:")) it else organizationGroupKey(it) }) }
    var focusedItemId by remember(room) { mutableStateOf<String?>(null) }
    var pane by remember(room) { mutableStateOf(ManagerPane.GROUPS) }
    var search by remember(room) { mutableStateOf("") }
    var filter by remember(room) { mutableStateOf(ManagerFilter.ALL) }
    var selectionMode by remember(room) { mutableStateOf(false) }
    var selection by remember(room) { mutableStateOf(emptySet<String>()) }
    var menu by remember(room) { mutableStateOf<ManagerMenu?>(null) }
    var move by remember(room) { mutableStateOf<ManagerMove?>(null) }
    var consumeCenterUp by remember { mutableStateOf(false) }
    var consumeBackUp by remember { mutableStateOf(false) }
    var positionInput by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<List<OrganizationChange>?>(null) }
    var undo by remember(room) { mutableStateOf<List<OrganizationChange>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val groupsState = rememberLazyListState()
    val itemsState = rememberLazyListState()
    val groupFocus = remember { mutableMapOf<String, FocusRequester>() }
    val itemFocus = remember { mutableMapOf<String, FocusRequester>() }
    val backFocus = remember { FocusRequester() }
    var initializedFocus by remember(room) { mutableStateOf(false) }
    var restoreRequest by remember { mutableIntStateOf(0) }
    var lastGroupIndex by remember(room) { mutableIntStateOf(0) }
    var lastItemIndex by remember(room) { mutableIntStateOf(0) }

    val groups by produceState(emptyList<ManagedGroup>(), room, library, sourceId) {
        value = if (library.loading || library.loadError) emptyList() else withContext(Dispatchers.Default) { managedGroups(room, library, sourceId) }
    }
    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey } ?: groups.firstOrNull()
    val itemSlice by produceState<Pair<String?, List<OrganizationItem>>>(null to emptyList(), room, selectedGroup, state) {
        value = selectedGroup?.key to withContext(Dispatchers.Default) { selectedGroup?.orderedItems(room, state).orEmpty() }
    }
    val orderedItems = itemSlice.second.takeIf { itemSlice.first == selectedGroup?.key }.orEmpty()
    fun itemEnabled(item: OrganizationItem): Boolean = state.enabledInView(room, item, selectedGroup?.key?.takeIf { selectedGroup.custom })
    fun accepts(enabled: Boolean) = filter == ManagerFilter.ALL || enabled == (filter == ManagerFilter.ENABLED)
    val visibleGroups = groups.filter { accepts(it.enabled(room, state)) && (pane != ManagerPane.GROUPS || search.isBlank() || it.name.contains(search, true)) }
        .let { rows -> if (move?.pane == ManagerPane.GROUPS) rows.sortedBy { move!!.ids.indexOf(it.key).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE } else rows }
    val visibleItems = orderedItems.filter { accepts(itemEnabled(it)) && (search.isBlank() || pane == ManagerPane.GROUPS || it.title.contains(search, true)) }
        .let { rows -> if (move?.pane == ManagerPane.ITEMS) rows.sortedBy { move!!.ids.indexOf(it.identity).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE } else rows }
    val focusedItem = orderedItems.firstOrNull { it.identity == focusedItemId } ?: visibleItems.firstOrNull()

    fun save(changes: List<OrganizationChange>, keepUndo: Boolean = true) {
        if (saving || changes.isEmpty()) return
        val previous = changes.map { change ->
            val old = state.rule(change.key)
            change.copy(enabled = old?.enabled, sort = old?.sort, position = old?.position)
        }
        saving = true
        error = false
        scope.launch {
            try {
                onChange(changes)
                if (keepUndo) undo = previous
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    fun restorePaneFocus() {
        restoreRequest++
    }

    fun leaveMenu() { menu = null; restorePaneFocus() }
    fun beginMove(destination: Int? = null) {
        search = ""
        filter = ManagerFilter.ALL
        selectionMode = false
        selection = emptySet()
        val ids = if (pane == ManagerPane.GROUPS) groups.map { it.key } else orderedItems.map { it.identity }
        val id = if (pane == ManagerPane.GROUPS) selectedGroup?.key else focusedItem?.identity
        if (id != null && ids.size > 1) move = ManagerMove(pane, id, destination?.let { movedOrganizationIds(ids, id, it) } ?: ids)
        menu = null
        restorePaneFocus()
    }

    fun back() {
        when {
            confirmation != null -> { confirmation = null; restorePaneFocus() }
            menu != null -> leaveMenu()
            move != null -> { move = null; restorePaneFocus() }
            selectionMode -> { selectionMode = false; selection = emptySet() }
            search.isNotBlank() -> search = ""
            else -> onBack()
        }
    }
    BackHandler(onBack = ::back)

    LaunchedEffect(room, sourceId, visibleGroups.map { it.key }) {
        if (!initializedFocus && visibleGroups.isNotEmpty()) {
            val target = visibleGroups.firstOrNull { it.key == selectedGroupKey } ?: visibleGroups.first()
            selectedGroupKey = target.key
            groupsState.scrollToItem(visibleGroups.indexOf(target))
            initializedFocus = groupFocus[target.key]?.requestFocusWhenAttached() == true
        } else if (initializedFocus && pane == ManagerPane.GROUPS && visibleGroups.none { it.key == selectedGroupKey }) {
            selectedGroupKey = visibleGroups.getOrNull(lastGroupIndex.coerceAtMost(visibleGroups.lastIndex))?.key
            if (visibleGroups.isNotEmpty()) groupsState.scrollToItem(lastGroupIndex.coerceIn(0, visibleGroups.lastIndex))
            groupFocus[selectedGroupKey]?.requestFocusWhenAttached() ?: backFocus.requestFocusWhenAttached()
        }
    }
    LaunchedEffect(visibleItems.map { it.identity }) {
        if (pane == ManagerPane.ITEMS && visibleItems.none { it.identity == focusedItemId }) {
            focusedItemId = visibleItems.getOrNull(lastItemIndex.coerceAtMost(visibleItems.lastIndex))?.identity
            if (visibleItems.isNotEmpty()) itemsState.scrollToItem(lastItemIndex.coerceIn(0, visibleItems.lastIndex))
            itemFocus[focusedItemId]?.requestFocusWhenAttached() ?: groupFocus[selectedGroup?.key]?.requestFocusWhenAttached()
        }
    }
    LaunchedEffect(library.loadError) { if (library.loadError) backFocus.requestFocusWhenAttached() }
    LaunchedEffect(room, selectedGroupKey, sourceId) {
        if (selectedGroupKey != null) try { onLocation(selectedGroupKey, sourceId) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = true }
    }
    LaunchedEffect(move) {
        val pending = move ?: return@LaunchedEffect
        val index = pending.ids.indexOf(pending.id)
        if (index >= 0) {
            if (pending.pane == ManagerPane.GROUPS) groupsState.scrollToItem(index) else itemsState.scrollToItem(index)
            if (pending.pane == ManagerPane.GROUPS) groupFocus[pending.id]?.requestFocusWhenAttached() else itemFocus[pending.id]?.requestFocusWhenAttached()
        }
    }
    LaunchedEffect(restoreRequest, menu, confirmation) {
        if (restoreRequest == 0 || menu != null || confirmation != null) return@LaunchedEffect
        if (pane == ManagerPane.ITEMS && focusedItem != null) {
            val index = visibleItems.indexOfFirst { it.identity == focusedItem.identity }
            if (index >= 0) itemsState.scrollToItem(index)
            if (itemFocus[focusedItem.identity]?.requestFocusWhenAttached() == true) return@LaunchedEffect
        }
        val index = visibleGroups.indexOfFirst { it.key == selectedGroup?.key }
        if (index >= 0) groupsState.scrollToItem(index)
        if (groupFocus[selectedGroup?.key]?.requestFocusWhenAttached() != true) backFocus.requestFocusWhenAttached()
    }

    val groupLabel: @Composable (ManagedGroup) -> String = { group ->
        when (group.key) {
            ORGANIZATION_HISTORY -> stringResource(R.string.manager_history)
            ORGANIZATION_RECENT -> stringResource(R.string.manager_recent)
            ORGANIZATION_FAVOURITES -> stringResource(R.string.manager_favourites)
            else -> group.name.ifBlank { stringResource(R.string.manager_ungrouped) }
        }
    }

    StreamMateScreenBackground { background ->
        Column(background.onPreviewKeyEvent { event ->
            if (consumeBackUp && event.key == Key.Back && event.type == KeyEventType.KeyUp) { consumeBackUp = false; return@onPreviewKeyEvent true }
            val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            if (consumeCenterUp && center && event.type == KeyEventType.KeyUp) { consumeCenterUp = false; return@onPreviewKeyEvent true }
            val pending = move ?: return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent event.key in listOf(Key.DirectionUp, Key.DirectionDown) || center
            when (event.key) {
                Key.Back -> { move = null; consumeBackUp = true; restorePaneFocus(); true }
                Key.DirectionUp, Key.DirectionDown -> {
                    val index = pending.ids.indexOf(pending.id)
                    move = pending.copy(ids = movedOrganizationIds(pending.ids, pending.id, index + if (event.key == Key.DirectionUp) -1 else 1))
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    val changes = if (pending.pane == ManagerPane.GROUPS) groupRankChanges(room, groups, pending.ids, sourceId)
                        else selectedGroup?.let { itemRankChanges(room, it, pending.ids, sourceId) }.orEmpty()
                    save(changes)
                    consumeCenterUp = true
                    move = null
                    true
                }
                else -> false
            }
        }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.manager_title), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                TvActionButton(stringResource(R.string.action_back), onClick = ::back, modifier = Modifier.focusRequester(backFocus), testTag = "manager-back")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryRoom.entries.forEach { value -> TvActionButton(roomLabel(value), selected = room == value, compact = true, onClick = { if (!saving && move == null) onRoom(value) }, testTag = "manager-room-${value.name}") }
                TvActionButton(library.sourceNames[sourceId] ?: stringResource(R.string.manager_all_sources), compact = true, onClick = {
                    val values = listOf<String?>(null) + library.sourceNames.keys.sorted()
                    sourceId = values[(values.indexOf(sourceId) + 1).mod(values.size)]
                    selection = emptySet(); initializedFocus = false
                }, testTag = "manager-source")
                TvActionButton(stringResource(when (filter) { ManagerFilter.ALL -> R.string.manager_all; ManagerFilter.ENABLED -> R.string.manager_enabled; ManagerFilter.DISABLED -> R.string.manager_disabled }), compact = true,
                    onClick = { filter = ManagerFilter.entries[(filter.ordinal + 1) % ManagerFilter.entries.size] }, testTag = "manager-filter")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TvUrlField(search, { search = it }, stringResource(R.string.manager_search), Modifier.weight(1f), editOnClickOnly = true, keyboardType = KeyboardType.Text, testTag = "manager-search")
                TvActionButton(stringResource(R.string.manager_clear), compact = true, onClick = { search = "" }, testTag = "manager-clear")
                TvActionButton(stringResource(R.string.manager_group_sort), compact = true, onClick = { menu = ManagerMenu.GROUP_SORT })
                TvActionButton(stringResource(R.string.manager_default_sort), compact = true, onClick = { menu = ManagerMenu.DEFAULT_SORT })
                TvActionButton(stringResource(R.string.manager_bulk), compact = true, onClick = { menu = ManagerMenu.BULK }, testTag = "manager-bulk")
                if (room == LibraryRoom.LIVE) TvActionButton(stringResource(R.string.manager_advanced), compact = true, onClick = onAdvanced)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.width(290.dp).fillMaxHeight().focusGroup().testTag("manager-groups"), state = groupsState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visibleGroups, key = { it.key }) { group ->
                        val focus = remember(groupFocus, group.key) { groupFocus.getOrPut(group.key) { FocusRequester() } }
                        val total = group.items.distinctBy { it.identity }.size
                        val enabled = group.items.filter { state.enabledInView(room, it, group.key.takeIf { group.custom }) }.distinctBy { it.identity }.size
                        ManagerRow(
                            title = groupLabel(group), subtitle = if (group.automatic) stringResource(R.string.manager_automatic_view) else "$enabled / $total",
                            enabled = group.enabled(room, state), selected = if (selectionMode && pane == ManagerPane.GROUPS) group.key in selection else selectedGroup?.key == group.key,
                            modifier = Modifier.focusRequester(focus).onFocusChanged { if (it.isFocused && move == null) {
                                if (pane != ManagerPane.GROUPS) selection = emptySet()
                                pane = ManagerPane.GROUPS; selectedGroupKey = group.key; lastGroupIndex = visibleGroups.indexOf(group)
                            } }.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && move == null) {
                                    if (visibleItems.isNotEmpty()) scope.launch { search = ""; pane = ManagerPane.ITEMS; selection = emptySet(); itemsState.scrollToItem(0); itemFocus[visibleItems.first().identity]?.requestFocusWhenAttached() }
                                    else menu = ManagerMenu.GROUP
                                    true
                                } else false
                            }.testTag("manager-group-${group.key}"),
                            onClick = { selectedGroupKey = group.key; if (selectionMode) selection = if (group.key in selection) selection - group.key else selection + group.key else menu = ManagerMenu.GROUP },
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedGroup?.let { groupLabel(it) }.orEmpty(), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selectedGroup != null && !selectedGroup.automatic) TvActionButton(stringResource(R.string.manager_item_sort), compact = true, onClick = { menu = ManagerMenu.ITEM_SORT })
                    }
                    if (visibleItems.isEmpty()) {
                        Text(stringResource(if (selectedGroup?.automatic == true) R.string.manager_automatic_help else R.string.manager_empty), Modifier.padding(16.dp), color = palette.textMuted)
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().focusGroup().testTag("manager-items"), state = itemsState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(visibleItems, key = { it.identity }) { item ->
                            val focus = remember(itemFocus, item.identity) { itemFocus.getOrPut(item.identity) { FocusRequester() } }
                            ManagerRow(item.title, library.sourceNames[item.sourceId].orEmpty(), itemEnabled(item), selectionMode && item.identity in selection, item.imageUrl,
                                Modifier.focusRequester(focus).onFocusChanged { if (it.isFocused) { pane = ManagerPane.ITEMS; focusedItemId = item.identity; lastItemIndex = visibleItems.indexOf(item) } }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && move == null) when (event.key) {
                                            Key.DirectionLeft -> { search = ""; pane = ManagerPane.GROUPS; selection = emptySet(); scope.launch { groupFocus[selectedGroup?.key]?.requestFocusWhenAttached() }; true }
                                            Key.DirectionRight -> { menu = ManagerMenu.ITEM; true }
                                            else -> false
                                        } else false
                                    }.testTag("manager-item-${item.identity}"),
                                onClick = {
                                    if (selectionMode) selection = if (item.identity in selection) selection - item.identity else selection + item.identity
                                    else if (!itemEnabled(item) && (!item.sourceEnabled || !state.globallyEnabled(room, item) || state.groupRule(room, item).enabled == false)) menu = ManagerMenu.ITEM
                                    else selectedGroup?.let { group -> save(itemVisibilityChanges(room, group, listOf(item), !itemEnabled(item))) }
                                },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when { library.loadError -> R.string.manager_load_error; library.loading -> R.string.manager_loading; error -> R.string.manager_save_error; saving -> R.string.manager_saving; move != null -> R.string.manager_move_help; selectionMode -> R.string.manager_selection_help; else -> R.string.manager_help }), Modifier.weight(1f), color = palette.textMuted, fontSize = 12.sp)
                if (library.loadError) TvActionButton(stringResource(R.string.manager_retry), compact = true, onClick = onRetry, testTag = "manager-retry")
                if (undo != null && !saving) TvActionButton(stringResource(R.string.manager_undo), compact = true, onClick = { undo?.let { save(it, keepUndo = false) }; undo = null }, testTag = "manager-undo")
            }
        }
    }

    if (menu != null) {
        ManagerDialog(onDismiss = ::leaveMenu) {
            when (menu) {
                ManagerMenu.GROUP -> selectedGroup?.let { group ->
                    Text(groupLabel(group), fontWeight = FontWeight.Bold)
                    ManagerAction(stringResource(if (group.enabled(room, state)) R.string.manager_disable_group else R.string.manager_enable_group)) {
                        save(groupVisibilityChanges(room, listOf(group), sourceId, !group.enabled(room, state))); leaveMenu()
                    }
                    if (!group.automatic) ManagerAction(stringResource(R.string.manager_item_sort)) { menu = ManagerMenu.ITEM_SORT }
                    ManagerAction(stringResource(R.string.manager_move)) { beginMove() }
                    ManagerAction(stringResource(R.string.manager_top)) { beginMove(0) }
                    ManagerAction(stringResource(R.string.manager_bottom)) { beginMove(Int.MAX_VALUE) }
                    ManagerAction(stringResource(R.string.manager_position)) { positionInput = ""; menu = ManagerMenu.POSITION }
                    ManagerAction(stringResource(R.string.manager_reset)) {
                        confirmation = state.rules.filter { rule -> group.groupKeys(room, sourceId).any { key -> rule.key.room == key.room && rule.key.sourceId == key.sourceId && rule.key.groupKey == key.groupKey } }
                            .map { OrganizationChange(it.key, changeEnabled = true, changeSort = true, changePosition = true) }
                        menu = null
                    }
                }
                ManagerMenu.ITEM -> focusedItem?.let { item ->
                    Text(item.title, maxLines = 2)
                    if (!item.sourceEnabled) Text(stringResource(R.string.manager_source_disabled))
                    else if (state.groupRule(room, item).enabled == false) Text(stringResource(R.string.manager_parent_disabled))
                    ManagerAction(stringResource(if (state.globallyEnabled(room, item)) R.string.manager_hide_everywhere else R.string.manager_show_everywhere)) {
                        save(listOf(OrganizationChange(OrganizationKey(room, itemKey = item.identity), enabled = !state.globallyEnabled(room, item), changeEnabled = true))); leaveMenu()
                    }
                    ManagerAction(stringResource(R.string.manager_move)) { beginMove() }
                    ManagerAction(stringResource(R.string.manager_top)) { beginMove(0) }
                    ManagerAction(stringResource(R.string.manager_bottom)) { beginMove(Int.MAX_VALUE) }
                    ManagerAction(stringResource(R.string.manager_position)) { positionInput = ""; menu = ManagerMenu.POSITION }
                }
                ManagerMenu.GROUP_SORT, ManagerMenu.ITEM_SORT, ManagerMenu.DEFAULT_SORT -> {
                    val sortingMenu = menu
                    val options = if (sortingMenu == ManagerMenu.GROUP_SORT) listOf(LibrarySort.PROVIDER, LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC, LibrarySort.MANUAL)
                        else if (room == LibraryRoom.LIVE) listOf(LibrarySort.PROVIDER, LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC, LibrarySort.MANUAL)
                        else listOf(LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC, LibrarySort.NEWEST, LibrarySort.OLDEST, LibrarySort.RATING, LibrarySort.MANUAL)
                    options.forEach { sort -> ManagerAction(sortLabel(sort)) {
                        val changes = when (sortingMenu) {
                            ManagerMenu.GROUP_SORT -> {
                                val ranked = groups.any { group -> group.groupKeys(room, sourceId).any { state.rule(it)?.position != null } }
                                if (sort == LibrarySort.MANUAL && !ranked) groupRankChanges(room, groups, groups.map { it.key }, sourceId)
                                else listOf(OrganizationChange(OrganizationKey(room, groupKey = ORGANIZATION_GROUP_ORDER), sort = sort, changeSort = true))
                            }
                            ManagerMenu.DEFAULT_SORT -> {
                                val seeds = if (sort == LibrarySort.MANUAL) groups.filter { !it.automatic }.flatMap { group ->
                                    val members = group.orderedItems(room, state)
                                    val inherited = group.groupKeys(room, sourceId).all { state.rule(it)?.sort == null }
                                    val ranked = members.any { state.memberRule(room, it, group.key.takeIf { group.custom }).position != null }
                                    if (inherited && !ranked) itemRankChanges(room, group, members.map { it.identity }, sourceId).filter { it.key.itemKey.isNotEmpty() } else emptyList()
                                } else emptyList()
                                seeds + OrganizationChange(OrganizationKey(room), sort = sort, changeSort = true)
                            }
                            else -> selectedGroup?.let { group ->
                                val ranked = orderedItems.any { state.memberRule(room, it, group.key.takeIf { group.custom }).position != null }
                                if (sort == LibrarySort.MANUAL && !ranked) itemRankChanges(room, group, orderedItems.map { it.identity }, sourceId)
                                else group.groupKeys(room, sourceId).map { OrganizationChange(it, sort = sort, changeSort = true) }
                            }.orEmpty()
                        }
                        save(changes); leaveMenu()
                    } }
                    if (sortingMenu == ManagerMenu.ITEM_SORT) ManagerAction(stringResource(R.string.manager_inherit)) {
                        save(selectedGroup?.groupKeys(room, sourceId).orEmpty().map { OrganizationChange(it, changeSort = true) }); leaveMenu()
                    }
                    if (sortingMenu == ManagerMenu.DEFAULT_SORT) ManagerAction(stringResource(R.string.manager_reset_sorts)) {
                        confirmation = state.rules.filter { it.key.room == room && it.key.itemKey.isEmpty() && it.key.groupKey.isNotEmpty() && it.key.groupKey != ORGANIZATION_GROUP_ORDER && it.sort != null }
                            .map { OrganizationChange(it.key, changeSort = true) }; menu = null
                    }
                }
                ManagerMenu.BULK -> {
                    ManagerAction(stringResource(if (selectionMode) R.string.manager_selection_done else R.string.manager_select_multiple)) { selectionMode = !selectionMode; selection = emptySet(); leaveMenu() }
                    ManagerAction(stringResource(R.string.manager_select_all)) {
                        selectionMode = true; selection = if (pane == ManagerPane.GROUPS) visibleGroups.mapTo(linkedSetOf()) { it.key } else visibleItems.mapTo(linkedSetOf()) { it.identity }; leaveMenu()
                    }
                    listOf(true, false).forEach { enabled -> ManagerAction(stringResource(if (enabled) R.string.manager_enable_selected else R.string.manager_disable_selected)) {
                        confirmation = if (pane == ManagerPane.GROUPS) groupVisibilityChanges(room, visibleGroups.filter { !selectionMode || it.key in selection }, sourceId, enabled)
                            else selectedGroup?.let { group -> itemVisibilityChanges(room, group, visibleItems.filter { !selectionMode || it.identity in selection }, enabled) }.orEmpty()
                        menu = null
                    } }
                }
                ManagerMenu.POSITION -> {
                    TvUrlField(positionInput, { positionInput = it.filter(Char::isDigit).take(6) }, stringResource(R.string.manager_position), editOnClickOnly = true, keyboardType = KeyboardType.Number)
                    ManagerAction(stringResource(R.string.manager_move)) { positionInput.toIntOrNull()?.let { beginMove(it - 1) } }
                }
                null -> Unit
            }
            ManagerAction(stringResource(R.string.action_back), ::leaveMenu)
        }
    }
    confirmation?.let { changes -> ManagerDialog(onDismiss = { confirmation = null; restorePaneFocus() }) {
        Text(stringResource(R.string.manager_confirm, changes.map { it.key.itemKey.ifBlank { it.key.groupKey } }.distinct().size), fontWeight = FontWeight.Bold)
        Text((library.sourceNames[sourceId] ?: stringResource(R.string.manager_all_sources)) + " · " + roomLabel(room))
        ManagerAction(stringResource(R.string.manager_apply)) { save(changes); confirmation = null; selection = emptySet(); restorePaneFocus() }
        ManagerAction(stringResource(R.string.action_back)) { confirmation = null; restorePaneFocus() }
    } }
}

@Composable
private fun ManagerRow(title: String, subtitle: String, enabled: Boolean, selected: Boolean, imageUrl: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.fillMaxWidth().height(57.dp).background(if (focused) palette.surfaceFocused else palette.surface, shape)
        .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else palette.surface, shape)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (selected) "●" else if (enabled) "✓" else "—", color = if (enabled) palette.accent else palette.textMuted)
        if (!imageUrl.isNullOrBlank()) AsyncImage(imageUrl, contentDescription = null, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled) palette.textPrimary else palette.textMuted, fontSize = 14.sp)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ManagerDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
        Column(Modifier.width(460.dp).heightIn(max = 480.dp).background(StreamMateThemeTokens.palette.surface, RoundedCornerShape(16.dp)).padding(18.dp).focusRequester(first)) {
            // The dialog owns focus and Back; parent lists cannot receive navigation through it.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
        }
    }
}

@Composable private fun ManagerAction(label: String, onClick: () -> Unit) = TvActionButton(label, onClick = onClick, compact = true, modifier = Modifier.fillMaxWidth())

@Composable internal fun roomLabel(room: LibraryRoom): String = stringResource(when (room) { LibraryRoom.LIVE -> R.string.manager_live; LibraryRoom.MOVIES -> R.string.manager_movies; LibraryRoom.SERIES -> R.string.manager_series })
@Composable private fun sortLabel(sort: LibrarySort): String = stringResource(when (sort) {
    LibrarySort.PROVIDER -> R.string.manager_provider; LibrarySort.TITLE_ASC -> R.string.manager_az; LibrarySort.TITLE_DESC -> R.string.manager_za;
    LibrarySort.NEWEST -> R.string.manager_newest; LibrarySort.OLDEST -> R.string.manager_oldest; LibrarySort.RATING -> R.string.manager_rating; LibrarySort.MANUAL -> R.string.manager_manual
})
