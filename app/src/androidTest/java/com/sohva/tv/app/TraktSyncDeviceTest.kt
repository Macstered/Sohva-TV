package com.sohva.tv.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.store.TraktAccount
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 51 §11 "Sync": once Home has settled, the loop syncs the connected profile from a fake
 * Trakt into the database and stores Watch next and Recommended; while video plays, it waits.
 * The account and titles are fictional.
 */
@RunWith(AndroidJUnit4::class)
class TraktSyncDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val trakt = MockWebServer()
    private val paths = ConcurrentLinkedQueue<String>()
    private val at = "2026-09-01T10:00:00.000Z"
    private val bodies = mapOf(
        "sync/last_activities" to """{"movies":{"watched_at":"$at","paused_at":"$at"},"episodes":{"watched_at":"$at","paused_at":"$at"}}""",
        "sync/watched/movies" to """[{"plays":1,"last_watched_at":"$at","movie":{"ids":{"trakt":1,"tmdb":603}}}]""",
        "sync/watched/shows" to """[{"last_watched_at":"$at","show":{"ids":{"trakt":10,"tmdb":1399}},"seasons":[{"number":1,"episodes":[{"number":1,"plays":1,"last_watched_at":"$at"}]}]}]""",
        "sync/playback" to """[{"type":"movie","progress":35.0,"paused_at":"$at","movie":{"ids":{"tmdb":604}}}]""",
        "shows/10/progress/watched" to """{"last_watched_at":"$at","next_episode":{"season":1,"number":2,"title":"Second"}}""",
        "shows/10" to """{"title":"A fictional show","ids":{"trakt":10,"tmdb":1399},"images":{"poster":["img.example/p.jpg"]}}""",
        "recommendations/movies" to """[{"title":"A fictional film","ids":{"trakt":5}}]""",
        "recommendations/shows" to "[]",
    )

    private val servers = object : ExternalResource() {
        override fun before() {
            trakt.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath.removePrefix("/")
                    paths += path
                    val body = bodies[path] ?: return MockResponse.Builder().code(404).body("{}").build()
                    return MockResponse.Builder().code(200).body(body).build()
                }
            }
            trakt.start()
            // Held until a test lets it go, so a loop the app starts on its own waits too.
            graph.playbackActive.value = true
            val host = graph.trakt!!
            host.useTestServer(trakt.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
            host.store.saveAccount(
                graph.data.profiles.activeId,
                TraktAccount("fictional-viewer", "uuid-1", TraktTokens("not-a-real-token", "not-a-real-refresh", System.currentTimeMillis() + 30 * TraktHost.DAY_MS, 90 * TraktHost.DAY_MS), false),
            )
        }

        override fun after() {
            graph.traktLoop?.stop()
            graph.playbackActive.value = false
            trakt.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(servers).around(compose)

    private fun home() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTagExists(RailItem.MOVIES.tag) }
        runBlocking { graph.continueFeed.awaitSettled() }
    }

    @Test
    fun theLoopSyncsTheConnectedProfileAfterHomeSettles() {
        home()
        val profile = graph.data.profiles.activeId
        graph.playbackActive.value = false
        graph.traktLoop!!.start(profile)
        compose.waitUntil(15_000) { runBlocking { graph.data.traktState.count(profile) } == 3 }
        compose.waitUntil(10_000) { runBlocking { graph.trakt!!.shelves.read(profile, TraktShelfKind.WATCH_NEXT) } != null }
        val next = runBlocking { graph.trakt!!.shelves.read(profile, TraktShelfKind.WATCH_NEXT) }!!.cards.single()
        assertEquals(2, next.number)
        compose.waitUntil(10_000) { runBlocking { graph.trakt!!.shelves.read(profile, TraktShelfKind.RECOMMENDED) } != null }
        assertFalse(runBlocking { graph.traktSync!!.firstSyncPending(profile) })
    }

    @Test
    fun theLoopWaitsWhileVideoPlays() {
        home()
        val profile = graph.data.profiles.activeId
        graph.traktLoop!!.start(profile)
        Thread.sleep(3_000)
        assertFalse(paths.toString(), paths.contains("sync/last_activities"))
        graph.playbackActive.value = false
        compose.waitUntil(15_000) { paths.contains("sync/last_activities") }
    }
}
