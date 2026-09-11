package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic metadata and artwork only; never invokes TMDB or an owner's configured service. */
class AddonLabCastTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun app(): StreamMateApplication {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        return (compose.activity.application as StreamMateApplication).also { check(it.packageName == "com.streammate.tv.lab") }
    }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun moviePortraitsNamesFallbackAndRemoteScrollSurviveCachedReopen() = fixture(series = false) { state ->
        waitFor("addon-movie-cast")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-find-sources") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addon-find-sources").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("addon-cast-member-0").assertIsFocused().assertTextContains("Actor 1").assertTextContains("Pilot")
        // The first portrait is an actual decoded synthetic PNG, not just a populated URL.
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithTag("addon-cast-photo-0", useUnmergedTree = true).captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.green > .6f && center.red < .25f
        }
        compose.onNodeWithTag("addon-cast-member-1").assertTextContains("AC")
        compose.onNodeWithTag("addon-cast-photo-1", useUnmergedTree = true).assertDoesNotExist()
        screenshot("addon-movie-cast.png")
        repeat(11) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("addon-cast-member-11").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("addon-cast-member-11").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("addon-find-sources").assertIsFocused()
        assertEquals(1, state.metadata.get())
        compose.runOnIdle { state.generation.intValue++ }
        waitFor("addon-movie-cast")
        assertEquals(1, state.metadata.get())
        assertTrue(state.photos.get() > 0)
        assertEquals(0, state.unexpected.get())
    }

    @Test fun seriesNamesLeaveEpisodesVisibleAndDoNotDownloadCastPhotos() = fixture(series = true) { state ->
        waitFor("addon-cast-names")
        compose.onNodeWithTag("addon-cast-names").assertTextContains("Actor 1", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("addon-movie-cast").assertDoesNotExist()
        compose.onAllNodesWithTag("addon-episode-card").onFirst().assertIsDisplayed()
        val cast = compose.onNodeWithTag("addon-cast-names").fetchSemanticsNode().boundsInRoot
        val episodes = compose.onNodeWithTag("addon-episodes").fetchSemanticsNode().boundsInRoot
        assertTrue("Cast must not overlap season/episode controls", cast.bottom < episodes.top)
        screenshot("addon-series-cast.png")
        compose.onAllNodesWithTag("addon-episode-card").onFirst().performClick()
        waitFor("addon-episode-details")
        compose.onNodeWithText("Pilot overview").assertIsDisplayed()
        compose.onNodeWithTag("addon-movie-cast").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onAllNodesWithTag("addon-episode-card").onFirst().assertIsFocused()
        compose.onNodeWithTag("addon-cast-names").assertIsDisplayed()
        assertEquals(0, state.photos.get()); assertEquals(1, state.metadata.get()); assertEquals(0, state.unexpected.get())
    }

    @Test fun absentCastDoesNotShowEmptyHeadingOrBlockSources() = fixture(series = false, withCast = false) { state ->
        waitFor("addon-play-source")
        compose.onNodeWithTag("addon-movie-cast").assertDoesNotExist()
        compose.onNodeWithTag("addon-cast-names").assertDoesNotExist()
        compose.onNodeWithTag("addon-find-sources").assertIsDisplayed()
        assertEquals(0, state.photos.get()); assertEquals(1, state.metadata.get()); assertEquals(0, state.unexpected.get())
    }

    private class Fixture {
        val metadata = AtomicInteger(); val photos = AtomicInteger(); val unexpected = AtomicInteger()
        val generation = mutableIntStateOf(0)
    }
    private fun fixture(series: Boolean, withCast: Boolean = true, verify: (Fixture) -> Unit): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val state = Fixture()
        MockWebServer().use { server ->
            val cast = if (!withCast) "" else """, "app_extras":{"cast":[${(1..12).joinToString { index ->
                """{"name":"Actor $index","character":"${if (index == 1) "Pilot" else "Character $index"}","photo":${if (index == 2) "null" else "\"${server.url("/portrait-$index.png")}\""}}"""
            }}]}"""
            val videos = if (series) """, "videos":[{"id":"pilot","title":"Pilot","season":1,"episode":1,"overview":"Pilot overview"},{"id":"second","title":"Second","season":1,"episode":2}]"""
                else """, "behaviorHints":{"defaultVideoId":"film"}"""
            val body = """{"meta":{"id":"title","type":"lab.cast","name":"${if (series) "The Long Journey — A Synthetic Series" else "A Synthetic Film"}","description":"${"An adventure across distant places, with a cast of unforgettable characters. ".repeat(if (series) 8 else 2)}","releaseInfo":"2026","imdbRating":"7.4"$cast$videos}}"""
            val portrait = portrait()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/meta/") -> { state.metadata.incrementAndGet(); MockResponse().setHeader("Cache-Control", "max-age=600").setBody(body) }
                    request.path!!.startsWith("/portrait-") -> { state.photos.incrementAndGet(); MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(portrait)) }
                    request.path!!.contains("/stream/") -> MockResponse().setBody("""{"streams":[{"name":"Synthetic source","url":"https://example.invalid/video"}]}""")
                    else -> { state.unexpected.incrementAndGet(); MockResponse().setResponseCode(404) }
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse(server.url("/manifest.json").toString(), true),
                """{"id":"test.lab.cast","version":"1","name":"Cast fixture","types":["lab.cast"],"resources":["meta","stream"],"catalogs":[]}""")
            try {
                val preview = AddonMedia(AddonMediaKey("lab.cast", "title"), "Preview title", null, "poster", null, null, null, emptyList())
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        key(state.generation.intValue) { AddonDetailsScreen(host, profile, installed, preview, {}, modifier) }
                    }
                } } }
                verify(state)
            } finally { host.store.remove(profile, installed.installationId) }
        }
    }
    private fun portrait(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(104, 104, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff28b6a8.toInt())
        val bytes = java.io.ByteArrayOutputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle(); return bytes
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 3_000)
        AddonLabScreenshots.capture(compose.activity, name)
    }
}
