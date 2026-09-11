package com.streammate.tv.addons

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.streammate.tv.app.*
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Ordinary debug app: real Home route and ordinary background policy, never Lab or stable data. */
class AddonIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(compose)
    private val app get() = compose.activity.application as StreamMateApplication

    @Before fun homeReady() {
        compose.awaitUntil(15_000) { compose.onAllNodesWithTag("home-hero-primary").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("com.streammate.tv.debug", app.packageName)
        assertFalse(app.container.runtimePolicy.isLab)
        assertTrue(app.container.runtimePolicy.automaticMaintenanceAllowed)
        assertTrue(app.container.runtimePolicy.automaticSportsRefreshAllowed)
        assertTrue(app.container.runtimePolicy.remindersAllowed)
    }

    @Test fun discoverOpensFromRegularHomeAndBackReturnsHome() {
        compose.onNodeWithTag("home-discover").assertIsDisplayed().performClick()
        compose.awaitUntil(10_000) { compose.onAllNodesWithTag("addon-shelves").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addon-manage").assertIsDisplayed().performClick()
        compose.awaitUntil(10_000) { compose.onAllNodesWithTag("addon-manager-list").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("addon-shelves").assertIsDisplayed()
        // Settings restores focus to the rail. Back first collapses the rail.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("addon-shelves").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.awaitUntil(10_000) { compose.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-live").assertIsDisplayed()
        compose.onNodeWithTag("home-movies").assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun homeDoesNotFetchInstalledAddonsUntilDiscoverIsEntered(): Unit = runBlocking {
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody(
                """{"metas":[{"id":"fixture.movie","type":"movie","name":"Integration fixture"}]}"""))
            val addon = host.store.install(profile, AddonEndpoint.parse(server.url("/Fixture/manifest.json").toString(), true),
                """{"id":"test.integration","version":"1","name":"Integration fixture","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"fixture","type":"movie","name":"Integration catalog"}]}""")
            try {
                compose.waitForIdle()
                assertEquals("Home must not prefetch installed addons", 0, server.requestCount)
                compose.onNodeWithTag("home-discover").performClick()
                compose.awaitUntil(10_000) { compose.onAllNodesWithTag("addon-landing-card").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, server.requestCount)
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithTag("home-hero-primary").assertIsDisplayed()
                compose.onNodeWithTag("home-discover").performClick()
                compose.awaitUntil(10_000) { compose.onAllNodesWithTag("addon-landing-card").fetchSemanticsNodes().isNotEmpty() }
                assertEquals("Returning must reuse the saved catalog", 1, server.requestCount)
                assertTrue(host.manager.list(profile).any { it.installationId == addon.installationId })
                assertFalse(host.access.allowed("other-profile"))
            } finally {
                host.store.remove(profile, addon.installationId)
            }
        }
    }

    @Test fun restrictionsHideHomeEntryAndDenyDirectUiAndStorageAccess(): Unit = runBlocking {
        val preferences = app.container.preferencesRepository
        val original = preferences.preferences.first()
        try {
            preferences.setAllowedGroups(original.activeProfileId, LibraryRoom.MOVIES, setOf("fixture-only"))
            compose.awaitUntil(10_000) { compose.onAllNodesWithTag("home-discover").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("home-movies").assertIsDisplayed()
            val host = AddonHost.get(app, app.container)
            try { host.manager.list(original.activeProfileId); fail("Restricted access was allowed") }
            catch (error: AddonException) { assertEquals(AddonFailure.ACCESS_DENIED, error.failure) }
            compose.runOnUiThread { compose.activity.setContent {
                StreamMateTheme { DiscoverAddonFeature().Screen(app.container, {}) }
            } }
            compose.awaitUntil(10_000) { compose.onAllNodesWithText("Addons are unavailable for restricted profiles.").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithTag("addon-shelves").assertCountEquals(0)
        } finally {
            preferences.setAllowedGroups(original.activeProfileId, LibraryRoom.MOVIES, original.activeRestriction.movies)
        }
    }
}
