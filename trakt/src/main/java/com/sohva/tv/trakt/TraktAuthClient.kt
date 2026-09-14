package com.sohva.tv.trakt

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** No cache, redirects, interceptors, automatic POST retries or raw-error reporting. */
class TraktAuthClient internal constructor(
    private val credentials: TraktAppCredentials,
    private val origin: HttpUrl,
) : TraktAuth {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()

    constructor(credentials: TraktAppCredentials) : this(credentials, "https://auth.trakt.tv/".toHttpUrl())

    override suspend fun deviceCode(): TraktDeviceCode {
        val response = post("oauth/device/code", buildJsonObject { put("client_id", credentials.clientId) })
        if (response.code != 200) response.fail()
        return parse {
            val obj = response.json()
            val url = obj.text("verification_url", 2048).toHttpUrl()
            require(url.isHttps && url.port == 443 && url.host in setOf("auth.trakt.tv", "trakt.tv") && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null && url.encodedPath == "/activate" && url.query == null)
            val expiry = obj.number("expires_in").also { require(it in 1..3600) }
            val interval = obj.number("interval").also { require(it in 1..300 && it <= expiry) }
            TraktDeviceCode(obj.text("device_code", 8192), obj.text("user_code", 128), url.toString(), expiry, interval)
        }
    }

    override suspend fun poll(code: TraktDeviceCode): TraktPoll {
        val response = post("oauth/device/token", buildJsonObject {
            put("client_id", credentials.clientId); put("client_secret", credentials.clientSecret); put("code", code.deviceCode)
        })
        return when (response.code) {
            200 -> TraktPoll.Authorized(tokens(response))
            400 -> {
                // Trakt documents 400 as pending here, but a named configuration error is not pending.
                val error = response.errorName()
                if (error != null && error != "authorization_pending") throw TraktException(TraktFailure.CONFIGURATION)
                TraktPoll.Pending
            }
            404 -> TraktPoll.InvalidCode
            409 -> TraktPoll.AlreadyUsed
            410 -> TraktPoll.Expired
            418 -> TraktPoll.Denied
            429 -> TraktPoll.SlowDown(response.retryAfter)
            else -> response.fail()
        }
    }

    override suspend fun refresh(refreshToken: String): TraktTokens {
        require(refreshToken.isNotBlank() && refreshToken.length <= 8192 && refreshToken.none(Char::isISOControl)) { "Invalid refresh token" }
        val response = post("oauth/token", buildJsonObject {
            put("client_id", credentials.clientId); put("client_secret", credentials.clientSecret)
            put("refresh_token", refreshToken); put("grant_type", "refresh_token")
            put("redirect_uri", credentials.redirectUri)
        })
        if (response.code == 400 && response.errorName() == "invalid_grant") throw TraktException(TraktFailure.REAUTHORIZE)
        if (response.code != 200) response.fail()
        return tokens(response)
    }

    private fun tokens(response: AuthResponse) = parse {
        val obj = response.json()
        require(obj.text("token_type", 32).equals("bearer", ignoreCase = true))
        val created = obj.number("created_at").also { require(it in 0..253_402_300_799L) }
        val expires = obj.number("expires_in").also { require(it in 1..31_536_000L) }
        TraktTokens(obj.text("access_token", 8192), obj.text("refresh_token", 8192), Math.addExact(created, expires) * 1000)
    }

    private suspend fun post(path: String, body: JsonObject): AuthResponse {
        return try {
            val request = Request.Builder().url(origin.resolve(path)!!)
                .header("trakt-api-version", "2").header("trakt-api-key", credentials.clientId)
                .header("Accept", "application/json").post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).await()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: TraktException) { throw error }
        // OkHttp header-validation messages may echo a rejected client ID.
        catch (_: IllegalArgumentException) { throw TraktException(TraktFailure.CONFIGURATION) }
        catch (_: Exception) { throw TraktException(TraktFailure.NETWORK) }
    }
    private suspend fun Call.await(): AuthResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(TraktException(TraktFailure.NETWORK))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    // Consume on OkHttp's worker while cancellation still owns this call.
                    val value = response.use {
                        val source = it.body.source()
                        source.request(MAX_BODY_BYTES + 1)
                        if (source.buffer.size > MAX_BODY_BYTES) throw TraktException(TraktFailure.INVALID_RESPONSE)
                        AuthResponse(it.code, source.readUtf8(), retryAfterSeconds(it.header("Retry-After"), System.currentTimeMillis()))
                    }
                    continuation.resume(value) { _, _, _ -> }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        error as? TraktException ?: TraktException(TraktFailure.NETWORK))
                }
            }
        })
    }
    private inline fun <T> parse(block: () -> T): T = try { block() } catch (_: Exception) { throw TraktException(TraktFailure.INVALID_RESPONSE) }
    private fun JsonObject.text(name: String, maximum: Int) = getValue(name).jsonPrimitive.let {
        require(it.isString)
        it.content.also { value -> require(value.isNotBlank() && value.length <= maximum && value.none(Char::isISOControl)) }
    }
    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.long
    private class AuthResponse(val code: Int, private val body: String, val retryAfter: Long?) {
        fun json() = Json.parseToJsonElement(body).jsonObject
        fun errorName(): String? = if (body.isBlank()) null else try { json()["error"]?.jsonPrimitive?.content }
            catch (_: Exception) { throw TraktException(TraktFailure.INVALID_RESPONSE) }
        fun fail(): Nothing = throw TraktException(when (code) {
            401, 403 -> TraktFailure.CONFIGURATION
            429 -> TraktFailure.RATE_LIMITED
            in 300..399 -> TraktFailure.INVALID_RESPONSE
            else -> TraktFailure.SERVICE
        }, retryAfter)
        override fun toString() = "AuthResponse([redacted])"
    }
    private companion object { const val MAX_BODY_BYTES = 64 * 1024L }
}

/** Retry-After permits either delta seconds or an HTTP date. Never retry a date early. */
internal fun retryAfterSeconds(value: String?, nowMillis: Long): Long? {
    val text = value?.trim() ?: return null
    text.toLongOrNull()?.let { return it.coerceIn(1, Long.MAX_VALUE / 1000) }
    return try {
        val remaining = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis
        if (remaining <= 0) 1 else remaining / 1000 + if (remaining % 1000 == 0L) 0 else 1
    } catch (_: Exception) { null }
}
