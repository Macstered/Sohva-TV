package com.sohva.tv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tracing.Trace
import com.sohva.tv.core.data.vod.RailGroup
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One wall (Movies or Series) for the life of its stack entry (spec 40 §4.1–4.9). Holds keys and
 * positions and the window of at most 600 entries, never a whole destination (§9.10). Every
 * request carries a serial; a late answer never replaces the current wall (VOD-FR-13).
 */
class LibraryModel(private val env: LibraryEnvironment) : ViewModel() {
    private val _view = MutableStateFlow(RailView.GROUPS)
    val view: StateFlow<RailView> = _view.asStateFlow()

    // History is there from the first frame, so first entry can focus it before the groups arrive.
    private val _rail = MutableStateFlow(listOf(RailRow(HISTORY_KEY, WallDestination.History, null, null)))
    val rail: StateFlow<List<RailRow>> = _rail.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _wall = MutableStateFlow(WallState.Idle)
    val wall: StateFlow<WallState> = _wall.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _ticks = MutableStateFlow<Set<String>>(emptySet())

    /** Film cards to tick as watched, read around the focused card (VOD-FR-37); series walls read none. */
    val ticks: StateFlow<Set<String>> = _ticks.asStateFlow()
    private var tickJob: Job? = null
    private var visibleJob: Job? = null

    private val _tvmazeCredit = MutableStateFlow(false)

    /** TVmaze's CC BY-SA credit under the header (spec 41 Q9). */
    val tvmazeCredit: StateFlow<Boolean> = _tvmazeCredit.asStateFlow()

    private val _note = MutableStateFlow<RefreshNote?>(null)
    val note: StateFlow<RefreshNote?> = _note.asStateFlow()

    /** The browse session (VOD-FR-56): the focused card and whether focus was on the wall. */
    var focusedKey: String? = null
        private set
    var focusedIndex: Int = 0
        private set
    var focusOnWall: Boolean = false
        private set

    /** First entry focuses History (VOD-FR-49); cleared once the screen has placed focus. */
    var firstEntry: Boolean = true
        private set

    private val lastOf = HashMap<RailView, String>()
    private var serial = 0
    private var loadJob: Job? = null
    private var edgeJob: Job? = null
    private var searchJob: Job? = null
    private var lastGroups: List<RailGroup> = emptyList()
    private var lastCounts: Map<String, Int> = emptyMap()

    /** Genre counts are read only once the Genres view has been used (VOD-FR-04). */
    private var genresUsed = false

    init {
        viewModelScope.launch { readRail() }
        viewModelScope.launch { _tvmazeCredit.value = runCatching { env.tvmazeCredit() }.getOrDefault(false) }
        select(HISTORY_KEY)
        watchChanges()
        if (env.room == WallRoom.MOVIES) viewModelScope.launch { _wall.collect { readTicks(it.window) } }
    }

