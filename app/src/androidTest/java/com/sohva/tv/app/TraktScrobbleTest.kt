package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.store.TraktAccount
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
 * Spec 51 §11 "Playback": a library film matched to TMDB sends start → pause (after 2.5 s) →
 * start → stop at 100 % to a fake Trakt; a pause shorter than the settle sends no pause; a film
 * without a TMDB match sends nothing. The account and the matches are fictional.
 */
@RunWith(AndroidJUnit4::class)
class TraktScrobbleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val trakt = MockWebServer()
    private val media = MockWebServer()
    private val scrobbles = ConcurrentLinkedQueue<Pair<String, String>>()

    private val servers = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            media.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
            }
            media.start()
            trakt.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    if (path.startsWith("/scrobble/")) scrobbles += path.removePrefix("/scrobble/") to request.body?.utf8().orEmpty()
                    return MockResponse.Builder().code(if (path.startsWith("/scrobble/")) 201 else 404).body("{}").build()
                }
            }
            trakt.start()
            LibraryFixture.seed(graph, groups = listOf("Drama"), perGroup = 4, stream = { media.url("/vod/$it.mp4").toString() })
            // Film 0 is matched to TMDB 603 by the library's metadata; film 1 has no match.
            graph.data.database.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO metadata_match (content_key, media_type, status, provider, external_id, genre, genres_version, replacement_title, replacement_poster, replace_provider_poster, updated_at) " +
                    "VALUES (?, 'movie', 'matched', 'tmdb', 'tmdb:603', NULL, 0, NULL, NULL, 0, 0)",
                arrayOf(LibraryFixture.key(0)),
            )
            val host = graph.trakt!!
            host.useTestServer(trakt.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
            host.store.saveAccount(
                graph.data.profiles.activeId,
                TraktAccount("fictional-viewer", "uuid-1", TraktTokens("not-a-real-token", "not-a-real-refresh", System.currentTimeMillis() + 30 * TraktHost.DAY_MS, 90 * TraktHost.DAY_MS), false),
            )
        }

        override fun after() {
            media.close()
            trakt.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(servers).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) = compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }

    private fun play(film: Int) {
        val row = "library-row-group:drama"
        val card = "library-card-${LibraryFixture.key(film)}"
        compose.waitUntil(15_000) { exists(RailItem.MOVIES.tag) }
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists(row) }
        compose.onNodeWithTag(row).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(row)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists(card) }
        compose.onNodeWithTag(card).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(card)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("details-watch") }
        compose.onNodeWithTag("details-watch").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { exists("screen-player") }
    }

    private fun actions() = scrobbles.map { it.first }

    @Test
    fun aMatchedFilmScrobblesStartPauseAndStop() {
        play(0)
        compose.waitUntil(15_000) { actions() == listOf("start") }
        assertTrue(scrobbles.first().second, scrobbles.first().second.contains("\"tmdb\":603"))
        // A pause shorter than the settle is a rebuffer: nothing is sent.
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        Thread.sleep(1_000)
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        Thread.sleep(3_000)
        assertEquals(listOf("start"), actions())
        // A real pause goes out after 2.5 s; playing again starts again.
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        compose.waitUntil(6_000) { actions() == listOf("start", "pause") }
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        compose.waitUntil(6_000) { actions() == listOf("start", "pause", "start") }
        // The end: STOP at 100 %.
        compose.waitUntil(40_000) { actions().lastOrNull() == "stop" }
        assertTrue(scrobbles.last().second, scrobbles.last().second.contains("\"progress\":100.0"))
    }

    @Test
    fun aFilmWithoutATmdbMatchSendsNothing() {
        play(1)
        Thread.sleep(6_000)
        assertTrue(actions().toString(), actions().isEmpty())
    }
}
