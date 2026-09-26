package com.sohva.tv.feature.discover.ui.landing

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.Artwork
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.store.WatchEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The landing's shelves and whether anything is browsable (FR-57, -58, -69). */
data class LandingSetup(val shelves: List<CatalogEntry>, val anyVisible: Boolean)

/** One shelf (FR-60, -61): [items] null while nothing is known; [done] once validated or failed. */
data class ShelfState(
    val items: List<MetaPreview>? = null,
    val stale: Boolean = false,
    val failure: AddonFailure? = null,
    val done: Boolean = false,
)

/** A Continue watching card (FR-64): its entry and the artwork and text to draw. */
data class ContinueCard(val entry: WatchEntry, val preview: MetaPreview) {
    val key: String get() = "${entry.identity.installation}|${entry.identity.mediaType}|${entry.identity.mediaId}"
}

sealed interface ContinueState {
    data object Loading : ContinueState

    data object Failed : ContinueState

    data class Ready(val cards: List<ContinueCard>) : ContinueState
}

/**
 * The Discover landing (spec 50 §4.9). Nothing here touches the network until a shelf is on
 * screen: the screen asks for the active shelf and the next two (FR-59). The model keeps at most 24
 * shelves' titles (FR-62); older ones fall back to their saved preview when revisited.
 */
class LandingModel(private val host: DiscoverHost, val profile: String) : ViewModel() {
    private val io = host.dispatchers.io

    private val _setup = MutableStateFlow<LandingSetup?>(null)
    val setup: StateFlow<LandingSetup?> = _setup.asStateFlow()

    private val _denied = MutableStateFlow(false)
    val denied: StateFlow<Boolean> = _denied.asStateFlow()

    /** Counts up when the shelves were rebuilt: the screen scrolls to the top (FR-57). */
    private val _rebuilt = MutableStateFlow(0)
    val rebuilt: StateFlow<Int> = _rebuilt.asStateFlow()

    private var signature: String? = null
    private val shelves = HashMap<String, MutableStateFlow<ShelfState>>()
    private val shelfLocks = HashMap<String, Mutex>()
    private val recent = LinkedHashMap<String, Unit>(32, 0.75f, true)

    private val _continue = MutableStateFlow<ContinueState>(ContinueState.Loading)
    val continueRow: StateFlow<ContinueState> = _continue.asStateFlow()
    private val repaired = HashSet<String>()

    /** Where focus was (row key, item id), restored when the landing comes back (FR-36). */
    var lastRow: String? = null
    var lastItem: String? = null

    /** Focus is in the rows (not the rail): only then may a refreshed shelf place it (FR-60). */
    var shelvesFocused: Boolean = false

    /** Scroll positions outlive the landing's composition (a title page replaces it), so return lands where it left. */
    val list: LazyListState = LazyListState()
    private val rowStates = HashMap<String, LazyListState>()

    fun rowState(key: String): LazyListState = rowStates.getOrPut(key) { LazyListState() }

    val hero: HeroResolver = HeroResolver(host, profile, viewModelScope)

    init {
        viewModelScope.launch { host.setupChanged.collect { reload() } }
    }

    /** FR-57: installations, order and visibility, no network; the shelves reset only when they changed. */
    private suspend fun reload() {
        try {
            val loaded = withContext(io) {
                val installations = host.manager.list(profile)
                val ordered = host.catalogs.ordered(profile, installations)
                val sig = installations.joinToString { "${it.id}:${it.revision}:${it.enabled}" } + "|" + ordered.joinToString { "${it.key}:${it.hidden}" }
                sig to LandingSetup(ordered.filter { it.visible && it.catalog.onLanding }, ordered.any { it.visible })
            }
            if (loaded.first != signature) {
                signature = loaded.first
                shelves.values.forEach { it.value = ShelfState() }
                recent.clear()
                _setup.value = loaded.second
                _rebuilt.value++
            }
        } catch (e: AddonException) {
            if (e.failure == AddonFailure.ACCESS_DENIED) _denied.value = true
        }
    }

    fun shelf(key: String): StateFlow<ShelfState> = synchronized(shelves) { shelves.getOrPut(key) { MutableStateFlow(ShelfState()) } }

