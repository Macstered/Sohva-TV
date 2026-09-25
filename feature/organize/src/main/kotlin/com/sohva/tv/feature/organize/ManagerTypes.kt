package com.sohva.tv.feature.organize

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.ManagerSourceRow
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import kotlinx.coroutines.flow.Flow

/**
 * What the library manager needs from the app (plan/03 §4.6): the manager's reads, the rules,
 * one write that resolves the rules into the library at once (a viewer-started action, spec 42
 * §9.5), the shared "show hidden" choice, the remembered location, and where to go.
 */
interface ManagerEnvironment {
    suspend fun sources(): List<ManagerSourceRow>

    suspend fun groups(room: OrgRoom, scope: String?): List<ManagedGroup>

    suspend fun items(room: OrgRoom, group: ManagedGroup, search: String?): List<ManagedItem>

    suspend fun rules(room: OrgRoom): List<OrgRule>

    /** Writes [changes] and brings the library up to date; the changes that undo it, or the failure. */
    suspend fun apply(changes: List<RuleChange>): Outcome<List<RuleChange>>

    /** `editors_show_hidden`, shared with channel management (ORG-31). */
    val showHidden: Flow<Boolean>

    suspend fun setShowHidden(value: Boolean)

    /** The room's last group key and source (ORG-28). */
    suspend fun location(room: OrgRoom): Pair<String?, String?>

    suspend fun saveLocation(room: OrgRoom, group: String?, source: String?)

    /** "Advanced" (Live): channel management (ORG-29). */
    fun openAdvanced()

    fun leave()
}

/** Where the manager opens (spec 42 ORG-FR-01): a room and, from the guide or a wall, a group. */
data class ManagerStart(val room: OrgRoom, val group: String? = null, val source: String? = null)

/** The filter button (ORG-FR-42). */
enum class ManagerFilter { ALL, ENABLED, DISABLED }

enum class ManagerPane { GROUPS, ITEMS }

/** The open dialog, if any (ORG-FR-49…54). */
sealed interface ManagerMenu {
    data class Group(val group: ManagedGroup) : ManagerMenu

    data class Item(val item: ManagedItem) : ManagerMenu

    data object GroupOrder : ManagerMenu

    data object DefaultOrder : ManagerMenu

    data class ContentOrder(val group: ManagedGroup) : ManagerMenu

    data object Bulk : ManagerMenu

    /** "Move to position": a 1-based number for the group or item being placed. */
    data class Position(val pane: ManagerPane, val key: String) : ManagerMenu

    /** A change that asks first (bulk, reset, reset sorts): [count] entries in [scopeLabel]. */
    data class Confirm(val changes: List<RuleChange>, val count: Int) : ManagerMenu
}

/** A move in progress (ORG-FR-52): the pane's whole order with the moved key in it. */
@Immutable
data class ManagerMove(val pane: ManagerPane, val order: List<String>, val moving: String)

/** The footer's line, highest priority first (ORG-FR-57). */
enum class ManagerFooter { LOAD_ERROR, LOADING, SAVE_ERROR, SAVING, MOVE, SELECTION, HELP }

@Immutable
data class ManagerState(
    val room: OrgRoom,
    val scope: String? = null,
    val sources: List<ManagerSourceRow> = emptyList(),
    val filter: ManagerFilter = ManagerFilter.ALL,
    /** Null while loading. */
    val groups: List<ManagedGroup>? = null,
    val loadFailed: Boolean = false,
    val selectedKey: String? = null,
    /** The selected group's items; null while they are read. */
    val items: List<ManagedItem>? = null,
    val pane: ManagerPane = ManagerPane.GROUPS,
    val search: String = "",
    val menu: ManagerMenu? = null,
    val move: ManagerMove? = null,
    /** Null outside selection mode; else the selected keys of the focused pane (ORG-FR-53). */
    val selection: Set<String>? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val undo: List<RuleChange>? = null,
) {
    val selectedGroup: ManagedGroup? get() = groups?.firstOrNull { it.key == selectedKey }

    val footer: ManagerFooter
        get() = when {
            loadFailed -> ManagerFooter.LOAD_ERROR
            groups == null -> ManagerFooter.LOADING
            saveFailed -> ManagerFooter.SAVE_ERROR
            saving -> ManagerFooter.SAVING
            move != null -> ManagerFooter.MOVE
            selection != null -> ManagerFooter.SELECTION
            else -> ManagerFooter.HELP
        }
}
