package com.sohva.tv.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.channels.ChannelFields
import com.sohva.tv.core.data.database.ChannelListEntity
import com.sohva.tv.core.data.database.EpgChannelOption
import com.sohva.tv.core.data.database.ManagedChannel
import com.sohva.tv.core.data.database.ManagerSource
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Channel management's screen model (spec 21 §4.2–4.5). The list is a [ChannelPages] rebuilt
 * when a filter changes; the screen reads rows by index and asks for pages as it scrolls. The
 * selection lives here, not in the screen body, so a focus move recomposes only the rows and the
 * editor (CHAN-NFR-03). Every write runs on the stores' own dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsModel(private val env: ChannelsEnvironment) : ViewModel() {
    private val _sources = MutableStateFlow<List<ManagerSource>>(emptyList())
    val sources: StateFlow<List<ManagerSource>> = _sources.asStateFlow()

    private val _filter = MutableStateFlow(ChannelFilter())
    val filter: StateFlow<ChannelFilter> = _filter.asStateFlow()

    /** What the search field shows while the viewer types; the filter follows 250 ms later. */
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    private val _pages = MutableStateFlow<ChannelPages?>(null)
    val pages: StateFlow<ChannelPages?> = _pages.asStateFlow()

    /** Bumped when a page arrives, so rows read in composition draw it. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _selected = MutableStateFlow<ChannelRow?>(null)
    val selected: StateFlow<ChannelRow?> = _selected.asStateFlow()

    private val _fields = MutableStateFlow(EditorFields())
    val fields: StateFlow<EditorFields> = _fields.asStateFlow()

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** A new serial asks the list to put focus on its first row (CHAN-FR-17). */
    private val _focusFirst = MutableStateFlow(0)
    val focusFirstRequest: StateFlow<Int> = _focusFirst.asStateFlow()

    val lists: StateFlow<List<ChannelListEntity>> = env.lists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The lists the selected channel is in (CHAN-NFR-05: the selected channel only). */
    val memberOf: StateFlow<Set<String>> = _selected.flatMapLatest { row -> row?.let { env.listsOf(it.key) } ?: flowOf(emptyList()) }
        .map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The list shown in the editor; kept across channels and wrapping at the end (CHAN-FR-52 fix). */
    private val _listIndex = MutableStateFlow(0)
    val listIndex: StateFlow<Int> = _listIndex.asStateFlow()

    /** The channel whose logo the phone page is open for, or null (CHAN-FR-40). */
    private val _logoFor = MutableStateFlow<ChannelRow?>(null)
    val logoFor: StateFlow<ChannelRow?> = _logoFor.asStateFlow()

    val phone: StateFlow<PhoneSetupState> = env.phone.stateIn(viewModelScope, SharingStarted.Eagerly, PhoneSetupState.Closed)

    private val _qr = MutableStateFlow<QrMatrix?>(null)
    val qr: StateFlow<QrMatrix?> = _qr.asStateFlow()

    private var sourceNames: Map<String, String> = emptyMap()
    private var searchJob: Job? = null
    private var rebuildJob: Job? = null

    init {
        viewModelScope.launch {
            val all = env.sources()
            _sources.value = all
            sourceNames = all.associate { it.id to it.name }
            _filter.value = _filter.value.copy(showHidden = env.showHidden.first())
            rebuild(focusFirst = true)
        }
        viewModelScope.launch { phone.collect(::onPhone) }
    }

    /** "Logo from phone": the page in logo mode for the selected channel and its dialog (CHAN-FR-40). */
    fun openLogoPhone() {
        val row = _selected.value ?: return
        _logoFor.value = row
        env.openLogoPhone(row.key, row.channel.name)
    }

    /** Close, Back or leaving the screen stops the page (CHAN-NFR-10). */
    fun closeLogoPhone() {
        _logoFor.value = null
        env.closePhone()
    }

    private var qrFor: String? = null

    private suspend fun onPhone(state: PhoneSetupState) {
        if (_logoFor.value == null) return
        if (state is PhoneSetupState.Open) {
            if (qrFor != state.url) {
                qrFor = state.url
                _qr.value = env.qrCode(state.url)
            }
            // A logo arrived: say so, stop the page, and show it (CHAN-FR-43).
            if (state.logoSaved) {
                closeLogoPhone()
                _status.value = Status(R.string.channels_logo_received, (_status.value?.serial ?: 0) + 1)
                refresh()
            }
        }
    }

    override fun onCleared() {
        if (_logoFor.value != null) env.closePhone()
    }

    // ---- Filters (CHAN-FR-11…16) ----------------------------------------------------------------

    /** All → each source → All; it does not wrap from the last source to the first (CHAN-FR-11). */
    fun cycleSource() {
        val ids = _sources.value.map { it.id }
        val current = _filter.value.sourceId
        val next = if (current == null) ids.firstOrNull() else ids.getOrNull(ids.indexOf(current) + 1)
        _filter.value = _filter.value.copy(sourceId = next, groupName = null)
        rebuild(focusFirst = true)
    }

    fun toggleSort() {
        val f = _filter.value
        _filter.value = f.copy(sort = if (f.sort == ChannelSort.PLAYLIST) ChannelSort.NAME else ChannelSort.PLAYLIST)
        rebuild(focusFirst = true)
    }

    fun toggleShowHidden() {
        val next = !_filter.value.showHidden
        _filter.value = _filter.value.copy(showHidden = next)
        viewModelScope.launch { env.setShowHidden(next) }
        rebuild(focusFirst = true)
    }

    fun chooseGroup(name: String?) {
        _filter.value = _filter.value.copy(groupName = name)
        rebuild(focusFirst = true)
    }

    /**
     * The search field (CHAN-FR-13, CHAN-NFR-02): debounced 250 ms, run in SQL. Focus is not
     * pulled from the field while the viewer types (CHAN-FR-17 rebuild rule).
     */
    fun setSearch(text: String) {
        val cut = text.take(SEARCH_MAX)
        _searchText.value = cut
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _filter.value = _filter.value.copy(search = cut)
            rebuild(focusFirst = false)
        }
    }

    /** Reads the counts and the first page again; the chosen group resets when it disappears. */
    private fun rebuild(focusFirst: Boolean) {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val f = _filter.value
            val ids = if (f.sourceId != null) listOf(f.sourceId) else _sources.value.map { it.id }
            val groups = ids.flatMap { env.groupNames(it) }.distinct()
            _groups.value = groups
            if (f.groupName != null && f.groupName !in groups) {
                _filter.value = f.copy(groupName = null)
                return@launch rebuild(focusFirst)
            }
            val counts = IntArray(ids.size) { env.count(ids[it], f) }
            val pages = ChannelPages(env, ids, f, counts, ::rowOf)
            pages.load(0)
            _pages.value = pages
            _version.value++
            val keep = _selected.value?.key?.let { key -> pages.heldRows().firstOrNull { it.second.key == key }?.second }
            select(keep ?: pages.rowAt(0))
            if (focusFirst) _focusFirst.value++
        }
    }

    // ---- Rows ------------------------------------------------------------------------------------

    /** Asks for the page holding [index] (off the main thread); the row draws when it arrives. */
    fun request(index: Int) {
        val pages = _pages.value ?: return
        val page = index / ChannelPages.PAGE
        if (page < 0 || page >= pages.pageCount || pages.isLoaded(page)) return
        // One read per page at a time: rows and the prefetch ask for the same page together.
        if (!loading.add(pages to page)) return
        viewModelScope.launch {
            try {
                pages.load(page)
            } finally {
                loading.remove(pages to page)
            }
            if (_pages.value === pages) _version.value++
        }
    }

    private val loading = HashSet<Pair<ChannelPages, Int>>()

    private fun rowOf(channel: ManagedChannel): ChannelRow {
        val line = channel.groupName?.takeIf { it.isNotBlank() } ?: sourceNames[channel.sourceId].orEmpty()
        return ChannelRow(channel, line, Initials.of(channel.name), hidden = !channel.visible)
    }

    /** Focus on a row selects it and clears the status (CHAN-FR-18); the editor's fields reset. */
    fun select(row: ChannelRow?) {
        if (row?.key != _selected.value?.key) _status.value = null
        _selected.value = row
        val channel = row?.channel ?: run {
            _fields.value = EditorFields()
            return
        }
        viewModelScope.launch {
            val custom = env.custom(channel.key)
            val manual = custom?.manualEpgId
            val epgName = manual?.let { env.epgName(channel.sourceId, it) }
            if (_selected.value?.key != channel.key) return@launch
            _fields.value = EditorFields(
                name = custom?.customName.orEmpty(),
                group = custom?.customGroupTitle.orEmpty(),
                logoUrl = custom?.customLogoUrl.orEmpty(),
                number = custom?.customNumber?.toString().orEmpty(),
                manualEpgId = manual,
                manualEpgName = epgName,
            )
        }
    }

    fun editFields(change: (EditorFields) -> EditorFields) {
        _fields.value = change(_fields.value)
    }

    // ---- Actions (CHAN-FR-25…31) ---------------------------------------------------------------

    fun save() = act(R.string.channels_saved) { key ->
        val f = _fields.value
        env.save(key, ChannelFields(f.name, f.group, f.logoUrl, f.number, f.manualEpgId))
    }

    fun toggleHidden() {
        val row = _selected.value ?: return
        val hide = !row.hidden
        act(if (hide) R.string.channels_hidden else R.string.channels_shown) { env.setHidden(it, hide) }
    }

    fun reset() = act(R.string.channels_reset_done) { env.reset(it) }

    /** Move up / down; only with Playlist order (CHAN-FR-29). */
    fun move(up: Boolean) {
        if (_filter.value.sort != ChannelSort.PLAYLIST) return
        val key = _selected.value?.key ?: return
        viewModelScope.launch {
            if (env.move(key, up, _filter.value)) {
                _status.value = Status(R.string.channels_order_updated, (_status.value?.serial ?: 0) + 1)
                refresh()
            }
        }
    }

    private fun act(status: Int, write: suspend (String) -> Unit) {
        val key = _selected.value?.key ?: return
        viewModelScope.launch {
            write(key)
            _status.value = Status(status, (_status.value?.serial ?: 0) + 1)
            refresh()
        }
    }

    /**
     * Reads the held pages again after a write (CHAN-NFR-07), keeping the list's place and the
     * selection; a channel the filters now hide (hidden with Show hidden off) gives way to the
     * first row, as beta 23 did.
     */
    private suspend fun refresh() {
        val pages = _pages.value ?: return rebuild(focusFirst = false)
        pages.reloadHeld()
        _version.value++
        val key = _selected.value?.key
        val same = key?.let { k -> pages.heldRows().firstOrNull { it.second.key == k }?.second }
        if (same != null) {
            // The shown values changed; the fields reset to the stored edit (§4.3).
            select(same)
        } else {
            select(pages.rowAt(0))
            _focusFirst.value++
        }
    }

    // ---- Guide mapping (CHAN-FR-24) -----------------------------------------------------------------

    fun chooseEpg(epgId: String?, name: String?) {
        _fields.value = _fields.value.copy(manualEpgId = epgId, manualEpgName = name)
    }

    // ---- Lists (CHAN-FR-50…54) ------------------------------------------------------------------------

    fun createList(name: String) {
        viewModelScope.launch {
            if (env.createList(name) != null) _status.value = Status(R.string.channels_list_created, (_status.value?.serial ?: 0) + 1)
        }
    }

    /** "List: …" steps to the next list and wraps to the first. */
    fun nextList() {
        val count = lists.value.size
        if (count > 0) _listIndex.value = (_listIndex.value + 1) % count
    }

    fun toggleMembership() {
        val list = lists.value.getOrNull(_listIndex.value.coerceAtMost(lists.value.lastIndex)) ?: return
        val key = _selected.value?.key ?: return
        val member = list.id in memberOf.value
        viewModelScope.launch {
            if (member) env.removeFromList(list.id, key) else env.addToList(list.id, key)
            _status.value = Status(if (member) R.string.channels_removed_from_list else R.string.channels_added_to_list, (_status.value?.serial ?: 0) + 1)
        }
    }

    fun deleteList() {
        val list = lists.value.getOrNull(_listIndex.value.coerceAtMost(lists.value.lastIndex)) ?: return
        viewModelScope.launch {
            env.deleteList(list.id)
            _listIndex.value = 0
            _status.value = Status(R.string.channels_list_deleted, (_status.value?.serial ?: 0) + 1)
        }
    }

    /** A page of the selected channel's source's XMLTV channels for the mapping picker (CHAN-NFR-06). */
    suspend fun epgPage(search: String, after: EpgChannelOption?): List<EpgChannelOption> {
        val sourceId = _selected.value?.channel?.sourceId ?: return emptyList()
        return env.epgOptions(sourceId, search, after, EPG_PAGE)
    }

    private companion object {
        const val SEARCH_MAX = 100
        const val SEARCH_DEBOUNCE_MS = 250L
        const val EPG_PAGE = 200
    }
}
