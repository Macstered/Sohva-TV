package com.sohva.tv.feature.live

import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideRules
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.core.model.text.StreamTags
import com.sohva.tv.core.model.time.TimeLabels
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The guide's screen model (spec 20). Composition-level state is in [StateFlow]s; the two values
 * rows draw with — the minute and the window start — are snapshot states read only in the draw
 * phase (GUIDE-NFR-02). Rows and schedules live in [RowPages] and [ProgrammeCache], both bounded.
 */
class GuideModel(private val env: GuideEnvironment, private val openedFor: String?) : ViewModel() {
    private val reads = env.reads

    private val _phase = MutableStateFlow(GuidePhase.LOADING)
    val phase: StateFlow<GuidePhase> = _phase.asStateFlow()

    private val _sources = MutableStateFlow<List<LiveSource>>(emptyList())
    val sources: StateFlow<List<LiveSource>> = _sources.asStateFlow()

    private val _source = MutableStateFlow<LiveSource?>(null)
    val source: StateFlow<LiveSource?> = _source.asStateFlow()

    private val _rail = MutableStateFlow<List<RailItem>>(emptyList())
    val rail: StateFlow<List<RailItem>> = _rail.asStateFlow()

    private val _entry = MutableStateFlow<RailEntry?>(null)
    val entry: StateFlow<RailEntry?> = _entry.asStateFlow()

    private val _list = MutableStateFlow<ListView?>(null)
    val list: StateFlow<ListView?> = _list.asStateFlow()

    private val _reading = MutableStateFlow(false)

    /** "Reading the guide…" above the kept rows after 400 ms (GUIDE-FR-36). */
    val readingNotice: StateFlow<Boolean> = _reading.asStateFlow()

    private val _window = MutableStateFlow(GuideWindow.AT_NOW)
    val window: StateFlow<GuideWindow> = _window.asStateFlow()

    /** Draw-phase values (GUIDE-NFR-02). */
    val nowState = mutableLongStateOf(env.clock.wallMillis())
    val windowStartState = mutableLongStateOf(GuideWindow.AT_NOW.startAt(nowState.longValue))

    private val _selection = MutableStateFlow<GuideSelection?>(null)
    val selection: StateFlow<GuideSelection?> = _selection.asStateFlow()

    private val _focus = MutableStateFlow<GuideFocus?>(null)
    val focus: StateFlow<GuideFocus?> = _focus.asStateFlow()
    private var focusSerial = 0

    val programmes = ProgrammeCache()

    val showNumbers: StateFlow<Boolean> = env.showChannelNumbers.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val favourites: StateFlow<Set<String>> = env.reads.favouriteKeys().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val savedSources: StateFlow<List<SavedSource>> = env.savedSources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val health = env.health.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _labels = MutableStateFlow(TimeLabels(TimeLabels.zoneOf(null), env.locale))
    val labels: StateFlow<TimeLabels> = _labels.asStateFlow()

    val overlays = GuideOverlays(this, viewModelScope)

    /** The pending page direction while its focus hand-off waits (GUIDE-FR-42..44); 0 = none. */
    var pendingPage: Int = 0
        private set
    private var pendingRow: Int = -1

    /** Whether the viewer picked the entry (GUIDE-FR-15), and whether the opened-for channel still decides. */
    private var chosenByViewer = false
    private var openedForUsable = openedFor != null
    private var listSerial = 0
    private var listJob: Job? = null
    private var programmeJob: Job? = null
    private var programmeRequest: Pair<Set<String>, Long>? = null
    private var viewport: IntRange = IntRange.EMPTY
    private var lastVisible = 0 to 0

    init {
        viewModelScope.launch { env.timeZone.collect { _labels.value = TimeLabels(TimeLabels.zoneOf(it), env.locale) } }
        viewModelScope.launch { tick() }
        viewModelScope.launch { watchSources() }
        viewModelScope.launch {
            // Writes re-read the rail and the list; a burst folds into one read (GUIDE-FR-37).
            reads.changes().drop(1).conflate().collect {
                refreshRail()
                reread()
                delay(REREAD_SPACING_MS)
            }
        }
        viewModelScope.launch {
            reads.guideChanges().drop(1).conflate().collect {
                programmes.clear()
                programmeRequest = null
                requestProgrammes()
                delay(REREAD_SPACING_MS)
            }
        }
    }

