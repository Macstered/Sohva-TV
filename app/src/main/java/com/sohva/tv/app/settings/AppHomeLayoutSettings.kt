package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.settings.HomeLayoutServices
import com.sohva.tv.feature.settings.HomeLayoutView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Settings › Home from the app graph (spec 02 HOME-FR-90): the active profile's layout, read and
 * written off the main thread. A restricted profile, or a build without Trakt or Sohva Sport, does
 * not list the rows it can never have (HOME-FR-88). Trakt rows can be added when Trakt is there;
 * watchlists only while the profile has an account (HOME-FR-94).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeLayoutSettings(private val graph: AppGraph) : HomeLayoutServices {
    private val io get() = graph.dispatchers.io

    override val current: Flow<HomeLayoutView> = graph.data.profiles.household.flatMapLatest { household ->
        val active = household.active
        val restricted = active.id in graph.data.profiles.restrictedIds()
        val host = graph.trakt?.takeIf { graph.flags.trakt && !restricted && it.configured }
        val unavailable = buildSet {
            if (!graph.flags.trakt || restricted) addAll(listOf(HomeLayout.WATCH_NEXT, HomeLayout.RECOMMENDED))
            if (!graph.flags.sport) add(HomeLayout.SPORT)
        }
        val account: Flow<Boolean> = if (host == null) {
            flowOf(false)
        } else {
            flow {
                host.account(active.id)
                emitAll(host.accounts.map { it[active.id] != null })
            }.distinctUntilChanged()
        }
        combine(graph.data.preferences.homeLayout(active.id), account) { layout, signedIn ->
            val addable = if (host == null) emptyList() else HomeLayout.ADDABLE.filter { signedIn || it !in HomeLayout.NEEDS_ACCOUNT }
            HomeLayoutView(active.name.takeIf { household.several }, layout, unavailable + layout.added.filter { it !in addable }, addable)
        }
    }.flowOn(io)

    override suspend fun save(layout: HomeLayout) = withContext(io) {
        val profile = graph.data.profiles.activeId
        graph.data.preferences.setHomeLayout(profile, layout)
        // A Trakt row shown again or just added is fetched now rather than at the next 15-minute cycle (HOME-FR-87, -94).
        val traktShown = layout.isShown(HomeLayout.WATCH_NEXT) || layout.isShown(HomeLayout.RECOMMENDED) || layout.added.any(layout::isShown)
        if (traktShown) graph.trakt?.requestSync(profile)
    }
}
