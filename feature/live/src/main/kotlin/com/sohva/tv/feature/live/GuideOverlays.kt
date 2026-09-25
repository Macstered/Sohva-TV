package com.sohva.tv.feature.live

import com.sohva.tv.core.model.guide.DialBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The dial read-out (GUIDE-FR-80..82): the digits typed, or the number that was not found. */
data class DialState(val digits: String?, val notFound: Int?)

/** Find programme (GUIDE-FR-90..92). */
data class SearchState(val visible: Boolean = false, val query: String = "")

/**
 * The guide's overlays and their timers: the rail, the options sheet, the actions dialog, the dial
 * and the search. Separate from [GuideModel] only to keep each file small (AGENTS.md §4 rule 7).
 */
class GuideOverlays internal constructor(private val model: GuideModel, private val scope: CoroutineScope) {
    private val _railOpen = MutableStateFlow(false)
    val railOpen: StateFlow<Boolean> = _railOpen.asStateFlow()

    private val _options = MutableStateFlow(false)
    val optionsOpen: StateFlow<Boolean> = _options.asStateFlow()

    private val _actions = MutableStateFlow<ActionsTarget?>(null)
    val actions: StateFlow<ActionsTarget?> = _actions.asStateFlow()

    private val _dial = MutableStateFlow(DialState(null, null))
    val dial: StateFlow<DialState> = _dial.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** Set when the list changed while the sheet was open (GUIDE-FR-93). */
    internal var listChangedUnderSheet = false

    private val buffer = DialBuffer()
    private var dialJob: Job? = null
    private var searchJob: Job? = null

    fun openRail() {
        model.cancelPending()
        _railOpen.value = true
    }

    fun closeRail() {
        _railOpen.value = false
    }

    fun openOptions() {
        listChangedUnderSheet = false
        _options.value = true
    }

    /**
     * Where focus goes when the sheet closes, decided before it hides (GUIDE-FR-93): the list's
     * first row when the list changed under it, the rail's Options button when the rail is open,
     * else the selected row.
     */
    fun closeOptions(): SheetReturn {
        val target = when {
            listChangedUnderSheet -> SheetReturn.FirstRow
            _railOpen.value -> SheetReturn.RailOptions
            else -> SheetReturn.SelectedRow
        }
        listChangedUnderSheet = false
        _options.value = false
        return target
    }

    fun openActions(target: ActionsTarget) {
        model.select(target.row, target.programme)
        _actions.value = target
    }

    fun closeActions(): ActionsTarget? = _actions.value.also { _actions.value = null }

    /** A digit typed while focus is in the grid (GUIDE-FR-80). */
    fun dialDigit(digit: Int) {
        if (!buffer.append(digit)) return
        _dial.value = DialState(buffer.digits, null)
        dialJob?.cancel()
        dialJob = scope.launch {
            delay(DialBuffer.COMMIT_MS)
            val number = buffer.number()
            // Look up first, then clear: clearing first cancelled the look-up in beta 23 (PLAY-FR-59).
            val index = if (number == null) -1 else model.dialIndex(number)
            buffer.clear()
            if (index >= 0) {
                _dial.value = DialState(null, null)
                model.focusRow(index, GuideModel.CHANNEL)
            } else {
                _dial.value = DialState(null, number ?: 0)
                delay(DialBuffer.NOT_FOUND_MS)
                _dial.value = DialState(null, null)
            }
        }
    }

    /** Find programme on the hero (GUIDE-FR-90): on opens the rail at the field; off clears. */
    fun toggleSearch() {
        val on = !_search.value.visible
        _search.value = SearchState(visible = on, query = "")
        if (on) {
            _railOpen.value = true
        } else {
            searchJob?.cancel()
            model.showSearch("")
        }
    }

    fun setQuery(text: String) {
        val query = text.take(MAX_QUERY)
        _search.value = _search.value.copy(query = query)
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            model.showSearch(query)
        }
    }

    internal fun clearSearch() {
        searchJob?.cancel()
        if (_search.value != SearchState()) _search.value = SearchState()
    }

    /** The match set follows paging (GUIDE-FR-92). */
    internal fun onWindowChanged() {
        val query = _search.value.query
        if (query.isNotBlank()) model.showSearch(query)
    }

    enum class SheetReturn { FirstRow, RailOptions, SelectedRow }

    companion object {
        const val MAX_QUERY: Int = 80
        const val SEARCH_DEBOUNCE_MS: Long = 250
    }
}