    // ---- Sources, rail and lists -------------------------------------------------------------

    private suspend fun watchSources() {
        reads.sources.collect { list ->
            _sources.value = list
            if (list.isEmpty()) {
                _phase.value = GuidePhase.EMPTY_LIBRARY
                _source.value = null
                return@collect
            }
            val current = _source.value
            if (current != null && list.any { it.id == current.id }) {
                _source.value = list.first { it.id == current.id }
                return@collect
            }
            val opened = openedFor?.let { reads.channel(it) }
            val lastWatched = env.lastChannel.first()?.let { reads.channel(it) }
            val chosen = GuideRules.chooseSource(
                list.map { it.id },
                openedFor = opened?.sourceId?.takeIf { openedForUsable },
                saved = env.lastGuideSource.first(),
                lastWatched = lastWatched?.sourceId,
            )
            selectSource(list.first { it.id == chosen })
        }
    }

    private suspend fun selectSource(source: LiveSource) {
        _source.value = source
        env.saveGuideSource(source.id)
        chosenByViewer = false
        refreshRail()
        val opened = openedFor?.takeIf { openedForUsable }?.let { reads.channel(it) }?.takeIf { it.sourceId == source.id }
        val entry = when {
            opened != null && opened.groupId == null -> RailEntry.All
            opened != null -> _rail.value.firstOrNull { (it.entry as? RailEntry.Group)?.group?.id == opened.groupId }?.entry ?: firstGroup()
            else -> firstGroup()
        }
        showEntry(entry, focusKey = opened?.key)
    }

    private fun firstGroup(): RailEntry =
        GuideRules.firstGroup(_rail.value.mapNotNull { it.entry as? RailEntry.Group }) { null } ?: RailEntry.All

    private suspend fun refreshRail() {
        val source = _source.value ?: return
        val groups = reads.rail(source.id)
        val ungrouped = reads.open(ListSpec.Ungrouped(source.id)).size
        val all = groups.sumOf { it.itemCount } + ungrouped
        val favouriteCount = favourites.value.size
        _rail.value = buildList {
            add(RailItem(RailEntry.Favourites, favouriteCount.takeIf { it > 0 }))
            add(RailItem(RailEntry.All, all.takeIf { it > 0 }))
            add(RailItem(RailEntry.Recent, null))
            groups.forEach { add(RailItem(RailEntry.Group(it), it.itemCount)) }
        }
        // A selected group that disappeared (GUIDE-FR-15).
        val selected = _entry.value as? RailEntry.Group ?: return
        if (_rail.value.none { (it.entry as? RailEntry.Group)?.group?.id == selected.group.id }) {
            showEntry(if (chosenByViewer) RailEntry.All else firstGroup(), focusKey = null)
        }
    }

    /** The viewer picked a rail entry (GUIDE-FR-27): the rail closes, focus goes to the first row. */
    fun chooseEntry(entry: RailEntry) {
        chosenByViewer = true
        overlays.clearSearch()
        viewModelScope.launch { showEntry(entry, focusKey = openedFor?.takeIf { openedForUsable }) }
    }

    /** Options › Source (GUIDE-FR-16, -94): the next source's first group; a no-op with one source. */
    fun switchSource() {
        val list = _sources.value
        val current = _source.value ?: return
        if (list.size < 2) return
        val next = list[(list.indexOfFirst { it.id == current.id } + 1) % list.size]
        openedForUsable = false
        overlays.clearSearch()
        overlays.listChangedUnderSheet = true
        viewModelScope.launch { selectSource(next) }
    }

