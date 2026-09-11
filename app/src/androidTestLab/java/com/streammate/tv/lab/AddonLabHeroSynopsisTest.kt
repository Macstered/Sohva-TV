package com.streammate.tv.lab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.sohva.tv.addons.*
import com.streammate.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic, emulator-only. Separate catalog and metadata services, no owner's URLs or data. */
class AddonLabHeroSynopsisTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val hero = hasAnyAncestor(hasTestTag("addon-hero"))

    @Test fun pendingSynopsisKeepsTitleVisibleWithoutEnglishFlash() = fixture { f ->
        focus("Slow")
        compose.waitUntil(10_000) { f.calls("slow") == 1 }
        compose.onNode(hero and hasText("Slow")).assertIsDisplayed()
        compose.onNode(hero and hasText("English catalog synopsis")).assertDoesNotExist()
        compose.onNode(hero and hasText("Hidas vanha kuvaus")).assertDoesNotExist()
        f.releaseSlow.countDown()
        waitForHero("Hidas vanha kuvaus")
        compose.onNode(hero and hasText("English catalog synopsis")).assertDoesNotExist()
        assertEquals(1, f.calls("slow")); assertEquals(0, f.unexpected.get())
    }

    @Test fun catalogOnlyHeroUsesFinnishDetailsCacheAndKeepsOriginalTitleAndIdentity() = fixture { f ->
        focus("Target")
        waitForHero("Suomenkielinen kohteen kuvaus")
        compose.onNode(hero and hasText("Target")).assertIsDisplayed()
        compose.onNode(hero and hasText("Canonical metadata title")).assertDoesNotExist()
        assertEquals(1, f.calls("target"))
        assertEquals(0, f.unexpected.get())
        compose.onNodeWithTag("addon-rail-discover").performClick()
        // Open details: the fresh metadata from the hero must be reused, and Back must
        // restore the same original catalog card (metadata returned a canonical ID).
        card("Target").performClick()
        waitForDetailsSynopsis("Suomenkielinen kohteen kuvaus")
        assertEquals(1, f.calls("target"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        card("Target").assertIsFocused()
        waitForHero("Suomenkielinen kohteen kuvaus")
        assertEquals(1, f.calls("target"))
        // A second catalog owner uses the SAME opaque ID but has its own metadata route.
        card("Target").performKeyInput { pressKey(Key.DirectionDown) }
        waitForHero("Toisen palvelun kuvaus")
        assertEquals(1, f.otherRequests.get())
        card("Other catalog title").performKeyInput { pressKey(Key.DirectionUp) }
        waitForHero("Suomenkielinen kohteen kuvaus")
        assertEquals(1, f.calls("target")); assertEquals(0, f.unexpected.get())
    }

    @Test fun detailsPreviewTitleDoesNotMeanLocalizedSynopsisIsReady() = fixture { f ->
        // Click without focusing the card first: metadata must come from details,
        // not a completed hero lookup. Keep its HTTP response pending explicitly.
        card("Deferred details").performClick()
        compose.waitUntil(10_000) { f.calls("details") == 1 }
        compose.onNodeWithTag("addon-details-loaded").assertIsDisplayed()
        compose.onNodeWithText("English catalog synopsis").assertIsDisplayed()
        compose.onNodeWithText("Viivästetty suomenkielinen kuvaus").assertDoesNotExist()
        f.releaseDetails.countDown()
        waitForDetailsSynopsis("Viivästetty suomenkielinen kuvaus")
        compose.onNodeWithText("English catalog synopsis").assertDoesNotExist()
        assertEquals(1, f.calls("details")); assertEquals(0, f.unexpected.get())
    }

    @Test fun lateResponseCannotOverwriteNewFocusAndRailAndUnavailableTitlesStayResponsive() = fixture { f ->
        focus("Slow")
        compose.waitUntil(10_000) { f.calls("slow") == 1 }
        focus("Next")
        waitForHero("Seuraavan kohteen kuvaus")
        f.releaseSlow.countDown()
        compose.mainClock.advanceTimeBy(700)
        compose.onNode(hero and hasText("Hidas vanha kuvaus")).assertDoesNotExist()
        compose.onNode(hero and hasText("Next")).assertIsDisplayed()
        focus("Unavailable")
        compose.waitUntil(10_000) { f.calls("unavailable") == 1 }
        waitForHero("English catalog synopsis")
        focus("Next"); waitForHero("Seuraavan kohteen kuvaus")
        focus("Unavailable"); waitForHero("English catalog synopsis")
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, f.calls("unavailable"))
        // Leave a never-requested title immediately; the rail must cancel its debounce.
        focus("Rail target")
        compose.onNodeWithTag("addon-search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, f.calls("rail")); assertEquals(0, f.unexpected.get())
    }

    @Test fun backgroundingCancelsHeroLookupAndForegroundResumeCanRetry() = fixture { f ->
        focus("Slow")
        compose.waitUntil(10_000) { f.calls("slow") == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        f.releaseSlow.countDown()
        Thread.sleep(750) // Observe the stopped Activity without advancing a foreground UI clock.
        assertEquals(1, f.calls("slow"))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForHero("Hidas vanha kuvaus")
        assertEquals(2, f.calls("slow"))
        assertEquals(0, f.unexpected.get())
    }

    private fun card(name: String) = compose.onNode(hasTestTag("addon-landing-card") and hasContentDescription(name))
    private fun focus(name: String) {
        card(name).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        card(name).assertIsFocused()
    }
    private fun waitForHero(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hero and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hero and hasText(text)).assertIsDisplayed()
    }
    private fun waitForDetailsSynopsis(text: String) {
        // The details title is rendered from the catalog preview before HTTP or
        // cache I/O completes. Its tag is not a metadata-readiness signal.
        val synopsis = hasText(text) and hasAnyAncestor(
            hasTestTag("addon-movie-details") or hasTestTag("addon-series-details"))
        compose.waitUntil(10_000) {
            compose.onAllNodes(synopsis).fetchSemanticsNodes().size == 1 &&
                compose.onNode(synopsis).isDisplayed()
        }
        compose.onNode(synopsis).assertIsDisplayed()
    }

    private class Fixture {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        val otherRequests = AtomicInteger()
        val unexpected = AtomicInteger()
        val releaseSlow = CountDownLatch(1)
        val releaseDetails = CountDownLatch(1)
        fun calls(id: String) = requests[id]?.get() ?: 0
    }
    private fun fixture(test: (Fixture) -> Unit): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val profile = preferences.activeProfileId
        val f = Fixture()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/catalog/")) {
                        val items = if (path.startsWith("/other/")) listOf("target" to "Other catalog title") else
                            listOf("target" to "Target", "slow" to "Slow", "next" to "Next", "unavailable" to "Unavailable", "rail" to "Rail target", "details" to "Deferred details")
                        return MockResponse().setHeader("Cache-Control", "max-age=600").setBody(items.joinToString(",", "{\"metas\":[", "]}") { (id, name) ->
                            """{"id":"$id","type":"lab.hero","name":"$name","description":"English catalog synopsis","releaseInfo":"2026"}"""
                        })
                    }
                    if (path.contains("/meta/")) {
                        val id = request.requestUrl!!.pathSegments.last().removeSuffix(".json")
                        val other = path.startsWith("/other/")
                        if (other) f.otherRequests.incrementAndGet()
                        else f.requests.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                        if (id == "slow") check(f.releaseSlow.await(20, TimeUnit.SECONDS))
                        if (id == "details") check(f.releaseDetails.await(20, TimeUnit.SECONDS))
                        if (id == "unavailable") return MockResponse().setResponseCode(503)
                        val description = when {
                            other -> "Toisen palvelun kuvaus"
                            id == "target" -> "Suomenkielinen kohteen kuvaus"
                            id == "next" -> "Seuraavan kohteen kuvaus"
                            id == "slow" -> "Hidas vanha kuvaus"
                            id == "details" -> "Viivästetty suomenkielinen kuvaus"
                            else -> "Muu kuvaus"
                        }
                        return MockResponse().setHeadersDelay(if (id == "details") 2 else 0, TimeUnit.SECONDS)
                            .setHeader("Cache-Control", "max-age=600").setBody(
                            """{"meta":{"id":"canonical-$id","type":"lab.hero","name":"Canonical metadata title","description":"$description","behaviorHints":{"defaultVideoId":"original-video"}}}""")
                    }
                    f.unexpected.incrementAndGet(); return MockResponse().setResponseCode(404)
                }
            }
            val installed = mutableListOf<InstalledAddon>()
            try {
                fun manifest(id: String, resources: String, catalogs: String) = """{"id":"$id","name":"Synthetic $id","version":"1","types":["lab.hero"],"resources":[$resources],"catalogs":[$catalogs]}"""
                val catalog = """{"type":"lab.hero","id":"home","name":"Synthetic catalog"}"""
                val owner = host.store.install(profile, AddonEndpoint.parse(server.url("/list/manifest.json").toString(), true), manifest("list", "\"catalog\"", catalog)).also { installed += it }
                host.store.install(profile, AddonEndpoint.parse(server.url("/metadata/manifest.json").toString(), true), manifest("metadata", "\"meta\"", "")).also { installed += it }
                val other = host.store.install(profile, AddonEndpoint.parse(server.url("/other/manifest.json").toString(), true), manifest("other", "\"catalog\",\"meta\"", catalog)).also { installed += it }
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(owner, installed[1], other) })
                    }
                } } }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card")).fetchSemanticsNodes().isNotEmpty() }
                test(f)
            } finally {
                f.releaseSlow.countDown()
                f.releaseDetails.countDown()
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.runOnUiThread { compose.activity.setContent {} }
                installed.forEach { host.store.remove(profile, it.installationId) }
            }
        }
    }
}
