package com.sohva.tv.app

import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.navigation.AppRouteCodec
import com.sohva.tv.app.navigation.CatalogueMode
import com.sohva.tv.app.navigation.startRoutes
import com.sohva.tv.app.shell.railItems
import com.sohva.tv.core.model.BuildKind
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun everyRouteSurvivesTheCodec() {
        val routes = listOf(
            AppRoute.Home, AppRoute.Guide, AppRoute.Today, AppRoute.Catalogue(CatalogueMode.MOVIES),
            AppRoute.Catalogue(CatalogueMode.SERIES), AppRoute.Search, AppRoute.Discover, AppRoute.ProfilePicker,
            AppRoute.Settings,
        )
        routes.forEach { assertEquals(it, AppRouteCodec.decode(AppRouteCodec.encode(it))) }
        assertNull(AppRouteCodec.decode("route-of-a-newer-build"))
    }

    @Test
    fun startScreenDecidesTheFirstStack() {
        assertEquals(listOf(AppRoute.Home), startRoutes(StartupScreen.HOME))
        assertEquals(listOf(AppRoute.Home, AppRoute.Guide), startRoutes(StartupScreen.GUIDE))
        // Until channels exist (M2) the last channel falls back to the guide, as for a missing channel.
        assertEquals(listOf(AppRoute.Home, AppRoute.Guide), startRoutes(StartupScreen.LAST_CHANNEL))
    }

    @Test
    fun discoverIsOnTheRailOnlyWhereTheBuildAllowsIt() {
        assertTrue(RailItem.DISCOVER in railItems(FeatureFlags.resolve(BuildKind.RELEASE, false)))
        assertFalse(RailItem.DISCOVER in railItems(FeatureFlags.resolve(BuildKind.DEMO, false)))
        assertFalse(RailItem.PROFILES in railItems(FeatureFlags.resolve(BuildKind.DEBUG, false)))
    }
}