    private fun specOf(entry: RailEntry, source: LiveSource): suspend () -> ListSpec = when (entry) {
        RailEntry.All -> { { ListSpec.All(source.id) } }
        RailEntry.Favourites -> { { reads.favourites(source.id) } }
        RailEntry.Recent -> { { reads.recents(source.id) } }
        is RailEntry.Group -> { { ListSpec.Group(source.id, entry.group.id) } }
    }

    /**
     * Reads [entry]'s list and replaces the rows in one step once the first page is formatted; the
     * old rows stay until then (GUIDE-FR-36). Focus goes to [focusKey]'s row, else the first row.
     */
    private suspend fun showEntry(entry: RailEntry, focusKey: String?, spec: ListSpec? = null, searching: Boolean = false) {
        val source = _source.value ?: return
        _entry.value = entry
        listJob?.cancel()
        val job = viewModelScope.launch {
            val notice = launch {
                delay(READING_NOTICE_MS)
                _reading.value = true
            }
            val wanted = spec ?: specOf(entry, source)()
            // A return within 10 minutes with nothing written gets the kept index: no read (GUIDE-FR-120).
            val list = env.keptList(wanted) ?: reads.open(wanted).also { if (!searching) env.keepList(it) }
            val target = focusKey?.let { key -> reads.channel(key) }?.takeIf { it.sourceId == source.id }
                ?.let { reads.indexOf(list, it) }?.takeIf { it >= 0 } ?: 0
            val view = ListView(entry, list, ++listSerial, searching)
            val pages = RowPages(list.size, ChannelList.PAGE)
            if (list.size > 0) pages.put(target / ChannelList.PAGE, format(list, target / ChannelList.PAGE), target / ChannelList.PAGE)
            notice.cancel()
            _reading.value = false
            programmes.clear()
            programmeRequest = null
            rowPages = pages
            _list.value = view
            _phase.value = GuidePhase.READY
            if (list.size == 0) {
                _selection.value = null
            } else {
                pages.peek(target)?.let { selectChannel(it) }
                focusRow(target, CHANNEL)
            }
        }
        listJob = job
    }

    /** The rows of the list on screen; replaced together with [list]. */
    var rowPages: RowPages = RowPages(0, ChannelList.PAGE)
        private set

    /** A write re-reads the current list; focus stays on the same channel (GUIDE-FR-37, -61). */
    private suspend fun reread() {
        val view = _list.value ?: return
        val source = _source.value ?: return
        if (view.searching) return
        val entry = view.entry
        val selectedKey = _selection.value?.row?.key
        val list = reads.open(specOf(entry, source)())
        env.keepList(list)
        val pages = RowPages(list.size, ChannelList.PAGE)
        val keep = selectedKey?.let { key -> reads.channel(key) }?.let { reads.indexOf(list, it) }?.takeIf { it >= 0 }
        val around = keep ?: lastVisible.first
        if (list.size > 0) {
            val page = (around.coerceIn(0, list.size - 1)) / ChannelList.PAGE
            pages.put(page, format(list, page), page)
        }
        rowPages = pages
        _list.value = ListView(entry, list, view.serial, false)
        requestProgrammes()
        // Data never moves focus (GUIDE-FR-78); the selection follows only when its channel left.
        if (keep == null && list.size > 0) pages.peek(0)?.let { selectChannel(it) }
        if (list.size == 0) _selection.value = null
    }

    private suspend fun format(list: ChannelList, page: Int): List<GuideRowData> {
        val rows = reads.page(list, page)
        val numbers = showNumbers.value
        val sourceName = _source.value?.name.orEmpty()
        return withContext(env.format) {
            rows.mapIndexed { i, channel -> rowData(page * ChannelList.PAGE + i, channel, numbers, sourceName) }
        }
    }

    private fun rowData(index: Int, channel: LiveChannel, numbers: Boolean, sourceName: String): GuideRowData {
        val shown = channel.number ?: (index + 1)
        val tags = StreamTags.of(channel.name).joinToString(" · ")
        val feed = tags.ifEmpty { channel.groupName?.takeIf { it.isNotBlank() } ?: sourceName }
        return GuideRowData(index, channel, if (numbers) shown.toString() else null, shown, feed, Initials.of(channel.name))
    }