    /**
     * FR-60: the saved preview first (no network; done if fresh), then the network (20 titles),
     * then, on failure, the error in place of the provisional preview. One load per shelf at a time;
     * a validated or failed shelf is not loaded again.
     */
    suspend fun loadShelf(entry: CatalogEntry) {
        val state = shelf(entry.key) as MutableStateFlow<ShelfState>
        val lock = synchronized(shelfLocks) { shelfLocks.getOrPut(entry.key) { Mutex() } }
        lock.withLock {
            touch(entry.key)
            if (state.value.done) return
            val preview = runCatching { host.browser.shelfPreview(profile, entry.installation, entry.catalog) }.getOrNull()
            if (preview != null) {
                state.value = ShelfState(preview.page.items, stale = !preview.fresh, done = preview.fresh)
                if (preview.fresh) return
            }
            state.value = try {
                val page = host.browser.catalog(profile, entry.installation, entry.catalog, emptyMap(), SHELF_ITEMS)
                ShelfState(page.value.items, stale = page.stale, done = true)
            } catch (e: AddonException) {
                if (e.failure == AddonFailure.ACCESS_DENIED) _denied.value = true
                ShelfState(failure = e.failure, done = true)
            }
        }
    }

    /** FR-62: at most 24 shelves keep their titles; the least recently used goes back to nothing. */
    private fun touch(key: String) = synchronized(shelves) {
        recent[key] = Unit
        while (recent.size > MAX_SHELVES) {
            val oldest = recent.keys.first()
            recent.remove(oldest)
            shelves[oldest]?.value = ShelfState()
        }
    }

    /**
     * FR-64: the profile's history with a resume position, its addon enabled, one card per title,
     * the first 20; artwork and text from saved details (stale allowed, no network), else the
     * snapshot kept with the progress. Reloaded every time the landing is shown.
     */
    suspend fun loadContinue() {
        try {
            val cards = withContext(io) {
                val enabled = host.installations.list(profile).filter { it.enabled }.map { it.id }.toSet()
                host.progress.recent(profile)
                    .filter { !it.completed && it.positionMs > 0 && it.identity.installation in enabled }
                    .distinctBy { Triple(it.identity.installation, it.identity.mediaType, it.identity.mediaId) }
                    .take(CONTINUE_ITEMS)
                    .map { e ->
                        val cached = runCatching {
                            host.browser.cachedDetails(profile, e.identity.installation, e.identity.mediaType, e.identity.mediaId, freshOnly = false)
                        }.getOrNull()
                        val preview = cached?.preview ?: MetaPreview(e.identity.mediaType, e.identity.mediaId, e.artwork.name.ifBlank { e.title }, e.artwork.poster, e.artwork.background)
                        ContinueCard(e, preview.copy(poster = preview.poster ?: e.artwork.poster, background = preview.background ?: e.artwork.background))
                    }
            }
            _continue.value = ContinueState.Ready(cards)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _continue.value = ContinueState.Failed
        }
    }

    /**
     * FR-65, once per Discover visit: cards still without a poster are looked up through the details
     * route (20 at most, 2 at a time, 8 s each, one attempt per title) and the poster is written into
     * the saved progress. The screen runs this while the landing is shown; leaving cancels it.
     */
    suspend fun repairArtwork() {
        val cards = (continueRow.value as? ContinueState.Ready)?.cards ?: return
        val todo = cards.filter { it.preview.poster == null && repaired.add(it.key) }.take(CONTINUE_ITEMS)
        if (todo.isEmpty()) return
        val workers = Semaphore(REPAIR_WORKERS)
        var found = false
        coroutineScope {
            todo.map { card ->
                async {
                    workers.withPermit {
                        val id = card.entry.identity
                        val details = withTimeoutOrNull(REPAIR_MS) {
                            runCatching { host.browser.details(profile, id.installation, id.mediaType, id.mediaId) }.getOrNull()
                        }?.value ?: return@withPermit
                        val poster = details.preview.poster ?: return@withPermit
                        host.progress.repairArtwork(profile, id, Artwork(details.preview.name, poster, details.preview.background ?: card.entry.artwork.background))
                        found = true
                    }
                }
            }.awaitAll()
        }
        if (found) loadContinue()
    }

    companion object {
        const val SHELF_ITEMS: Int = 20
        private const val MAX_SHELVES = 24
        private const val CONTINUE_ITEMS = 20
        private const val REPAIR_WORKERS = 2
        private const val REPAIR_MS = 8_000L
    }
}
