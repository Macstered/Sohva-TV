package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.protocol.DeviceCode
import com.sohva.tv.feature.trakt.protocol.DevicePoll
import com.sohva.tv.feature.trakt.protocol.RefreshResult
import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.protocol.TraktApiClient
import com.sohva.tv.feature.trakt.protocol.TraktAuthClient
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktIdentityClient
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import com.sohva.tv.feature.trakt.protocol.TraktKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Spec 51 §11 "Unit": the auth, identity and API clients against a fake Trakt. */
class TraktProtocolTest {
    private val server = MockWebServer()
    private val base = OkHttpClient()
    private val credentials = TraktCredentials("fictional-client-id", "not-a-real-secret")
    private lateinit var auth: TraktAuthClient
    private lateinit var api: TraktApiClient

    @Before
    fun start() {
        server.start()
        auth = TraktAuthClient(base, credentials, server.url("/"))
        api = TraktApiClient(base, credentials, Dispatchers.Unconfined, server.url("/"), pageCap = 3)
    }

    @After
    fun stop() = server.close()

    private fun answer(code: Int, body: String = "{}", vararg headers: Pair<String, String>) =
        server.enqueue(MockResponse.Builder().code(code).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build())

    private fun failure(block: suspend () -> Unit): TraktException {
        try {
            runBlocking { block() }
        } catch (e: TraktException) {
            return e
        }
        fail("no failure")
        error("unreachable")
    }

    private val code = DeviceCode("dev-code", "ABCD1234", "https://trakt.tv/activate", 600, 5)

