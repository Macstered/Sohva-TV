package com.sohva.tv.app.navigation

import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.feature.player.ArchiveWindow
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

    /** Channel management (spec 21), over the guide. */
    data object Channels : AppRoute

    /**
     * The library manager (spec 42 §3) for [room], at [group] and [source] when a screen gave
     * them. Restored after process death at the room's remembered place (ORG-FR-01).
     */
    data class LibraryManager(val room: OrgRoom, val group: String? = null, val source: String? = null) : AppRoute

    /** A film's page (spec 40 §3), by content key. */
    data class FilmDetails(val key: String) : AppRoute

    /** A series' page (spec 40 §3), by series key. */
    data class SeriesDetails(val key: String) : AppRoute

    /**
     * A film or an episode from [startMs] (spec 30 §3.1). Never restored after process death:
     * it restores to the page underneath.
     */
    data class VodPlayer(val contentKey: String, val startMs: Long) : AppRoute

    /**
     * Playback of a channel (spec 30 §3.1): live, or with [archive] a programme from the
     * provider's archive (spec 22). [returnToGuide]: Back from the bare picture leaves to
     * `[Home, Guide]` on this channel; catch-up always pops. [recordWatched] is false for playback
     * a reminder or a notification started (spec 21 CHAN-FR-61). Never restored after process death.
     */
    data class Player(
        val channelKey: String,
        val returnToGuide: Boolean,
        val archive: ArchiveWindow? = null,
        val recordWatched: Boolean = true,
    ) : AppRoute
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
        AppRoute.Channels -> "channels"
        is AppRoute.LibraryManager -> "manager:${route.room.name}"
        is AppRoute.FilmDetails -> "film:${route.key}"
        is AppRoute.SeriesDetails -> "seriespage:${route.key}"
        is AppRoute.Player -> "player"
        is AppRoute.VodPlayer -> "player"
    }

    override fun decode(value: String): AppRoute? = when {
        value.startsWith("film:") -> AppRoute.FilmDetails(value.removePrefix("film:"))
        value.startsWith("seriespage:") -> AppRoute.SeriesDetails(value.removePrefix("seriespage:"))
        value.startsWith("manager:") -> OrgRoom.entries.firstOrNull { it.name == value.removePrefix("manager:") }?.let { AppRoute.LibraryManager(it) }
        else -> decodePlain(value)
    }

    private fun decodePlain(value: String): AppRoute? = when (value) {
        "home" -> AppRoute.Home
        "guide" -> AppRoute.Guide
        "today" -> AppRoute.Today
        "catalogue:movies" -> AppRoute.Catalogue(CatalogueMode.MOVIES)
        "catalogue:series" -> AppRoute.Catalogue(CatalogueMode.SERIES)
        "search" -> AppRoute.Search
        "discover" -> AppRoute.Discover
        "profiles" -> AppRoute.ProfilePicker
        "settings" -> AppRoute.Settings
        "channels" -> AppRoute.Channels
        // A playback route restores to the screen underneath it (spec 01 §4.4).
        else -> null
    }
}

/**
 * The stack a cold start opens (spec 01 SHELL-FR-11..12): Last channel plays [lastChannel] over
 * `[Home, Guide]`; without one it opens the guide, which is also the player's fallback when the
 * channel is gone.
 */
fun startRoutes(screen: StartupScreen, lastChannel: String? = null): List<AppRoute> = when (screen) {
    StartupScreen.HOME -> listOf(AppRoute.Home)
    StartupScreen.GUIDE -> listOf(AppRoute.Home, AppRoute.Guide)
    StartupScreen.LAST_CHANNEL -> listOfNotNull(AppRoute.Home, AppRoute.Guide, lastChannel?.let { AppRoute.Player(it, returnToGuide = true) })
}
