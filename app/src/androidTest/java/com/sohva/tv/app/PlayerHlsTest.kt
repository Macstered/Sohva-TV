package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 30 §11: an HLS live channel plays through the service, and the playlist's `User-Agent` and
 * `Referer` go with every request — the playlist and its segments, relative and absolute alike
 * (beta 23 lost them after the manifest, L-23).
 */
@RunWith(AndroidJUnit4::class)
class PlayerHlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val segments = TestMedia.aacSegments()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/hls/live.m3u8" -> MockResponse.Builder()
                            .addHeader("Content-Type", "application/vnd.apple.mpegurl")
                            .body(playlist(segments.size))
                            .build()
                        path.startsWith("/hls/seg") || path.startsWith("/abs/seg") -> {
                            val i = path.substringAfter("seg").substringBefore(".aac").toInt()
                            MockResponse.Builder().addHeader("Content-Type", "audio/aac").body(Buffer().write(segments[i])).build()
                        }
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            server.start()
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            // The playlist entries carry their own headers (spec 30 PLAY-FR-17).
            GuideFixture.seed(
                app.graph, groups = 1, perGroup = 2, stream = { _, _ -> server.url("/hls/live.m3u8").toString() },
                userAgent = "FixtureAgent/1.0", referrer = "http://provider.example/",
            )
        }

        override fun after() = server.close()
    }

    /** Even segments relative to the playlist, odd ones absolute. */
    private fun playlist(count: Int): String = buildString {
        append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n")
        for (i in 0 until count) {
            append("#EXTINF:4.0,\n")
            append(if (i % 2 == 0) "seg$i.aac\n" else server.url("/abs/seg$i.aac").toString() + "\n")
        }
        append("#EXT-X-ENDLIST\n")
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    @Test
    fun everyRequestCarriesThePlaylistsHeaders() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(20_000) { requests.any { it.url.encodedPath == "/hls/seg0.aac" } && requests.any { it.url.encodedPath == "/abs/seg1.aac" } }
        val seen = requests.filter { it.url.encodedPath.endsWith(".m3u8") || it.url.encodedPath.endsWith(".aac") }
        assertTrue(seen.size >= 3)
        for (r in seen) {
            assertEquals(r.url.encodedPath, "FixtureAgent/1.0", r.headers["User-Agent"])
            assertEquals(r.url.encodedPath, "http://provider.example/", r.headers["Referer"])
        }
        assertTrue(!compose.onAllNodesWithTagExists("player-banner"))
    }
}
