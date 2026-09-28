package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.home.HomeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › Home needs from the app (spec 02 HOME-FR-90). */
interface HomeLayoutServices {
    /** The active profile's layout, and its name when the household has several profiles. */
    val current: Flow<HomeLayoutView>

    suspend fun save(layout: HomeLayout)

    /** "Add a Trakt list" (HOME-FR-99): an address, a number or words from a name. */
    suspend fun findLists(text: String): ListFinding = ListFinding.Failed

    /** Adds [list] at the end of the layout, shown, with its name kept for its row. */
    suspend fun addList(list: ListChoice) = Unit

    /** The phone page in Trakt list mode (HOME-FR-100). */
    val listPhone: Flow<ListPhone> get() = kotlinx.coroutines.flow.flowOf(ListPhone.Closed)

    fun openListPhone() = Unit

    fun closeListPhone() = Unit
}

/** A public Trakt list the search found (HOME-FR-99). */
data class ListChoice(val id: Long, val name: String, val owner: String?, val items: Int, val likes: Int)

/** What "Add a Trakt list" found (HOME-FR-99). */
sealed interface ListFinding {
    data class Found(val lists: List<ListChoice>) : ListFinding

    data object Nothing : ListFinding

    /** An address of a list only its owner can see. */
    data object Private : ListFinding

    data object Failed : ListFinding
}

/** The phone page for Trakt lists (HOME-FR-100); [Open.added] names the lists it has added so far. */
sealed interface ListPhone {
    data object Closed : ListPhone

    data object NoNetwork : ListPhone

    data class Open(val url: String, val qr: com.sohva.tv.core.model.phone.QrMatrix?, val added: List<String> = emptyList()) : ListPhone
}

/** The "Add a Trakt list" dialog (HOME-FR-99): the typed text, and the search's state and answer. */
data class ListDialogUi(val query: String = "", val searching: Boolean = false, val finding: ListFinding? = null)

/**
 * [unavailable]: rows this profile never has, left out of the list: Trakt's for a restricted
 * profile (HOME-FR-88) or a build without the feature. [addable]: the rows it can add (HOME-FR-94).
 */
data class HomeLayoutView(
    val profileName: String?,
    val layout: HomeLayout,
    val unavailable: Set<String> = emptySet(),
    val addable: List<String> = emptyList(),
    /** Public lists' own names by row id (HOME-FR-99). */
    val names: Map<String, String> = emptyMap(),
    /** Public lists can be added: Trakt is in the build and this profile may use it (HOME-FR-99). */
    val lists: Boolean = false,
)

/** Reorder, Show / hide, or Trakt rows: add and remove (HOME-FR-94). */
enum class HomeLayoutMode { ORDER, VISIBILITY, ADD }

/** [order] is the list as drawn: the stored order, or the draft of a move in progress. */
data class HomeLayoutUi(
    val view: HomeLayoutView? = null,
    val mode: HomeLayoutMode = HomeLayoutMode.ORDER,
    val order: List<String> = emptyList(),
    val moving: String? = null,
    val original: List<String> = emptyList(),
    val listDialog: ListDialogUi? = null,
    val phoneOpen: Boolean = false,
)

/**
 * Settings › Home for the life of the Settings screen (spec 02 HOME-FR-90): moves are drafts and the
 * order is written once, when placed; Back restores it. A switch writes at once. Nothing here runs
 * on the main thread but list changes; the app's services write off it.
 */
class HomeLayoutSettings internal constructor(private val services: HomeLayoutServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HomeLayoutUi())
    val state: StateFlow<HomeLayoutUi> = _state.asStateFlow()

    init {
        scope.launch {
            services.current.collect { view ->
                // A move in progress keeps its draft; the stored order arrives with the placing.
                _state.update { s -> if (s.moving != null) s.copy(view = view) else s.copy(view = view, order = listed(view)) }
            }
        }
    }

    private fun listed(view: HomeLayoutView) = view.layout.rows.map { it.id }.filter { it !in view.unavailable }

    fun mode(mode: HomeLayoutMode) = _state.update { if (it.moving != null) it else it.copy(mode = mode) }

    fun pickUp(id: String) = _state.update { if (it.moving != null) it else it.copy(moving = id, original = it.order) }

    /** Moves the picked-up row by [delta] places, or to [to]; nothing is written (FR-90). */
    fun move(delta: Int, to: Int? = null) = _state.update { s ->
        val id = s.moving ?: return@update s
        val list = s.order.toMutableList()
        val from = list.indexOf(id)
        if (from < 0) return@update s
        val target = (to ?: (from + delta)).coerceIn(0, list.lastIndex)
        if (target == from) return@update s
        list.add(target, list.removeAt(from))
        s.copy(order = list)
    }

    /** OK: the order is written once. */
    fun place() {
        val s = _state.value
        val view = s.view ?: return
        if (s.moving == null) return
        _state.update { it.copy(moving = null, original = emptyList()) }
        if (s.order != s.original) save(view.layout.withOrder(s.order))
    }

    /** Back while moving: the order before the move, nothing written. */
    fun cancelMove() = _state.update { it.copy(order = it.original.ifEmpty { it.order }, moving = null, original = emptyList()) }

    fun toggle(id: String) {
        val view = _state.value.view ?: return
        save(view.layout.withShown(id, !view.layout.isShown(id)))
    }

    /** Trakt rows: an added row goes, another is added at the end while fewer than 8 are (HOME-FR-94). */
    fun toggleAdded(id: String) {
        val layout = _state.value.view?.layout ?: return
        val next = if (id in layout.added) layout.withRemoved(id) else layout.withAdded(id)
        if (next != layout) save(next)
    }

    /** HOME-FR-98: an added row shows only the titles the library has, or every title again. */
    fun toggleLibraryOnly(id: String) {
        val layout = _state.value.view?.layout ?: return
        save(layout.withLibraryOnly(id, !layout.isLibraryOnly(id)))
    }

    // ---- Trakt lists (HOME-FR-99, -100) ----

    fun openListDialog() = _state.update { if (it.moving != null) it else it.copy(listDialog = ListDialogUi()) }

    fun closeListDialog() = _state.update { it.copy(listDialog = null) }

    fun setListQuery(text: String) = _state.update { s -> s.listDialog?.let { s.copy(listDialog = it.copy(query = text.take(300))) } ?: s }

    fun searchLists() {
        val dialog = _state.value.listDialog ?: return
        if (dialog.query.isBlank() || dialog.searching) return
        _state.update { it.copy(listDialog = dialog.copy(searching = true, finding = null)) }
        scope.launch {
            val found = services.findLists(dialog.query)
            _state.update { s -> s.listDialog?.let { s.copy(listDialog = it.copy(searching = false, finding = found)) } ?: s }
        }
    }

    /** OK on a result: the list joins Home; the dialog closes. At 8 added rows nothing is added. */
    fun chooseList(list: ListChoice) {
        val layout = _state.value.view?.layout ?: return
        if (layout.added.size >= HomeLayout.MAX_ADDED) return
        _state.update { it.copy(listDialog = null) }
        scope.launch { services.addList(list) }
    }

    fun openPhone() {
        _state.update { it.copy(phoneOpen = true) }
        services.openListPhone()
    }

    fun closePhone() {
        services.closeListPhone()
        _state.update { it.copy(phoneOpen = false) }
    }

    val listPhone: Flow<ListPhone> get() = services.listPhone

    fun reset() {
        if (_state.value.moving != null) return
        save(HomeLayout.DEFAULT)
    }

    private fun save(layout: HomeLayout) {
        scope.launch { services.save(layout) }
    }
}
