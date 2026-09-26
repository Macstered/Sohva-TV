package com.sohva.tv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.search.SearchTerms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Search for the life of its stack entry (spec 03 §4): each change cancels the running search,
 * clears the results and, after 250 ms without a further change, starts the groups, at most three
 * at a time, each appended when it finishes. One search is in flight at a time; a group over 1 s
 * is abandoned and counts as failed (§9).
 */
class SearchModel(private val env: SearchEnvironment) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun setText(value: String) {
        val cut = SearchTerms.cut(value)
        if (cut == _text.value) return
        _text.value = cut
        job?.cancel()
        val term = SearchTerms.term(cut)
        if (term == null) {
            _state.value = SearchState()
            return
        }
        _state.value = SearchState(term = term, running = true)
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val permits = Semaphore(PARALLEL)
            // Started in the order Sport, Channels, Films, Series, Episodes (SEARCH-FR-10).
            val groups: List<suspend (String) -> List<SearchResult>> = listOf(env::sport, env::live, env::films, env::series, env::episodes)
            val running = groups.map { group ->
                launch {
                    // A failure or a group over the limit is null; a superseded search's cancellation propagates.
                    val rows = try {
                        permits.withPermit { withTimeoutOrNull(GROUP_LIMIT_MS) { group(term) } }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    _state.update { s ->
                        if (rows == null) s.copy(failed = true) else s.copy(results = s.results + rows.filter { r -> s.results.none { it.key == r.key } })
                    }
                }
            }
            running.forEach { it.join() }
            _state.update { it.copy(running = false) }
        }
    }

    fun open(result: SearchResult) = env.open(result)

    fun leave() = env.leave()

    companion object {
        const val DEBOUNCE_MS: Long = 250
        const val PARALLEL: Int = 3
        const val GROUP_LIMIT_MS: Long = 1_000
    }
}
