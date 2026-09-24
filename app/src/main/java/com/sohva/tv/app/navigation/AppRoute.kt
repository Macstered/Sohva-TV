package com.sohva.tv.app.navigation

import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.ui.design.navigation.RouteCodec

/**
 * The app's destinations (spec 01 §3.2). Routes carry ids only, never records, so the stack can
 * be saved as text. Milestones add their routes and arguments here.
 */
sealed interface AppRoute {
    data object Home : AppRoute
    data object Guide : AppRoute
    data object Today : AppRoute
    data class Catalogue(val mode: CatalogueMode) : AppRoute
    data object Search : AppRoute
    data object Discover : AppRoute
    data object ProfilePicker : AppRoute
    data object Settings : AppRoute

    /**
     * Live playback of a channel (spec 30 §3.1). [returnToGuide]: Back from the bare picture
     * leaves to `[Home, Guide]` on this channel. Never restored after process death (spec 01).
     */
    data class Player(val channelKey: String, val returnToGuide: Boolean) : AppRoute
}

enum class CatalogueMode { MOVIES, SERIES }

object AppRouteCodec : RouteCodec<AppRoute> {
    override fun encode(route: AppRoute): String = when (route) {
        AppRoute.Home -> "home"
        AppRoute.Guide -> "guide"
        AppRoute.Today -> "today"
        is AppRoute.Catalogue -> "catalogue:${route.mode.name.lowercase()}"
        AppRoute.Search -> "search"
        AppRoute.Discover -> "discover"
        AppRoute.ProfilePicker -> "profiles"
        AppRoute.Settings -> "settings"
        is AppRoute.Player -> "player"
    }

    override fun decode(value: String): AppRoute? = when (value) {
        "home" -> AppRoute.Home
        "guide" -> AppRoute.Guide
        "today" -> AppRoute.Today
        "catalogue:movies" -> AppRoute.Catalogue(CatalogueMode.MOVIES)
        "catalogue:series" -> AppRoute.Catalogue(CatalogueMode.SERIES)
        "search" -> AppRoute.Search
        "discover" -> AppRoute.Discover
        "profiles" -> AppRoute.ProfilePicker
        "settings" -> AppRoute.Settings
        // A playback route restores to the screen underneath it (spec 01 §4.4).
        else -> null
    }
}

/**
 * The stack a cold start opens (spec 01 SHELL-FR-11..12). Last channel needs the channel store
 * (M2); until then it opens the guide, which is also its fallback for a missing channel.
 */
fun startRoutes(screen: StartupScreen): List<AppRoute> = when (screen) {
    StartupScreen.HOME -> listOf(AppRoute.Home)
    StartupScreen.GUIDE, StartupScreen.LAST_CHANNEL -> listOf(AppRoute.Home, AppRoute.Guide)
}
