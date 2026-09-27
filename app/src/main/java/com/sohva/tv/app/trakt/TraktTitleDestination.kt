package com.sohva.tv.app.trakt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.ui.TitleRequest
import com.sohva.tv.feature.trakt.ui.TraktTitleScreen
import com.sohva.tv.ui.design.navigation.BackStack

/**
 * FR-30 step 2: the title's ids against the viewer's addons, IMDb first, then `tmdb:<n>`, through
 * Discover's details route (enabled addons serving `meta` for the key, in priority order). The
 * first answer replaces this screen with its Discover page, on the named episode when one matches;
 * none, or no Discover for this profile, shows "not available".
 */
@Composable
fun TraktTitleDestination(route: AppRoute.TraktTitle, stack: BackStack<AppRoute>, graph: AppGraph, back: () -> Unit) {
    var missing by rememberSaveable(route) { mutableStateOf(false) }
    TraktTitleScreen(route.title, missing, back)
    LaunchedEffect(route) {
        if (missing) return@LaunchedEffect
        val found = find(graph, route)
        // Applied only while this screen is still on top (spec 01 SHELL-FR-17).
        if (stack.top.route != route) return@LaunchedEffect
        if (found == null) missing = true else stack.replaceTop(found)
    }
}

private suspend fun find(graph: AppGraph, route: AppRoute.TraktTitle): AppRoute.DiscoverTitle? {
    val host = graph.discover ?: return null
    val profile = graph.data.profiles.activeId
    if (!host.access.allowed(profile)) return null
    val type = if (route.show) "series" else "movie"
    for (id in listOfNotNull(route.imdb, route.tmdb?.let { "tmdb:$it" })) {
        val details = try {
            host.browser.details(profile, null, type, id).value
        } catch (e: AddonException) {
            if (e.failure.revocation) return null
            continue
        }
        host.keepTitle(TitleRequest(null, details.preview))
        val video = if (route.season != null && route.number != null) {
            details.videos.firstOrNull { it.season == route.season && it.episode == route.number }?.id
        } else {
            null
        }
        return AppRoute.DiscoverTitle(null, type, details.preview.id, video)
    }
    return null
}
