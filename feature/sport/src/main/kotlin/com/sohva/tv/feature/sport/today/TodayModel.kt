package com.sohva.tv.feature.sport.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.sport.TodayFilter
import com.sohva.tv.core.model.sport.TodayLists
import com.sohva.tv.core.model.sport.TodaySections
import com.sohva.tv.core.model.sport.TodayTab
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.SportsChannel
import com.sohva.tv.core.model.sport.pairing.SportsChannels
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamOrder
import com.sohva.tv.feature.sport.feed.FeedState
import com.sohva.tv.feature.sport.hub.MatchEventsState
import com.sohva.tv.feature.sport.provider.CacheState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What pairing found for a game (spec 60 §4.10): watchable (Available, confirmed included) and Possible streams. */
data class WatchSummary(val available: Int, val possible: Int)

/** What Today needs from the app (plan/03 §4.6). */
interface TodayEnvironment {
    val feed: StateFlow<FeedState>
    val follows: Flow<SportFollows>

    /** The active profile's favourite games (SPORT-FR-96). */
    val favourites: Flow<Set<String>>

    /**
     * Per game id, the streams pairing found (spec 60 §4.10): the decisions applied, other profiles'
     * channels left out and the priority order applied. Empty until pairing runs.
     */
    val streams: Flow<Map<String, List<StreamMatch>>>

    /** A game to open in the hub once the list holds it (SPORT-NAV-04): from Home, a reminder or a notification. */
    val pendingGame: StateFlow<String?>

    /** The stored reminder ids, for "Reminder set" (SPORT-FR-95). */
    val reminders: Flow<Set<String>>

    /** Where lists are filtered and grouped (AppDispatchers.ui): never the main thread. */
    val format: CoroutineDispatcher

    fun now(): Long

    fun refresh()

    /** Today is on screen with the app in front (SPORT-FR-27). */
    fun setVisible(visible: Boolean)

    fun consumePendingGame()

    /** Sets or removes the game's reminder (SPORT-FR-95); [channelKey] is what it will play. */
    suspend fun toggleReminder(event: SportEvent, channelKey: String?)

    /** Confirm, reject or (null) restore (SPORT-FR-76); false when the save failed. */
    suspend fun decide(eventId: String, channelKey: String, decision: Decision?): Boolean

    /** Watch: the channel plays, Back returns to Today with the hub open (SPORT-NAV-02). */
    fun play(channelKey: String)

    /** A football game's match events through their 2-minute cache (SPORT-FR-91); throws [SportsException]. */
    suspend fun incidents(eventId: String): Pair<List<Incident>, CacheState>

    fun openGuide()

    fun openSettings()
}

/** Everything Today draws, computed once per change off the main thread (spec 60 §9 rule). */
data class TodayView(
    val tabs: List<TodayTab> = listOf(TodayTab(TodayFilter.All, 0)),
    val filter: TodayFilter = TodayFilter.All,
    val sections: TodaySections = TodaySections(emptyList(), emptyList(), emptyList()),
    val watch: Map<String, WatchSummary> = emptyMap(),
    /** "Sports channels now" for the games on screen (SPORT-25). */
    val channels: List<SportsChannel> = emptyList(),
    val favourites: Set<String> = emptySet(),
    val anyEvents: Boolean = false,
    val loading: Boolean = false,
    /** The last refresh failed and there is nothing to show (SPORT-FR-54). */
    val failure: SportsProblem? = null,
    /** A failure while games are shown: one line under the tabs (SPORT-FR-54 rebuild). */
    val notice: SportsProblem? = null,
    val complete: Boolean = false,
    val zoneId: String = "",
)

/**
 * The Today screen's model (spec 60 §4.5, §4.6). The selected tab and the open hub survive the
 * player and process recreation (SPORT-FR-47, SPORT-NAV-02); focus moves only on entry and after
 * a tab is chosen, never because data arrived (SPORT-FR-60 rebuild).
 */
class TodayModel(private val env: TodayEnvironment) : ViewModel() {
    private val filterKey = MutableStateFlow<String?>(null)

    /** The screen's saved tab and hub after process recreation (it keeps them with `rememberSaveable`). */
    fun restore(key: String?, hub: String?) {
        if (filterKey.value == null && key != null) filterKey.value = key
        if (_hub.value == null && hub != null) _hub.value = hub
    }

    val view: StateFlow<TodayView> = combine(env.feed, env.follows, env.favourites, env.streams, filterKey) { feed, follows, favourites, streams, key ->
        val watch = streams.mapValues { (_, list) -> StreamOrder.counts(list).let { (available, possible) -> WatchSummary(available, possible) } }
        val watchable = watch.filterValues { it.available > 0 }.keys
        val filter = TodayLists.effective(TodayFilter.fromKey(key), follows)
        val shown = TodayLists.filter(feed.events, filter, watchable, favourites)
        val sections = TodayLists.sections(shown)
        TodayView(
            tabs = TodayLists.tabs(feed.events, follows, watchable, favourites),
            filter = filter,
            sections = sections,
            watch = watch,
            channels = SportsChannels.of(sections.live + sections.later + sections.finished, streams),
            favourites = favourites,
            anyEvents = feed.events.isNotEmpty(),
            loading = feed.loading,
            failure = feed.failure.takeIf { feed.events.isEmpty() },
            notice = if (feed.events.isEmpty()) null else feed.failure ?: feed.cause.takeIf { feed.partial },
            complete = feed.complete,
            zoneId = feed.zoneId,
        )
    }.flowOn(env.format).stateIn(viewModelScope, SharingStarted.Eagerly, TodayView())

