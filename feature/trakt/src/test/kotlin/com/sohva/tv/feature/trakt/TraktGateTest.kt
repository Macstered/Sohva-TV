package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.protocol.DeviceCode
import com.sohva.tv.feature.trakt.protocol.DevicePoll
import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.protocol.TraktApiClient
import com.sohva.tv.feature.trakt.protocol.TraktAuthClient
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktGate
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Decision "Trakt request pacing": one door for every Trakt request of the app. */
class TraktGateTest {
    private val server = MockWebServer()
    private val arrivals = ConcurrentLinkedQueue<Pair<String, Long>>()

    @Volatile private var answer: () -> MockResponse = { MockResponse.Builder().code(201).body("{}").build() }

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrivals += request.url.encodedPath to System.nanoTime() / 1_000_000
                return answer()
            }
        }
        server.start()
    }

    @After
    fun stop() = server.close()

    private val creds = TraktCredentials("fictional-client-id", "not-a-real-secret")
    private val movie = TraktItem.Movie(TraktIds(tmdb = 603))

    private fun api(gate: TraktGate) = TraktApiClient(OkHttpClient(), creds, Dispatchers.Unconfined, server.url("/"), gate = gate)

    @Test
    fun aRateLimitStopsEveryLaterRequestUntilItsWaitIsOver() = runBlocking {
        val gate = TraktGate()
        answer = { MockResponse.Builder().code(429).addHeader("Retry-After", "30").body("{}").build() }
        val first = try {
            api(gate).lastActivities("acc")
            fail("expected a rate limit")
            error("unreachable")
        } catch (e: TraktException) {
            e
        }
        assertEquals(TraktFailure.RATE_LIMITED, first.failure)
        // A second client on the same gate (the sign-in's) is refused without a request.
        val auth = TraktAuthClient(OkHttpClient(), creds, server.url("/"), gate)
        val second = try {
            auth.deviceCode()
            fail("expected a rate limit")
            error("unreachable")
        } catch (e: TraktException) {
            e
        }
        assertEquals(TraktFailure.RATE_LIMITED, second.failure)
        assertTrue("${second.retryAfterSeconds}", (second.retryAfterSeconds ?: 0) in 29..30)
        assertEquals(1, arrivals.size)
        assertEquals(1L, gate.requests)
    }

    @Test
    fun writesLeaveAtLeastASecondApart() = runBlocking {
        val gate = TraktGate()
        val client = api(gate)
        client.scrobble("acc", movie, ScrobbleAction.START, 1.0)
        client.scrobble("acc", movie, ScrobbleAction.PAUSE, 2.0)
        client.scrobble("acc", movie, ScrobbleAction.STOP, 100.0)
        val times = arrivals.map { it.second }
        assertEquals(3, times.size)
        assertTrue("gaps ${times.zipWithNext { a, b -> b - a }}", times.zipWithNext { a, b -> b - a }.all { it >= TraktGate.WRITE_GAP_MS - 20 })
    }

    @Test
    fun aDevicePollsSlowDownDoesNotCloseTheDoor() = runBlocking {
        val gate = TraktGate()
        answer = { MockResponse.Builder().code(429).body("{}").build() }
        val auth = TraktAuthClient(OkHttpClient(), creds, server.url("/"), gate)
        val poll = auth.poll(DeviceCode("dev", "FICT1234", "https://trakt.tv/activate", 600, 5))
        assertTrue(poll is DevicePoll.SlowDown)
        assertEquals(0L, gate.waitSeconds())
    }
}
