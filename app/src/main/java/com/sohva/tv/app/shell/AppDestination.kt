package com.sohva.tv.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.settings.AppSettingsServices
import com.sohva.tv.app.navigation.CatalogueMode
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.feature.home.HomeScreen
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.settings.SettingsModel
import com.sohva.tv.feature.settings.SettingsScreen
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.navigation.BackStack

/** The route table: the only place that knows every screen (plan/03 §4.3 rule 2). */
@Composable
fun AppDestination(route: AppRoute, stack: BackStack<AppRoute>, graph: AppGraph) {
    val flags = graph.flags
    val back = { stack.pop() }
    when (route) {
        AppRoute.Home -> {
            val items = remember(flags) { railItems(flags) }
            HomeScreen(items, onOpen = { stack.push(it.route()) })
        }
        AppRoute.Guide -> PlaceholderScreen(R.string.home_live_tv, { back() }, "screen-guide")
        AppRoute.Today -> PlaceholderScreen(R.string.home_sportmate, { back() }, "screen-today")
        is AppRoute.Catalogue -> when (route.mode) {
            CatalogueMode.MOVIES -> PlaceholderScreen(R.string.home_movies, { back() }, "screen-movies")
            CatalogueMode.SERIES -> PlaceholderScreen(R.string.home_series, { back() }, "screen-series")
        }
        AppRoute.Search -> PlaceholderScreen(R.string.home_search, { back() }, "screen-search")
        AppRoute.Discover -> PlaceholderScreen(R.string.home_discover, { back() }, "screen-discover")
        AppRoute.ProfilePicker -> PlaceholderScreen(R.string.profile_active_title, { back() }, "screen-profiles")
        AppRoute.Settings -> {
            // Scoped to this stack entry: popped with Settings (plan/03 §4.5). Accounts waits for Trakt (M6).
            val model = viewModel { SettingsModel(AppSettingsServices(graph), accounts = false) }
            SettingsScreen(model, onBack = { back() })
        }
    }
}

/**
 * Rail items for this build (spec 01 SHELL-FR-61): Discover where the build allows addons (the
 * restricted-profile check joins in M6); Who is watching only with more than one profile (M6).
 */
internal fun railItems(flags: FeatureFlags): List<RailItem> = RailItem.entries.filter { item ->
    when (item) {
        RailItem.DISCOVER -> flags.discover
        RailItem.PROFILES -> false
        else -> true
    }
}

private fun RailItem.route(): AppRoute = when (this) {
    RailItem.LIVE_TV -> AppRoute.Guide
    RailItem.SPORT -> AppRoute.Today
    RailItem.MOVIES -> AppRoute.Catalogue(CatalogueMode.MOVIES)
    RailItem.SERIES -> AppRoute.Catalogue(CatalogueMode.SERIES)
    RailItem.SEARCH -> AppRoute.Search
    RailItem.DISCOVER -> AppRoute.Discover
    RailItem.PROFILES -> AppRoute.ProfilePicker
    RailItem.SETTINGS -> AppRoute.Settings
}
