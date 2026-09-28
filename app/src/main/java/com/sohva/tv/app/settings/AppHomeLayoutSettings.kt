package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.settings.HomeLayoutServices
import com.sohva.tv.feature.settings.HomeLayoutView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Settings › Home from the app graph (spec 02 HOME-FR-90): the active profile's layout, read and
 * written off the main thread. A restricted profile, or a build without Trakt or Sohva Sport, does
 * not list the rows it can never have (HOME-FR-88).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeLayoutSettings(private val graph: AppGraph) : HomeLayoutServices {
    private val io get() = graph.dispatchers.io

    override val current: Flow<HomeLayoutView> = graph.data.profiles.household.flatMapLatest { household ->
        val active = household.active
        val restricted = active.id in graph.data.profiles.restrictedIds()
        val unavailable = buildSet {
            if (!graph.flags.trakt || restricted) addAll(listOf(HomeLayout.WATCH_NEXT, HomeLayout.RECOMMENDED))
            if (!graph.flags.sport) add(HomeLayout.SPORT)
        }
        graph.data.preferences.homeLayout(active.id).map { HomeLayoutView(active.name.takeIf { household.several }, it, unavailable) }
    }.flowOn(io)

    override suspend fun save(layout: HomeLayout) = withContext(io) {
        val profile = graph.data.profiles.activeId
        graph.data.preferences.setHomeLayout(profile, layout)
        // A Trakt row shown again is fetched now rather than at the next 15-minute cycle (HOME-FR-87).
        if (layout.isShown(HomeLayout.WATCH_NEXT) || layout.isShown(HomeLayout.RECOMMENDED)) graph.trakt?.requestSync(profile)
    }
}
