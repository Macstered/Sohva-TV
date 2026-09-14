package com.sohva.tv.trakt

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** User-visible pairing material only. Never exposes the device_code or client secret. */
class TraktPairingPrompt internal constructor(
    val verificationUrl: String, val userCode: String, val expiresInSeconds: Long,
) {
    override fun toString() = "TraktPairingPrompt([redacted])"
}

enum class TraktAuthorizationEnd { DENIED, EXPIRED, INVALID_CODE, ALREADY_USED }
class TraktAuthorizationException(val reason: TraktAuthorizationEnd) : Exception("Trakt authorization: ${reason.name}")

/** No durable connection or sync consent is created just by obtaining this result. */
class TraktAuthorization internal constructor(val identity: TraktIdentity, val tokens: TraktTokens) {
    override fun toString() = "TraktAuthorization([redacted])"
}

fun interface TraktAuthorizationClient {
    suspend fun authorize(onPrompt: suspend (TraktPairingPrompt) -> Unit): TraktAuthorization
}

/**
 * One caller-owned authorization attempt. UI cancellation cancels its HTTP request.
 * No database writes or watch-history access; the host must bind a verified result
 * to the original profile/generation before persisting it. History sync requires
 * separate consent and is not activated by this authorization.
 */
class TraktDeviceAuthorizer(
    private val auth: TraktAuth,
    private val identities: TraktIdentityLookup,
    private val monotonicMillis: () -> Long = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) },
    private val allowed: suspend () -> Boolean = { true },
) : TraktAuthorizationClient {
    override suspend fun authorize(onPrompt: suspend (TraktPairingPrompt) -> Unit): TraktAuthorization {
        checkAccess()
        val startedAt = monotonicMillis()
        val code = auth.deviceCode()
        checkAccess()
        // Count the code request itself against its lifetime; never extend expiry
        // because a slow response or suspended UI arrived late.
        val schedule = TraktPollSchedule(code, monotonicMillis())
        val remaining = code.expiresInSeconds * 1000 - (monotonicMillis() - startedAt)
        if (remaining <= 0) throw TraktAuthorizationException(TraktAuthorizationEnd.EXPIRED)
        val tokens = withTimeoutOrNull(remaining) {
            onPrompt(TraktPairingPrompt(code.verificationUrl, code.userCode, (remaining + 999) / 1000))
            pollUntilAuthorized(code, schedule)
        } ?: throw TraktAuthorizationException(TraktAuthorizationEnd.EXPIRED)
        // Device expiry limits polling, not the already-issued access token.
        checkAccess()
        val identity = identities.identify(tokens)
        checkAccess()
        return TraktAuthorization(identity, tokens)
    }

    private suspend fun pollUntilAuthorized(code: TraktDeviceCode, schedule: TraktPollSchedule): TraktTokens {
        while (true) {
            checkAccess()
            if (schedule.expired(monotonicMillis())) throw TraktAuthorizationException(TraktAuthorizationEnd.EXPIRED)
            delay(schedule.waitMillis(monotonicMillis()))
            checkAccess()
            if (schedule.expired(monotonicMillis())) throw TraktAuthorizationException(TraktAuthorizationEnd.EXPIRED)
            val result = auth.poll(code)
            checkAccess()
            when (result) {
                is TraktPoll.Authorized -> return result.tokens
                TraktPoll.Denied -> throw TraktAuthorizationException(TraktAuthorizationEnd.DENIED)
                TraktPoll.Expired -> throw TraktAuthorizationException(TraktAuthorizationEnd.EXPIRED)
                TraktPoll.InvalidCode -> throw TraktAuthorizationException(TraktAuthorizationEnd.INVALID_CODE)
                TraktPoll.AlreadyUsed -> throw TraktAuthorizationException(TraktAuthorizationEnd.ALREADY_USED)
                TraktPoll.Pending, is TraktPoll.SlowDown -> schedule.polled(monotonicMillis(), result)
            }
        }
    }

    private suspend fun checkAccess() {
        currentCoroutineContext().ensureActive()
        if (!allowed()) throw TraktException(TraktFailure.ACCESS_DENIED)
        currentCoroutineContext().ensureActive()
    }
}
