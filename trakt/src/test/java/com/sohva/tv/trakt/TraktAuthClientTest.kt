package com.sohva.tv.trakt

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class TraktAuthClientTest {
    @Test fun retryAfterAcceptsSecondsAndHttpDatesWithoutRoundingDown() {
        assertEquals(90L, retryAfterSeconds("90", 0))
        assertEquals(120L, retryAfterSeconds("Thu, 01 Jan 1970 00:02:00 GMT", 1))
        assertEquals(1L, retryAfterSeconds("Thu, 01 Jan 1970 00:02:00 GMT", 120_000))
        assertNull(retryAfterSeconds("invalid", 0))
    }
    private val credentials = TraktAppCredentials("synthetic-client", "synthetic-secret", "https://example.invalid/registered-callback")
    private val code = TraktDeviceCode("synthetic-device", "ABCD1234", "https://auth.trakt.tv/activate", 600, 5)
    private val tokens = """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","token_type":"bearer","created_at":1700000000,"expires_in":604800}"""

    @Test fun deviceCodeUsesOnlyClientIdAndAcceptsOfficialActivationPage(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"device_code":"synthetic-device","user_code":"ABCD1234","verification_url":"https://auth.trakt.tv/activate","expires_in":600,"interval":5}"""))
            val result = TraktAuthClient(credentials, server.url("/")).deviceCode()
            assertEquals("ABCD1234", result.userCode)
            assertEquals(5, result.intervalSeconds)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/oauth/device/code", request.path)
            assertEquals("2", request.getHeader("trakt-api-version"))
            assertEquals(setOf("client_id"), Json.parseToJsonElement(request.body.readUtf8()).jsonObject.keys)
            assertFalse(result.toString().contains("ABCD"))
        }
    }

    @Test fun expectedPollingStatusesAreNotTreatedAsImmediateFailure(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktAuthClient(credentials, server.url("/"))
            listOf(400, 429, 404, 409, 410, 418).forEach { server.enqueue(MockResponse().setResponseCode(it).setHeader("Retry-After", "12")) }
            assertEquals(TraktPoll.Pending, client.poll(code))
            assertEquals(12L, (client.poll(code) as TraktPoll.SlowDown).retryAfterSeconds)
            assertEquals(TraktPoll.InvalidCode, client.poll(code))
            assertEquals(TraktPoll.AlreadyUsed, client.poll(code))
            assertEquals(TraktPoll.Expired, client.poll(code))
            assertEquals(TraktPoll.Denied, client.poll(code))
        }
    }

    @Test fun authorizationAndRefreshReturnBothTokensWithoutLoggingSecrets(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokens))
            server.enqueue(MockResponse().setBody(tokens.replace("synthetic-refresh", "rotated-refresh")))
            val client = TraktAuthClient(credentials, server.url("/"))
            val authorized = client.poll(code) as TraktPoll.Authorized
            assertEquals("synthetic-access", authorized.tokens.accessToken)
            assertEquals(1700604800000L, authorized.tokens.expiresAtMillis)
            assertFalse(authorized.toString().contains("synthetic"))
            assertFalse(credentials.toString().contains("synthetic-secret"))
            val refreshed = client.refresh(authorized.tokens.refreshToken)
            assertEquals("rotated-refresh", refreshed.refreshToken)
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("/oauth/token", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("refresh_token", body["grant_type"]!!.jsonPrimitive.content)
            assertEquals(credentials.redirectUri, body["redirect_uri"]!!.jsonPrimitive.content)
            assertEquals("synthetic-refresh", body["refresh_token"]!!.jsonPrimitive.content)
        }
    }

    @Test fun invalidRefreshRequiresReauthorizationAndIsNotRetried(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant","error_description":"synthetic-private-detail"}"""))
            val error = failure { TraktAuthClient(credentials, server.url("/")).refresh("old-refresh") }
            assertEquals(TraktFailure.REAUTHORIZE, error.failure)
            assertFalse(error.toString().contains("synthetic-private-detail"))
            assertNull(error.cause)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun redirectsNeverForwardDeviceCodesOrCredentials(): Unit = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { destination ->
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture")))
            val error = failure { TraktAuthClient(credentials, server.url("/")).poll(code) }
            assertEquals(TraktFailure.INVALID_RESPONSE, error.failure)
            assertEquals(0, destination.requestCount)
            assertEquals(1, server.requestCount)
        } }
    }

    @Test fun unexpectedActivationHostsAndOversizedResponsesAreRejected(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"device_code":"code","user_code":"ABCD","verification_url":"https://evil.invalid/activate","expires_in":600,"interval":5}"""))
            server.enqueue(MockResponse().setBody("x".repeat(70 * 1024)))
            val client = TraktAuthClient(credentials, server.url("/"))
            assertEquals(TraktFailure.INVALID_RESPONSE, failure { client.deviceCode() }.failure)
            assertEquals(TraktFailure.INVALID_RESPONSE, failure { client.poll(code) }.failure)
        }
    }

    @Test fun invalidConfigurationIsNotAnEndlessPendingCode(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_client"}"""))
            assertEquals(TraktFailure.CONFIGURATION, failure { TraktAuthClient(credentials, server.url("/")).poll(code) }.failure)
        }
    }

    @Test fun invalidHeaderValueCannotEscapeInAnUnsanitizedException(): Unit = runBlocking {
        MockWebServer().use { server ->
            val malformed = TraktAppCredentials("private-client-\u2603", "private-secret", credentials.redirectUri)
            val error = failure { TraktAuthClient(malformed, server.url("/")).deviceCode() }
            assertEquals(TraktFailure.CONFIGURATION, error.failure)
            assertFalse(error.toString().contains("private"))
            assertNull(error.cause)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun cancellationStopsAnInFlightRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                withTimeout(200) { TraktAuthClient(credentials, server.url("/")).poll(code) }
                fail("Expected cancellation")
            } catch (_: TimeoutCancellationException) { }
            assertTrue(server.requestCount <= 1)
        }
    }

    @Test fun pollScheduleWaitsSlowsDownAndExpiresWithoutWallClockDependency() {
        val schedule = TraktPollSchedule(code, 1000)
        assertFalse(schedule.ready(5999))
        assertTrue(schedule.ready(6000))
        schedule.polled(6000, TraktPoll.SlowDown(null))
        assertEquals(10_000, schedule.waitMillis(6000))
        schedule.polled(16_000, TraktPoll.SlowDown(30))
        assertEquals(30_000, schedule.waitMillis(16_000))
        assertTrue(schedule.expired(601_000))
        assertFalse(schedule.ready(601_000))
        assertEquals(0, schedule.waitMillis(601_000))
    }

    private suspend fun failure(action: suspend () -> Any?): TraktException {
        try { action(); fail("Expected typed failure") } catch (error: TraktException) { return error }
        error("Unreachable")
    }
}
