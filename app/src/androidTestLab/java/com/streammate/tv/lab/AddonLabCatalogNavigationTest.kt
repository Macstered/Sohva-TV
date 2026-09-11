package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic emulator fixtures only: never writes the user's catalog order. */
class AddonLabCatalogNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun app(): StreamMateApplication {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        return (compose.activity.application as StreamMateApplication).also { check(it.packageName == "com.streammate.tv.lab") }
    }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun manifest(count: Int = 4) = """{"id":"test.navigation","version":"1","name":"Synthetic provider","types":["lab.navigation"],"resources":["catalog"],"catalogs":[${(1..count).joinToString(",") { """{"id":"catalog$it","type":"lab.navigation","name":"Catalog $it","pageSize":24,"extra":[{"name":"skip"}]}""" }}]}"""

    @Test fun bottomAutomaticallyPagesAndErrorsNeedExplicitRetry(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        MockWebServer().use { server ->
            val skips = CopyOnWriteArrayList<Int>(); val second = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val skip = Regex("skip=(\\d+)").find(request.path!!)?.groupValues?.get(1)?.toInt() ?: 0
                    skips += skip
                    if (skip == 24 && second.incrementAndGet() == 1) return MockResponse().setResponseCode(503)
                    // Third response repeats the previous page: paging must stop without duplicates.
                    val start = if (skip >= 24) 24 else 0
                    return MockResponse().setBody("""{"metas":[${(start + 1..start + 24).joinToString(",") { """{"id":"tile$it","type":"lab.navigation","name":"Title $it"}""" }}]}""")
                }
            }
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), manifest())
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonCatalogScreen(host, preferences.activeProfileId, installed, installed.manifest.catalogs.first(), {}, modifier)
                    }
                } } }
                waitFor("addon-media-card")
                assertEquals(listOf(0), skips.toList())
                compose.onNodeWithText("Load more").assertDoesNotExist()
                compose.onNodeWithTag("addon-catalog-grid").performScrollToIndex(23)
                waitFor("addon-page-retry")
                compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
                assertEquals(listOf(0, 24), skips.toList())
                compose.onNodeWithTag("addon-page-retry").performScrollTo().performClick()
                compose.waitUntil(10_000) { second.get() == 2 && compose.onAllNodes(hasTestTag("addon-page-retry")).fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithTag("addon-catalog-grid").performScrollToIndex(47)
                compose.waitUntil(10_000) { 48 in skips && compose.onAllNodes(hasTestTag("addon-page-loading")).fetchSemanticsNodes().isEmpty() }
                compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
                assertEquals(listOf(0, 24, 24, 48), skips.toList())
                compose.onNodeWithText("Title 48").assertIsDisplayed()
                compose.onNodeWithText("Load more").assertDoesNotExist()
                screenshot("addon-auto-paging.png")
            } finally { host.store.remove(preferences.activeProfileId, installed.installationId) }
        }
    }

    @Test fun settingsSectionsAndRemoteBackReturnToCatalogLanding(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setBody("""{"metas":[]}""") }
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), manifest())
            val escaped = AtomicInteger()
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    BackHandler { escaped.incrementAndGet() }
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, { escaped.incrementAndGet() }, modifier, initiallyManage = true, loadInstallations = { listOf(installed) })
                    }
                } } }
                waitFor("addon-manager-list")
                screenshot("addon-settings-installed.png")
                compose.onNodeWithTag("addon-settings-setup").performClick()
                compose.onNodeWithTag("addon-import").assertIsDisplayed()
                compose.onNodeWithTag("addon-url").assertExists()
                screenshot("addon-settings-setup.png")
                compose.onNodeWithTag("addon-settings-subtitles").performClick()
                compose.onNodeWithTag("addon-subtitle-languages").assertIsDisplayed()
                screenshot("addon-settings-subtitles.png")
                back()
                waitFor("addon-discover")
                compose.onNodeWithTag("addon-manager-list").assertDoesNotExist()
                assertEquals(0, escaped.get())
                compose.onNodeWithTag("addon-manage").performClick()
                waitFor("addon-manager-list")
                compose.onNodeWithTag("addon-catalog-order").performClick()
                waitFor("addon-catalog-order-list")
                back(); waitFor("addon-manager-list")
                back(); waitFor("addon-discover")
                assertEquals(0, escaped.get())
            } finally { host.store.remove(preferences.activeProfileId, installed.installationId) }
        }
    }

    @Test fun catalogMoveSavesRestoresFocusAndReopensInSavedOrder(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse("https://example.invalid/navigation/manifest.json"), manifest())
        val entries = AddonCatalogOrdering.ordered(listOf(installed), emptyList())
        var saved = emptyList<String>()
        var saves = 0
        val generation = mutableIntStateOf(0)
        val before = host.store.list(preferences.activeProfileId).map { it.installationId }
        try {
            compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                    key(generation.intValue) { AddonCatalogOrderScreen(host, preferences.activeProfileId, listOf(installed), {}, modifier,
                        loadOrder = { saved }, saveOrder = { saved = it; saves++ }, loadHidden = { emptySet() }) }
                }
            } } }
            waitFor("addon-order-row-${entries[0].key}")
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performClick()
            waitFor("addon-order-moving")
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").assertTextContains("Moving · 2").assertIsFocused()
            assertEquals(0, saves)
            screenshot("addon-catalog-order.png")
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(10_000) { saved.size == 4 && compose.onAllNodes(hasTestTag("addon-order-moving")).fetchSemanticsNodes().isEmpty() }
            assertEquals(1, saves)
            assertEquals(listOf(entries[1].key, entries[0].key, entries[2].key, entries[3].key), saved)
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").assertIsFocused()
            compose.runOnIdle { generation.intValue++ }
            waitFor("addon-order-row-${entries[1].key}")
            compose.onNodeWithTag("addon-order-row-${entries[1].key}").assertTextContains("1")
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performClick()
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performKeyInput { pressKey(Key.MoveHome) }
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(10_000) { saved == entries.map { it.key } }
            assertEquals(2, saves)
            assertEquals(before, host.store.list(preferences.activeProfileId).map { it.installationId })
        } finally { host.store.remove(preferences.activeProfileId, installed.installationId) }
    }
    @Test fun heldDpadMovesAcrossLongListAndBackCancelsWithoutSaving(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val installed = host.store.install(profile, AddonEndpoint.parse("https://example.invalid/navigation/manifest.json"), manifest(32))
        val entries = AddonCatalogOrdering.ordered(listOf(installed), emptyList())
        var saved = emptyList<String>(); var saves = 0; var escaped = 0; var failSave = true
        val last = "addon-order-row-${entries.last().key}"
        try {
            compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                    AddonCatalogOrderScreen(host, profile, listOf(installed), { escaped++ }, modifier,
                        loadOrder = { saved }, saveOrder = { if (failSave) error("Synthetic storage failure") else { saved = it; saves++ } },
                        loadHidden = { emptySet() })
                }
            } } }
            waitFor("addon-catalog-order-list")
            compose.onNodeWithTag("addon-catalog-order-list").performScrollToIndex(31)
            compose.onNodeWithTag(last).performClick()
            waitFor("addon-order-moving")
            // Native key repeats, as from holding a TV remote, must not write on every step.
            compose.runOnUiThread {
                val now = android.os.SystemClock.uptimeMillis()
                repeat(31) { index -> compose.activity.dispatchKeyEvent(android.view.KeyEvent(
                    now, now + index, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP, index)) }
                compose.activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
            }
            compose.onNodeWithTag(last).assertTextContains("Moving · 1").assertIsFocused()
            assertEquals(0, saves)
            compose.onNodeWithTag(last).performKeyInput { pressKey(Key.Back) }
            compose.onNodeWithTag(last).assertTextContains("32").assertIsFocused()
            compose.onNodeWithTag("addon-order-moving").assertDoesNotExist()
            assertEquals(0, saves); assertEquals(0, escaped)
            compose.onNodeWithTag(last).performClick()
            compose.onNodeWithTag(last).performKeyInput { pressKey(Key.MoveHome) }
            compose.onNodeWithTag(last).performKeyInput { pressKey(Key.DirectionCenter) }
            waitFor("addon-order-error")
            compose.onNodeWithTag(last).assertTextContains("Moving · 1").assertIsFocused()
            assertEquals(0, saves)
            compose.runOnIdle { failSave = false }
            compose.onNodeWithTag(last).performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(10_000) { saves == 1 }
            assertEquals(listOf(entries.last().key) + entries.dropLast(1).map { it.key }, saved)
            compose.onNodeWithTag(last).assertTextContains("1").assertIsFocused()
            compose.onNodeWithTag("addon-order-moving").assertDoesNotExist()
            compose.onNodeWithTag("addon-order-error").assertDoesNotExist()
        } finally { host.store.remove(profile, installed.installationId) }
    }

    @Test fun visibilitySwitchesReopenWithoutChangingOrderAndDoNotFlipOnFailure(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val installed = host.store.install(profile, AddonEndpoint.parse("https://example.invalid/navigation/manifest.json"), manifest())
        val entries = AddonCatalogOrdering.ordered(listOf(installed), emptyList())
        var hidden = emptySet<String>(); var failSave = false; var orderSaves = 0
        val generation = mutableIntStateOf(0)
        val toggle = "addon-visibility-${entries[0].key}"
        try {
            compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                    key(generation.intValue) { AddonCatalogOrderScreen(host, profile, listOf(installed), {}, modifier,
                        loadOrder = { entries.map { it.key } }, saveOrder = { orderSaves++ }, loadHidden = { hidden },
                        saveVisibility = { catalog, shown ->
                            if (failSave) error("Synthetic storage failure")
                            hidden = if (shown) hidden - catalog else hidden + catalog
                            hidden
                        }) }
                }
            } } }
            waitFor("addon-order-row-${entries[0].key}")
            compose.onNodeWithTag("addon-visibility-mode").performClick()
            compose.onNodeWithTag(toggle).assertIsSelected().performClick()
            compose.onNodeWithTag(toggle).assertIsNotSelected().assertIsFocused()
            assertEquals(setOf(entries[0].key), hidden)
            screenshot("addon-catalog-visibility.png")
            compose.runOnIdle { generation.intValue++ }
            waitFor("addon-order-row-${entries[0].key}")
            compose.onNodeWithTag("addon-order-row-${entries[0].key}").assertTextContains("1")
            compose.onNodeWithTag("addon-visibility-mode").performClick()
            compose.onNodeWithTag(toggle).assertIsNotSelected()
            compose.runOnIdle { failSave = true }
            compose.onNodeWithTag(toggle).performClick()
            waitFor("addon-order-error")
            compose.onNodeWithTag(toggle).assertIsNotSelected().assertIsFocused()
            assertEquals(setOf(entries[0].key), hidden)
            compose.runOnIdle { failSave = false }
            compose.onNodeWithTag(toggle).performClick()
            compose.onNodeWithTag(toggle).assertIsSelected()
            assertTrue(hidden.isEmpty()); assertEquals(0, orderSaves)
        } finally { host.store.remove(profile, installed.installationId) }
    }

    @Test fun hiddenCatalogsAreNotFetchedOrListedAndContinueWatchingSurvives(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val profile = preferences.activeProfileId
        MockWebServer().use { server ->
            val requests = CopyOnWriteArrayList<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (!path.contains("/catalog/")) return MockResponse().setResponseCode(404)
                    requests += path
                    return MockResponse().setBody("""{"metas":[{"id":"tile","type":"lab.navigation","name":"Visible title"}]}""")
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), manifest(2))
            val entries = AddonCatalogOrdering.ordered(listOf(installed), emptyList())
            val watch = AddonWatchIdentity(installed.installationId, AddonMediaKey("lab.navigation", "tile"), AddonMediaKey("lab.navigation", "video"))
            host.progress.save(host.progress.begin(profile, watch, "Continue fixture",
                AddonWatchArtwork("Continue fixture", server.url("/poster.png").toString())), 1, 5_000, 45_000)
            host.catalogVisibility.setVisible(profile, entries[0].key, false)
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) })
                    }
                } } }
                waitFor("addon-landing-card"); waitFor("addon-continue-card")
                compose.onNodeWithTag("addon-continue-card").assertIsDisplayed()
                compose.onNodeWithText("Catalog 1").assertDoesNotExist()
                assertTrue(requests.isNotEmpty())
                assertTrue(requests.all { it.contains("/catalog2") })
                compose.onNodeWithTag("addon-filter-discover").performClick()
                waitFor("addon-choice-Catalog")
                compose.onNodeWithTag("addon-choice-Catalog").performClick()
                compose.onNode(hasText("Catalog 2") and hasAnyAncestor(hasTestTag("addon-choice-options"))).assertExists()
                compose.onNodeWithText("Catalog 1").assertDoesNotExist()
                back(); back(); waitFor("addon-discover")
                // Change visibility through the actual settings route and return without a restart.
                compose.onNodeWithTag("addon-manage").performClick()
                waitFor("addon-catalog-order")
                compose.onNodeWithTag("addon-catalog-order").performClick()
                waitFor("addon-order-row-${entries[0].key}")
                compose.onNodeWithTag("addon-visibility-mode").performClick()
                compose.onNodeWithTag("addon-visibility-${entries[1].key}").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-visibility-${entries[1].key}") and isNotSelected() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                back(); back(); waitFor("addon-discover")
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("No visible catalogs. Open Addons & setup to show catalogs or add your services.")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-continue-card").assertIsDisplayed()
                compose.onNodeWithTag("addon-landing-card").assertDoesNotExist()
                val fetched = requests.size
                compose.onNodeWithTag("addon-filter-discover").performClick()
                compose.onNodeWithText("No visible catalogs. Show catalogs in Addons & setup.").assertIsDisplayed()
                compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
                assertEquals(fetched, requests.size)
                assertTrue(host.store.list(profile).single { it.installationId == installed.installationId }.enabled)
                assertEquals(5_000L, host.progress.get(profile, watch)?.resumePositionMillis)
            } finally {
                entries.forEach { host.catalogVisibility.setVisible(profile, it.key, true) }
                host.progress.remove(profile, watch)
                host.store.remove(profile, installed.installationId)
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 3_000)
        AddonLabScreenshots.capture(compose.activity, name)
    }
}
