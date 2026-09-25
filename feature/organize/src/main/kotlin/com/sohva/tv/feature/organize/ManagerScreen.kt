package com.sohva.tv.feature.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The library manager (spec 42 §4.9, design/03 §5.3): title and Back, the room / scope / filter
 * row, the search and order row, the two panes and the footer. Back goes innermost first and
 * leaves through the window only when nothing is open (ORG-FR-03).
 */
@Composable
fun LibraryManagerScreen(model: ManagerModel) = trace("Manager:Screen") {
    val state by model.state.collectAsStateWithLifecycle()
    val focus = rememberManagerFocus()
    val groups = remember(state) { ordered(model.visibleGroups(state), state.move, ManagerPane.GROUPS) { it.key } }
    val items = remember(state) { ordered(model.visibleItems(state), state.move, ManagerPane.ITEMS) { it.identity } }
    ScreenBackground(Modifier.fillMaxSize().testTag("screen-manager")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp).onFocusChanged { focus.screenFocused = it.hasFocus }) {
            Header(model, focus)
            Spacer(Modifier.height(8.dp))
            RoomRow(model, state)
            Spacer(Modifier.height(6.dp))
            ToolRow(model, state)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupsPane(model, state, groups, focus, Modifier.width(290.dp).fillMaxHeight())
                ItemsPane(model, state, items, focus, Modifier.weight(1f).fillMaxHeight())
            }
            Footer(model, state)
        }
    }
    ManagerMenus(model, state)
    BackHandler(enabled = state.menu == null && (state.move != null || state.selection != null || state.search.isNotEmpty())) {
        when {
            state.move != null -> model.cancelMove()
            state.selection != null -> model.toggleSelectionMode()
            else -> model.clearSearch()
        }
    }
    FocusEffects(model, state, groups, items, focus)
}

/** During a move the pane shows the move's order; rows the filter hides stay hidden. */
private fun <T> ordered(rows: List<T>, move: ManagerMove?, pane: ManagerPane, key: (T) -> String): List<T> {
    if (move == null || move.pane != pane) return rows
    val byKey = rows.associateBy(key)
    return move.order.mapNotNull(byKey::get)
}

/**
 * Where focus goes (ORG-FR-02, -03, -47, -48, -52, -55): on entry and after a room or scope change
 * the selected group; into the items pane its first row; back out the selected group; during a
 * move the moved row; after a dialog closes or a move ends the row it came from; a row that the
 * filter removed hands focus to the row now at its index. Data arriving never moves focus
 * otherwise.
 */
@Composable
private fun FocusEffects(model: ManagerModel, state: ManagerState, groups: List<ManagedGroup>, items: List<ManagedItem>, focus: ManagerFocus) {
    val loaded = state.groups != null
    LaunchedEffect(state.room, state.scope, loaded, state.loadFailed) {
        if (state.loadFailed) {
            focus.back.requestFocusWhenAttached()
        } else if (loaded) {
            val at = groups.indexOfFirst { it.key == state.selectedKey }.takeIf { it >= 0 } ?: if (groups.isEmpty()) -1 else 0
            if (at >= 0 && groups[at].key != state.selectedKey) model.focusGroup(groups[at].key)
            focus.focus(focus.groupList, at, focus.group(groups.getOrNull(at)?.key.orEmpty()))
        }
    }
    LaunchedEffect(state.pane) {
        if (state.pane == ManagerPane.ITEMS) {
            focus.focus(focus.itemList, if (items.isEmpty()) -1 else 0, focus.item(items.firstOrNull()?.identity.orEmpty()))
        } else if (loaded) {
            focusGroup(groups, state.selectedKey, focus)
        }
    }
    val moving = state.move?.moving
    LaunchedEffect(moving, state.move?.order) {
        val move = state.move ?: return@LaunchedEffect
        if (move.pane == ManagerPane.GROUPS) {
            focus.focus(focus.groupList, groups.indexOfFirst { it.key == moving }, focus.group(moving.orEmpty()))
        } else {
            focus.focus(focus.itemList, items.indexOfFirst { it.identity == moving }, focus.item(moving.orEmpty()))
        }
    }
    // Closing a dialog or ending a move: back to the row. A reload only acts when it took the
    // focused row away (nothing on screen has focus any more), never while focus is elsewhere.
    val settled = state.menu == null && state.move == null
    LaunchedEffect(settled) {
        if (!settled) {
            focus.restorePending = true
        } else if (focus.restorePending) {
            focus.restorePending = false
            restore(model, state, groups, items, focus)
        }
    }
    LaunchedEffect(state.groups, state.items) {
        withFrameNanos { }
        if (settled && loaded && !focus.screenFocused) restore(model, state, groups, items, focus)
    }
}

