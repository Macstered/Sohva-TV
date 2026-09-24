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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    internal suspend fun onPlaying(channel: LiveChannel) {
        val spec = channel.groupId?.let { ListSpec.Group(channel.sourceId, it) } ?: ListSpec.Ungrouped(channel.sourceId)
        val list = reads.open(spec)
        playingList = list
        playingIndex = reads.indexOf(list, channel)
        playingGroup = channel.groupName
    }

    /** Next/previous in the playing group, wrapping (PLAY-FR-56); false when there is nothing to step to. */
    fun step(delta: Int): Boolean {
        val list = playingList ?: return false
        if (playingIndex < 0 || list.size < 2) return false
        val target = Math.floorMod(playingIndex + delta, list.size)
        scope.launch { rowAt(list, target)?.let { model.play(it.key) } }
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
        _open.value = false
        _groups.value = null
        _view.value = null
        shownList = null
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
        scope.launch {
            val row = rowAt(list, index) ?: return@launch
            if (row.key != model.playing.value?.channel?.key) model.play(row.key)
        }
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

    /** Reads the page around the selection and, once, the now titles of its rows. */
    private fun refresh() {
        val list = shownList ?: return
        job?.cancel()
        job = scope.launch {
            val page = selected / ChannelList.PAGE
            val rows = reads.page(list, page)
            val base = page * ChannelList.PAGE
            val current = _view.value
            _view.value = ChannelListView(
                shownGroupName, list.size, selected, base, rows,
                if (current?.firstIndex == base && current.groupName == shownGroupName) current.nowTitles else emptyMap(),
                model.playing.value?.channel?.key,
            )
            if (current?.firstIndex == base && current.groupName == shownGroupName && current.nowTitles.isNotEmpty()) return@launch
            val source = reads.sources.first().firstOrNull { it.id == list.spec.sourceId } ?: return@launch
            val now = model.now()
            val ids = rows.mapNotNull { it.epgId }.distinct()
            val schedules = reads.schedules(source, ids, GuideWindow.anchor(now))
            val titles = HashMap<String, String>()
            for (row in rows) {
                val live = row.epgId?.let { schedules[it] }?.firstOrNull { it.isLive(now) } ?: continue
                titles[row.key] = live.title
            }
            _view.value = _view.value?.copy(nowTitles = titles)
        }
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
