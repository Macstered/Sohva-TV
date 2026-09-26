package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.IncidentKind
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.feature.sport.feed.FeedState
import com.sohva.tv.feature.sport.provider.CacheState
import com.sohva.tv.feature.sport.today.TodayEnvironment
import com.sohva.tv.feature.sport.today.TodayModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 60 SPORT-NAV-04 (the waiting game) and SPORT-FR-71, -91 (match events) on the Today model. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun game(id: Int, sport: SportType = SportType.FOOTBALL) = SportEvent(
        "api-sports:${sport.provider}:$id", sport, "39", "Premier League", null, Side("Northbridge", null), Side("Harbor", null),
        0, 20 * 60, EventStatus.LIVE, "1 – 0", null, "30′",
    )

    private class Env(private val scope: TestScope, override val format: CoroutineDispatcher) : TodayEnvironment {
        override val feed = MutableStateFlow(FeedState())
        override val follows: Flow<SportFollows> = flowOf(SportFollows.DEFAULT)
        override val favourites: Flow<Set<String>> = flowOf(emptySet())
        override val streams = MutableStateFlow<Map<String, List<StreamMatch>>>(emptyMap())
        override val pendingGame = MutableStateFlow<String?>(null)
        override val reminders: Flow<Set<String>> = flowOf(emptySet())
        var requests = 0
        var failing = false
        var answer = CacheState.MISS

        override fun now(): Long = scope.testScheduler.currentTime
        override fun refresh() = Unit
        override fun setVisible(visible: Boolean) = Unit
        override fun consumePendingGame() {
            pendingGame.value = null
        }
        override suspend fun toggleReminder(event: SportEvent, channelKey: String?) = Unit
        override suspend fun decide(eventId: String, channelKey: String, decision: Decision?): Boolean = true
        override fun play(channelKey: String) = Unit
        override suspend fun incidents(eventId: String): Pair<List<Incident>, CacheState> {
            requests++
            if (failing) throw SportsException(SportsProblem.UNAVAILABLE)
            return listOf(Incident("$eventId:incident:0", 12, 0, IncidentKind.GOAL, "Normal Goal", null, "Northbridge", "A. Scorer", null)) to answer
        }
        override fun openGuide() = Unit
        override fun openSettings() = Unit
    }

    private fun TestScope.setUp(): Pair<Env, TodayModel> {
        val env = Env(this, dispatcher)
        return env to TodayModel(env)
    }

    @Test
    fun aWaitingGameOpensTheHubOnceTheListHoldsIt() = runTest(dispatcher) {
        val (env, model) = setUp()
        env.pendingGame.value = game(1).id
        env.feed.value = FeedState(loading = true)
        runCurrent()
        assertNull("not in the list yet", model.hub.value)
        env.feed.value = FeedState(events = listOf(game(2), game(1)), complete = true)
        runCurrent()
        assertEquals(game(1).id, model.hub.value)
        assertEquals(game(1), model.hubEvent.value)
        assertNull("consumed", env.pendingGame.value)
    }

    @Test
    fun aCompleteLoadWithoutTheGameDropsTheRequest() = runTest(dispatcher) {
        val (env, model) = setUp()
        env.pendingGame.value = game(9).id
        env.feed.value = FeedState(events = listOf(game(1)), complete = true)
        runCurrent()
        assertNull(env.pendingGame.value)
        // A later refresh that holds it never opens the hub by surprise (SPORT-NAV-04 rebuild).
        env.feed.value = FeedState(events = listOf(game(1), game(9)), complete = true)
        runCurrent()
        assertNull(model.hub.value)
    }

    @Test
    fun theHubFollowsTheGameAndLosesItWhenARefreshDropsIt() = runTest(dispatcher) {
        val (env, model) = setUp()
        env.feed.value = FeedState(events = listOf(game(1)), complete = true)
        model.openHub(game(1))
        runCurrent()
        env.feed.value = FeedState(events = listOf(game(1).copy(score = "2 – 0")), complete = true)
        runCurrent()
        assertEquals("2 – 0", model.hubEvent.value?.score)
        env.feed.value = FeedState(events = emptyList(), complete = true)
        runCurrent()
        assertNull("the screen closes the hub on this", model.hubEvent.value)
    }

    @Test
    fun matchEventsLoadOnceWithinTheirFreshnessAndKeepTheListAfterAFailure() = runTest(dispatcher) {
        val (env, model) = setUp()
        model.loadMatchEvents(game(1))
        runCurrent()
        assertEquals(1, model.matchEvents.value.incidents?.size)
        model.loadMatchEvents(game(1))
        runCurrent()
        assertEquals("fresh: no second request", 1, env.requests)
        env.failing = true
        model.loadMatchEvents(game(1), force = true)
        runCurrent()
        val s = model.matchEvents.value
        assertEquals(2, env.requests)
        assertTrue(s.failed)
        assertEquals("the old list stays", 1, s.incidents?.size)
        // Only football has match events (SPORT-FR-90).
        model.loadMatchEvents(game(2, SportType.ICE_HOCKEY))
        runCurrent()
        assertEquals(2, env.requests)
    }

    @Test
    fun aStaleAnswerIsMarkedCachedAndAFirstFailureHasNothingToShow() = runTest(dispatcher) {
        val (env, model) = setUp()
        env.answer = CacheState.STALE
        model.loadMatchEvents(game(1))
        runCurrent()
        assertTrue(model.matchEvents.value.cached)
        env.failing = true
        model.loadMatchEvents(game(3))
        runCurrent()
        val s = model.matchEvents.value
        assertEquals(game(3).id, s.eventId)
        assertNull(s.incidents)
        assertTrue(s.failed)
        assertFalse(s.loading)
    }

    private fun stream(event: SportEvent, channel: String, score: Int) = StreamMatch(
        event.id, channel, "Channel $channel", "p", "T", MatchSource.GUIDE, 0, 0, true, score, Confidence.AVAILABLE,
    )

    @Test
    fun theHubKeepsItsStreamOrderWhileOpen() = runTest(dispatcher) {
        val (env, model) = setUp()
        val g = game(1)
        env.feed.value = FeedState(events = listOf(g), complete = true)
        env.streams.value = mapOf(g.id to listOf(stream(g, "a", 100), stream(g, "b", 90)))
        model.openHub(g)
        runCurrent()
        assertEquals(listOf("a", "b"), model.hubStreams.value.map { it.channelKey })
        // A decision re-sorts the list; the open hub keeps its rows where they were and appends new ones.
        env.streams.value = mapOf(g.id to listOf(stream(g, "c", 95), stream(g, "b", 90).withDecision(Decision.REJECTED), stream(g, "a", 100)))
        runCurrent()
        assertEquals(listOf("a", "b", "c"), model.hubStreams.value.map { it.channelKey })
        env.streams.value = mapOf(g.id to listOf(stream(g, "c", 95), stream(g, "a", 100)))
        runCurrent()
        assertEquals(listOf("a", "c"), model.hubStreams.value.map { it.channelKey })
        // Reopening applies the current order.
        model.closeHub()
        runCurrent()
        model.openHub(g)
        runCurrent()
        assertEquals(listOf("c", "a"), model.hubStreams.value.map { it.channelKey })
    }
}
