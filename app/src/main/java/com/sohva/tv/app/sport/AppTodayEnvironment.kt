package com.sohva.tv.app.sport

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.profile.openManaged
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderIds
import com.sohva.tv.core.model.reminder.ReminderKind
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamOrder
import com.sohva.tv.feature.sport.feed.FeedState
import com.sohva.tv.feature.sport.provider.CacheState
import com.sohva.tv.feature.sport.today.TodayEnvironment
import com.sohva.tv.app.profile.ChannelStarter
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
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

    /**
     * Pairing's streams for the active profile (spec 60 §4.10): a restricted profile is not offered
     * channels outside its allowed live groups (§10 Q6), then the priority codes order each
     * confidence (SPORT-FR-117). Read off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val streams: Flow<Map<String, List<StreamMatch>>>
        get() {
            val profile = graph.data.profiles.household.map { it.active.id }.distinctUntilChanged()
            return combine(graph.sport.pairing.streams, graph.data.preferences.sportsPriority, profile) { streams, priority, active ->
                val keys = streams.values.flatten().map { it.channelKey }.distinct()
                val allowed = keys.chunked(ALLOWED_CHUNK).flatMapTo(HashSet()) { graph.data.pairingDao.allowed(it, active) }
                streams.mapValues { (_, list) -> StreamOrder.present(list.filter { it.channelKey in allowed }, priority) }
            }.flowOn(io)
        }

    override val pendingGame: StateFlow<String?> get() = graph.sport.pendingGame

    override val reminders: Flow<Set<String>> get() = graph.reminders.ids

    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override fun consumePendingGame() {
        graph.sport.pendingGame.value = null
    }

    /**
     * SPORT-FR-95: kind `event`, "home – away", the competition, the start, and the first Available
     * stream's channel; without one the reminder opens the hub.
     */
    override suspend fun toggleReminder(event: SportEvent, channelKey: String?) {
        graph.reminders.toggle(
            Reminder(ReminderIds.event(event.id), ReminderKind.EVENT, event.id, channelKey, event.title, event.competition, event.startMillis, graph.clock.wallMillis()),
        )
    }

    override suspend fun decide(eventId: String, channelKey: String, decision: Decision?): Boolean = graph.sport.pairing.decide(eventId, channelKey, decision)

    // Not "for the guide": Back returns to Today with the hub open (SPORT-NAV-02).
    override fun play(channelKey: String) = ChannelStarter(graph, stack).play(channelKey, forGuide = false)

    override suspend fun incidents(eventId: String): Pair<List<Incident>, CacheState> =
        if (graph.flags.demoContent) emptyList<Incident>() to CacheState.HIT else graph.sport.repository.incidents(eventId)

    override fun now(): Long = graph.clock.wallMillis()

    override fun refresh() = graph.sport.feed.refresh()

    override fun setVisible(visible: Boolean) = graph.sport.visible(SportGraph.Viewer.TODAY, visible)

    override fun openGuide() {
        stack.push(AppRoute.Guide)
    }

    override fun openSettings() = graph.openManaged(AppRoute.Settings, stack)

    private companion object {
        /** SQLite's 999-variable limit on older Android versions. */
        const val ALLOWED_CHUNK = 500
    }
}
