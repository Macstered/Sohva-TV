package com.sohva.tv.feature.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.core.data.org.ManagedKind
import com.sohva.tv.core.data.org.ManagerChanges
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The library manager (spec 42 §4.9) for the life of its stack entry: one state, events in, every
 * read and write on the environment's threads. Focusing a group reads its items only after focus
 * has rested 150 ms; the location is stored 1 s after the rail settles (ORG-FR-45). One save at a
 * time; a failed save leaves everything as it was (ORG-FR-55).
 */
class ManagerModel(private val env: ManagerEnvironment, start: ManagerStart) : ViewModel() {
    private val _state = MutableStateFlow(ManagerState(start.room))
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var locationJob: Job? = null
    private var enterWhenRead = false

    init {
        viewModelScope.launch {
            val showHidden = env.showHidden.first()
            val sources = runCatching { env.sources() }.getOrDefault(emptyList())
            val (group, source) = if (start.group != null) keyOf(start.group) to start.source else env.location(start.room)
            _state.update { it.copy(sources = sources, filter = if (showHidden) ManagerFilter.ALL else ManagerFilter.ENABLED, scope = source?.takeIf { s -> sources.any { it.id == s } }) }
            load(select = group)
        }
    }

    /** A group given by name becomes its name key; `@` and `name:` keys are kept (ORG-FR-01). */
    private fun keyOf(group: String): String = if (group.startsWith("@") || group.startsWith("name:")) group else OrgKeys.nameKey(group)

    private suspend fun load(select: String? = _state.value.selectedKey) {
        val s = _state.value
        val groups = runCatching { env.groups(s.room, s.scope) }.getOrElse {
            _state.update { it.copy(loadFailed = true) }
            return
        }
        val target = select?.takeIf { key -> groups.any { it.key == key } } ?: groups.firstOrNull()?.key
        val keepItems = target == s.selectedKey && s.items != null
        _state.update { it.copy(groups = groups, loadFailed = false, selectedKey = target, items = if (keepItems) it.items else null) }
        readItems(delayMs = 0)
    }

    fun retry() {
        _state.update { it.copy(loadFailed = false, groups = null) }
        viewModelScope.launch { load() }
    }

    // ---- What the panes show (ORG-FR-42, -46) --------------------------------------------------

    fun visibleGroups(s: ManagerState = _state.value): List<ManagedGroup> = s.groups.orEmpty().filter { g ->
        val byFilter = when (s.filter) {
            ManagerFilter.ALL -> true
            ManagerFilter.ENABLED -> g.shown
            ManagerFilter.DISABLED -> !g.shown
        }
        byFilter && (s.pane != ManagerPane.GROUPS || s.search.isBlank() || g.label.contains(s.search.trim(), ignoreCase = true))
    }

    fun visibleItems(s: ManagerState = _state.value): List<ManagedItem> = s.items.orEmpty().filter { item ->
        val byFilter = when (s.filter) {
            ManagerFilter.ALL -> true
            ManagerFilter.ENABLED -> item.enabled
            ManagerFilter.DISABLED -> !item.enabled
        }
        byFilter && (s.pane != ManagerPane.ITEMS || s.search.isBlank() || item.title.contains(s.search.trim(), ignoreCase = true))
    }

    // ---- Top bar (ORG-FR-41, -42, -58) --------------------------------------------------------

    fun selectRoom(room: OrgRoom) {
        val s = _state.value
        if (s.saving || s.move != null || room == s.room) return
        viewModelScope.launch {
            val (group, source) = env.location(room)
            _state.value = ManagerState(room, scope = source?.takeIf { id -> s.sources.any { it.id == id } }, sources = s.sources, filter = s.filter)
            load(select = group)
        }
    }

    /** All sources → each source by id → All sources. */
    fun cycleScope() {
        val s = _state.value
        if (s.saving || s.move != null) return
        val ids = s.sources.map { it.id }
        val next = if (s.scope == null) ids.firstOrNull() else ids.getOrNull(ids.indexOf(s.scope) + 1)
        _state.update { it.copy(scope = next, groups = null, items = null, selection = null, pane = ManagerPane.GROUPS) }
        viewModelScope.launch { load(select = null) }
        saveLocationLater()
    }

    /** All → Enabled → Disabled → All; All and Enabled are remembered, Disabled is a passing look. */
    fun cycleFilter() {
        val next = when (_state.value.filter) {
            ManagerFilter.ALL -> ManagerFilter.ENABLED
            ManagerFilter.ENABLED -> ManagerFilter.DISABLED
            ManagerFilter.DISABLED -> ManagerFilter.ALL
        }
        _state.update { it.copy(filter = next) }
        if (next != ManagerFilter.DISABLED) viewModelScope.launch { env.setShowHidden(next == ManagerFilter.ALL) }
    }

    fun setSearch(text: String) = _state.update { it.copy(search = text.take(SEARCH_MAX)) }

    fun clearSearch() = _state.update { it.copy(search = "") }

    fun openAdvanced() = env.openAdvanced()

    fun leave() = env.leave()

