package com.sohva.tv.feature.player

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.guide.DialBuffer
import com.sohva.tv.core.model.guide.GuideWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The channel list as shown (PLAY-FR-50..55): one page of rows around the selection and their now titles. */
@Immutable
data class ChannelListView(
    val groupName: String?,
    val size: Int,
    val selected: Int,
    val firstIndex: Int,
    val rows: List<LiveChannel>,
    val nowTitles: Map<String, String>,
    val playingKey: String?,
)

@Immutable
data class GroupListView(val groups: List<LiveGroup>, val selected: Int)

/**
 * The playing channel's group for channel up/down, zap-back and the channel list (spec 30 §4.6–4.7
 * and §9): the group is a [ChannelList] index; rows are read a page at a time, now-playing titles
 * only for the page shown and only while the list is open.
 */
class PlayerChannels internal constructor(private val model: PlayerModel, private val reads: LiveReads, private val scope: CoroutineScope) {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _view = MutableStateFlow<ChannelListView?>(null)
    val view: StateFlow<ChannelListView?> = _view.asStateFlow()

    private val _groups = MutableStateFlow<GroupListView?>(null)
    val groups: StateFlow<GroupListView?> = _groups.asStateFlow()

    /** The playing channel's list and its place in it. */
    private var playingList: ChannelList? = null
    private var playingIndex = -1
    private var playingGroup: String? = null

    /** The list shown while browsing another group (reset on close, PLAY-FR-53). */
    private var shownList: ChannelList? = null
    private var shownGroupName: String? = null
    private var selected = 0
    private var job: Job? = null

    /** The list whose page [view] shows, and the page [job] is still reading rows for (-1: none). */
    private var viewList: ChannelList? = null
    private var loadingPage = -1

    /** The now-titles read and the rows it covers ([titledFrom] until [titledTo], list indexes). */
    private var titlesJob: Job? = null
    private var titledFrom = 0
    private var titledTo = 0

    internal suspend fun onPlaying(channel: LiveChannel) {
        val spec = channel.groupId?.let { ListSpec.Group(channel.sourceId, it) } ?: ListSpec.Ungrouped(channel.sourceId)
        val list = reads.open(spec)
        val index = reads.indexOf(list, channel)
        currentCoroutineContext().ensureActive()
        // Publish the list and position together, before the model commits the playing channel.
        playingList = list
        playingIndex = index
        playingGroup = channel.groupName
    }

    /** Next/previous in the playing group, wrapping (PLAY-FR-56); false when there is nothing to step to. */
    fun step(delta: Int): Boolean {
        val list = playingList ?: return false
        if (playingIndex < 0 || list.size < 2) return false
        val target = Math.floorMod(playingIndex + delta, list.size)
        model.playSelected { rowAt(list, target)?.key }
        return true
    }

    private suspend fun rowAt(list: ChannelList, index: Int): LiveChannel? =
        reads.page(list, index / ChannelList.PAGE).getOrNull(index % ChannelList.PAGE)

    /** Opens on the playing channel's group with the playing channel selected (PLAY-FR-53, -55). */
    fun openList(withGroups: Boolean = false): Boolean {
        val list = playingList ?: return false
        if (list.size == 0) return false
        model.hideBox()
        shownList = list
        shownGroupName = playingGroup
        selected = playingIndex.coerceAtLeast(0)
        _open.value = true
        refresh()
        if (withGroups) openGroups()
        return true
    }

    fun close() {
        job?.cancel()
        forgetTitles()
        _open.value = false
        _groups.value = null
        _view.value = null
        shownList = null
        viewList = null
        loadingPage = -1
    }

    /** Up/Down in the channel list, wrapping (PLAY-FR-52). */
    fun move(delta: Int) {
        val list = shownList ?: return
        if (list.size == 0) return
        selected = Math.floorMod(selected + delta, list.size)
        refresh()
    }

    /** OK: tune the selected channel unless it is playing, and close. */
    fun choose() {
        val list = shownList ?: return
        val index = selected
        close()
        model.playSelected { rowAt(list, index)?.key }
    }

    // ---- Group list --------------------------------------------------------------------------------

    fun openGroups(): Boolean {
        val sourceId = model.playing.value?.channel?.sourceId ?: return false
        scope.launch {
            // The playing source's groups in rail order; blank titles and switched-off groups are not in the rail.
            val groups = reads.rail(sourceId).filter { it.name.isNotBlank() }
            if (groups.isEmpty()) return@launch
            val at = groups.indexOfFirst { it.name == shownGroupName }.coerceAtLeast(0)
            _groups.value = GroupListView(groups, at)
        }
        return true
    }

    fun closeGroups() {
        _groups.value = null
    }

    fun moveGroup(delta: Int) {
        val g = _groups.value ?: return
        _groups.value = g.copy(selected = Math.floorMod(g.selected + delta, g.groups.size))
    }

