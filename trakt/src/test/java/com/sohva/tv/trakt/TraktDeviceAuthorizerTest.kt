package com.sohva.tv.trakt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraktDeviceAuthorizerTest {
    private val tokens = TraktTokens("private-access", "private-refresh", Long.MAX_VALUE)
    private val identity = TraktIdentity("immutable-account", "visible-username")
    private val code = TraktDeviceCode("private-device", "TEST1234", "https://auth.trakt.tv/activate", 60, 5)

    @Test fun pendingAndSlowdownWaitBeforeRetryAndOnlyVerifyAfterAuthorization() = runTest {
        val auth = FakeDeviceAuth(code)
        val times = mutableListOf<Long>()
        auth.results += listOf(TraktPoll.Pending, TraktPoll.SlowDown(20), TraktPoll.Authorized(tokens))
        auth.onPoll = { times += testScheduler.currentTime }
        var lookups = 0
        val result = authorizer(auth, TraktIdentityLookup {
            assertEquals(3, auth.polls)
            assertSame(tokens, it)
            lookups++
            identity
        }).authorize {
            assertEquals(0, auth.polls)
            assertEquals("TEST1234", it.userCode)
            assertEquals("https://auth.trakt.tv/activate", it.verificationUrl)
            assertFalse(it.toString().contains("TEST1234"))
        }
        assertEquals(listOf(5_000L, 10_000L, 30_000L), times)
        assertEquals(1, lookups)
        assertSame(identity, result.identity)
        assertSame(tokens, result.tokens)
        assertFalse(result.toString().contains("private"))
        assertFalse(result.toString().contains("immutable"))
    }

    @Test fun terminalPollingStatusesStayDistinctAndNeverReadIdentity() = runTest {
        val cases = listOf(
            TraktPoll.Denied to TraktAuthorizationEnd.DENIED,
            TraktPoll.Expired to TraktAuthorizationEnd.EXPIRED,
            TraktPoll.InvalidCode to TraktAuthorizationEnd.INVALID_CODE,
            TraktPoll.AlreadyUsed to TraktAuthorizationEnd.ALREADY_USED,
        )
        for ((status, expected) in cases) {
            val auth = FakeDeviceAuth(code).apply { results += status }
            try {
                authorizer(auth).authorize { }
                fail("Expected terminal authorization status")
            } catch (error: TraktAuthorizationException) {
                assertEquals(expected, error.reason)
                assertNull(error.cause)
            }
            assertEquals(1, auth.polls)
        }
    }

    @Test fun timeoutStopsPendingPollsAtDeadline() = runTest {
        val auth = FakeDeviceAuth(TraktDeviceCode("device", "CODE", "https://auth.trakt.tv/activate", 12, 5))
        assertEquals(TraktAuthorizationEnd.EXPIRED, expiry { authorizer(auth).authorize { } })
        assertEquals(12_000L, testScheduler.currentTime)
        assertEquals(2, auth.polls)
    }

    @Test fun slowDeviceResponseDoesNotExtendExpiryAndFirstPollStillWaitsFullInterval() = runTest {
        val auth = FakeDeviceAuth(TraktDeviceCode("device", "CODE", "https://auth.trakt.tv/activate", 10, 5))
        auth.onDevice = { delay(3_000) }
        val times = mutableListOf<Long>()
        auth.onPoll = { times += testScheduler.currentTime }
        assertEquals(TraktAuthorizationEnd.EXPIRED, expiry { authorizer(auth).authorize { } })
        assertEquals(listOf(8_000L), times)
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test fun codeAlreadyExpiredInTransitDoesNotDisplayPrompt() = runTest {
        val auth = FakeDeviceAuth(code).apply { onDevice = { delay(60_001) } }
        var prompts = 0
        assertEquals(TraktAuthorizationEnd.EXPIRED, expiry { authorizer(auth).authorize { prompts++ } })
        assertEquals(0, prompts)
        assertEquals(0, auth.polls)
    }

    @Test fun cancellationInterruptsInflightPollAndNeverVerifiesAccount() = runTest {
        val entered = CompletableDeferred<Unit>()
        val auth = FakeDeviceAuth(code).apply { onPoll = { entered.complete(Unit); awaitCancellation() } }
        val job = launch { authorizer(auth).authorize { } }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, auth.polls)
    }

    @Test fun accessDeniedBeforeStartDoesNotRequestCode() = runTest {
        val auth = FakeDeviceAuth(code)
        assertEquals(TraktFailure.ACCESS_DENIED, failure { authorizer(auth, allowed = { false }).authorize { } })
        assertEquals(0, auth.codes)
    }

    @Test fun accessChangeWhileWaitingStopsBeforeNextHttpRequest() = runTest {
        val auth = FakeDeviceAuth(code)
        var allowed = true
        val job = async { failure { authorizer(auth, allowed = { allowed }).authorize { allowed = false } } }
        runCurrent()
        assertEquals(TraktFailure.ACCESS_DENIED, job.await())
        assertEquals(0, auth.polls)
    }

    @Test fun accessChangeDuringTokenExchangeDoesNotReadIdentity() = runTest {
        var allowed = true
        val auth = FakeDeviceAuth(code).apply {
            results += TraktPoll.Authorized(tokens)
            onPoll = { allowed = false }
        }
        assertEquals(TraktFailure.ACCESS_DENIED, failure { authorizer(auth, allowed = { allowed }).authorize { } })
    }

    @Test fun accessChangeDuringIdentityVerificationDoesNotReturnTokens() = runTest {
        var allowed = true
        val auth = FakeDeviceAuth(code).apply { results += TraktPoll.Authorized(tokens) }
        val lookup = TraktIdentityLookup { allowed = false; identity }
        assertEquals(TraktFailure.ACCESS_DENIED, failure { authorizer(auth, lookup) { allowed }.authorize { } })
    }

    @Test fun identityFailureNeverReturnsAnUnverifiedAuthorization() = runTest {
        val auth = FakeDeviceAuth(code).apply { results += TraktPoll.Authorized(tokens) }
        val lookup = TraktIdentityLookup { throw TraktException(TraktFailure.INVALID_RESPONSE) }
        assertEquals(TraktFailure.INVALID_RESPONSE, failure { authorizer(auth, lookup).authorize { } })
        assertEquals(1, auth.polls)
    }

    @Test fun networkFailureIsNotRetriedAsIfDeviceTokenWereStillUnconsumed() = runTest {
        val auth = FakeDeviceAuth(code).apply { onPoll = { throw TraktException(TraktFailure.NETWORK) } }
        assertEquals(TraktFailure.NETWORK, failure { authorizer(auth).authorize { } })
        assertEquals(1, auth.polls)
    }

    private fun TestScope.authorizer(
        auth: TraktAuth,
        lookup: TraktIdentityLookup = TraktIdentityLookup { error("Unexpected identity request") },
        allowed: suspend () -> Boolean = { true },
    ) = TraktDeviceAuthorizer(auth, lookup, { testScheduler.currentTime }, allowed)

    private suspend fun expiry(action: suspend () -> Any?): TraktAuthorizationEnd {
        try { action(); fail("Expected expiry") }
        catch (error: TraktAuthorizationException) { return error.reason }
        error("Unreachable")
    }

    private suspend fun failure(action: suspend () -> Any?): TraktFailure {
        try { action(); fail("Expected sanitized failure") }
        catch (error: TraktException) { return error.failure }
        error("Unreachable")
    }

    private class FakeDeviceAuth(private val code: TraktDeviceCode) : TraktAuth {
        val results = ArrayDeque<TraktPoll>()
        var onDevice: suspend () -> Unit = { }
        var onPoll: suspend () -> Unit = { }
        var codes = 0
        var polls = 0
        override suspend fun deviceCode(): TraktDeviceCode { codes++; onDevice(); return code }
        override suspend fun poll(code: TraktDeviceCode): TraktPoll {
            polls++; onPoll()
            return results.removeFirstOrNull() ?: TraktPoll.Pending
        }
        override suspend fun refresh(refreshToken: String): TraktTokens = error("Authorization must not refresh tokens")
    }
}
