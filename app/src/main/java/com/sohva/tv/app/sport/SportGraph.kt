package com.sohva.tv.app.sport

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildInfo
import com.sohva.tv.core.model.BuildKind
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.feature.sport.feed.SportFeed
import com.sohva.tv.feature.sport.pairing.PairingCache
import com.sohva.tv.feature.sport.pairing.StreamPairing
import com.sohva.tv.feature.sport.provider.SportsHttp
import com.sohva.tv.feature.sport.provider.SportsRepository
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.TodayRules
import com.sohva.tv.feature.player.ScoreTickerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl

/**
 * Sohva Sport's part of the graph (spec 60): nothing is built until first use, which the start
 * sequence places after Home's first read (SPORT-FR-29, spec 60 §9 "Start-up"). The client uses the
 * app's shared HTTP client (spec 72 ABOUT-NFR-06 rule for every service).
 */
class SportGraph(private val graph: AppGraph) {
    /** Device tests answer from a local server; read at every request (production: API-Sports' hosts). */
    @Volatile var testHosts: ((SportType) -> HttpUrl)? = null

    val repository: SportsRepository by lazy {
        val http = SportsHttp(graph.sync.http.client) { sport -> testHosts?.invoke(sport) ?: SportsHttp.productionHost(sport) }
        SportsRepository(
            graph.data.sportDao, http, { graph.data.serviceKeys.apiSports() },
            graph.clock, graph.dispatchers.io, graph.diagnostics,
        )
    }

    /**
     * A game to open in the hub once Today's list holds it (SPORT-NAV-04): from Home, a reminder or
     * a notification; dropped after the first complete load without it (rebuild).
     */
    val pendingGame: kotlinx.coroutines.flow.MutableStateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null)

    fun openGame(eventId: String) {
        pendingGame.value = eventId
    }

    /** What keeps the feed polling (SPORT-FR-27): Today on screen, or the score ticker over the player. */
    enum class Viewer { TODAY, TICKER }

    private val viewers = java.util.EnumSet.noneOf(Viewer::class.java)

    /** [viewer] is on screen with the app in front, or no longer; the feed polls while any is. */
    fun visible(viewer: Viewer, on: Boolean) {
        val (any, today) = synchronized(viewers) {
            if (on) viewers += viewer else viewers -= viewer
            viewers.isNotEmpty() to (Viewer.TODAY in viewers)
        }
        feed.setVisible(any)
        // Only Today reads streams: pairing never runs under the player or the ticker (§9 rule).
        pairing.setActive(today)
    }

    /** The player's score ticker (spec 30 §4.20, SPORT-FR-99); its switch lasts for the app session. */
    val ticker: ScoreTickerSource by lazy { Ticker() }

    private inner class Ticker : ScoreTickerSource {
        private val on = MutableStateFlow(false)
        override val shown: StateFlow<Boolean> = on.asStateFlow()

        // Recomputed when the games change and on each minute, so the three-hour window moves (rebuild).
        private val minutes: Flow<Long> = flow {
            while (true) {
                val now = graph.clock.wallMillis()
                emit(now)
                delay(MINUTE - now % MINUTE)
            }
        }
        override val games: Flow<List<SportEvent>> =
            combine(feed.state, minutes) { state, now -> TodayRules.ticker(state.events, now) }.distinctUntilChanged().flowOn(graph.dispatchers.ui)
        override val zoneId: Flow<String> = feed.state.map { it.zoneId }.distinctUntilChanged()

        /** Turning it on with nothing loaded refreshes once (PLAY-FR-121). */
        override fun toggle() {
            on.value = !on.value
            if (on.value && feed.state.value.events.isEmpty()) feed.refresh()
        }

        override fun setVisible(visible: Boolean) = visible(Viewer.TICKER, visible)
    }

    /**
     * Stream pairing (spec 60 §4.10) on the background-priority bulk thread, fed by today's games and
     * by changes to the tables its generation reads.
     */
    val pairing: StreamPairing by lazy {
        StreamPairing(
            graph.data.pairingDao, graph.data.sportDao, graph.data.pairingInputChanges(), feed.state.map { it.events },
            PairingCache(java.io.File(graph.app.cacheDir, PairingCache.FILE_NAME)), graph.clock, graph.appScope, graph.dispatchers.bulk, graph.diagnostics,
        )
    }

    /** Today's games for Today, Home, Search, reminders and the ticker; the Lab build never refreshes by itself. */
    val feed: SportFeed by lazy {
        // The demo build shows fictional games and asks no service (SPORT-57).
        val days = if (graph.flags.demoContent) com.sohva.tv.feature.sport.provider.DemoSportDays(graph.clock) else repository
        SportFeed(
            days, graph.data.preferences.sportFollows, graph.appZone, graph.clock, graph.appScope, graph.diagnostics,
            automatic = BuildInfo.KIND != BuildKind.LAB,
        )
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