    /** OK on a group: show it; the playing channel stays selected if it is in it (PLAY-FR-52). */
    fun chooseGroup() {
        val g = _groups.value ?: return
        val group = g.groups[g.selected]
        val sourceId = model.playing.value?.channel?.sourceId ?: return
        _groups.value = null
        scope.launch {
            val list = reads.open(ListSpec.Group(sourceId, group.id))
            shownList = list
            shownGroupName = group.name
            val playing = model.playing.value?.channel
            selected = if (playing != null && playing.groupId == group.id) reads.indexOf(list, playing).coerceAtLeast(0) else 0
            refresh()
        }
    }

    /** Reads the page around the selection; the now titles follow for the rows around the highlight. */
    private fun refresh() {
        val list = shownList ?: return
        val page = selected / ChannelList.PAGE
        val current = _view.value
        if (current != null && viewList === list && current.firstIndex == page * ChannelList.PAGE) {
            // The rows are on screen: the highlight moves in this key press, without a read (PLAY-FR-54).
            // A read of another page is stale now.
            if (loadingPage >= 0 && loadingPage != page) {
                job?.cancel()
                loadingPage = -1
            }
            _view.value = current.copy(selected = selected)
            ensureTitles()
            return
        }
        job?.cancel()
        forgetTitles()
        loadingPage = page
        job = scope.launch {
            val rows = reads.page(list, page)
            _view.value = ChannelListView(shownGroupName, list.size, selected, page * ChannelList.PAGE, rows, emptyMap(), model.playing.value?.channel?.key)
            viewList = list
            loadingPage = -1
            ensureTitles()
        }
    }

    private fun forgetTitles() {
        titlesJob?.cancel()
        titlesJob = null
        titledFrom = 0
        titledTo = 0
    }

    /**
     * Now titles for the rows around the highlight only (spec 30 §9 "only for the page shown"): a
     * group can hold hundreds of channels, and one read of all their programmes kept the first
     * title waiting for seconds on a busy box. [TITLE_REACH] rows either side are read; moving
     * within [TITLE_EDGE] rows of that range's end reads the next range. Titles already read stay,
     * at most one page of them.
     */
    private fun ensureTitles() {
        val view = _view.value ?: return
        val list = viewList ?: return
        val first = view.firstIndex
        val end = first + view.rows.size
        val covered = titledTo > titledFrom &&
            (selected - TITLE_EDGE >= titledFrom || titledFrom <= first) &&
            (selected + TITLE_EDGE < titledTo || titledTo >= end)
        if (covered) return
        // A read in flight that includes the highlight lands first; it asks again when it is done.
        if (titlesJob?.isActive == true && selected in titledFrom until titledTo) return
        titlesJob?.cancel()
        val from = maxOf(first, selected - TITLE_REACH)
        val to = minOf(end, selected + TITLE_REACH + 1)
        if (to <= from) return
        titledFrom = from
        titledTo = to
        titlesJob = scope.launch {
            val source = reads.source(list.spec.sourceId) ?: return@launch
            val now = model.now()
            val rows = view.rows.subList(from - first, to - first)
            val schedules = reads.schedules(source, rows.mapNotNull { it.epgId }.distinct(), GuideWindow.anchor(now))
            val titles = HashMap<String, String>()
            for (row in rows) {
                val live = row.epgId?.let { schedules[it] }?.firstOrNull { it.isLive(now) } ?: continue
                titles[row.key] = live.title
            }
            val shown = _view.value ?: return@launch
            if (viewList !== list || shown.firstIndex != first) return@launch
            _view.value = shown.copy(nowTitles = shown.nowTitles + titles)
            titlesJob = null
            ensureTitles()
        }
    }

    private companion object {
        /** About seven rows are on screen; this covers them with room to scroll before the next read. */
        const val TITLE_REACH = 12
        const val TITLE_EDGE = 5
    }
}

/**
 * Dialling in live playback (PLAY-FR-59): four digits, 2 s after the last; looked up in the
 * source's All-channels order with the shared rule; "No channel N" for 1.5 s.
 */
class PlayerDial internal constructor(private val model: PlayerModel, private val reads: LiveReads, private val scope: CoroutineScope) {
    private val buffer = DialBuffer()
    private val _state = MutableStateFlow<Pair<String?, Int?>>(null to null)

    /** (digits typed, number not found). */
    val state: StateFlow<Pair<String?, Int?>> = _state.asStateFlow()
    private var job: Job? = null

    fun digit(d: Int) {
        if (!buffer.append(d)) return
        _state.value = buffer.digits to null
        job?.cancel()
        job = scope.launch {
            delay(DialBuffer.COMMIT_MS)
            val number = buffer.number()
            val sourceId = model.playing.value?.channel?.sourceId
            // The look-up finishes before the buffer is cleared (PLAY-FR-59).
            val found = if (number == null || sourceId == null) null else find(sourceId, number)
            buffer.clear()
            if (found != null) {
                _state.value = null to null
                model.play(found)
            } else {
                _state.value = null to (number ?: 0)
                delay(DialBuffer.NOT_FOUND_MS)
                _state.value = null to null
            }
        }
    }

    private suspend fun find(sourceId: String, number: Int): String? {
        val list = reads.open(ListSpec.All(sourceId))
        val index = reads.dial(list, number)
        if (index < 0) return null
        return reads.page(list, index / ChannelList.PAGE).getOrNull(index % ChannelList.PAGE)?.key
    }
}
