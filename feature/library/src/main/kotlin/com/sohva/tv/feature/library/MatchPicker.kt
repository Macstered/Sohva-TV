package com.sohva.tv.feature.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One open match picker (spec 40 §4.14, spec 41 §4.14), owned by its page's model: the query,
 * the results, whether a choice is pinned, and [finished] once a choice, an undo or Close ends
 * it. The page's dialog places focus back on "Wrong details?" and then closes it, so focus never
 * falls to the first focusable of the page (lessons 4.1).
 */
class MatchPicker internal constructor(
    private val env: TitleEnvironment,
    private val scope: CoroutineScope,
    val target: PickerTarget,
    query: String,
    /** A choice (its record) or an undo (null) changes the page's metadata. */
    private val onMetadata: (TitleMetadata?) -> Unit,
) {
    private val _query = MutableStateFlow(query.take(QUERY_MAX))
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<PickerResults>(PickerResults.Searching)
    val results: StateFlow<PickerResults> = _results.asStateFlow()

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var searching: Job? = null
    private var writing: Job? = null

    init {
        scope.launch { _pinned.value = env.isPinned(target) }
        search()
    }

    fun setQuery(value: String) {
        _query.value = value.take(QUERY_MAX)
    }

    /** Searches at once on open and on "Search" (VOD-FR-104); a new search replaces a running one. */
    fun search() {
        searching?.cancel()
        _results.value = PickerResults.Searching
        searching = scope.launch {
            val found = env.searchMatches(target, _query.value)
            _results.value = if (found.isEmpty()) PickerResults.Nothing else PickerResults.Found(found)
        }
    }

    /** OK on a result pins it; the picker closes when the write finishes, also when it failed (META-FR-75). */
    fun choose(result: MatchResult) {
        if (writing?.isActive == true) return
        writing = scope.launch {
            env.chooseMatch(target, result)?.let(onMetadata)
            _finished.value = true
        }
    }

    /** "Undo my choice" clears the page's metadata and closes (VOD-FR-106). */
    fun undo() {
        if (writing?.isActive == true) return
        writing = scope.launch {
            env.undoMatch(target)
            onMetadata(null)
            _finished.value = true
        }
    }

    fun close() {
        _finished.value = true
    }

    /** The dialog is gone: a search still running is of no use; a write in progress finishes. */
    internal fun dispose() {
        searching?.cancel()
    }

    private companion object {
        const val QUERY_MAX = 80
    }
}
