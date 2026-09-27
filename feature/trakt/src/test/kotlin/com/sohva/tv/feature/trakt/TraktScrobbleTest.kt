package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.scrobble.TraktItems
import com.sohva.tv.feature.trakt.scrobble.TraktScrobbler
import com.sohva.tv.feature.trakt.scrobble.TraktScrobbles
import com.sohva.tv.feature.trakt.store.TraktAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 51 §11 "Identity mapping" and "Scrobbler". */
@OptIn(ExperimentalCoroutinesApi::class)
class TraktScrobbleTest {
    private val movie = TraktItem.Movie(TraktIds(tmdb = 603))

    @Test
    fun idsMapOnlyFromTheRulesAccepted() {
        assertEquals(TraktItem.Movie(TraktIds(imdb = "tt0133093")), TraktItems.addon("movie", "tt0133093", "tt0133093", null, null))
        assertEquals(TraktItem.Movie(TraktIds(tmdb = 603)), TraktItems.addon("movie", "tmdb:603", "tmdb:603", null, null))
        assertNull(TraktItems.addon("movie", "kitsu:1", "kitsu:1", null, null))
        assertNull(TraktItems.addon("channel", "tt0133093", "x", null, null))
        assertEquals(TraktItem.Episode(TraktIds(imdb = "tt0944947"), 2, 5), TraktItems.addon("series", "tt0944947", "tt0944947:2:5", null, null))
        assertEquals(TraktItem.Episode(TraktIds(imdb = "tt0944947"), 1, 1), TraktItems.addon("series", "tt0944947", "whatever", 1, 1))
        assertNull(TraktItems.addon("series", "tt0944947", "tt0944947", null, null))
        assertEquals(603L, TraktItems.tmdbId("tmdb", "tmdb:603"))
        assertEquals(603L, TraktItems.tmdbId("tmdb", "603"))
        // TVmaze ids look like TMDB ids and are never accepted (lesson 5).
        assertNull(TraktItems.tmdbId("tvmaze", "603"))
    }

    private class Sent(val list: MutableList<Pair<ScrobbleAction, Double>> = ArrayList())

    @Test
    fun startPauseAfterTheSettleStopAtTheEnd() = runTest {
        val sent = Sent()
        val s = TraktScrobbler("p", { _, _, a, pct -> sent.list += a to pct }, { _, _ -> }, this)
        s.begin(movie)
        s.playing(true, 0, 100_000)
        s.playing(true, 1_000, 100_000)
        s.playing(false, 20_000, 100_000)
        advanceTimeBy(2_000)
        assertEquals(listOf(ScrobbleAction.START), sent.list.map { it.first })
        advanceTimeBy(600)
        s.playing(true, 20_000, 100_000)
        s.ended()
        s.release(100_000, 100_000)
        assertEquals(listOf(ScrobbleAction.START to 0.0, ScrobbleAction.PAUSE to 20.0, ScrobbleAction.START to 20.0, ScrobbleAction.STOP to 100.0), sent.list)
    }

    @Test
    fun aRebufferIsNotAPauseAndLeavingSendsAStop() = runTest {
        val sent = Sent()
        val s = TraktScrobbler("p", { _, _, a, pct -> sent.list += a to pct }, { _, _ -> }, this)
        s.playing(true, 0, 0)
        // The item resolves after playback began: START at once.
        s.begin(movie)
        s.playing(false, 5_000, 50_000)
        advanceTimeBy(1_000)
        s.playing(true, 5_000, 50_000)
        advanceTimeBy(5_000)
        s.release(25_000, 50_000)
        assertEquals(listOf(ScrobbleAction.START to 0.0, ScrobbleAction.STOP to 50.0), sent.list)
    }

    @Test
    fun aTitleWithoutAnItemSendsNothing() = runTest {
        val sent = Sent()
        val s = TraktScrobbler("p", { _, _, a, pct -> sent.list += a to pct }, { _, _ -> }, this)
        s.begin(null)
        s.playing(true, 0, 10)
        s.ended()
        assertTrue(sent.list.isEmpty())
    }

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun host(prefs: FakePrefs = FakePrefs()) = TraktHost(
        TraktCredentials("fictional-client-id", "not-a-real-secret"), { OkHttpClient() }, store(prefs), FakeClock(),
        TraktDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined), { true }, FakeLog(), CoroutineScope(Dispatchers.Unconfined),
        monotonic = { 0L }, forgetCache = {}, authOrigin = server.url("/"), apiOrigin = server.url("/"),
    ).also { it.store.saveAccount("p", TraktAccount("viewer", "u", TraktTokens("acc", "ref", Long.MAX_VALUE / 2, TraktHost.DAY_MS * 90), false)) }

    private fun answer(code: Int) = server.enqueue(MockResponse.Builder().code(code).body("{}").build())

    @Test
    fun theQueueKeepsTheLatestPerTitleStopsAtAnOutageAndDropsRejections() = runBlocking {
        val h = host()
        val q = TraktScrobbles(h)
        val episode = TraktItem.Episode(TraktIds(tmdb = 1399), 1, 1)
        // Offline: nothing leaves, the latest report per title stays, in order.
        answer(503)
        q.submit("p", movie, ScrobbleAction.START, 1.0).join()
        answer(503)
        q.submit("p", episode, ScrobbleAction.START, 2.0).join()
        answer(503)
        q.submit("p", movie, ScrobbleAction.PAUSE, 3.0).join()
        assertEquals(listOf(episode to ScrobbleAction.START, movie to ScrobbleAction.PAUSE), q.load("p").map { it.item to it.action })
        // Back online: the first is rejected for good and dropped, the second delivered.
        answer(422)
        answer(201)
        q.deliver("p")
        assertTrue(q.load("p").isEmpty())
    }

    @Test
    fun aKilledPlaybackIsPausedAtTheNextStart() = runBlocking {
        val h = host()
        val q = TraktScrobbles(h)
        answer(201)
        q.submit("p", movie, ScrobbleAction.START, 10.0).join()
        q.active("p", 42.0)
        // A new process: the stored START is closed with a PAUSE at the last known percentage.
        val after = TraktScrobbles(h)
        answer(201)
        after.closeInterrupted("p")
        server.takeRequest()
        val pause = server.takeRequest()
        assertTrue(pause.url.encodedPath.endsWith("/scrobble/pause"))
        assertTrue(pause.body?.utf8().orEmpty(), pause.body?.utf8().orEmpty().contains("\"progress\":10.0"))
        assertNull(h.store.secret("active:p"))
    }
}
