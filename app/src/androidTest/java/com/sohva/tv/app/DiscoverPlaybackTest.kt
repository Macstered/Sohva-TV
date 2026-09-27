package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.discover.store.WatchIdentity
import com.sohva.tv.feature.home.RailItem
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 50 §11 "Instrumented" for addon playback with a synthetic addon on the test's own server:
 * the loading screen (Cancel focused, media keys swallowed, Back leaves), a movie's stream with its
 * own header and the end back on its page with progress completed, the background stop with
 * Retry with fresh source, and an episode's end opening the next episode, which plays from 0.
 * Streams use the emulator's own address: loopback stream URLs are refused (FR-21).
 */
@RunWith(AndroidJUnit4::class)
class DiscoverPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val media = ConcurrentLinkedQueue<Pair<String, String?>>()
    private val streamCalls = ConcurrentLinkedQueue<String>()
    private lateinit var mediaBase: String

    private val manifest = """{"id":"org.example.synthetic","version":"1.0.0","name":"Synthetic provider","types":["movie","series"],
        "resources":["catalog","meta","stream"],
        "catalogs":[{"type":"movie","id":"films","name":"Films"},{"type":"series","id":"shows","name":"Shows"}]}"""

    private fun stream(id: String) = """{"streams":[{"name":"Synthetic 720p","description":"Test clip $id","url":"$mediaBase/media/$id.mp4",
        "behaviorHints":{"proxyHeaders":{"request":{"X-Test":"yes"}}}}]}"""

    private val seed = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    if (path.startsWith("/media/")) {
                        media += path to request.headers["X-Test"]
                        val slow = path.contains("slow")
                        return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes()))
                            .apply { if (slow) headersDelay(30, TimeUnit.SECONDS) }.build()
                    }
                    val body = when {
                        path.endsWith("/manifest.json") -> manifest
                        path.contains("/catalog/movie/films") -> """{"metas":[{"type":"movie","id":"film1","name":"Fictional Film"},{"type":"movie","id":"slow1","name":"Slow Film"}]}"""
                        path.contains("/catalog/series/shows") -> """{"metas":[{"type":"series","id":"show1","name":"Fictional Show"}]}"""
                        path.contains("/meta/movie/") -> {
                            val id = path.substringAfter("/meta/movie/").removeSuffix(".json")
                            """{"meta":{"type":"movie","id":"$id","name":"Film $id"}}"""
                        }
                        path.contains("/meta/series/show1") -> """{"meta":{"type":"series","id":"show1","name":"Fictional Show","videos":[
                            {"id":"show1:1:1","title":"Pilot","season":1,"episode":1},{"id":"show1:1:2","title":"Second","season":1,"episode":2}]}}"""
                        path.contains("/stream/") -> {
                            val id = path.substringAfterLast("/").removeSuffix(".json").replace(":", "-")
                            streamCalls += id
                            stream(id)
                        }
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            mediaBase = "http://${deviceAddress()}:${server.port}"
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, "http://127.0.0.1:${server.port}/cfg/manifest.json") }
            // `-e matchFrameRate off` on a real TV: tells a display-mode switch apart from other causes.
            if (InstrumentationRegistry.getArguments().getString("matchFrameRate") == "off") runBlocking { graph.data.preferences.setMatchFrameRate(false) }
        }

        override fun after() {
            runBlocking { graph.data.preferences.setMatchFrameRate(true) }
            server.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    /** The emulator's own IPv4 address: reachable from itself, and not loopback. */
    private fun deviceAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focusedTag(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("waiting for $tag, focused: ${focusedTag()}", e)
        }
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun click(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun openTitle(card: String) {
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
        click(card)
        compose.waitUntil(10_000) { exists("discover-title") }
    }

    private val source = "discover-source-Synthetic provider-0"

    private fun mediaFor(id: String): List<String?> = media.filter { it.first == "/media/$id.mp4" }.map { it.second }

    private fun progress(type: String, id: String, video: String) = runBlocking {
        val profile = graph.data.profiles.activeId
        host.progress.get(profile, WatchIdentity(host.installations.list(profile).single().id, type, id, type, video))
    }

    @Test
    fun aPausedTitleShowsOnHomeAndOkOpensItsPage() {
        val profile = graph.data.profiles.activeId
        val installation = runBlocking { host.installations.list(profile).single().id }
        val identity = WatchIdentity(installation, "movie", "film1", "movie", "film1")
        runBlocking {
            host.progress.save(profile, identity, "Fictional Film", com.sohva.tv.feature.discover.store.Artwork("Fictional Film", null, null), 6_000, 20_000, false, host.nextSession(), 1)
        }
        // One card for the title (HOME-10), keyed by installation, media and video (HOME-FR-23).
        val card = "home-resume-discover:$installation:film1:film1"
        compose.waitUntil(15_000) { exists(card) }
        compose.onNodeWithTag(card).performSemanticsAction(SemanticsActions.OnClick)
        // OK opens the title page (HOME-15), where Continue is offered; Back returns Home.
        compose.waitUntil(10_000) { exists("discover-title") }
        compose.waitUntil(10_000) { exists("discover-title-primary") }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists(card) && !exists("discover-title") }
    }

    @Test
    fun aMovieStartsWithItsHeaderAndEndsOnItsPageWatched() {
        openTitle("discover-card-1-film1")
        click(source)
        // The stream's own header reaches its origin (FR-95).
        compose.waitUntil(20_000) { mediaFor("film1").isNotEmpty() }
        assertEquals("yes", mediaFor("film1").first())
        compose.waitUntil(20_000) { exists("screen-player") && !exists("addon-loading") }
        // The first end returns to the movie's page (FR-93), with the progress completed (FR-107).
        compose.waitUntil(45_000) { exists("discover-title") && !exists("screen-player") }
        compose.waitUntil(5_000) { progress("movie", "film1", "film1")?.completed == true }
    }

    @Test
    fun theLoadingScreenFocusesCancelSwallowsMediaKeysAndBackLeaves() {
        openTitle("discover-card-1-slow1")
        click(source)
        compose.waitUntil(10_000) { exists("addon-loading") }
        awaitFocus("addon-cancel")
        assertTrue(exists("addon-stage"))
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        assertTrue(exists("addon-loading"))
        awaitFocus("addon-cancel")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("discover-title") && !exists("screen-player") }
    }

    @Test
    fun theBackgroundStopsPlaybackAndRetryResolvesAFreshSource() {
        openTitle("discover-card-1-film1")
        click(source)
        compose.waitUntil(20_000) { exists("screen-player") && !exists("addon-loading") && mediaFor("film1").isNotEmpty() }
        Thread.sleep(2_000)
        val streamsBefore = streamCalls.count { it == "film1" }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        // Nothing restarts unattended (FR-91): the banner and its button, which has focus.
        compose.waitUntil(10_000) { exists("addon-stopped") }
        awaitFocus("addon-retry")
        val saved = progress("movie", "film1", "film1")
        assertNotNull(saved)
        assertFalse(saved!!.completed)
        val mediaBefore = mediaFor("film1").size
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // The same provider asked again, and the new stream played.
        compose.waitUntil(15_000) { streamCalls.count { it == "film1" } > streamsBefore }
        compose.waitUntil(20_000) { mediaFor("film1").size > mediaBefore && !exists("addon-stopped") }
    }

    @Test
    fun anEpisodeEndOpensTheNextEpisodeWhichPlaysFromTheStart() {
        openTitle("discover-card-2-show1")
        click("discover-episode-show1:1:1")
        click(source)
        compose.waitUntil(20_000) { mediaFor("show1-1-1").isNotEmpty() }
        // The end opens the second episode's page, which starts its first source by itself.
        compose.waitUntil(45_000) { mediaFor("show1-1-2").isNotEmpty() }
        assertTrue(progress("series", "show1", "show1:1:1")!!.completed)
        compose.waitUntil(45_000) { exists("discover-title") && !exists("screen-player") }
        assertTrue(progress("series", "show1", "show1:1:2")!!.completed)
    }
}
