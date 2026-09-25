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
import com.sohva.tv.app.channels.AppChannelsEnvironment
import com.sohva.tv.app.library.AppLibraryEnvironment
import com.sohva.tv.app.library.AppTitleEnvironment
import com.sohva.tv.app.library.LibraryNavigation
import com.sohva.tv.app.library.TitleNavigation
import com.sohva.tv.app.live.AppGuideEnvironment
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.navigation.CatalogueMode
import com.sohva.tv.app.organize.AppManagerEnvironment
import com.sohva.tv.app.organize.ManagerNavigation
import com.sohva.tv.app.settings.AppSettingsServices
import com.sohva.tv.core.data.vod.ContentKeys
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.feature.channels.ChannelsModel
import com.sohva.tv.feature.channels.ChannelsScreen
import com.sohva.tv.feature.home.HomeScreen
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.library.FilmModel
import com.sohva.tv.feature.library.FilmPage
import com.sohva.tv.feature.library.LibraryModel
import com.sohva.tv.feature.library.LibraryScreen
import com.sohva.tv.feature.library.SeriesModel
import com.sohva.tv.feature.library.SeriesPage
import com.sohva.tv.feature.live.GuideModel
import com.sohva.tv.feature.live.GuideNavigation
import com.sohva.tv.feature.live.GuideScreen
import com.sohva.tv.feature.organize.LibraryManagerScreen
import com.sohva.tv.feature.organize.ManagerModel
import com.sohva.tv.feature.organize.ManagerStart
import com.sohva.tv.feature.player.ArchiveWindow
import com.sohva.tv.feature.player.ExternalStream
import com.sohva.tv.feature.player.PlayerModel
import com.sohva.tv.feature.player.PlayerNavigation
import com.sohva.tv.feature.player.PlayerScreen
import com.sohva.tv.feature.player.VodPlay
import com.sohva.tv.feature.settings.SettingsModel
import com.sohva.tv.feature.settings.SettingsScreen
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.launch

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
            val model = viewModel { PlayerModel(graph.player.screen(locale), route.channelKey, navigation, route.archive, route.recordWatched) }
            PlayerScreen(model)
        }
        AppRoute.Today -> PlaceholderScreen(R.string.home_sportmate, { back() }, "screen-today")
        is AppRoute.Catalogue -> {
            val room = if (route.mode == CatalogueMode.MOVIES) WallRoom.MOVIES else WallRoom.SERIES
            // Scoped to this stack entry; the browse session (graph.browseSessions) outlives it (spec 40 VOD-FR-56).
            val navigation = remember(stack, graph) { libraryNavigation(stack, graph) }
            val model = viewModel { LibraryModel(AppLibraryEnvironment(graph, room, navigation), graph.browseSessions.getValue(room)) }
            LibraryScreen(model)
        }
        AppRoute.Search -> PlaceholderScreen(R.string.home_search, { back() }, "screen-search")
        AppRoute.Discover -> PlaceholderScreen(R.string.home_discover, { back() }, "screen-discover")
        AppRoute.ProfilePicker -> PlaceholderScreen(R.string.profile_active_title, { back() }, "screen-profiles")
        AppRoute.Channels -> {
            // Scoped to this stack entry: popped with the screen (plan/03 §4.5).
            val model = viewModel { ChannelsModel(AppChannelsEnvironment(graph)) }
            ChannelsScreen(model, onBack = { back() })
        }
        is AppRoute.LibraryManager -> {
            // Scoped to this stack entry: popped with the screen (plan/03 §4.5).
            val navigation = remember(stack) { managerNavigation(stack) }
            val model = viewModel { ManagerModel(AppManagerEnvironment(graph, navigation), ManagerStart(route.room, route.group, route.source)) }
            LibraryManagerScreen(model)
        }
        is AppRoute.FilmDetails -> {
            val play = remember(stack, graph) { vodStarter(stack, graph) }
            val model = viewModel { FilmModel(AppTitleEnvironment(graph, play), route.key) }
            FilmPage(model)
        }
        is AppRoute.SeriesDetails -> {
            val play = remember(stack, graph) { vodStarter(stack, graph) }
            val model = viewModel { SeriesModel(AppTitleEnvironment(graph, play), route.key) }
            SeriesPage(model)
        }
        is AppRoute.VodPlayer -> {
            val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT
            val navigation = remember(stack, graph, route) { vodNavigation(route, stack, graph) }
            val model = viewModel {
                PlayerModel(graph.player.screen(locale), route.contentKey, navigation, vod = VodPlay(route.contentKey, route.startMs))
            }
            PlayerScreen(model)
        }
        AppRoute.Settings -> {
            // Scoped to this stack entry: popped with Settings (plan/03 §4.5). Accounts waits for Trakt (M6).
            val openManager: () -> Unit = remember(stack) { { stack.push(AppRoute.LibraryManager(OrgRoom.LIVE)) } }
            val model = viewModel { SettingsModel(AppSettingsServices(graph, openManager), accounts = false) }
            SettingsScreen(model, onBack = { back() })
        }
    }
}

