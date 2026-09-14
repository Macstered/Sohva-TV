package com.sohva.tv.trakt

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Stable account identity is user.ids.uuid, never the changeable username/slug. */
class TraktIdentity internal constructor(val accountId: String, val username: String) {
    override fun toString() = "TraktIdentity([redacted])"
}

fun interface TraktIdentityLookup {
    suspend fun identify(tokens: TraktTokens): TraktIdentity
}

/** Read-only API boundary. This client cannot write history or change account settings. */
class TraktIdentityClient internal constructor(
    private val clientId: String,
    private val origin: HttpUrl,
) : TraktIdentityLookup {
    constructor(clientId: String) : this(clientId, "https://api.trakt.tv/".toHttpUrl())

    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun identify(tokens: TraktTokens): TraktIdentity {
        return try {
            val request = Request.Builder().url(origin.resolve("users/settings")!!)
                .header("trakt-api-version", "2").header("trakt-api-key", clientId)
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .header("Accept", "application/json").get().build()
            client.newCall(request).identity()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: TraktException) { throw error }
        catch (_: Exception) { throw TraktException(TraktFailure.NETWORK) }
    }

    private suspend fun Call.identity(): TraktIdentity = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(TraktException(TraktFailure.NETWORK))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val identity = response.use {
                        if (it.code != 200) throw TraktException(when (it.code) {
                            401 -> TraktFailure.REAUTHORIZE
                            403 -> TraktFailure.ACCESS_DENIED
                            429 -> TraktFailure.RATE_LIMITED
                            in 300..399 -> TraktFailure.INVALID_RESPONSE
                            else -> TraktFailure.SERVICE
                        }, retryAfterSeconds(it.header("Retry-After"), System.currentTimeMillis()))
                        val source = it.body.source()
                        source.request(MAX_BODY_BYTES + 1)
                        if (source.buffer.size > MAX_BODY_BYTES) throw TraktException(TraktFailure.INVALID_RESPONSE)
                        parseIdentity(source.readUtf8())
                    }
                    continuation.resume(identity) { _, _, _ -> }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        error as? TraktException ?: TraktException(TraktFailure.NETWORK))
                }
            }
        })
    }

    private fun parseIdentity(body: String): TraktIdentity = try {
        val user = Json.parseToJsonElement(body).jsonObject.getValue("user").jsonObject
        val id = user.getValue("ids").jsonObject.getValue("uuid").jsonPrimitive
        val username = user.getValue("username").jsonPrimitive
        require(id.isString && username.isString)
        // Treat the documented UUID as opaque; do not require a guessed encoding.
        require(id.content.isNotBlank() && id.content.length <= 256 && id.content.none { it.isWhitespace() || it.isISOControl() })
        require(username.content.isNotBlank() && username.content.length <= 256 && username.content.none(Char::isISOControl))
        TraktIdentity(id.content, username.content)
    } catch (_: Exception) { throw TraktException(TraktFailure.INVALID_RESPONSE) }

    private companion object { const val MAX_BODY_BYTES = 64 * 1024L }
}
