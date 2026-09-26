package com.sohva.tv.feature.sport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.sport.provider.CacheState
import com.sohva.tv.feature.sport.provider.SportsHttp
import com.sohva.tv.feature.sport.provider.SportsRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 60 §11 "Cache": fresh hits cost nothing, stale data covers outages for 24 hours, the key and the quota come first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SportsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java).allowMainThreadQueries().build()
    private val server = MockWebServer()
    private var now = 1_790_400_000_000L // 2026-09-26 in Helsinki
    private var key: String? = "fictional-key"
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) = Unit
        override fun error(event: String, message: String?, error: Throwable?) = Unit
        override fun snapshot(): List<String> = emptyList()
    }
    private lateinit var repo: SportsRepository
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2026, 9, 26)

    private fun day(vararg leagues: Int) = """{"errors":[],"response":[${leagues.joinToString(",") { l ->
        """{"fixture":{"id":${1000 + l},"timestamp":1790448300,"status":{"short":"NS"}},"league":{"id":$l,"name":"L$l"},"teams":{"home":{"name":"H$l"},"away":{"name":"A$l"}},"goals":{}}"""
    }}]}"""

    private fun answer(body: String, remaining: Int? = 97) =
        MockResponse.Builder().body(body).apply { if (remaining != null) addHeader("x-ratelimit-requests-remaining", "$remaining") }.build()

    @Before
    fun start() {
        server.start()
        repo = SportsRepository(db.sport(), SportsHttp(OkHttpClient()) { server.url("/${it.provider}/") }, { key }, clock, Dispatchers.Unconfined, log)
    }

    @After
    fun stop() {
        server.close()
        db.close()
    }

    @Test
    fun aFreshDayCostsNothingAndOtherCompetitionsAreAlreadyThere() = runBlocking {
        server.enqueue(answer(day(39, 140)))
        val first = repo.day(SportType.FOOTBALL, today, zone, setOf("39"))
        assertEquals(CacheState.MISS, first.state)
        assertEquals(listOf("api-sports:football:1039"), first.events.map { it.id })
        assertEquals(97, first.quotaRemaining)
        val request = server.takeRequest()
        assertEquals("/football/fixtures?date=2026-09-26&timezone=Europe%2FHelsinki", request.target)
        assertEquals("fictional-key", request.headers["x-apisports-key"])
        // Following La Liga too is a database read, not a request (§9).
        now += 10 * 60_000
        val second = repo.day(SportType.FOOTBALL, today, zone, setOf("39", "140"))
        assertEquals(CacheState.HIT, second.state)
        assertEquals(2, second.events.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anOutageShowsTheSavedDayForADayThenFails() = runBlocking {
        server.enqueue(answer(day(39)))
        repo.day(SportType.FOOTBALL, today, zone, setOf("39"))
        now += 21 * 60_000
        server.enqueue(MockResponse.Builder().code(503).build())
        val stale = repo.day(SportType.FOOTBALL, today, zone, setOf("39"))
        assertEquals(CacheState.STALE, stale.state)
        assertEquals(1, stale.events.size)
        now += 25 * 60 * 60_000L
        server.enqueue(MockResponse.Builder().code(503).build())
        try {
            repo.day(SportType.FOOTBALL, today, zone, setOf("39"))
            fail("no fallback after the stale window")
        } catch (e: SportsException) {
            assertEquals(SportsProblem.HTTP, e.problem)
            assertEquals(503, e.status)
        }
    }

    @Test
    fun noKeyFailsBeforeAnyRequest() = runBlocking {
        key = null
        try {
            repo.day(SportType.ICE_HOCKEY, today, zone, setOf("16"))
            fail()
        } catch (e: SportsException) {
            assertEquals(SportsProblem.KEY_MISSING, e.problem)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aSpentQuotaStopsThatSportUntilTheNextUtcDay() = runBlocking {
        server.enqueue(answer(day(39), remaining = 0))
        repo.day(SportType.FOOTBALL, today, zone, setOf("39"))
        assertEquals(0, repo.quotas()[SportType.FOOTBALL])
        now += 30 * 60_000
        // Stale data stays on screen; no request is made.
        assertEquals(CacheState.STALE, repo.day(SportType.FOOTBALL, today, zone, setOf("39")).state)
        assertEquals(1, server.requestCount)
        // Another sport has its own allowance.
        server.enqueue(answer("""{"response":[]}"""))
        repo.day(SportType.ICE_HOCKEY, today, zone, setOf("16"))
        assertEquals(2, server.requestCount)
        // The provider's own "request limit" error counts the same way.
        now += 24 * 60 * 60_000L
        server.enqueue(answer("""{"errors":{"requests":"You have reached the request limit for the day"},"response":[]}""", remaining = null))
        try {
            repo.day(SportType.ICE_HOCKEY, today.plusDays(1), zone, setOf("16"))
            fail()
        } catch (e: SportsException) {
            assertEquals(SportsProblem.QUOTA_EXHAUSTED, e.problem)
        }
        try {
            repo.day(SportType.ICE_HOCKEY, today.plusDays(1), zone, setOf("16"))
            fail()
        } catch (e: SportsException) {
            assertEquals(SportsProblem.QUOTA_EXHAUSTED, e.problem)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun onlyFootballAsksForCurrentSeasonsAndOversizedAnswersFail() = runBlocking {
        server.enqueue(answer("""{"response":[{"league":{"id":39,"name":"Premier League"},"country":{"name":"England"}}]}"""))
        assertEquals(1, repo.competitions(SportType.FOOTBALL).size)
        assertEquals("/football/leagues?current=true", server.takeRequest().target)
        server.enqueue(answer("""{"response":[]}"""))
        repo.competitions(SportType.ICE_HOCKEY)
        assertEquals("/hockey/leagues", server.takeRequest().target)
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray((SportsHttp.MAX_BODY + 1).toInt()))).build())
        try {
            repo.day(SportType.RUGBY, today, zone, setOf("1"))
            fail()
        } catch (e: SportsException) {
            assertEquals(SportsProblem.TOO_LARGE, e.problem)
        }
    }

    @Test
    fun sportsWithoutCompetitionsKeepEveryEvent() = runBlocking {
        server.enqueue(answer("""{"response":[{"id":4,"date":"2026-09-26T20:00:00+00:00","status":{"short":"NS"},"fighters":{"first":{"name":"A"},"second":{"name":"B"}}}]}"""))
        val mma = repo.day(SportType.MMA, today, zone, null)
        assertEquals(1, mma.events.size)
        assertTrue(server.takeRequest().target.startsWith("/mma/fights?date=2026-09-26"))
    }
}
