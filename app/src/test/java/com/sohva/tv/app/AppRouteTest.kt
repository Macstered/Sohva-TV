package com.sohva.tv.app

import com.sohva.tv.core.model.org.OrgRoom
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
        )
        routes.forEach { assertEquals(it, AppRouteCodec.decode(AppRouteCodec.encode(it))) }
        assertNull(AppRouteCodec.decode("route-of-a-newer-build"))
    }

    /** Spec 04 PROF-FR-34: Settings and the managers come back behind the gate; gates themselves never restore. */
    @Test
    fun managedScreensRestoreBehindTheGate() {
        for (route in listOf(AppRoute.Settings, AppRoute.Channels, AppRoute.LibraryManager(OrgRoom.MOVIES))) {
            assertEquals(AppRoute.ProfileGate(null, route), AppRouteCodec.decode(AppRouteCodec.encode(route)))
        }
        assertNull(AppRouteCodec.decode(AppRouteCodec.encode(AppRoute.ProfileGate("p1", null))))
        assertNull(AppRouteCodec.decode(AppRouteCodec.encode(AppRoute.PinGate("s:c1", null, false, true, true))))
    }

    @Test
    fun startScreenDecidesTheFirstStack() {
        assertEquals(listOf(AppRoute.Home), startRoutes(StartupScreen.HOME))
        assertEquals(listOf(AppRoute.Home, AppRoute.Guide), startRoutes(StartupScreen.GUIDE))
        // Until channels exist (M2) the last channel falls back to the guide, as for a missing channel.
        assertEquals(listOf(AppRoute.Home, AppRoute.Guide), startRoutes(StartupScreen.LAST_CHANNEL))
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Guide, AppRoute.Player("s:c1", returnToGuide = true)),
            startRoutes(StartupScreen.LAST_CHANNEL, "s:c1"),
        )
    }

    @Test
    fun discoverIsOnTheRailOnlyWhereTheBuildAllowsIt() {
        assertTrue(RailItem.DISCOVER in railItems(FeatureFlags.resolve(BuildKind.RELEASE, false), severalProfiles = false, restricted = false))
        assertFalse(RailItem.DISCOVER in railItems(FeatureFlags.resolve(BuildKind.DEMO, false), severalProfiles = false, restricted = false))
        // Spec 04 PROF-FR-14, -16: never for a restricted profile; Who is watching only with two or more profiles.
        assertFalse(RailItem.DISCOVER in railItems(FeatureFlags.resolve(BuildKind.RELEASE, false), severalProfiles = true, restricted = true))
        assertFalse(RailItem.PROFILES in railItems(FeatureFlags.resolve(BuildKind.DEBUG, false), severalProfiles = false, restricted = false))
        assertTrue(RailItem.PROFILES in railItems(FeatureFlags.resolve(BuildKind.DEBUG, false), severalProfiles = true, restricted = false))
    }
}