    // ---- Viewport, pages and programmes ------------------------------------------------------

    /** The grid's visible rows changed (GUIDE-FR-50): read their pages and their programmes. */
    fun onViewport(firstVisible: Int, visibleCount: Int) {
        lastVisible = firstVisible to visibleCount
        val view = _list.value ?: return
        val range = GuideRules.programmeRows(firstVisible, visibleCount, view.size)
        viewport = range
        if (range.isEmpty()) return
        val pages = rowPages
        val needed = (range.first / ChannelList.PAGE)..(range.last / ChannelList.PAGE)
        for (page in needed) {
            if (pages.has(page)) continue
            viewModelScope.launch {
                val rows = format(view.list, page)
                if (rowPages === pages) {
                    pages.put(page, rows, firstVisible / ChannelList.PAGE)
                    requestProgrammes()
                }
            }
        }
        requestProgrammes()
    }

    /**
     * Reads the schedules of the rows around the viewport that are not in the cache; a changed
     * request cancels the previous one, so a slow old read never lands (GUIDE-FR-54).
     */
    private fun requestProgrammes() {
        val source = _source.value ?: return
        val rows = rowPages.loaded(viewport)
        if (rows.isEmpty()) return
        val start = windowStartState.longValue
        val ids = rows.mapNotNullTo(LinkedHashSet()) { it.channel.epgId }.filterTo(LinkedHashSet()) { !programmes.isKnown(it) }
        if (ids.isEmpty()) {
            settlePending()
            return
        }
        val request = ids to start
        if (request == programmeRequest && programmeJob?.isActive == true) return
        programmeRequest = request
        programmeJob?.cancel()
        programmeJob = viewModelScope.launch {
            val found = reads.schedules(source, ids, start)
            val labels = _labels.value
            val formatted = withContext(env.format) {
                ids.associateWith { id ->
                    val list = found[id].orEmpty()
                    if (list.isEmpty()) RowSchedule.EMPTY else RowSchedule(list, list.map { labels.guideRange(it.start, it.stop) })
                }
            }
            if (windowStartState.longValue != start) return@launch
            programmes.putAll(formatted)
            settlePending()
            refreshSelectionProgramme()
        }
    }

    // ---- Time window and paging --------------------------------------------------------------

    private suspend fun tick() {
        while (true) {
            val now = env.clock.wallMillis()
            nowState.longValue = now
            val start = _window.value.startAt(now)
            if (start != windowStartState.longValue) moveWindowTo(_window.value)
            // Aligned to the minute boundary (GUIDE-FR-112).
            delay(GuideWindow.MINUTE_MS - now % GuideWindow.MINUTE_MS + 5)
        }
    }

    private fun moveWindowTo(window: GuideWindow) {
        _window.value = window
        windowStartState.longValue = window.startAt(env.clock.wallMillis())
        programmes.clear()
        programmeRequest = null
        requestProgrammes()
        overlays.onWindowChanged()
    }

    /**
     * Pages the window by [direction] × 90 min (or a day with [days]) for the row at [rowIndex]
     * (GUIDE-FR-43): focus parks on the channel column; when the row's programmes arrive, it goes to
     * the first (forward) or last (back) block. Returns false when nothing moved (the key is still
     * consumed at the limits).
     */
    fun page(direction: Int, rowIndex: Int, days: Boolean = false): Boolean {
        if (pendingPage != 0) return false
        val delta = (if (days) GuideWindow.DAY_MS else GuideWindow.PAGE_MS) * direction
        val moved = _window.value.moved(delta, env.clock.wallMillis()) ?: return false
        pendingPage = direction
        pendingRow = rowIndex
        moveWindowTo(moved)
        return true
    }

    /** Moving to another channel or opening the rail cancels the hand-off (GUIDE-FR-44). */
    fun cancelPending() {
        pendingPage = 0
        pendingRow = -1
    }