    // ---- Groups and items (ORG-FR-45, -47, -48) -------------------------------------------------

    /** Focus on a group selects it; its items are read once focus rests (150 ms). */
    fun focusGroup(key: String) {
        if (_state.value.move != null || key == _state.value.selectedKey) return
        enterWhenRead = false
        _state.update { it.copy(selectedKey = key, items = null) }
        readItems(REST_MS)
        saveLocationLater()
    }

    private fun readItems(delayMs: Long) {
        itemsJob?.cancel()
        val s = _state.value
        val group = s.selectedGroup ?: return
        itemsJob = viewModelScope.launch {
            delay(delayMs)
            val items = runCatching { env.items(s.room, group, null) }.getOrNull() ?: return@launch
            if (_state.value.selectedKey != group.key) return@launch
            _state.update { it.copy(items = items) }
            if (enterWhenRead) {
                enterWhenRead = false
                enterItemsFrom(group)
            }
        }
    }

    /**
     * Right on a group (ORG-FR-47): the items pane; once they arrive when they are still being
     * read; the group menu when there is nothing to enter.
     */
    fun enterItemsFrom(group: ManagedGroup): Boolean {
        val s = _state.value
        if (s.selectedKey != group.key || s.move != null) return true
        when {
            !hasItems(group) -> openMenu(ManagerMenu.Group(group))
            s.items == null -> enterWhenRead = true
            visibleItems(s).isEmpty() -> openMenu(ManagerMenu.Group(group))
            else -> enterItems()
        }
        return true
    }

