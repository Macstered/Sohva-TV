package com.sohva.tv.app.shell

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.live.AppGuideEnvironment
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.navigation.CatalogueMode
import com.sohva.tv.app.settings.AppSettingsServices
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.feature.home.HomeScreen
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.live.GuideModel
import com.sohva.tv.feature.live.GuideNavigation
import com.sohva.tv.feature.live.GuideScreen
import com.sohva.tv.feature.player.ExternalStream
import com.sohva.tv.feature.player.PlayerModel
import com.sohva.tv.feature.player.PlayerNavigation
import com.sohva.tv.feature.player.PlayerScreen
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
        AppRoute.Guide -> {
            val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT
            // Opened for the channel last played from the guide in this process (spec 20 §3.1).
            val model = viewModel { GuideModel(AppGuideEnvironment(graph, locale), graph.guideFocusChannel) }
            val navigation = remember(stack, graph) { guideNavigation(stack, graph) }
            GuideScreen(model, navigation)
        }
        is AppRoute.Player -> {
            val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT
            // One model per player session: zaps happen inside it, so the controller, the picture
            // shape and the previous channel carry across them (spec 30 PLAY-FR-23, -58, -81).
            val navigation = remember(stack, graph, route) { playerNavigation(route, stack, graph) }
            val model = viewModel { PlayerModel(graph.player.screen(locale), route.channelKey, navigation) }
            PlayerScreen(model)
        }
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

private fun guideNavigation(stack: BackStack<AppRoute>, graph: AppGraph) = object : GuideNavigation {
    override fun play(channelKey: String) {
        graph.guideFocusChannel = channelKey
        stack.push(AppRoute.Player(channelKey, returnToGuide = true))
    }

    override fun openSettings() {
        stack.push(AppRoute.Settings)
    }

    override fun notYetAvailable() {
        graph.notYetAvailable()
    }

    override fun leave() {
        stack.pop()
    }
}

/**
 * Leaving the player (spec 30 §3.2–3.3): Back from the bare picture of a guide-started live player
 * leaves to `[Home, Guide]` with the guide on the channel just watched; anything else pops. The
 * mapped Home, Guide, Sport and Guide-at-channel actions reset the stack (PLAY-FR-07).
 */
private fun playerNavigation(route: AppRoute.Player, stack: BackStack<AppRoute>, graph: AppGraph) = object : PlayerNavigation {
    override fun leave(channelKey: String) {
        if (route.returnToGuide) guideAt(channelKey) else stack.pop()
    }

    override fun guideAt(channelKey: String) {
        graph.guideFocusChannel = channelKey
        stack.resetTo(listOf(AppRoute.Home, AppRoute.Guide))
    }

    override fun home() = stack.resetTo(listOf(AppRoute.Home))

    override fun guide() = stack.resetTo(listOf(AppRoute.Home, AppRoute.Guide))

    override fun sport() = stack.resetTo(listOf(AppRoute.Home, AppRoute.Today))

    /** `ACTION_VIEW` with the address, any video type, a new task and the headers both ways players read them (PLAY-FR-115). */
    override fun openExternal(stream: ExternalStream): Throwable? = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(stream.address.toUri(), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (stream.headers.isNotEmpty()) {
            intent.putExtra("com.android.browser.headers", Bundle().apply { stream.headers.forEach { (k, v) -> putString(k, v) } })
            intent.putExtra("headers", stream.headers.flatMap { (k, v) -> listOf(k, v) }.toTypedArray())
        }
        graph.app.startActivity(intent)
    }.exceptionOrNull()
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
