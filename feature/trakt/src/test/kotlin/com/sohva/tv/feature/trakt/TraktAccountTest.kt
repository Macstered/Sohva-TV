package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.account.SignInFailure
import com.sohva.tv.feature.trakt.account.SignInResult
import com.sohva.tv.feature.trakt.account.TraktSignIn
import com.sohva.tv.feature.trakt.protocol.TraktAuthClient
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktIdentity
import com.sohva.tv.feature.trakt.protocol.TraktIdentityClient
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.store.TraktAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 51 §11 "Authorizer" and the account rules (FR-02, -08..-11, §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class TraktAccountTest {
    private val server = MockWebServer()
    private val base = OkHttpClient()
    private val credentials = TraktCredentials("fictional-client-id", "not-a-real-secret")

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun answer(code: Int, body: String = "{}") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun deviceCode(expires: Int = 600, interval: Int = 5) =
        answer(200, """{"device_code":"d","user_code":"U1","verification_url":"https://trakt.tv/activate","expires_in":$expires,"interval":$interval}""")

    private val tokens = """{"access_token":"acc","refresh_token":"ref","token_type":"bearer","created_at":1000,"expires_in":7776000}"""
    private val user = """{"user":{"username":"viewer","ids":{"uuid":"u-1"}}}"""

    private fun TestScope.signIn(allowed: () -> Boolean = { true }) = TraktSignIn(
        TraktAuthClient(base, credentials, server.url("/")),
        TraktIdentityClient(base, credentials, server.url("/")),
        { currentTime },
    ) { allowed() }

    @Test
    fun pendingWaitsAFullIntervalThenTheAccountIsRead() = runTest {
        deviceCode(interval = 5)
        answer(400, """{"error":"authorization_pending"}""")
        answer(429)
        answer(200, tokens)
        answer(200, user)
        var prompted = false
        val result = signIn().run { prompted = true }
        assertTrue(prompted)
        val ok = result as SignInResult.Success
        assertEquals("u-1", ok.identity.uuid)
        // One interval before the first poll, one after Pending, then the slowed interval (5 + 5 s).
        assertEquals(5_000L + 5_000L + 10_000L, currentTime)
    }

    @Test
    fun terminalAnswersAreDistinctAndNeverReadTheAccount() = runTest {
        for ((status, reason) in listOf(418 to SignInFailure.DECLINED, 410 to SignInFailure.EXPIRED, 404 to SignInFailure.UNUSABLE, 409 to SignInFailure.UNUSABLE)) {
            deviceCode()
            answer(status)
            assertEquals(SignInResult.Failed(reason), signIn().run {})
        }
        assertEquals(8, server.requestCount)
    }

    @Test
    fun theDeadlineStopsPollingAndAccessIsCheckedEveryStep() = runTest {
        deviceCode(expires = 12, interval = 5)
        // Polls at 5 s and 10 s; at 15 s the 12 s code has expired.
        repeat(2) { answer(400) }
        assertEquals(SignInResult.Failed(SignInFailure.EXPIRED), signIn().run {})
        assertEquals(3, server.requestCount)
        // Access lost while waiting: no further request.
        var allowed = true
        deviceCode()
        val before = server.requestCount
        val result = signIn { allowed }.run { allowed = false }
        assertEquals(SignInResult.Failed(SignInFailure.ACCESS), result)
        assertEquals(before + 1, server.requestCount)
    }

    @Test
    fun aNetworkFailureEndsTheAttempt() = runTest {
        deviceCode()
        server.close()
        assertEquals(SignInResult.Failed(SignInFailure.OTHER), signIn().run {})
    }

    private fun host(prefs: FakePrefs, clock: FakeClock, forgotten: MutableList<String> = ArrayList()) = TraktHost(
        credentials, { base }, store(prefs), clock, TraktDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined), { true }, FakeLog(),
        kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined), monotonic = { 0L }, forgetCache = { forgotten += it },
    )

    @Test
    fun accountsAreStoredEncryptedAndADifferentAccountStartsClean() = runBlocking {
        val prefs = FakePrefs()
        val forgotten = ArrayList<String>()
        val h = host(prefs, FakeClock(), forgotten)
        val t = TraktTokens("acc", "ref", Long.MAX_VALUE / 2, TraktHost.DAY_MS * 90)
        h.saveSignIn("default", SignInResult.Success(t, TraktIdentity("u-1", "viewer")))
        assertFalse(prefs.values.values.any { it.contains("acc") || it.contains("viewer") })
        h.store.putSecret("pending:default", "[1]")
        h.saveSignIn("default", SignInResult.Success(t, TraktIdentity("u-1", "viewer")))
        assertEquals("[1]", h.store.secret("pending:default"))
        h.saveSignIn("default", SignInResult.Success(t, TraktIdentity("u-2", "other")))
        assertNull(h.store.secret("pending:default"))
        assertEquals(listOf("default"), forgotten)
        h.disconnect("default")
        assertNull(h.store.account("default"))
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun tokensRefreshNearExpiryAndInvalidGrantAsksToSignInAgain() = runBlocking {
        val clock = FakeClock()
        val h = TraktHost(
            credentials, { base }, store(), clock, TraktDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined), { true }, FakeLog(),
            kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined), monotonic = { 0L }, forgetCache = {}, authOrigin = server.url("/"), apiOrigin = server.url("/"),
        )
        // A 24-hour token 13 hours before expiry is not refreshed (FR-09: the margin is half the lifetime).
        h.store.saveAccount("default", TraktAccount("viewer", "u-1", TraktTokens("acc", "ref", clock.now + 13 * 3_600_000L, TraktHost.DAY_MS), false))
        assertEquals("acc", h.tokens("default")?.access)
        assertEquals(0, server.requestCount)
        // A three-month token within a day of expiry is refreshed once.
        h.store.saveAccount("other", TraktAccount("viewer", "u-1", TraktTokens("old", "ref", clock.now + 3_600_000L, TraktHost.DAY_MS * 90), false))
        answer(200, """{"access_token":"new","refresh_token":"r2","token_type":"bearer","created_at":${clock.now / 1000},"expires_in":7776000}""")
        assertEquals("new", h.tokens("other")?.access)
        assertEquals("new", h.tokens("other")?.access)
        assertEquals(1, server.requestCount)
        // invalid_grant: the tokens go, the account stays and says so.
        h.store.saveAccount("third", TraktAccount("viewer", "u-1", TraktTokens("old", "ref", clock.now, TraktHost.DAY_MS * 90), false))
        answer(400, """{"error":"invalid_grant"}""")
        assertNull(h.tokens("third"))
        val flagged = h.store.account("third")!!
        assertTrue(flagged.reauthorize)
        assertNull(flagged.tokens)
    }
}
