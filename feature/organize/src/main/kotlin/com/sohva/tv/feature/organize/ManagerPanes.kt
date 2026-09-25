package com.sohva.tv.feature.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The manager's focus bookkeeping: one requester per composed row, the pane states for scrolling a
 * target into view, and the last focused item for returning after a dialog (ORG-FR-03). Each map
 * holds one pane's rows only: it starts over when the pane shows another room, scope or group, so
 * it never outgrows one list (≤ 2,000 items, spec 42 §9.2).
 */
@Stable
internal class ManagerFocus(val groupList: LazyListState, val itemList: LazyListState) {
    private val groups = HashMap<String, FocusRequester>()
    private val items = HashMap<String, FocusRequester>()
    private var groupsOf: Any? = null
    private var itemsOf: String? = null
    val back: FocusRequester = FocusRequester()
    var lastItem: String? = null

    /** Whether anything on the screen (not a dialog) has focus. */
    var screenFocused: Boolean = false

    /** A dialog or move is open; focus goes back to the row when it ends. */
    var restorePending: Boolean = false

    /** Called while composing the groups pane of [list] (room and scope). */
    fun groupsOf(list: Any) {
        if (list != groupsOf) groups.clear()
        groupsOf = list
    }

    /** Called while composing the items pane of [group]. */
    fun itemsOf(group: String?) {
        if (group != itemsOf) {
            items.clear()
            lastItem = null
        }
        itemsOf = group
    }

    fun group(key: String): FocusRequester = groups.getOrPut(key) { FocusRequester() }

    fun item(key: String): FocusRequester = items.getOrPut(key) { FocusRequester() }

    /** Scrolls [index] of [list] into view, then focuses it; Back when it cannot be reached. */
    suspend fun focus(list: LazyListState, index: Int, requester: FocusRequester) {
        if (index < 0) {
            back.requestFocusWhenAttached()
            return
        }
        val shown = list.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!shown) list.scrollToItem(index)
        if (!requester.requestFocusWhenAttached()) back.requestFocusWhenAttached()
    }
}

@Composable
internal fun rememberManagerFocus(): ManagerFocus {
    val groups = rememberLazyListState()
    val items = rememberLazyListState()
    return remember { ManagerFocus(groups, items) }
}

/** Keys common to a row: Up/Down step a move; the pane decides Left and Right. */
private fun Modifier.rowKeys(model: ManagerModel, moving: Boolean, sideways: (Key) -> Boolean): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
        Key.DirectionUp, Key.DirectionDown -> if (moving) {
            model.step(up = e.key == Key.DirectionUp)
            true
        } else {
            false
        }
        Key.DirectionLeft, Key.DirectionRight -> moving || sideways(e.key)
        else -> false
    }
}

/** The groups pane (ORG-FR-40, -43, -47): 290 dp, rows 4 dp apart. */
@Composable
internal fun GroupsPane(model: ManagerModel, state: ManagerState, groups: List<ManagedGroup>, focus: ManagerFocus, modifier: Modifier) {
    val move = state.move?.takeIf { it.pane == ManagerPane.GROUPS }
    focus.groupsOf(state.room to state.scope)
    LazyColumn(modifier.testTag("manager-groups"), state = focus.groupList, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(groups, key = { it.key }) { g ->
            val mark = when {
                state.selection != null && g.key in state.selection -> RowMark.SELECTED
                g.shown -> RowMark.ON
                else -> RowMark.OFF
            }
            ManagerRow(
                mark, groupLabel(g), groupSubtitle(g),
                onClick = { if (move != null) model.placeMove() else model.groupOk(g) },
                modifier = Modifier
                    .focusRequester(focus.group(g.key))
                    .onFocusChanged { if (it.isFocused && move == null) model.focusGroup(g.key) }
                    .rowKeys(model, move != null) { key -> key == Key.DirectionRight && model.enterItemsFrom(g) }
                    .testTag("manager-group-${g.key}"),
                dimmed = !g.shown,
                moving = move?.moving == g.key,
            )
        }
    }
}

/** The items pane (ORG-FR-44, -48): header, a help line, rows. */
@Composable
internal fun ItemsPane(model: ManagerModel, state: ManagerState, items: List<ManagedItem>, focus: ManagerFocus, modifier: Modifier) {
    val group = state.selectedGroup
    val move = state.move?.takeIf { it.pane == ManagerPane.ITEMS }
    focus.itemsOf(state.selectedKey)
    Column(modifier.testTag("manager-items")) {
        if (group != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    groupLabel(group), Modifier.weight(1f), style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Sohva.palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ManagerModel.hasItems(group)) {
                    TvActionButton(
                        stringResource(R.string.manager_item_sort), { model.openMenu(ManagerMenu.ContentOrder(group)) },
                        Modifier.testTag("manager-content-order"), compact = true,
                    )
                }
            }
        }
        val help = when {
            group == null -> null
            !ManagerModel.hasItems(group) -> R.string.manager_automatic_help
            state.items != null && items.isEmpty() -> R.string.manager_empty
            else -> null
        }
        if (help != null) Text(stringResource(help), Modifier.padding(16.dp).testTag("manager-items-help"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        LazyColumn(state = focus.itemList, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.identity }) { item ->
                val mark = when {
                    state.selection != null && item.identity in state.selection -> RowMark.SELECTED
                    item.enabled -> RowMark.ON
                    else -> RowMark.OFF
                }
                ManagerRow(
                    mark, item.title, item.sourceName,
                    onClick = { if (move != null) model.placeMove() else model.itemOk(item) },
                    modifier = Modifier
                        .focusRequester(focus.item(item.identity))
                        .onFocusChanged { if (it.isFocused) focus.lastItem = item.identity }
                        .rowKeys(model, move != null) { key ->
                            if (key == Key.DirectionRight) model.openMenu(ManagerMenu.Item(item)) else model.leaveItems()
                            true
                        }
                        .testTag("manager-item-${item.identity}"),
                    image = item.image,
                    imageName = item.title,
                    dimmed = !item.enabled,
                    moving = move?.moving == item.identity,
                )
            }
        }
    }
}
