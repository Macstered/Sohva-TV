package com.sohva.tv.feature.trakt.account

import com.sohva.tv.feature.trakt.protocol.DeviceCode
import com.sohva.tv.feature.trakt.protocol.DevicePoll
import com.sohva.tv.feature.trakt.protocol.TraktAuthClient
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktIdentity
import com.sohva.tv.feature.trakt.protocol.TraktIdentityClient
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import kotlinx.coroutines.delay

/** Why a sign-in ended without an account (spec 51 FR-04). */
enum class SignInFailure { DECLINED, EXPIRED, UNUSABLE, RATE_LIMITED, CONFIGURATION, ACCESS, OTHER }

sealed interface SignInResult {
    class Success(val tokens: TraktTokens, val identity: TraktIdentity) : SignInResult {
        override fun toString(): String = "Success"
    }

    data class Failed(val reason: SignInFailure) : SignInResult
}

/**
 * One device sign-in (spec 51 FR-02): the code, the prompt, polling on a monotonic clock, then the
 * account's identity. The code's lifetime counts from before its request (a slow answer never
 * extends it); the first poll waits a full interval; network failures end the attempt. [allowed]
 * (the restricted-profile rule, FR-36) is checked before every request. The caller owns the
 * coroutine: cancelling it cancels the HTTP call in flight.
 */
class TraktSignIn(
    private val auth: TraktAuthClient,
    private val identity: TraktIdentityClient,
    /** Monotonic milliseconds (elapsedRealtime on a device, virtual time in tests). */
    private val now: () -> Long,
    private val allowed: suspend () -> Boolean,
) {
    suspend fun run(onPrompt: (DeviceCode) -> Unit): SignInResult = try {
        attempt(onPrompt)
    } catch (e: TraktException) {
        SignInResult.Failed(
            when (e.failure) {
                TraktFailure.RATE_LIMITED -> SignInFailure.RATE_LIMITED
                TraktFailure.CONFIGURATION -> SignInFailure.CONFIGURATION
                else -> SignInFailure.OTHER
            },
        )
    }

    private suspend fun attempt(onPrompt: (DeviceCode) -> Unit): SignInResult {
        if (!allowed()) return SignInResult.Failed(SignInFailure.ACCESS)
        val started = now()
        val code = auth.deviceCode()
        val deadline = started + code.expiresInSeconds * 1_000
        if (now() >= deadline) return SignInResult.Failed(SignInFailure.EXPIRED)
        onPrompt(code)
        var interval = code.intervalSeconds * 1_000
        var next = now() + interval
        var tokens: TraktTokens? = null
        while (tokens == null) {
            val wait = next - now()
            if (wait > 0) delay(wait)
            if (now() >= deadline) return SignInResult.Failed(SignInFailure.EXPIRED)
            if (!allowed()) return SignInResult.Failed(SignInFailure.ACCESS)
            when (val poll = auth.poll(code)) {
                is DevicePoll.Granted -> tokens = poll.tokens
                DevicePoll.Pending -> Unit
                is DevicePoll.SlowDown -> interval = poll.intervalSeconds * 1_000
                DevicePoll.InvalidCode, DevicePoll.AlreadyUsed -> return SignInResult.Failed(SignInFailure.UNUSABLE)
                DevicePoll.Expired -> return SignInResult.Failed(SignInFailure.EXPIRED)
                DevicePoll.Denied -> return SignInResult.Failed(SignInFailure.DECLINED)
            }
            next = now() + interval
        }
        if (!allowed()) return SignInResult.Failed(SignInFailure.ACCESS)
        val who = identity.identity(tokens.access)
        if (!allowed()) return SignInResult.Failed(SignInFailure.ACCESS)
        return SignInResult.Success(tokens, who)
    }
}
