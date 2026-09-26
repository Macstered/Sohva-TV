package com.sohva.tv.app.sport

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.profile.openManaged
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.feature.sport.feed.FeedState
import com.sohva.tv.feature.sport.today.TodayEnvironment
import com.sohva.tv.feature.sport.today.WatchSummary
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Today's view of the app (spec 60 §4.5); stores are read off the main thread. */
class AppTodayEnvironment(private val graph: AppGraph, private val stack: BackStack<AppRoute>) : TodayEnvironment {
    private val io get() = graph.dispatchers.io

    override val feed: StateFlow<FeedState> get() = graph.sport.feed.state

    override val follows: Flow<SportFollows> get() = graph.data.preferences.sportFollows.flowOn(io)

    /** The active profile's favourites; a profile switch brings the other set (SPORT-FR-96). */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val favourites: Flow<Set<String>>
        get() = graph.data.profiles.household.map { it.active.id }.distinctUntilChanged()
            .flatMapLatest { graph.data.preferences.favouriteEventsOf(it) }.flowOn(io)

    // Pairing arrives with the streams (M8 part 4).
    override val watch: Flow<Map<String, WatchSummary>> get() = flowOf(emptyMap())

    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override fun now(): Long = graph.clock.wallMillis()

    override fun refresh() = graph.sport.feed.refresh()

    override fun setVisible(visible: Boolean) = graph.sport.visible(SportGraph.Viewer.TODAY, visible)

    override fun openGuide() {
        stack.push(AppRoute.Guide)
    }

    override fun openSettings() = graph.openManaged(AppRoute.Settings, stack)
}
