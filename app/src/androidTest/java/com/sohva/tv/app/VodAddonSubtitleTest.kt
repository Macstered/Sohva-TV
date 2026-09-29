package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.feature.home.RailItem
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 30 PLAY-FR-141: a library film whose file has no Finnish subtitle, with Finnish preferred,
 * gets one from a Discover subtitle addon while it plays, asked by the IMDb id its TMDB details give;
 * the subtitle list is then Discover's picker with that subtitle chosen. Only the id reaches the
 * addon. TMDB, the addon, the clip and the subtitle file are the test's own server.
 */
@RunWith(AndroidJUnit4::class)
class VodAddonSubtitleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val requests = ConcurrentLinkedQueue<String>()
    private lateinit var base: String

    private val manifest = """{"id":"org.example.vodsubs","version":"1.0.0","name":"Synthetic subtitles","types":["movie","series"],
        "resources":["subtitles"],"catalogs":[]}"""

    private val seed = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += path + (request.url.encodedQuery?.let { "?$it" } ?: "")
                    if (path.startsWith("/vod/")) {
                        return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
                    }
                    val body = when {
                        path.endsWith("/search/movie") && request.url.queryParameter("query") == "Drama 0000" ->
                            """{"results":[{"id":949,"title":"Harbour Lights","original_title":"Drama 0000","release_date":"2000-03-01","popularity":12.5}]}"""
                        path.endsWith("/movie/949") -> """{"id":949,"title":"Harbour Lights","original_title":"Drama 0000","release_date":"2000-03-01","imdb_id":"tt0000949"}"""
                        path.endsWith("/manifest.json") -> manifest
                        path.contains("/subtitles/movie/tt0000949") -> """{"subtitles":[{"id":"s-fi","lang":"fin","url":"$base/subs/fi.srt"}]}"""
                        path == "/subs/fi.srt" -> "1\n00:00:00,500 --> 00:00:19,000\nHei\n"
                        else -> """{"results":[]}"""
                    }
                    return MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
                }
            }
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            base = "http://${deviceAddress()}:${server.port}"
            graph.metadata.useEndpoints(server.url("/3/"), server.url("/tvmaze/"))
            LibraryFixture.seed(graph, perGroup = 2, stream = { "$base/vod/$it.mp4" })
            host.testAllowHttp = true
            runBlocking {
                graph.data.secrets.write(ServiceKeys.TMDB_TOKEN, "fictional-token")
                graph.data.secrets.write(ServiceKeys.TMDB_ENABLED, "true")
                graph.metadata.settings.reload()
                graph.data.preferences.setVodLanguage(VodLanguageSlot.SUBTITLES, "fi")
                host.manager.install(graph.data.profiles.activeId, "http://127.0.0.1:${server.port}/cfg/manifest.json")
            }
        }

        override fun after() {
            runBlocking { graph.data.preferences.setVodLanguage(VodLanguageSlot.SUBTITLES, null) }
            server.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun deviceAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun focusAndPress(tag: String) {
        compose.waitUntil(10_000) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test
    fun aFilmWithoutAPreferredSubtitleGetsOneFromTheAddons() {
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        focusAndPress("library-row-group:drama")
        focusAndPress("library-card-${LibraryFixture.key(0)}")
        compose.waitUntil(10_000) { exists("screen-film") }
        focusAndPress("details-watch")
        compose.waitUntil(15_000) { requests.any { it.startsWith("/vod/") } }
        // While it plays: the addon is asked by IMDb id only (no file name, size or hash), and the file comes.
        compose.waitUntil(20_000) { requests.contains("/subs/fi.srt") }
        val asked = requests.first { it.contains("/subtitles/") }
        assertTrue(asked, asked.contains("/subtitles/movie/tt0000949") && !asked.contains("filename") && !asked.contains("videoHash"))
        // The subtitle list is Discover's picker, with the addon's Finnish subtitle there.
        compose.waitUntil(10_000) { exists("screen-player") }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("player-transport-subtitles") }
        compose.onNodeWithTag("player-transport-subtitles").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("addon-subtitle-picker") }
        assertTrue(exists("addon-subtitles-language-fi"))
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("addon-subtitle-picker") }
    }
}
