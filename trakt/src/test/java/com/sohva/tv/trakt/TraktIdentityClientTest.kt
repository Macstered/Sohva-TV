package com.sohva.tv.trakt

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class TraktIdentityClientTest {
    private val tokens = TraktTokens("private-access", "private-refresh", Long.MAX_VALUE)
    private val body = """{"user":{"username":"display-name","ids":{"slug":"mutable-slug","uuid":"stable-id"}},"account":{"email":"not-for-logs"}}"""

    @Test fun getSettingsUsesAccessTokenAndClientIdOnlyAndRetainsStableIdentity(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            val identity = TraktIdentityClient("public-client-id", server.url("/")).identify(tokens)
            assertEquals("stable-id", identity.accountId)
            assertEquals("display-name", identity.username)
            assertEquals("TraktIdentity([redacted])", identity.toString())
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/users/settings", request.path)
            assertEquals("Bearer private-access", request.getHeader("Authorization"))
            assertEquals("public-client-id", request.getHeader("trakt-api-key"))
            assertEquals("2", request.getHeader("trakt-api-version"))
            assertEquals(0L, request.bodySize)
            assertFalse(request.headers.toString().contains("private-refresh"))
            assertFalse(request.headers.toString().contains("client_secret"))
        }
    }

    @Test fun missingOrMalformedStableIdentityNeverFallsBackToUsername(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktIdentityClient("client", server.url("/"))
            val malformed = listOf(
                "{}", "not-json", """{"user":{"username":"name","ids":{"slug":"name"}}}""",
                body.replace("\"stable-id\"", "123"), body.replace("stable-id", ""),
                body.replace("stable-id", "x".repeat(257)), body.replace("stable-id", "two words"),
                body.replace("display-name", ""), body.replace("display-name", "line\\nname"),
            )
            for (value in malformed) {
                server.enqueue(MockResponse().setBody(value))
                assertEquals(TraktFailure.INVALID_RESPONSE, failure { client.identify(tokens) }.failure)
            }
        }
    }

    @Test fun redirectsNeverForwardAccountBearerToken(): Unit = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { destination ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/capture")))
            assertEquals(TraktFailure.INVALID_RESPONSE, failure { TraktIdentityClient("client", server.url("/")).identify(tokens) }.failure)
            assertEquals(1, server.requestCount)
            assertEquals(0, destination.requestCount)
        } }
    }

    @Test fun rateLimitsAndAuthFailuresStaySanitizedAndAreNotAutomaticallyRetried(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktIdentityClient("client", server.url("/"))
            for ((status, expected) in listOf(401 to TraktFailure.REAUTHORIZE, 403 to TraktFailure.ACCESS_DENIED,
                429 to TraktFailure.RATE_LIMITED, 503 to TraktFailure.SERVICE)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After", "30").setBody("private-response"))
                val error = failure { client.identify(tokens) }
                assertEquals(expected, error.failure)
                assertEquals(30L, error.retryAfterSeconds)
                assertFalse(error.toString().contains("private"))
                assertNull(error.cause)
            }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun oversizedResponseIsRejectedBeforeJsonParsing(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(70 * 1024)))
            assertEquals(TraktFailure.INVALID_RESPONSE, failure { TraktIdentityClient("client", server.url("/")).identify(tokens) }.failure)
        }
    }

    @Test fun cancellationStopsIdentityRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                withTimeout(200) { TraktIdentityClient("client", server.url("/")).identify(tokens) }
                fail("Expected cancellation")
            } catch (_: TimeoutCancellationException) { }
            assertTrue(server.requestCount <= 1)
        }
    }

    private suspend fun failure(action: suspend () -> Any?): TraktException {
        try { action(); fail("Expected sanitized failure") }
        catch (error: TraktException) { return error }
        error("Unreachable")
    }
}
