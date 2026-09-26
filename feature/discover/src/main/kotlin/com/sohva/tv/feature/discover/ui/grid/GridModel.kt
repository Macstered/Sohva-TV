package com.sohva.tv.feature.discover.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.data.AddonBrowser
import com.sohva.tv.feature.discover.protocol.AddonCatalog
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.CatalogExtra
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.CatalogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The grid's titles and paging state (FR-117). */
data class GridState(
    val entry: CatalogEntry? = null,
    val values: Map<String, String> = emptyMap(),
    val titles: List<MetaPreview> = emptyList(),
    val loading: Boolean = false,
    val failure: AddonFailure? = null,
    val stale: Boolean = false,
    val ended: Boolean = false,
    val limited: Boolean = false,
    val formOpen: Boolean = false,
    /** Required choices are missing: nothing loads (FR-118). */
    val needsChoice: Boolean = false,
) {
    val catalog: AddonCatalog? get() = entry?.catalog

    /** Choosers for option extras (FR-118). */
    val choices: List<CatalogExtra> get() = catalog?.extras?.filter { it.options.isNotEmpty() }.orEmpty()

    /** Free-text extras edited in the form (FR-119); `skip` is the app's own. */
    val textExtras: List<CatalogExtra> get() = catalog?.extras?.filter { it.options.isEmpty() && it.name != AddonCatalog.SKIP }.orEmpty()
}

/**
 * A catalog's Show all grid, or the Discover filter page when [filterPage] (spec 50 §4.17): pages
 * of titles requested one at a time with `skip = skip + received`, stopping on an empty or short
 * page, a page that adds nothing (a provider ignoring skip), a catalog without `skip`, or 1,000
 * titles. A failed page keeps what is loaded and waits for Retry.
 */
class GridModel(private val host: DiscoverHost, private val profile: String, first: CatalogEntry?, val filterPage: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(GridState(entry = first))
    val state: StateFlow<GridState> = _state.asStateFlow()

    /** The filter page's catalogs: every visible one, choices required or not (FR-120). */
    private val _catalogs = MutableStateFlow<List<CatalogEntry>?>(null)
    val catalogs: StateFlow<List<CatalogEntry>?> = _catalogs.asStateFlow()

    private var next = 0
    private var job: Job? = null

    init {
        viewModelScope.launch {
            if (filterPage) {
                val visible = runCatching {
                    withContext(host.dispatchers.io) { host.catalogs.visible(profile, host.manager.list(profile)) }
                }.getOrDefault(emptyList())
                _catalogs.value = visible
                if (_state.value.entry == null) visible.firstOrNull()?.let { choose(it) }
            } else {
                start()
            }
        }
    }

    /** The filter page's Catalog chooser (FR-120). */
    fun choose(entry: CatalogEntry) {
        _state.value = GridState(entry = entry)
        start()
    }

    /** A chooser's value; null clears it (FR-118). Reloads from the first page when possible. */
    fun setValue(name: String, value: String?) {
        _state.update { s -> s.copy(values = if (value == null) s.values - name else s.values + (name to value)) }
        start()
    }

    fun toggleForm() = _state.update { it.copy(formOpen = !it.formOpen) }

    /** Apply in the free-text form (FR-119): the typed values replace the old ones. */
    fun applyText(values: Map<String, String>) {
        _state.update { s ->
            val kept = s.values.filterKeys { k -> s.textExtras.none { it.name == k } }
            s.copy(values = kept + values.filterValues { it.isNotBlank() }.mapValues { it.value.take(MAX_TEXT) }, formOpen = false)
        }
        start()
    }

    /** Refresh titles (FR-39): from the first page, past the fresh cache. */
    fun refresh() = start(refresh = true)

    /** Retry loading titles after a failed page (no automatic retry). */
    fun retry() {
        if (_state.value.failure == null) return
        _state.update { it.copy(failure = null) }
        page(refresh = false)
    }

    /** The last loaded title became visible: the next page, if paging goes on and nothing blocks it. */
    fun nearEnd() {
        val s = _state.value
        if (s.loading || s.failure != null || s.formOpen || s.ended || s.needsChoice || s.titles.isEmpty()) return
        page(refresh = false)
    }

    private fun start(refresh: Boolean = false) {
        job?.cancel()
        val s = _state.value
        val catalog = s.catalog ?: return
        val missing = catalog.requiredChoices.any { s.values[it.name].isNullOrBlank() }
        next = 0
        _state.value = s.copy(titles = emptyList(), loading = false, failure = null, stale = false, ended = false, limited = false, needsChoice = missing,
            // The form opens by itself when a text extra is required (FR-119).
            formOpen = s.formOpen || (missing && s.textExtras.any { it.required && s.values[it.name].isNullOrBlank() }))
        if (!missing) page(refresh)
    }

    private fun page(refresh: Boolean) {
        val s = _state.value
        val entry = s.entry ?: return
        val catalog = entry.catalog
        val extras = buildMap {
            putAll(s.values)
            if (catalog.pages && next > 0) put(AddonCatalog.SKIP, next.toString())
        }
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            try {
                val fetched = host.browser.catalog(profile, entry.installation, catalog, extras, AddonBrowser.GRID_ITEMS, refresh)
                val page = fetched.value
                _state.update { cur ->
                    val known = cur.titles.map { it.type to it.id }.toHashSet()
                    val fresh = page.items.filter { known.add(it.type to it.id) }
                    val titles = (cur.titles + fresh).take(MAX_TITLES)
                    val short = catalog.pageSize?.let { page.receivedCount < it } ?: false
                    val ended = !catalog.pages || page.receivedCount == 0 || short || fresh.isEmpty() || titles.size >= MAX_TITLES
                    next += page.receivedCount
                    cur.copy(titles = titles, loading = false, stale = fetched.stale, ended = ended, limited = titles.size >= MAX_TITLES)
                }
            } catch (e: AddonException) {
                _state.update { it.copy(loading = false, failure = e.failure) }
            }
        }
    }

    private companion object {
        const val MAX_TITLES = 1_000
        const val MAX_TEXT = 1_024
    }
}
