package com.sohva.tv.feature.discover.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.data.AddonBrowser
import com.sohva.tv.feature.discover.protocol.AddonCatalog
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.CatalogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A search hit: the title and the addon whose catalog found it (FR-114: never merged across addons). */
data class SearchHit(val owner: String, val item: MetaPreview) {
    val key: String get() = "$owner|${item.type}|${item.id}"
}

enum class SearchPhase { IDLE, SEARCHING, DONE }

data class SearchState(
    val eligible: Int = -1,
    /** More than 32 eligible catalogs: only the first 32 are searched (FR-113). */
    val limited: Boolean = false,
    val query: String = "",
    val searched: String = "",
    val phase: SearchPhase = SearchPhase.IDLE,
    val answered: Int = 0,
    val total: Int = 0,
    val partial: Boolean = false,
    val stale: Boolean = false,
    val movies: List<SearchHit> = emptyList(),
    val series: List<SearchHit> = emptyList(),
    val moviesCapped: Boolean = false,
    val seriesCapped: Boolean = false,
)

/**
 * Discover Search (spec 50 §4.16): one query across the eligible catalogs, three at a time, 12 s
 * each and 30 s overall, results arriving catalog by catalog into Movies and Series rows of at most
 * 100. Typing never searches; the query never reaches diagnostics.
 */
class SearchModel(private val host: DiscoverHost, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var catalogs: List<CatalogEntry> = emptyList()
    private var job: Job? = null

    /** Rows' positions survive a trip to a title and back (FR-116). */
    val movieRow: LazyListState = LazyListState()
    val seriesRow: LazyListState = LazyListState()
    var lastFocus: String? = null

    init {
        viewModelScope.launch {
            val eligible = runCatching {
                withContext(host.dispatchers.io) {
                    host.catalogs.visible(profile, host.manager.list(profile))
                        .filter { it.installation.manifest.supports("catalog", it.catalog.type, it.catalog.id) && it.catalog.searchable }
                }
            }.getOrDefault(emptyList())
            catalogs = eligible.take(MAX_CATALOGS)
            _state.update { it.copy(eligible = eligible.size, limited = eligible.size > MAX_CATALOGS) }
        }
    }

    fun type(text: String) {
        val trimmed = text.take(MAX_QUERY)
        _state.update { it.copy(query = trimmed) }
        if (trimmed.isBlank()) clear()
    }

    fun clear() {
        job?.cancel()
        _state.update { SearchState(eligible = it.eligible, limited = it.limited) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || catalogs.isEmpty()) return
        job?.cancel()
        _state.update { it.copy(searched = query, phase = SearchPhase.SEARCHING, answered = 0, total = catalogs.size, partial = false, stale = false, movies = emptyList(), series = emptyList()) }
        job = viewModelScope.launch {
            val workers = Semaphore(WORKERS)
            val seen = HashSet<String>()
            val complete = withTimeoutOrNull(OVERALL_MS) {
                kotlinx.coroutines.coroutineScope {
                    catalogs.forEach { entry ->
                        launch {
                            workers.withPermit {
                                val result = withTimeoutOrNull(PER_CATALOG_MS) {
                                    try {
                                        val extras = buildMap {
                                            put(AddonCatalog.SEARCH, query)
                                            if (entry.catalog.pages) put(AddonCatalog.SKIP, "0")
                                        }
                                        host.browser.catalog(profile, entry.installation, entry.catalog, extras, AddonBrowser.SEARCH_ITEMS)
                                    } catch (e: AddonException) {
                                        null
                                    }
                                }
                                publish(entry, result?.value?.items, result?.stale == true, seen)
                            }
                        }
                    }
                }
            }
            _state.update { it.copy(phase = SearchPhase.DONE, partial = it.partial || complete == null) }
        }
    }

    private fun publish(entry: CatalogEntry, items: List<MetaPreview>?, stale: Boolean, seen: MutableSet<String>) = synchronized(seen) {
        val owner = entry.installation.id
        val hits = items.orEmpty().filter { it.type == "movie" || it.type == "series" }.map { SearchHit(owner, it) }.filter { seen.add(it.key) }
        _state.update { s ->
            val movies = s.movies + hits.filter { it.item.type == "movie" }
            val series = s.series + hits.filter { it.item.type == "series" }
            s.copy(
                answered = s.answered + 1,
                partial = s.partial || items == null,
                stale = s.stale || stale,
                movies = movies.take(MAX_ROW), series = series.take(MAX_ROW),
                moviesCapped = movies.size > MAX_ROW, seriesCapped = series.size > MAX_ROW,
            )
        }
    }

    private companion object {
        const val MAX_CATALOGS = 32
        const val MAX_QUERY = 256
        const val MAX_ROW = 100
        const val WORKERS = 3
        const val PER_CATALOG_MS = 12_000L
        const val OVERALL_MS = 30_000L
    }
}
