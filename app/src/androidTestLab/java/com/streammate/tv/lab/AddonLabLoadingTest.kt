package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.toPixelMap
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AddonLabLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun heroReservesLogoSpaceWithoutTextFlashAndRecoversFromMissingArtwork() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        check(compose.activity.packageName == "com.streammate.tv.lab")
        val allowLogo = CountDownLatch(1); val allowFailure = CountDownLatch(1); val allowNext = CountDownLatch(1)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val bitmap = android.graphics.Bitmap.createBitmap(160, 44, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(20, 210, 40))
        val png = java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); output.toByteArray()
        }
        bitmap.recycle()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty(); requests += path
                    val gate = when (path) { "/logo.png" -> allowLogo; "/missing.png" -> allowFailure; else -> allowNext }
                    if (!gate.await(20, TimeUnit.SECONDS)) return MockResponse().setResponseCode(504)
                    return if (path == "/missing.png") MockResponse().setResponseCode(404)
                    else MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png))
                }
            }
            fun title(id: String, path: String?) = AddonMedia(AddonMediaKey("movie", id), "Synthetic $id", null,
                "poster", null, "Synthetic synopsis", "2026", emptyList(), logo = path?.let { server.url(it).toString() })
            val current = mutableStateOf<AddonMedia?>(title("first", "/logo.png"))
            fun waitForRequest(path: String) = compose.waitUntil(10_000) { path in requests }
            fun assertNoPlainTitle() = compose.onNodeWithTag("addon-hero-title-text").assertDoesNotExist()
            fun waitForGreenLogo() = compose.waitUntil(10_000) {
                val pixels = compose.onNodeWithTag("addon-hero-logo").captureToImage().toPixelMap()
                val pixel = pixels[pixels.width / 2, pixels.height / 2]
                pixel.green > .7f && pixel.red < .2f
            }
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    AddonHero(current.value, Modifier.height(240.dp))
                } } }
                waitForRequest("/logo.png"); assertNoPlainTitle()
                val pendingSynopsis = compose.onNodeWithText("Synthetic synopsis").fetchSemanticsNode().boundsInRoot
                allowLogo.countDown(); waitForGreenLogo(); assertNoPlainTitle()
                assertEquals(pendingSynopsis, compose.onNodeWithText("Synthetic synopsis").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle { current.value = title("missing", "/missing.png") }
                waitForRequest("/missing.png"); assertNoPlainTitle()
                allowFailure.countDown()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-hero-title-text")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-hero-title-text").assertTextEquals("Synthetic missing")
                // The previous image failure must not show text for the next title.
                compose.runOnIdle { current.value = title("next", "/next.png") }
                waitForRequest("/next.png"); assertNoPlainTitle()
                allowNext.countDown(); waitForGreenLogo(); assertNoPlainTitle()
                compose.runOnIdle { current.value = title("without logo", null) }
                compose.onNodeWithTag("addon-hero-title-text").assertTextEquals("Synthetic without logo")
                compose.onNodeWithTag("addon-hero-logo").assertDoesNotExist()
                compose.runOnIdle { current.value = null }
                compose.onNodeWithTag("addon-hero-title-text").assertTextEquals("Discover")
                assertEquals(listOf("/logo.png", "/missing.png", "/next.png"), requests.toList())
            } finally { allowLogo.countDown(); allowFailure.countDown(); allowNext.countDown() }
        }
    }

    @Test fun savedRowRendersBeforeSlowRefreshAndPosterHasNoLetterFlash(): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val profile = preferences.activeProfileId
        val allowRefresh = CountDownLatch(1); val allowPoster = CountDownLatch(1)
        val requests = AtomicInteger(); val unexpected = AtomicInteger()
        val generation = mutableIntStateOf(0)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/catalog/") -> {
                        val first = requests.incrementAndGet() == 1
                        if (!first) check(allowRefresh.await(20, TimeUnit.SECONDS))
                        val name = if (first) "Old title" else "New title"
                        MockResponse().setHeader("Cache-Control", if (first) "max-age=0" else "max-age=600")
                            .setBody("""{"metas":[{"id":"$name","name":"$name","type":"lab.loading","poster":"${server.url("/poster.png")}","releaseInfo":"2026","imdbRating":"7.4"}]}""")
                    }
                    request.path == "/poster.png" -> { check(allowPoster.await(20, TimeUnit.SECONDS)); MockResponse().setResponseCode(404) }
                    else -> { unexpected.incrementAndGet(); MockResponse().setResponseCode(404) }
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse(server.url("/manifest.json").toString(), true),
                """{"id":"test.loading.ui","name":"Fixture","version":"1","types":["lab.loading"],"resources":["catalog"],"catalogs":[{"id":"home","type":"lab.loading","name":"Saved catalog"}]}""")
            try {
                host.browser.catalog(profile, installed.installationId, "lab.loading", "home")
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        key(generation.intValue) { AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) }) }
                    }
                } } }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card")).fetchSemanticsNodes().isNotEmpty() && requests.get() == 2 }
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nothing to continue yet") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("addon-landing-card").assertContentDescriptionEquals("Old title").assertIsFocused()
                // No title/year/rating beneath the artwork and no transient first-letter text.
                compose.onNode(hasAnyAncestor(hasTestTag("addon-landing-card")) and hasText("O"), useUnmergedTree = true).assertDoesNotExist()
                compose.onNode(hasAnyAncestor(hasTestTag("addon-landing-card")) and hasText("Old title"), useUnmergedTree = true).assertDoesNotExist()
                compose.onNode(hasAnyAncestor(hasTestTag("addon-landing-card")) and hasText("2026 · 7.4"), useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithText("Loading titles…").assertDoesNotExist()
                compose.onNodeWithTag("addon-poster-fallback", useUnmergedTree = true).assertDoesNotExist()
                AddonLabScreenshots.capture(compose.activity, "addon-saved-row-loading.png")
                allowRefresh.countDown()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card") and hasContentDescription("New title") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                allowPoster.countDown()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-poster-fallback"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-poster-fallback", useUnmergedTree = true).assertTextContains("New title")
                compose.runOnIdle { generation.intValue++ }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card") and hasContentDescription("New title")).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(2, requests.get()); assertEquals(0, unexpected.get())
            } finally { allowRefresh.countDown(); allowPoster.countDown(); host.store.remove(profile, installed.installationId) }
        }
    }
}