    @Test
    fun theDeviceCodeSendsOnlyTheClientIdAndAcceptsOnlyTheActivationPage() = runBlocking {
        answer(200, """{"device_code":"d","user_code":"U1","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}""")
        val c = auth.deviceCode()
        assertEquals("U1", c.userCode)
        val sent = server.takeRequest()
        assertEquals("""{"client_id":"fictional-client-id"}""", sent.body?.utf8())
        assertEquals("2", sent.headers["trakt-api-version"])
        for (bad in listOf("http://trakt.tv/activate", "https://trakt.tv.example/activate", "https://trakt.tv/activate?x=1", "https://trakt.tv:8443/activate", "https://trakt.tv/other")) {
            answer(200, """{"device_code":"d","user_code":"U1","verification_url":"$bad","expires_in":600,"interval":5}""")
            assertEquals(bad, TraktFailure.INVALID_RESPONSE, failure { auth.deviceCode() }.failure)
        }
        answer(200, """{"device_code":"d","user_code":"U1","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":900}""")
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { auth.deviceCode() }.failure)
    }

    @Test
    fun pollStatusesMapAndANamed400IsNotEndlessPending() = runBlocking {
        answer(400)
        assertEquals(DevicePoll.Pending, auth.poll(code))
        answer(400, """{"error":"authorization_pending"}""")
        assertEquals(DevicePoll.Pending, auth.poll(code))
        answer(400, """{"error":"invalid_client"}""")
        assertEquals(TraktFailure.CONFIGURATION, failure { auth.poll(code) }.failure)
        answer(404)
        assertEquals(DevicePoll.InvalidCode, auth.poll(code))
        answer(409)
        assertEquals(DevicePoll.AlreadyUsed, auth.poll(code))
        answer(410)
        assertEquals(DevicePoll.Expired, auth.poll(code))
        answer(418)
        assertEquals(DevicePoll.Denied, auth.poll(code))
        answer(429, "{}", "Retry-After" to "30")
        assertEquals(DevicePoll.SlowDown(30), auth.poll(code))
        answer(429)
        assertEquals(DevicePoll.SlowDown(10), auth.poll(code))
        answer(200, """{"access_token":"acc","refresh_token":"ref","token_type":"bearer","created_at":1000,"expires_in":7776000}""")
        val granted = auth.poll(code) as DevicePoll.Granted
        assertEquals((1000L + 7_776_000) * 1000, granted.tokens.expiresAt)
        assertFalse(granted.tokens.toString().contains("acc"))
    }

    @Test
    fun tokensAreCheckedAndInvalidGrantMeansSignInAgain() = runBlocking {
        answer(200, """{"access_token":"acc","refresh_token":"ref","token_type":"mac","created_at":1000,"expires_in":10}""")
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { auth.poll(code) }.failure)
        answer(400, """{"error":"invalid_grant"}""")
        assertEquals(RefreshResult.Reauthorize, auth.refresh("ref"))
        answer(200, """{"access_token":"a2","refresh_token":"r2","token_type":"bearer","created_at":2000,"expires_in":100}""")
        assertTrue(auth.refresh("ref") is RefreshResult.Refreshed)
        answer(503)
        assertEquals(TraktFailure.SERVICE, failure { auth.refresh("ref") }.failure)
    }

    @Test
    fun redirectsAreNeverFollowedAndOversizedBodiesRefused() = runBlocking {
        answer(302, "", "Location" to server.url("/elsewhere").toString())
        failure { auth.poll(code) }
        assertEquals(1, server.requestCount)
        answer(200, "x".repeat(70 * 1024))
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { auth.deviceCode() }.failure)
        val none = TraktAuthClient(base, TraktCredentials.NONE, server.url("/"))
        assertEquals(TraktFailure.CONFIGURATION, failure { none.deviceCode() }.failure)
    }

    @Test
    fun identityNeedsTheUuid() = runBlocking {
        val identity = TraktIdentityClient(base, credentials, server.url("/"))
        answer(200, """{"user":{"username":"viewer","ids":{"slug":"viewer","uuid":"0f1e2d3c"}}}""")
        val who = identity.identity("acc")
        assertEquals("0f1e2d3c", who.uuid)
        assertEquals("Bearer acc", server.takeRequest().headers["Authorization"])
        answer(200, """{"user":{"username":"viewer","ids":{"slug":"viewer"}}}""")
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { identity.identity("acc") }.failure)
        answer(401)
        assertEquals(TraktFailure.REAUTHORIZE, failure { identity.identity("acc") }.failure)
    }

    @Test
    fun scrobbleBodiesAndFailureMapping() = runBlocking {
        answer(201)
        api.scrobble("acc", TraktItem.Movie(TraktIds(tmdb = 603, imdb = "tt0133093")), ScrobbleAction.START, 12.5)
        val movie = server.takeRequest()
        assertTrue(movie.url.encodedPath.endsWith("/scrobble/start"))
        assertEquals("""{"movie":{"ids":{"tmdb":603,"imdb":"tt0133093"}},"progress":12.5}""", movie.body?.utf8())
        answer(409)
        api.scrobble("acc", TraktItem.Episode(TraktIds(tmdb = 1399), 1, 2), ScrobbleAction.STOP, 100.0)
        assertEquals("""{"show":{"ids":{"tmdb":1399}},"episode":{"season":1,"number":2},"progress":100.0}""", server.takeRequest().body?.utf8())
        val cases = mapOf(401 to TraktFailure.REAUTHORIZE, 403 to TraktFailure.CONFIGURATION, 429 to TraktFailure.RATE_LIMITED, 502 to TraktFailure.SERVICE, 422 to TraktFailure.REJECTED)
        for ((status, expected) in cases) {
            answer(status, "{}", "Retry-After" to "7")
            val e = failure { api.scrobble("acc", TraktItem.Movie(TraktIds(tmdb = 1)), ScrobbleAction.PAUSE, 5.0) }
            assertEquals(expected, e.failure)
            if (status == 429) assertEquals(7L, e.retryAfterSeconds)
        }
    }

    @Test
    fun playbackAndActivitiesAreRead() = runBlocking {
        answer(200, """[{"type":"movie","progress":42.5,"paused_at":"2026-09-20T10:00:00.000Z","movie":{"title":"X","ids":{"trakt":1,"tmdb":603}}},
            {"type":"episode","progress":10,"paused_at":"bad","episode":{"season":2,"number":3},"show":{"ids":{"tmdb":1399}}},
            {"type":"episode","progress":10,"episode":{"season":2},"show":{"ids":{"tmdb":1}}}]""")
        val paused = api.playback("acc")
        assertEquals(2, paused.size)
        assertEquals(42.5, paused[0].progress, 0.0)
        assertTrue(paused[0].pausedAt > 0)
        assertEquals(0L, paused[1].pausedAt)
        assertEquals("limit=500", server.takeRequest().url.query)
        answer(200, """{"movies":{"watched_at":"2026-09-01T00:00:00Z","paused_at":"2026-09-02T00:00:00Z"},"episodes":{"watched_at":"2026-09-03T00:00:00Z"}}""")
        val a = api.lastActivities("acc")
        assertEquals(a.episodesWatched, a.newest)
        assertEquals(0L, a.episodesPaused)
    }

    @Test
    fun watchedListsWalkEveryPageAndFailWhole() = runBlocking {
        answer(200, """[{"plays":2,"last_watched_at":"2026-09-01T00:00:00Z","movie":{"ids":{"tmdb":1}}}]""", "X-Pagination-Page-Count" to "2")
        answer(200, """[{"plays":1,"movie":{"ids":{"imdb":"tt0000002"}}}]""", "X-Pagination-Page-Count" to "2")
        assertEquals(2, api.watchedMovies("acc").size)
        assertEquals("page=1&limit=250", server.takeRequest().url.query)
        assertEquals("page=2&limit=250", server.takeRequest().url.query)
        // No header: one page.
        answer(200, """[{"last_watched_at":"2026-09-01T00:00:00Z","show":{"ids":{"tmdb":1399}},"seasons":[{"number":1,"episodes":[{"number":1,"plays":1},{"number":2,"plays":1}]}]}]""")
        val shows = api.watchedShows("acc")
        assertEquals(2, shows.single().episodes.size)
        assertEquals("extended=progress&page=1&limit=100", server.takeRequest().url.query)
        // A failing page fails the call; the cap stops a runaway walk.
        answer(200, "[]", "X-Pagination-Page-Count" to "2")
        answer(500)
        assertEquals(TraktFailure.SERVICE, failure { api.watchedMovies("acc") }.failure)
        repeat(3) { answer(200, "[]", "X-Pagination-Page-Count" to "9") }
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { api.watchedMovies("acc") }.failure)
    }

    @Test
    fun rowsParseTitlesImagesAndProgress() = runBlocking {
        answer(200, """[{"movie":{"title":"Film","year":2024,"overview":"About.","ids":{"trakt":5,"tmdb":9},"images":{"poster":["walter-r2.trakt.tv/p.jpg"],"fanart":["https://img.example/f.jpg"]}}},{"movie":{"ids":{"trakt":6}}}]""")
        val recs = api.recommendations("acc", TraktKind.MOVIE)
        assertEquals(1, recs.size)
        assertEquals("https://walter-r2.trakt.tv/p.jpg", recs[0].poster)
        assertEquals("https://img.example/f.jpg", recs[0].fanart)
        answer(200, """{"last_watched_at":"2026-09-01T00:00:00Z","next_episode":{"season":1,"number":4,"title":"Fourth"}}""")
        assertEquals(4, api.showProgress("acc", 77)?.nextNumber)
        answer(404)
        assertNull(api.showProgress("acc", 78))
        answer(200, """{"last_watched_at":"2026-09-01T00:00:00Z","next_episode":null}""")
        assertNull(api.showProgress("acc", 79)?.nextNumber)
    }
}