    /** Counts up on entry and when a tab is chosen: the screen then focuses the first game, else the tab. */
    private val _focusTurn = MutableStateFlow(1)
    val focusTurn: StateFlow<Int> = _focusTurn.asStateFlow()

    /** The minute ticker for the header clock, on the minute boundary, only while shown (SPORT-FR-45 rebuild). */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(env.now())
            delay(MINUTE - env.now() % MINUTE)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), env.now())

    private val _hub = MutableStateFlow<String?>(null)

    /** The game the hub shows, by id (SPORT-FR-70); null when the hub is closed. */
    val hub: StateFlow<String?> = _hub.asStateFlow()

    /** The hub's game looked up in the whole list, so its score updates while open (SPORT-FR-70). */
    val hubEvent: StateFlow<SportEvent?> = combine(env.feed, _hub) { feed, id -> id?.let { feed.events.firstOrNull { e -> e.id == it } } }
        .flowOn(env.format).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _matchEvents = MutableStateFlow(MatchEventsState())
    val matchEvents: StateFlow<MatchEventsState> = _matchEvents.asStateFlow()
    private var eventsJob: Job? = null

    val reminders: StateFlow<Set<String>> = env.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptySet())

    // The hub's stream order, fixed while it is open (SPORT-FR-78); touched only by [hubStreams]' one collector.
    private var hubOrder: List<String> = emptyList()
    private var hubOrderFor: String? = null

    /**
     * The open game's streams in the order they had when the hub opened: vanished ones dropped, new
     * ones appended, so a decision or a refresh never moves a row under the viewer (SPORT-FR-78).
     */
    val hubStreams: StateFlow<List<StreamMatch>> = combine(env.streams, _hub) { streams, id ->
        if (id == null) {
            hubOrderFor = null
            return@combine emptyList()
        }
        val list = streams[id].orEmpty()
        val keys = list.map { it.channelKey }
        hubOrder = if (hubOrderFor != id) keys else hubOrder.filter { it in keys } + keys.filter { it !in hubOrder }
        hubOrderFor = id
        val byKey = list.associateBy { it.channelKey }
        hubOrder.map(byKey::getValue)
    }.flowOn(env.format).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _decisionFailed = MutableStateFlow(false)

    /** The last Confirm, Reject or Restore was not saved (MATCH_DECISION_SAVE). */
    val decisionFailed: StateFlow<Boolean> = _decisionFailed.asStateFlow()

    init {
        // SPORT-NAV-04: open the waiting game once the list holds it; a complete load without it
        // drops the request, so yesterday's game never opens later by surprise (rebuild).
        viewModelScope.launch {
            combine(env.pendingGame, env.feed) { id, feed -> id to feed }.collect { (id, feed) ->
                if (id == null) return@collect
                when {
                    feed.events.any { it.id == id } -> {
                        env.consumePendingGame()
                        _hub.value = id
                    }
                    feed.complete && !feed.loading -> env.consumePendingGame()
                }
            }
        }
    }

    /** OK on a tab selects it (focus alone does not); focus then goes to its first game. */
    fun select(filter: TodayFilter) {
        filterKey.value = filter.key
        _focusTurn.update { it + 1 }
    }

    fun openHub(event: SportEvent) {
        _hub.value = event.id
    }

    /** The screen places focus on the list first, then calls this (AGENTS.md §5 rule 2). */
    fun closeHub() {
        _hub.value = null
    }

    /**
     * Loads [event]'s match events when the hub shows it (SPORT-FR-71): unless loaded within their
     * 2-minute freshness or loading; [force] (Refresh, Try again) asks again, the cache still answering
     * inside the window (SPORT-FR-91). Never polled while the hub is open.
     */
    fun loadMatchEvents(event: SportEvent, force: Boolean = false) {
        if (!event.detailsAvailable) return
        val s = _matchEvents.value
        val same = s.eventId == event.id
        if (same && s.loading) return
        if (same && !force && s.incidents != null && env.now() - s.loadedAt < EVENTS_FRESH_MS) return
        _matchEvents.value = (if (same) s else MatchEventsState(eventId = event.id)).copy(loading = true)
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            _matchEvents.value = try {
                val (incidents, state) = env.incidents(event.id)
                MatchEventsState(event.id, incidents, loading = false, failed = false, cached = state == CacheState.STALE, loadedAt = env.now())
            } catch (e: SportsException) {
                // An earlier list stays with the cached-data line (SPORT-FR-91).
                _matchEvents.value.copy(loading = false, failed = true)
            }
        }
    }

    /** The reminder plays the first Available, non-rejected stream in the current order, if any (SPORT-FR-95). */
    fun toggleReminder(event: SportEvent) {
        val channel = hubStreams.value.firstOrNull { it.eventId == event.id && it.confidence == Confidence.AVAILABLE }?.channelKey
        viewModelScope.launch { env.toggleReminder(event, channel) }
    }

    fun decide(stream: StreamMatch, decision: Decision?) {
        viewModelScope.launch { _decisionFailed.value = !env.decide(stream.eventId, stream.channelKey, decision) }
    }

    fun play(channelKey: String) = env.play(channelKey)

    fun refresh() = env.refresh()

    fun setVisible(visible: Boolean) = env.setVisible(visible)

    fun openGuide() = env.openGuide()

    fun openSettings() = env.openSettings()

    override fun onCleared() = env.setVisible(false)

    private companion object {
        const val MINUTE = 60_000L
        const val EVENTS_FRESH_MS = 2 * 60_000L
        const val STOP_MS = 5_000L
    }
}