    private fun settlePending() {
        if (pendingPage == 0) return
        val row = rowPages.peek(pendingRow)
        if (row == null) {
            cancelPending()
            return
        }
        val schedule = programmes.schedule(row.channel.epgId) ?: return
        val blocks = drawnBlocks(schedule.programmes)
        val column = when {
            blocks.isEmpty() -> 0
            pendingPage > 0 -> blocks.first()
            else -> blocks.last()
        }
        val index = pendingRow
        cancelPending()
        select(row, schedule.programmes.getOrNull(column))
        focusRow(index, column)
    }

    /** Indexes of the programmes drawn in the current window (GUIDE-FR-56). */
    fun drawnBlocks(schedule: List<GuideProgramme>): List<Int> {
        val start = windowStartState.longValue
        val end = start + GuideWindow.LENGTH_MS
        val out = ArrayList<Int>(schedule.size)
        for (i in schedule.indices) {
            val p = schedule[i]
            val clippedEnd = minOf(p.stop, end, schedule.getOrNull(i + 1)?.start ?: Long.MAX_VALUE)
            if (p.stop > start && p.start < end && clippedEnd > maxOf(p.start, start)) out += i
        }
        return out
    }

    fun isAtNow(): Boolean = _window.value.isAtNow(env.clock.wallMillis())

    // ---- Selection and focus -----------------------------------------------------------------

    /** Focus on a row's channel column (GUIDE-FR-60). */
    fun selectChannel(row: GuideRowData) {
        val schedule = programmes.schedule(row.channel.epgId)?.programmes.orEmpty()
        select(row, GuideRules.channelSelection(schedule, env.clock.wallMillis()))
    }

    fun select(row: GuideRowData, programme: GuideProgramme?) {
        val current = _selection.value
        if (current != null && current.row.key == row.key && current.programme?.id == programme?.id) return
        _selection.value = GuideSelection(row, programme)
    }

    /** Programmes arriving keep the selected one by id, or the one covering its start (GUIDE-FR-61). */
    private fun refreshSelectionProgramme() {
        val current = _selection.value ?: return
        val schedule = programmes.schedule(current.row.channel.epgId)?.programmes ?: return
        val start = windowStartState.longValue
        val kept = GuideRules.keepSelection(
            schedule, current.programme?.id, current.programme?.start, start, start + GuideWindow.LENGTH_MS, env.clock.wallMillis(),
        )
        if (kept?.id != current.programme?.id) _selection.value = GuideSelection(current.row, kept)
    }

    fun focusRow(index: Int, column: Int) {
        _focus.value = GuideFocus(index, column, ++focusSerial)
    }

    fun toggleFavourite(key: String) {
        viewModelScope.launch {
            reads.toggleFavourite(key)
            if (_entry.value == RailEntry.Favourites) reread()
            refreshRail()
        }
    }

    /** Find programme's filtered list (GUIDE-FR-92), or the plain list again for a blank query. */
    internal fun showSearch(query: String) {
        val source = _source.value ?: return
        val entry = _entry.value ?: return
        viewModelScope.launch {
            if (query.isBlank()) {
                showEntry(entry, focusKey = null)
                return@launch
            }
            val base = when (entry) {
                is RailEntry.Group -> ListSpec.Group(source.id, entry.group.id)
                else -> ListSpec.All(source.id)
            }
            val matches = reads.search(base, source, query, windowStartState.longValue)
            showEntry(entry, focusKey = null, spec = matches, searching = true)
        }
    }

    /** Resolves a dialled number against the list on screen (GUIDE-FR-81). */
    internal suspend fun dialIndex(number: Int): Int {
        val view = _list.value ?: return -1
        return reads.dial(view.list, number)
    }

    fun syncAll() = env.syncAll()

    /** The hero's synopsis, by primary key off the main thread (GUIDE-NFR-13). */
    suspend fun description(programmeId: Long): String? = reads.description(programmeId)

    companion object {
        const val CHANNEL: Int = -1

        /** A focus hand-off that keeps the row's column (Down from the hero, GUIDE-FR-66). */
        const val KEEP: Int = -3
        const val READING_NOTICE_MS: Long = 400
        private const val REREAD_SPACING_MS: Long = 250
    }
}
