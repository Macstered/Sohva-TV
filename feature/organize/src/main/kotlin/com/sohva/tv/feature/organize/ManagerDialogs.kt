package com.sohva.tv.feature.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.core.data.org.ManagedKind
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** One menu entry: a label, what it does, and a test tag. */
internal data class MenuEntry(val label: String, val tag: String, val danger: Boolean = false, val action: () -> Unit)

/**
 * A manager menu (design/03 §5.3): a platform dialog, a 460 × ≤ 480 dp card on `panel`, a title and
 * a scrolling column of full-width compact buttons; focus starts on the first. Back closes it.
 */
@Composable
internal fun ManagerMenuDialog(title: String?, entries: List<MenuEntry>, onDismiss: () -> Unit, note: String? = null) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(460.dp).heightIn(max = 480.dp).testTag("manager-menu"), corner = 16.dp) {
                if (title != null) DialogTitle(title)
                if (note != null) Text(note, Modifier.padding(bottom = 6.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    entries.forEachIndexed { i, e ->
                        TvActionButton(
                            e.label, e.action,
                            Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier).testTag(e.tag),
                            state = SurfaceState(danger = e.danger), compact = true,
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
    }
}

/** The dialog of [menu] (ORG-FR-49…54). */
@Composable
internal fun ManagerMenus(model: ManagerModel, state: ManagerState) {
    val menu = state.menu ?: return
    val close = model::closeMenu
    when (menu) {
        is ManagerMenu.Group -> {
            val g = menu.group
            val entries = buildList {
                add(MenuEntry(stringResource(if (g.shown) R.string.manager_disable_group else R.string.manager_enable_group), "manager-menu-shown") { model.setGroupShown(g, !g.shown) })
                if (g.kind == ManagedKind.GROUP) add(MenuEntry(stringResource(R.string.manager_item_sort), "manager-menu-content-order") { model.openMenu(ManagerMenu.ContentOrder(g)) })
                add(MenuEntry(stringResource(R.string.manager_move), "manager-menu-move") { model.beginMove(ManagerPane.GROUPS, g.key) })
                add(MenuEntry(stringResource(R.string.manager_top), "manager-menu-top") { model.beginMove(ManagerPane.GROUPS, g.key, ManagerModel.MoveTarget.Top) })
                add(MenuEntry(stringResource(R.string.manager_bottom), "manager-menu-bottom") { model.beginMove(ManagerPane.GROUPS, g.key, ManagerModel.MoveTarget.Bottom) })
                add(MenuEntry(stringResource(R.string.manager_position), "manager-menu-position") { model.openMenu(ManagerMenu.Position(ManagerPane.GROUPS, g.key)) })
                add(MenuEntry(stringResource(R.string.manager_reset), "manager-menu-reset", danger = true) { model.askResetGroup(g) })
                add(MenuEntry(stringResource(R.string.action_back), "manager-menu-back", action = close))
            }
            ManagerMenuDialog(groupLabel(g), entries, close)
        }
        is ManagerMenu.Item -> {
            val item = menu.item
            val note = when {
                item.sourceDisabled -> stringResource(R.string.manager_source_disabled)
                item.groupHidden -> stringResource(R.string.manager_parent_disabled)
                else -> null
            }
            val entries = listOf(
                MenuEntry(stringResource(if (item.hiddenEverywhere) R.string.manager_show_everywhere else R.string.manager_hide_everywhere), "manager-menu-everywhere") {
                    model.setEverywhere(item, item.hiddenEverywhere)
                },
                MenuEntry(stringResource(R.string.manager_move), "manager-menu-move") { model.beginMove(ManagerPane.ITEMS, item.identity) },
                MenuEntry(stringResource(R.string.manager_top), "manager-menu-top") { model.beginMove(ManagerPane.ITEMS, item.identity, ManagerModel.MoveTarget.Top) },
                MenuEntry(stringResource(R.string.manager_bottom), "manager-menu-bottom") { model.beginMove(ManagerPane.ITEMS, item.identity, ManagerModel.MoveTarget.Bottom) },
                MenuEntry(stringResource(R.string.manager_position), "manager-menu-position") { model.openMenu(ManagerMenu.Position(ManagerPane.ITEMS, item.identity)) },
                MenuEntry(stringResource(R.string.action_back), "manager-menu-back", action = close),
            )
            ManagerMenuDialog(item.title, entries, close, note)
        }
        ManagerMenu.GroupOrder -> ManagerMenuDialog(
            null,
            listOf(OrgSort.PROVIDER, OrgSort.TITLE_ASC, OrgSort.TITLE_DESC, OrgSort.MANUAL).map { sort ->
                MenuEntry(stringResource(sortLabel(sort)), "manager-sort-${sort.wire}") { model.chooseGroupOrder(sort) }
            },
            close,
        )
        ManagerMenu.DefaultOrder -> ManagerMenuDialog(
            null,
            OrgSort.offered(state.room).map { sort -> MenuEntry(stringResource(sortLabel(sort)), "manager-sort-${sort.wire}") { model.chooseDefaultOrder(sort) } } +
                MenuEntry(stringResource(R.string.manager_reset_sorts), "manager-sort-reset") { model.askResetSorts() },
            close,
        )
        is ManagerMenu.ContentOrder -> ManagerMenuDialog(
            null,
            OrgSort.offered(state.room).map { sort ->
                MenuEntry(stringResource(sortLabel(sort)), "manager-sort-${sort.wire}") { model.chooseContentOrder(menu.group, sort) }
            } + MenuEntry(stringResource(R.string.manager_inherit), "manager-sort-inherit") { model.chooseContentOrder(menu.group, null) },
            close,
        )
        ManagerMenu.Bulk -> ManagerMenuDialog(
            null,
            listOf(
                MenuEntry(stringResource(if (state.selection == null) R.string.manager_select_multiple else R.string.manager_selection_done), "manager-bulk-mode") { model.toggleSelectionMode() },
                MenuEntry(stringResource(R.string.manager_select_all), "manager-bulk-all") { model.selectAll() },
                MenuEntry(stringResource(R.string.manager_enable_selected), "manager-bulk-enable") { model.askBulk(on = true) },
                MenuEntry(stringResource(R.string.manager_disable_selected), "manager-bulk-disable") { model.askBulk(on = false) },
            ),
            close,
        )
        is ManagerMenu.Position -> PositionDialog(model, menu)
        is ManagerMenu.Confirm -> ManagerMenuDialog(
            stringResource(R.string.manager_confirm, menu.count),
            listOf(
                MenuEntry(stringResource(R.string.manager_apply), "manager-confirm-apply", danger = true) { model.confirm() },
                MenuEntry(stringResource(R.string.action_back), "manager-confirm-back", action = close),
            ),
            close,
            note = scopeLine(state),
        )
    }
}

/** "Move to position": a digits field (at most 6); an empty field does nothing (ORG-FR-52). */
@Composable
private fun PositionDialog(model: ManagerModel, menu: ManagerMenu.Position) {
    var text by remember { mutableStateOf("") }
    val entries = listOf(
        MenuEntry(stringResource(R.string.manager_apply), "manager-position-apply") {
            val n = text.toIntOrNull()
            if (n == null || n < 1) model.closeMenu() else model.beginMove(menu.pane, menu.key, ManagerModel.MoveTarget.Position(n))
        },
        MenuEntry(stringResource(R.string.action_back), "manager-position-back", action = model::closeMenu),
    )
    Dialog(onDismissRequest = model::closeMenu, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val field = remember { FocusRequester() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(460.dp).testTag("manager-position"), corner = 16.dp) {
                DialogTitle(stringResource(R.string.manager_position))
                TvUrlField(
                    text, { value -> text = value.filter(Char::isDigit).take(6) }, stringResource(R.string.manager_position),
                    Modifier.fillMaxWidth().focusRequester(field).testTag("manager-position-field"),
                    input = FieldInput(keyboard = KeyboardType.Number, compact = true),
                )
                entries.forEach { e -> TvActionButton(e.label, e.action, Modifier.fillMaxWidth().padding(top = 5.dp).testTag(e.tag), compact = true) }
            }
        }
        LaunchedEffect(Unit) { field.requestFocusWhenAttached() }
    }
}

/** "<scope> · <room>" under a confirmation (ORG-FR-54). */
@Composable
private fun scopeLine(state: ManagerState): String {
    val scope = state.scope?.let { id -> state.sources.firstOrNull { it.id == id }?.name } ?: stringResource(R.string.manager_all_sources)
    return "$scope · ${stringResource(roomLabel(state.room))}"
}

internal fun sortLabel(sort: OrgSort): Int = when (sort) {
    OrgSort.PROVIDER -> R.string.manager_provider
    OrgSort.TITLE_ASC -> R.string.manager_az
    OrgSort.TITLE_DESC -> R.string.manager_za
    OrgSort.NEWEST -> R.string.manager_newest
    OrgSort.OLDEST -> R.string.manager_oldest
    OrgSort.RATING -> R.string.manager_rating
    OrgSort.MANUAL -> R.string.manager_manual
}

internal fun roomLabel(room: OrgRoom): Int = when (room) {
    OrgRoom.LIVE -> R.string.manager_live
    OrgRoom.MOVIES -> R.string.manager_movies
    OrgRoom.SERIES -> R.string.manager_series
}