private suspend fun restore(model: ManagerModel, state: ManagerState, groups: List<ManagedGroup>, items: List<ManagedItem>, focus: ManagerFocus) {
    val last = focus.lastItem
    if (state.pane != ManagerPane.ITEMS || last == null) return focusGroup(groups, state.selectedKey, focus)
    val at = items.indexOfFirst { it.identity == last }
    val index = if (at >= 0) at else focus.itemList.firstVisibleItemIndex.coerceAtMost(items.lastIndex)
    if (index >= 0) focus.focus(focus.itemList, index, focus.item(items[index].identity)) else model.leaveItems()
}

private suspend fun focusGroup(groups: List<ManagedGroup>, key: String?, focus: ManagerFocus) {
    val at = groups.indexOfFirst { it.key == key }
    val index = if (at >= 0) at else focus.groupList.firstVisibleItemIndex.coerceAtMost(groups.lastIndex)
    focus.focus(focus.groupList, index, focus.group(groups.getOrNull(index)?.key.orEmpty()))
}

@Composable
private fun Header(model: ManagerModel, focus: ManagerFocus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.manager_title),
            style = Sohva.typography.display.copy(fontSize = 25.sp, fontWeight = FontWeight.Bold),
            color = Sohva.palette.textPrimary,
            maxLines = 1,
        )
        TvActionButton(stringResource(R.string.action_back), model::leave, Modifier.focusRequester(focus.back).testTag("manager-back"), icon = TvIcons.Back)
    }
}

/** Rooms, scope and filter (ORG-FR-41, -42, -58). */
@Composable
private fun RoomRow(model: ManagerModel, state: ManagerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (room in OrgRoom.entries) {
            TvActionButton(
                stringResource(roomLabel(room)), { model.selectRoom(room) },
                Modifier.testTag("manager-room-${room.name}"), state = SurfaceState(selected = room == state.room), compact = true,
            )
        }
        val scope = state.sources.firstOrNull { it.id == state.scope }?.name ?: stringResource(R.string.manager_all_sources)
        TvActionButton(scope, model::cycleScope, Modifier.testTag("manager-scope"), compact = true)
        val filter = when (state.filter) {
            ManagerFilter.ALL -> R.string.manager_all
            ManagerFilter.ENABLED -> R.string.manager_enabled
            ManagerFilter.DISABLED -> R.string.manager_disabled
        }
        TvActionButton(stringResource(filter), model::cycleFilter, Modifier.testTag("manager-filter"), compact = true)
    }
}

/** Search, Clear, Group order, Default order, Select / bulk and (Live) Advanced (ORG-FR-46, -58). */
@Composable
private fun ToolRow(model: ManagerModel, state: ManagerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TvUrlField(
            state.search, model::setSearch, stringResource(R.string.manager_search),
            Modifier.weight(1f).testTag("manager-search"), icon = TvIcons.Search, input = FieldInput(keyboard = KeyboardType.Text),
        )
        TvActionButton(stringResource(R.string.manager_clear), model::clearSearch, Modifier.testTag("manager-clear"), compact = true)
        TvActionButton(stringResource(R.string.manager_group_sort), { model.openMenu(ManagerMenu.GroupOrder) }, Modifier.testTag("manager-group-order"), compact = true)
        TvActionButton(stringResource(R.string.manager_default_sort), { model.openMenu(ManagerMenu.DefaultOrder) }, Modifier.testTag("manager-default-order"), compact = true)
        TvActionButton(
            stringResource(R.string.manager_bulk), { model.openMenu(ManagerMenu.Bulk) },
            Modifier.testTag("manager-bulk"), state = SurfaceState(selected = state.selection != null), compact = true,
        )
        if (state.room == OrgRoom.LIVE) {
            TvActionButton(stringResource(R.string.manager_advanced), model::openAdvanced, Modifier.testTag("manager-advanced"), compact = true)
        }
    }
}

/** The footer line by priority and Retry / Undo (ORG-FR-55…57). */
@Composable
private fun Footer(model: ManagerModel, state: ManagerState) {
    val footer = state.footer
    val text = when (footer) {
        ManagerFooter.LOAD_ERROR -> R.string.manager_load_error
        ManagerFooter.LOADING -> R.string.manager_loading
        ManagerFooter.SAVE_ERROR -> R.string.manager_save_error
        ManagerFooter.SAVING -> R.string.manager_saving
        ManagerFooter.MOVE -> R.string.manager_move_help
        ManagerFooter.SELECTION -> R.string.manager_selection_help
        ManagerFooter.HELP -> R.string.manager_help
    }
    Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(text), Modifier.weight(1f).testTag("manager-footer"), style = Sohva.typography.caption, color = Sohva.palette.textMuted, maxLines = 2)
        if (footer == ManagerFooter.LOAD_ERROR) {
            TvActionButton(stringResource(R.string.manager_retry), model::retry, Modifier.testTag("manager-retry"), compact = true)
        } else if (state.undo != null && !state.saving) {
            TvActionButton(stringResource(R.string.manager_undo), model::undo, Modifier.testTag("manager-undo"), compact = true)
        }
    }
}