    /** At most 200 films around the focused card (VOD-FR-37); a newer read replaces an older one. */
    private fun readTicks(window: WallWindow) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            val from = maxOf(window.first, focusedIndex - TICK_SPAN / 2)
            val films = (from until minOf(window.end, from + TICK_SPAN)).mapNotNull { window.itemAt(it)?.row }.map { it.key to it.workKey }
            _ticks.value = runCatching { env.watched(films) }.getOrDefault(_ticks.value)
        }
    }

    val room: WallRoom get() = env.room

    /** OK on a rail row (VOD-FR-06): focus alone never selects. Selecting what is loading does nothing. */
    fun select(key: String) {
        if (key == _selected.value && loadJob?.isActive == true) return
        val row = _rail.value.firstOrNull { it.key == key } ?: when {
            key == HISTORY_KEY -> historyRow()
            // A genre before its count has arrived (VOD-FR-07: Action at once).
            key.startsWith(GENRE_PREFIX) -> Genre.ofWire(key.removePrefix(GENRE_PREFIX))?.let { genreRow(it, null) } ?: return
            else -> return
        }
        searchJob?.cancel()
        _selected.value = key
        lastOf[_view.value] = key
        focusedKey = null
        focusedIndex = 0
        load(row.destination)
    }

    /** The Groups / Genres toggle (VOD-FR-07): back to the view's last destination, else its first row. */
    fun showView(view: RailView) {
        if (view == _view.value) return
        _view.value = view
        val rows = rows(view, lastGroups)
        _rail.value = rows
        val target = lastOf[view]?.takeIf { key -> rows.any { it.key == key } }
            ?: _selected.value?.takeIf { it == HISTORY_KEY }
            ?: rows.firstOrNull { it.key != HISTORY_KEY }?.key
            ?: if (view == RailView.GENRES) GENRE_PREFIX + Genre.ACTION.wire else HISTORY_KEY
        select(target)
        if (view == RailView.GENRES && !genresUsed) {
            genresUsed = true
            viewModelScope.launch { readRail() }
        }
    }

    /** Search inside the destination (VOD-FR-40…44): 250 ms debounce, clearing queries at once. */
    fun setSearch(text: String) {
        val cut = text.take(SEARCH_MAX)
        if (cut == _search.value) return
        _search.value = cut
        searchJob?.cancel()
        val destination = _wall.value.destination ?: return
        _wall.update { it.copy(current = false, failed = false) }
        focusedKey = null
        focusedIndex = 0
        if (cut.isBlank()) {
            load(destination)
        } else {
            searchJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                load(destination)
            }
        }
    }

    /** A card took focus: remember it, and read the next or previous page when near an edge (§9.3). */
    fun focused(index: Int, key: String, columns: Int) {
        val moved = kotlin.math.abs(index - focusedIndex) >= TICK_SPAN / 4
        focusedIndex = index
        focusedKey = key
        focusOnWall = true
        val state = _wall.value
        if (moved && env.room == WallRoom.MOVIES) readTicks(state.window)
        lookUpVisibleLater()
        if (!state.current || edgeJob?.isActive == true) return
        val window = state.window
        when {
            window.wantsNext(index, columns) -> edge(forward = true)
            window.wantsPrevious(index, columns) -> edge(forward = false)
        }
    }

    /**
     * The titles around the focused card once focus has rested a second (spec 41 Q10): every
     * focus move cancels the wait and the lookups, so nothing runs while a key is held.
     */
    private fun lookUpVisibleLater() {
        visibleJob?.cancel()
        visibleJob = viewModelScope.launch {
            delay(VISIBLE_REST_MS)
            val window = _wall.value.window
            val around = (maxOf(window.first, focusedIndex - VISIBLE_SPAN) until minOf(window.end, focusedIndex + VISIBLE_SPAN))
            env.lookUpVisible(around.mapNotNull(window::itemAt))
        }
    }

    /** A rail control took focus (VOD-FR-54). */
    fun railFocused() {
        focusOnWall = false
    }

    fun entryPlaced() {
        firstEntry = false
    }

    /** OK on a card (VOD-FR-52): remember it, clear a search, open the details page. */
    fun open(item: WallItem, index: Int) {
        focusedKey = item.row.key
        focusedIndex = index
        focusOnWall = true
        if (_search.value.isNotEmpty()) setSearch("")
        env.open(item)
    }

    fun openSheet() {
        _sheetOpen.value = true
    }

    fun closeSheet() {
        _sheetOpen.value = false
    }

    fun openManager() {
        _sheetOpen.value = false
        env.openManager()
    }

    fun leave() {
        if (_search.value.isNotEmpty()) setSearch("")
        env.leave()
    }

    /** Options › Refresh (VOD-FR-46): the sheet stays open; the header reports the result. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                _note.value = env.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun load(destination: WallDestination) {
        val mine = ++serial
        loadJob?.cancel()
        edgeJob?.cancel()
        _wall.update { if (it.destination == destination) it.copy(current = false, failed = false) else it.copy(destination = destination, current = false, failed = false) }
        loadJob = viewModelScope.launch {
            // From the request to the published first page: the wall's open time in traces (spec 40 §9.1).
            Trace.beginAsyncSection(LOAD_SECTION, mine)
            try {
                var page = env.page(destination, _search.value, null, true, WallWindow.PAGE)
                if (page.isEmpty() && expectsTitles(destination)) {
                    // An import or metadata write may be mid-change: ask once more before saying "no titles" (VOD-FR-16).
                    delay(TRANSIENT_EMPTY_MS)
                    if (mine != serial) return@launch
                    page = env.page(destination, _search.value, null, true, WallWindow.PAGE)
                }
                if (mine == serial) _wall.value = WallState(destination, WallWindow.of(page), current = true, failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The detail goes to the diagnostics log; the screen says the plain sentence (VOD-FR-15).
                if (mine == serial) _wall.update { it.copy(current = false, failed = true) }
            } finally {
                Trace.endAsyncSection(LOAD_SECTION, mine)
            }
        }
    }

    private fun edge(forward: Boolean) {
        val mine = serial
        val state = _wall.value
        val destination = state.destination ?: return
        val anchor = (if (forward) state.window.items.lastOrNull() else state.window.items.firstOrNull()) ?: return
        edgeJob = viewModelScope.launch {
            val page = runCatching { env.page(destination, _search.value, anchor, forward, WallWindow.PAGE) }.getOrNull() ?: return@launch
            if (mine != serial) return@launch
            _wall.update { now ->
                if (now.destination != destination) now else now.copy(window = if (forward) now.window.appended(page) else now.window.prepended(page))
            }
        }
    }

    /**
     * Imports and progress writes re-read the rail and the pages in memory, keeping focus by key
     * (VOD-FR-18). Nothing is re-read while a load is running; the next change catches up.
     */
    @OptIn(FlowPreview::class)
    private fun watchChanges() {
        viewModelScope.launch {
            env.changes().debounce(CHANGE_QUIET_MS).collect {
                readRail()
                refreshWindow()
                // A progress write leaves the window equal, so the ticks are read here too.
                if (env.room == WallRoom.MOVIES) readTicks(_wall.value.window)
            }
        }
    }

    private suspend fun readRail() {
        lastGroups = runCatching { env.groups() }.getOrElse { return }
        if (genresUsed) lastCounts = runCatching { env.genreCounts() }.getOrDefault(lastCounts)
        val rows = rows(_view.value, lastGroups)
        _rail.value = rows
        // The selected group disappeared while others remain → the first group (VOD-FR-09).
        val selected = _selected.value
        if (selected != null && rows.none { it.key == selected } && selected != HISTORY_KEY) {
            select(rows.firstOrNull { it.key != HISTORY_KEY }?.key ?: HISTORY_KEY)
        }
    }

    private suspend fun refreshWindow() {
        val state = _wall.value
        val destination = state.destination ?: return
        if (!state.current || loadJob?.isActive == true || edgeJob?.isActive == true) return
        val mine = serial
        val first = state.window.items.firstOrNull()
        val reread = ArrayList<WallItem>()
        var from: WallItem? = first?.let(::justBefore)
        val wanted = maxOf(state.window.items.size, WallWindow.PAGE)
        while (reread.size < wanted) {
            val page = runCatching { env.page(destination, _search.value, from, true, WallWindow.PAGE) }.getOrNull() ?: return
            reread += page
            if (page.size < WallWindow.PAGE) break
            from = page.last()
        }
        if (mine != serial) return
        if (reread.isEmpty() && state.window.items.isNotEmpty() && expectsTitles(destination)) {
            // A transient empty never replaces a wall with titles unless it is still empty 500 ms later (VOD-FR-16).
            delay(TRANSIENT_EMPTY_MS)
            val again = runCatching { env.page(destination, _search.value, null, true, WallWindow.PAGE) }.getOrNull() ?: return
            if (mine != serial) return
            if (again.isNotEmpty()) {
                _wall.value = state.copy(window = WallWindow.of(again))
                return
            }
        }
        val atEnd = reread.size < wanted || (reread.size == wanted && state.window.atEnd)
        _wall.value = state.copy(window = WallWindow(state.window.first, reread.take(WallWindow.MAX_ITEMS), atEnd))
    }

    /** A provider group whose count says it has titles, read without a search (VOD-FR-16). */
    private fun expectsTitles(destination: WallDestination): Boolean =
        destination is WallDestination.Group && _search.value.isBlank() && (_rail.value.firstOrNull { it.destination == destination }?.count ?: 0) > 0

    /** A cursor that sorts just before [item], so a forward read includes it. */
    private fun justBefore(item: WallItem): WallItem =
        if (item.progressKey.isNotEmpty()) item.copy(progressKey = item.progressKey + '\u0000') else item.copy(row = item.row.copy(id = item.row.id - 1))

    private fun rows(view: RailView, groups: List<RailGroup>): List<RailRow> = buildList {
        add(historyRow())
        when (view) {
            RailView.GROUPS -> {
                if (groups.isEmpty()) add(RailRow(ALL_KEY, WallDestination.AllGroups, null, null))
                groups.forEach { g -> add(RailRow("group:" + g.name.lowercase(Locale.ROOT), WallDestination.Group(g.name, g.groupIds), g.name, g.count)) }
            }
            // The 22 genres in their fixed order, then Unsorted, each only with titles (VOD-FR-04).
            RailView.GENRES -> {
                Genre.entries.forEach { g -> lastCounts[g.wire]?.takeIf { it > 0 }?.let { add(genreRow(g, it)) } }
                lastCounts[""]?.takeIf { it > 0 }?.let { add(RailRow(UNSORTED_KEY, WallDestination.Unsorted, null, it)) }
            }
        }
    }

    private fun historyRow() = RailRow(HISTORY_KEY, WallDestination.History, null, null)

    private fun genreRow(genre: Genre, count: Int?) = RailRow(GENRE_PREFIX + genre.wire, WallDestination.OfGenre(genre), null, count)

    companion object {
        const val HISTORY_KEY: String = "history"
        const val ALL_KEY: String = "all"
        const val UNSORTED_KEY: String = "unsorted"
        const val GENRE_PREFIX: String = "genre:"
        const val SEARCH_MAX: Int = 80
        const val SEARCH_DEBOUNCE_MS: Long = 250
        const val CHANGE_QUIET_MS: Long = 500
        const val LOAD_SECTION: String = "Library:Load"
        const val TICK_SPAN: Int = 200
        const val TRANSIENT_EMPTY_MS: Long = 500
        const val VISIBLE_REST_MS: Long = 1_000
        const val VISIBLE_SPAN: Int = 24
    }
}
