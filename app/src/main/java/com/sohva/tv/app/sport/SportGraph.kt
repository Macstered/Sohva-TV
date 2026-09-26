package com.sohva.tv.app.sport

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildInfo
import com.sohva.tv.core.model.BuildKind
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.feature.sport.feed.SportFeed
import com.sohva.tv.feature.sport.provider.SportsHttp
import com.sohva.tv.feature.sport.provider.SportsRepository
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

    /** Today's games for Today, Home, Search, reminders and the ticker; the Lab build never refreshes by itself. */
    val feed: SportFeed by lazy {
        SportFeed(
            repository, graph.data.preferences.sportFollows, graph.appZone, graph.clock, graph.appScope, graph.diagnostics,
            automatic = BuildInfo.KIND != BuildKind.LAB,
        )
    }
}
