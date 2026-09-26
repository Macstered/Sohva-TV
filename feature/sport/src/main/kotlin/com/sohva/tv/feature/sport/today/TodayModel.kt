package com.sohva.tv.feature.sport.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.sport.TodayFilter
import com.sohva.tv.core.model.sport.TodayLists
import com.sohva.tv.core.model.sport.TodaySections
import com.sohva.tv.core.model.sport.TodayTab
import com.sohva.tv.feature.sport.feed.FeedState
import kotlinx.coroutines.CoroutineDispatcher
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

/** What pairing found for a game (spec 60 §4.10): watchable (Available, confirmed included) and Possible streams. */
data class WatchSummary(val available: Int, val possible: Int)

/** What Today needs from the app (plan/03 §4.6). */
interface TodayEnvironment {
    val feed: StateFlow<FeedState>
    val follows: Flow<SportFollows>

    /** The active profile's favourite games (SPORT-FR-96). */
    val favourites: Flow<Set<String>>

    /** Per game id; empty until pairing runs (spec 60 §4.10). */
    val watch: Flow<Map<String, WatchSummary>>

    /** Where lists are filtered and grouped (AppDispatchers.ui): never the main thread. */
    val format: CoroutineDispatcher

    fun now(): Long

    fun refresh()

    /** Today is on screen with the app in front (SPORT-FR-27). */
    fun setVisible(visible: Boolean)

    fun openGuide()

    fun openSettings()
}

/** Everything Today draws, computed once per change off the main thread (spec 60 §9 rule). */
data class TodayView(
    val tabs: List<TodayTab> = listOf(TodayTab(TodayFilter.All, 0)),
    val filter: TodayFilter = TodayFilter.All,
    val sections: TodaySections = TodaySections(emptyList(), emptyList(), emptyList()),
    val watch: Map<String, WatchSummary> = emptyMap(),
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
 * The Today screen's model (spec 60 §4.5). The selected tab survives the player and process
 * recreation (SPORT-FR-47); focus moves only on entry and after a tab is chosen, never because
 * data arrived (SPORT-FR-60 rebuild).
 */
class TodayModel(private val env: TodayEnvironment) : ViewModel() {
    private val filterKey = MutableStateFlow<String?>(null)

    /** The screen's saved tab after process recreation (it keeps it with `rememberSaveable`). */
    fun restore(key: String?) {
        if (filterKey.value == null && key != null) filterKey.value = key
    }

    val view: StateFlow<TodayView> = combine(env.feed, env.follows, env.favourites, env.watch, filterKey) { feed, follows, favourites, watch, key ->
        val watchable = watch.filterValues { it.available > 0 }.keys
        val filter = TodayLists.effective(TodayFilter.fromKey(key), follows)
        val shown = TodayLists.filter(feed.events, filter, watchable, favourites)
        TodayView(
            tabs = TodayLists.tabs(feed.events, follows, watchable, favourites),
            filter = filter,
            sections = TodayLists.sections(shown),
            watch = watch,
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

    /** OK on a tab selects it (focus alone does not); focus then goes to its first game. */
    fun select(filter: TodayFilter) {
        filterKey.value = filter.key
        _focusTurn.update { it + 1 }
    }

    fun refresh() = env.refresh()

    fun setVisible(visible: Boolean) = env.setVisible(visible)

    fun openGuide() = env.openGuide()

    fun openSettings() = env.openSettings()

    override fun onCleared() = env.setVisible(false)

    private companion object {
        const val MINUTE = 60_000L
    }
}