    private fun saveLocationLater() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            delay(LOCATION_MS)
            val s = _state.value
            env.saveLocation(s.room, s.selectedKey, s.scope)
        }
    }

    fun enterItems() = _state.update { it.copy(pane = ManagerPane.ITEMS, search = "", selection = null) }

    fun leaveItems() = _state.update { it.copy(pane = ManagerPane.GROUPS, search = "", selection = null) }

    /** OK on a group: its menu, or in selection mode its selection (ORG-FR-47). */
    fun groupOk(group: ManagedGroup) {
        val selection = _state.value.selection
        if (selection != null) return toggleSelected(group.key)
        _state.update { it.copy(menu = ManagerMenu.Group(group)) }
    }

    /** OK on an item: selection, the menu when something else keeps it off, else on/off in this group (ORG-FR-48). */
    fun itemOk(item: ManagedItem) {
        val s = _state.value
        if (s.selection != null) return toggleSelected(item.identity)
        if (item.blocked) return openMenu(ManagerMenu.Item(item))
        val group = s.selectedGroup ?: return
        apply(ManagerChanges.toggle(s.room, group, item))
    }

    fun openMenu(menu: ManagerMenu) = _state.update { it.copy(menu = menu) }

    fun closeMenu() = _state.update { it.copy(menu = null) }

    // ---- Menus (ORG-FR-49…51) ------------------------------------------------------------------

    fun setGroupShown(group: ManagedGroup, shown: Boolean) = apply(ManagerChanges.groupShown(_state.value.room, group, shown))

    fun setEverywhere(item: ManagedItem, shown: Boolean) = apply(ManagerChanges.everywhere(_state.value.room, item, shown))

    fun chooseGroupOrder(sort: OrgSort) = apply(ManagerChanges.groupOrder(_state.value.room, sort, _state.value.groups.orEmpty()))

    fun chooseDefaultOrder(sort: OrgSort) = apply(ManagerChanges.roomSort(_state.value.room, sort))

    fun chooseContentOrder(group: ManagedGroup, sort: OrgSort?) {
        val items = if (group.key == _state.value.selectedKey) _state.value.items.orEmpty() else emptyList()
        apply(ManagerChanges.groupSort(_state.value.room, group, sort, items))
    }

    fun askResetGroup(group: ManagedGroup) = confirmWith { rules -> ManagerChanges.resetGroup(group, rules) }

    fun askResetSorts() = confirmWith { rules -> ManagerChanges.resetSorts(_state.value.room, rules) }

    private fun confirmWith(build: (List<com.sohva.tv.core.model.org.OrgRule>) -> List<RuleChange>) {
        viewModelScope.launch {
            val changes = build(env.rules(_state.value.room))
            _state.update { it.copy(menu = if (changes.isEmpty()) null else ManagerMenu.Confirm(changes, countOf(changes))) }
        }
    }

    /** Distinct item keys, or group keys for group-level changes (ORG-FR-54). */
    private fun countOf(changes: List<RuleChange>): Int {
        val items = changes.map { it.key.itemKey }.filter { it.isNotEmpty() }.toSet()
        return if (items.isNotEmpty()) items.size else changes.map { it.key.groupKey }.toSet().size
    }

    fun confirm() {
        val menu = _state.value.menu as? ManagerMenu.Confirm ?: return
        _state.update { it.copy(selection = null) }
        apply(menu.changes)
    }

    // ---- Selection and bulk (ORG-FR-53) ---------------------------------------------------------

    fun toggleSelectionMode() = _state.update { it.copy(selection = if (it.selection == null) emptySet() else null, menu = null) }

    fun selectAll() = _state.update { s ->
        val keys = if (s.pane == ManagerPane.GROUPS) visibleGroups(s).map { it.key } else visibleItems(s).map { it.identity }
        s.copy(selection = keys.toSet(), menu = null)
    }

    private fun toggleSelected(key: String) = _state.update { s ->
        val now = s.selection ?: return@update s
        s.copy(selection = if (key in now) now - key else now + key)
    }

    /** Enable or disable the selected rows, or every visible row outside selection mode, after a confirmation. */
    fun askBulk(on: Boolean) {
        val s = _state.value
        val changes = if (s.pane == ManagerPane.GROUPS) {
            val groups = visibleGroups(s).filter { s.selection == null || it.key in s.selection }
            ManagerChanges.bulkGroups(s.room, groups, on)
        } else {
            val group = s.selectedGroup ?: return
            val items = visibleItems(s).filter { s.selection == null || it.identity in s.selection }
            ManagerChanges.bulkItems(s.room, group, items, on)
        }
        _state.update { it.copy(menu = if (changes.isEmpty()) null else ManagerMenu.Confirm(changes, countOf(changes))) }
    }

    // ---- Moves (ORG-FR-52) ----------------------------------------------------------------------

    /** Begins a move of [key] in [pane]; [to] places it first, last or at a 1-based position right away. */
    fun beginMove(pane: ManagerPane, key: String, to: MoveTarget = MoveTarget.Here) {
        val s = _state.value
        val order = if (pane == ManagerPane.GROUPS) s.groups.orEmpty().map { it.key } else s.items.orEmpty().map { it.identity }
        if (key !in order) return
        val without = order - key
        val at = when (to) {
            MoveTarget.Here -> order.indexOf(key)
            MoveTarget.Top -> 0
            MoveTarget.Bottom -> without.size
            is MoveTarget.Position -> (to.oneBased - 1).coerceIn(0, without.size)
        }
        val placed = without.toMutableList().apply { add(at, key) }
        _state.update { it.copy(move = ManagerMove(pane, placed, key), search = "", selection = null, menu = null) }
    }

    /** Up or Down past the next visible neighbour: a filtered-out row is never the swap partner. */
    fun step(up: Boolean) = _state.update { s ->
        val move = s.move ?: return@update s
        val visible = (if (move.pane == ManagerPane.GROUPS) visibleGroups(s).map { it.key } else visibleItems(s).map { it.identity }).toSet()
        val order = move.order.toMutableList()
        val at = order.indexOf(move.moving)
        var to = at
        do { to += if (up) -1 else 1 } while (to in order.indices && order[to] !in visible)
        if (to !in order.indices) return@update s
        order.removeAt(at)
        order.add(to, move.moving)
        s.copy(move = move.copy(order = order))
    }

    fun cancelMove() = _state.update { it.copy(move = null) }

    /** OK places the moved row: places for the whole order, the pane becomes manually ordered. */
    fun placeMove() {
        val s = _state.value
        val move = s.move ?: return
        val changes = if (move.pane == ManagerPane.GROUPS) {
            val byKey = s.groups.orEmpty().associateBy { it.key }
            ManagerChanges.moveGroup(s.room, move.order.mapNotNull(byKey::get))
        } else {
            val group = s.selectedGroup ?: return
            val byKey = s.items.orEmpty().associateBy { it.identity }
            val order = move.order.mapNotNull(byKey::get)
            ManagerChanges.moveItem(s.room, group, order, order.first { it.identity == move.moving })
        }
        _state.update { it.copy(move = null) }
        apply(changes)
    }

    // ---- Saving and Undo (ORG-FR-55, -56) --------------------------------------------------------

    fun undo() {
        val undo = _state.value.undo ?: return
        apply(undo, undoable = false)
    }

    private fun apply(changes: List<RuleChange>, undoable: Boolean = true) {
        if (_state.value.saving || changes.isEmpty()) return
        _state.update { it.copy(saving = true, saveFailed = false, menu = null) }
        viewModelScope.launch {
            when (val result = runCatching { env.apply(changes) }.getOrElse { Outcome.Failed(com.sohva.tv.core.model.error.AppError.Unknown) }) {
                is Outcome.Failed -> _state.update { it.copy(saving = false, saveFailed = true) }
                is Outcome.Ok -> {
                    _state.update { it.copy(saving = false, undo = if (undoable) result.value else null) }
                    load()
                }
            }
        }
    }

    sealed interface MoveTarget {
        data object Here : MoveTarget
        data object Top : MoveTarget
        data object Bottom : MoveTarget
        data class Position(val oneBased: Int) : MoveTarget
    }

    companion object {
        const val REST_MS: Long = 150
        const val LOCATION_MS: Long = 1_000
        const val SEARCH_MAX: Int = 80

        /** Shortcuts have no items and no content order (ORG-FR-44). */
        fun hasItems(group: ManagedGroup): Boolean = group.kind != ManagedKind.SHORTCUT
    }
}