private fun guideNavigation(stack: BackStack<AppRoute>, graph: AppGraph) = object : GuideNavigation {
    override fun play(channelKey: String) {
        graph.guideFocusChannel = channelKey
        stack.push(AppRoute.Player(channelKey, returnToGuide = true))
    }

    /** Catch-up opens the player over the guide; Back pops to it (spec 22 §3). */
    override fun playArchive(channelKey: String, start: Long, stop: Long) {
        graph.guideFocusChannel = channelKey
        stack.push(AppRoute.Player(channelKey, returnToGuide = false, archive = ArchiveWindow(start, stop)))
    }

    override fun openSettings() {
        stack.push(AppRoute.Settings)
    }

    override fun openChannels() {
        stack.push(AppRoute.Channels)
    }

    // The options sheet stays open underneath, so Back returns to it (GUIDE-FR-95).
    override fun openManager(group: String?, source: String?) {
        stack.push(AppRoute.LibraryManager(OrgRoom.LIVE, group, source))
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

    // Live and catch-up have no completion (spec 30 Q-06).
    override fun finished(contentKey: String) = Unit

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
 * Details pages open the VOD player over themselves (spec 30 §3.1), a Similar card another film
 * page on top (spec 40 §3), and "Source: …" the record's web page in whatever the TV has.
 */
private fun vodStarter(stack: BackStack<AppRoute>, graph: AppGraph) = object : TitleNavigation {
    override fun play(key: String, startMs: Long) {
        stack.push(AppRoute.VodPlayer(key, startMs))
    }

    override fun openFilm(key: String) {
        stack.push(AppRoute.FilmDetails(key))
    }

    override fun openUrl(url: String) {
        // A TV without a browser has nothing to open it with; the page stays as it was (VOD-FR-66).
        runCatching { graph.app.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * The VOD player's exits (spec 30 §3.2, PLAY-FR-132): Back pops to the page that started it. At
 * the end, the next episode replaces the player when "Continue to the next episode" is on;
 * otherwise the player pops and, when the screen underneath is not a details page, the title's page
 * is pushed. A completion after the viewer left changes nothing (SHELL-FR-29).
 */
private fun vodNavigation(route: AppRoute.VodPlayer, stack: BackStack<AppRoute>, graph: AppGraph) = object : PlayerNavigation {
    override fun leave(channelKey: String) {
        stack.pop()
    }

    override fun guideAt(channelKey: String) = Unit

    override fun home() = stack.resetTo(listOf(AppRoute.Home))

    override fun guide() = stack.resetTo(listOf(AppRoute.Home, AppRoute.Guide))

    override fun sport() = stack.resetTo(listOf(AppRoute.Home, AppRoute.Today))

    override fun openExternal(stream: ExternalStream): Throwable? = UnsupportedOperationException("live only")

    override fun finished(contentKey: String) {
        graph.appScope.launch(graph.dispatchers.main) {
            if (stack.top.route != route) return@launch
            val episode = ContentKeys.isEpisode(contentKey)
            val next = if (episode && graph.data.preferences.autoPlayNextEpisode()) graph.data.titles.nextEpisode(contentKey) else null
            val details = if (episode) graph.data.titles.seriesOfEpisode(contentKey)?.let(AppRoute::SeriesDetails) else AppRoute.FilmDetails(contentKey)
            if (stack.top.route != route) return@launch
            if (next != null) {
                stack.replaceTop(AppRoute.VodPlayer(next, 0))
                return@launch
            }
            stack.pop()
            val under = stack.top.route
            if (details != null && under !is AppRoute.FilmDetails && under !is AppRoute.SeriesDetails) stack.push(details)
        }
    }
}

/** "Advanced" opens channel management over the manager; Back returns to it (spec 42 §3). */
private fun managerNavigation(stack: BackStack<AppRoute>) = object : ManagerNavigation {
    override fun openAdvanced() {
        stack.push(AppRoute.Channels)
    }

    override fun leave() {
        stack.pop()
    }
}

private fun libraryNavigation(stack: BackStack<AppRoute>, graph: AppGraph) = object : LibraryNavigation {
    override fun open(room: WallRoom, item: WallItem) {
        stack.push(if (room == WallRoom.MOVIES) AppRoute.FilmDetails(item.row.key) else AppRoute.SeriesDetails(item.row.key))
    }

    override fun openManager(room: WallRoom, group: String?) {
        stack.push(AppRoute.LibraryManager(if (room == WallRoom.MOVIES) OrgRoom.MOVIES else OrgRoom.SERIES, group))
    }

    override fun leave() {
        stack.pop()
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
