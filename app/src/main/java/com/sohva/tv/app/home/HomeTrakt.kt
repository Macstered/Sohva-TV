package com.sohva.tv.app.home

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.feature.home.TraktCard
import com.sohva.tv.feature.home.TraktLists
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.shelf.TraktRowSource
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home's Trakt rows and their route (spec 02 HOME-FR-22, -30…32, -48, -94; spec 51 FR-28, -30). */
internal object HomeTrakt {
    /**
     * The active profile's stored lists, read once and then served from memory, and whether its
     * first sync is still to come; empty for a restricted profile whatever is stored (HOME-FR-32).
     */
    fun lists(graph: AppGraph): Flow<TraktLists> {
        val host = graph.trakt ?: return flowOf(TraktLists.EMPTY)
        return flow {
            val profile = graph.data.profiles.activeId
            if (!host.access.allowed(profile)) {
                emit(TraktLists.EMPTY)
                return@flow
            }
            host.account(profile)
            host.shelves.read(profile, TraktShelfKind.WATCH_NEXT)
            host.shelves.read(profile, TraktShelfKind.RECOMMENDED)
            // The added rows the layout shows (HOME-FR-94), each read from the store once.
            val layout = graph.data.preferences.homeLayout(profile).first()
            val sources = layout.added.filter(layout::isShown).mapNotNull(TraktRowSource::of)
            sources.forEach { host.rowLists.read(profile, it) }
            emitAll(
                combine(host.shelves.shelves, host.accounts, host.rowLists.lists) { shelves, accounts, lists ->
                    TraktLists(
                        next = shelves[profile to TraktShelfKind.WATCH_NEXT]?.cards.orEmpty().map { card(it, next = true) },
                        recommended = shelves[profile to TraktShelfKind.RECOMMENDED]?.cards.orEmpty().map { card(it, next = false) },
                        firstSync = accounts[profile] != null && graph.traktSync?.firstSyncPending(profile) == true,
                        rows = sources.associate { s ->
                            // A watchlist shows only while the profile has its account (it is the account's).
                            val cards = if (s.account && accounts[profile] == null) null else lists[s.key(profile)]?.cards
                            s.id to cards.orEmpty().map { card(it, next = false, source = s.id) }
                        },
                    )
                },
            )
        }.distinctUntilChanged().flowOn(graph.dispatchers.io)
    }

    /** An added row's card key starts with its row, so a title in two rows keeps two focus places (HOME-FR-94). */
    private fun card(c: com.sohva.tv.feature.trakt.shelf.TraktCard, next: Boolean, source: String? = null) = TraktCard(
        key = source?.let { "$it/${c.key}" } ?: c.key, next = next, source = source, show = c.kind == TraktKind.SHOW, title = c.title, year = c.year,
        season = c.season, number = c.number, episodeTitle = c.episodeTitle, poster = c.poster, fanart = c.fanart,
        overview = c.overview, tmdb = c.ids.tmdb, imdb = c.ids.imdb,
    )

    /**
     * FR-30: the library's own details when a copy with that TMDB id exists (a series only when
     * TMDB made the match), else the Trakt title screen; pushed only while Home is on top.
     */
    fun open(graph: AppGraph, stack: BackStack<AppRoute>, card: TraktCard) {
        graph.appScope.launch(graph.dispatchers.main) {
            val library = withContext(graph.dispatchers.io) {
                val tmdb = card.tmdb ?: return@withContext null
                if (card.show) {
                    graph.data.traktLibrary.seriesFor(tmdb)?.let { AppRoute.SeriesDetails(it) }
                } else {
                    graph.data.traktLibrary.filmFor(tmdb)?.let { AppRoute.FilmDetails(it) }
                }
            }
            val route = library ?: AppRoute.TraktTitle(card.show, card.title, card.imdb, card.tmdb, card.season, card.number)
            if (stack.top.route == AppRoute.Home) stack.push(route)
        }
    }
}
