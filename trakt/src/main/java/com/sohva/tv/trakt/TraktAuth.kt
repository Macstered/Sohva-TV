package com.sohva.tv.trakt

enum class TraktFailure { NETWORK, INVALID_RESPONSE, CONFIGURATION, REAUTHORIZE, RATE_LIMITED, SERVICE, STORAGE, ACCESS_DENIED, ACCOUNT_CHANGED, ACCOUNT_IN_USE }
class TraktException(val failure: TraktFailure, val retryAfterSeconds: Long? = null) : Exception("Trakt: ${failure.name}")

/** Supplied by an approved credential strategy; never hardcoded in the app or logs. */
class TraktAppCredentials(val clientId: String, val clientSecret: String, val redirectUri: String) {
    init { require(listOf(clientId, clientSecret, redirectUri).all { it.isNotBlank() && it.length <= 4096 && it.none(Char::isISOControl) }) { "Invalid Trakt application credentials" } }
    override fun toString() = "TraktAppCredentials([redacted])"
}
class TraktTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long) {
    init {
        require(expiresAtMillis > 0 && listOf(accessToken, refreshToken).all { it.isNotBlank() && it.length <= 8192 && it.none(Char::isISOControl) }) { "Invalid Trakt tokens" }
    }
    override fun toString() = "TraktTokens([redacted])"
}
class TraktDeviceCode internal constructor(
    internal val deviceCode: String, val userCode: String, val verificationUrl: String,
    val expiresInSeconds: Long, val intervalSeconds: Long,
) {
    override fun toString() = "TraktDeviceCode([redacted])"
}
sealed interface TraktPoll {
    class Authorized(val tokens: TraktTokens) : TraktPoll { override fun toString() = "Authorized([redacted])" }
    data object Pending : TraktPoll
    class SlowDown(val retryAfterSeconds: Long?) : TraktPoll
    data object Expired : TraktPoll
    data object Denied : TraktPoll
    data object InvalidCode : TraktPoll
    data object AlreadyUsed : TraktPoll
}
interface TraktAuth {
    suspend fun deviceCode(): TraktDeviceCode
    suspend fun poll(code: TraktDeviceCode): TraktPoll
    suspend fun refresh(refreshToken: String): TraktTokens
}

/** UI drives this using a monotonic clock. Clock changes cannot extend an expired code. */
class TraktPollSchedule(code: TraktDeviceCode, nowMillis: Long) {
    private val deadline = nowMillis + code.expiresInSeconds * 1000
    private var interval = code.intervalSeconds * 1000
    private var next = nowMillis + interval
    fun expired(nowMillis: Long) = nowMillis >= deadline
    fun ready(nowMillis: Long) = !expired(nowMillis) && nowMillis >= next
    fun waitMillis(nowMillis: Long): Long = (minOf(next, deadline) - nowMillis).coerceAtLeast(0)
    fun polled(nowMillis: Long, result: TraktPoll = TraktPoll.Pending) {
        if (result is TraktPoll.SlowDown) interval = maxOf(interval + 5000, (result.retryAfterSeconds ?: 0).coerceIn(0, 3600) * 1000)
        next = nowMillis + interval
    }
}
