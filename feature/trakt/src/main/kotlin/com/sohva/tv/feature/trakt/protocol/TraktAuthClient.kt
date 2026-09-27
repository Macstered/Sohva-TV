package com.sohva.tv.feature.trakt.protocol

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A refresh's answer (FR-08): new tokens, or the saved sign-in is no longer accepted. */
sealed interface RefreshResult {
    class Refreshed(val tokens: TraktTokens) : RefreshResult {
        override fun toString(): String = "Refreshed"
    }

    data object Reauthorize : RefreshResult
}

/**
 * Trakt's device authorisation (spec 51 FR-02..04, FR-08): the device code, the poll and the
 * refresh, on `auth.trakt.tv` only, 20 s per call, 64 KiB bodies. The device code, the client
 * secret and tokens go only in request bodies to that origin and are never logged.
 */
class TraktAuthClient(base: OkHttpClient, private val credentials: TraktCredentials, private val origin: HttpUrl = ORIGIN) {
    private val http = TraktHttp(base, TIMEOUT_S, MAX_BYTES)

    /** Step 1: only the client id is sent; only the official activation page is accepted. */
    suspend fun deviceCode(): DeviceCode {
        val body = TraktJson.write { beginObject().name("client_id").value(credentials.clientId).endObject() }
        val r = post("oauth/device/code", body)
        failOn(r.code, r)
        var device: String? = null
        var user: String? = null
        var url: String? = null
        var expires: Long? = null
        var interval: Long? = null
        TraktJson.parse(r.body) { j ->
            j.fields { f ->
                when (f) {
                    "device_code" -> device = j.text(1_024)
                    "user_code" -> user = j.text(128)
                    "verification_url" -> url = j.text(256)
                    "expires_in" -> expires = j.long()
                    "interval" -> interval = j.long()
                    else -> j.skipValue()
                }
            }
        }
        val e = expires?.takeIf { it in 1..3_600 } ?: throw TraktException(TraktFailure.INVALID_RESPONSE)
        val i = interval?.takeIf { it in 1..300 && it <= e } ?: throw TraktException(TraktFailure.INVALID_RESPONSE)
        val address = url?.takeIf(::activationPage) ?: throw TraktException(TraktFailure.INVALID_RESPONSE)
        return DeviceCode(device ?: throw TraktException(TraktFailure.INVALID_RESPONSE), user ?: throw TraktException(TraktFailure.INVALID_RESPONSE), address, e, i)
    }

    /** Step 4: one poll; 400 is pending unless its body names another error (then configuration). */
    suspend fun poll(code: DeviceCode): DevicePoll {
        val body = TraktJson.write {
            beginObject().name("client_id").value(credentials.clientId).name("client_secret").value(credentials.clientSecret).name("code").value(code.deviceCode).endObject()
        }
        val r = post("oauth/device/token", body)
        return when (r.code) {
            200 -> DevicePoll.Granted(tokens(r))
            400 -> when (error(r)) {
                null, "authorization_pending" -> DevicePoll.Pending
                else -> throw TraktException(TraktFailure.CONFIGURATION)
            }
            404 -> DevicePoll.InvalidCode
            409 -> DevicePoll.AlreadyUsed
            410 -> DevicePoll.Expired
            418 -> DevicePoll.Denied
            429 -> DevicePoll.SlowDown(maxOf(code.intervalSeconds + SLOW_DOWN_S, (r.retryAfterSeconds() ?: 0).coerceAtMost(TraktHttp.MAX_RETRY_AFTER)))
            401, 403 -> throw TraktException(TraktFailure.CONFIGURATION)
            in 500..599 -> throw TraktException(TraktFailure.SERVICE)
            else -> throw TraktException(TraktFailure.INVALID_RESPONSE)
        }
    }

    /** FR-08: 400 with `invalid_grant` means the saved sign-in is gone; it is not retried. */
    suspend fun refresh(refreshToken: String): RefreshResult {
        val body = TraktJson.write {
            beginObject().name("client_id").value(credentials.clientId).name("client_secret").value(credentials.clientSecret)
                .name("refresh_token").value(refreshToken).name("grant_type").value("refresh_token").name("redirect_uri").value(REDIRECT_URI).endObject()
        }
        val r = post("oauth/token", body)
        if (r.code == 200) return RefreshResult.Refreshed(tokens(r))
        if ((r.code == 400 || r.code == 401) && error(r) == "invalid_grant") return RefreshResult.Reauthorize
        failOn(r.code, r)
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    }

    private suspend fun post(path: String, json: String): TraktResponse {
        if (!credentials.configured) throw TraktException(TraktFailure.CONFIGURATION)
        val request = Request.Builder().url(origin.newBuilder().addPathSegments(path).build())
            .header("Accept", "application/json")
            .header("trakt-api-version", TraktHttp.API_VERSION)
            .header("trakt-api-key", TraktHttp.safe(credentials.clientId))
            .post(json.toRequestBody(JSON))
            .build()
        return http.call(request)
    }

    private fun error(r: TraktResponse): String? = runCatching {
        var e: String? = null
        TraktJson.parse(r.body.clone()) { j -> j.fields { f -> if (f == "error") e = j.text(128) else j.skipValue() } }
        e
    }.getOrNull()

    /** FR-03: bearer tokens, sane lengths, an expiry from `created_at` + `expires_in`. */
    private fun tokens(r: TraktResponse): TraktTokens {
        var access: String? = null
        var refresh: String? = null
        var type: String? = null
        var created: Long? = null
        var expiresIn: Long? = null
        TraktJson.parse(r.body) { j ->
            j.fields { f ->
                when (f) {
                    "access_token" -> access = j.text(8_192)
                    "refresh_token" -> refresh = j.text(8_192)
                    "token_type" -> type = j.text(32)
                    "created_at" -> created = j.long()
                    "expires_in" -> expiresIn = j.long()
                    else -> j.skipValue()
                }
            }
        }
        if (!type.equals("bearer", ignoreCase = true)) throw TraktException(TraktFailure.INVALID_RESPONSE)
        val c = created?.takeIf { it in 0..253_402_300_799 } ?: throw TraktException(TraktFailure.INVALID_RESPONSE)
        val e = expiresIn?.takeIf { it in 1..31_536_000 } ?: throw TraktException(TraktFailure.INVALID_RESPONSE)
        return TraktTokens(access ?: throw TraktException(TraktFailure.INVALID_RESPONSE), refresh ?: throw TraktException(TraktFailure.INVALID_RESPONSE), (c + e) * 1_000)
    }

    private fun failOn(code: Int, r: TraktResponse) {
        when (code) {
            in 200..299 -> Unit
            401, 403 -> throw TraktException(TraktFailure.CONFIGURATION)
            429 -> throw TraktException(TraktFailure.RATE_LIMITED, r.retryAfterSeconds())
            in 500..599 -> throw TraktException(TraktFailure.SERVICE)
            else -> throw TraktException(TraktFailure.INVALID_RESPONSE)
        }
    }

    companion object {
        val ORIGIN: HttpUrl = "https://auth.trakt.tv/".toHttpUrl()
        const val REDIRECT_URI: String = "urn:ietf:wg:oauth:2.0:oob"
        private const val TIMEOUT_S = 20L
        private const val MAX_BYTES = 64L * 1024
        private const val SLOW_DOWN_S = 5L
        private val JSON = "application/json".toMediaType()

        /** Exactly https://trakt.tv/activate or https://auth.trakt.tv/activate on port 443 (FR-02). */
        fun activationPage(url: String): Boolean {
            val u = url.toHttpUrlOrNull() ?: return false
            return u.isHttps && u.port == 443 && (u.host == "trakt.tv" || u.host == "auth.trakt.tv") && u.encodedPath == "/activate" &&
                u.query == null && u.fragment == null && u.username.isEmpty() && u.password.isEmpty() && !url.contains('#') && !url.contains('?')
        }
    }
}

/** `users/settings` (FR-02 step 5): the uuid is required; the username never stands in for it. */
class TraktIdentityClient(base: OkHttpClient, private val credentials: TraktCredentials, private val origin: HttpUrl = TraktApiClient.ORIGIN) {
    private val http = TraktHttp(base, 20, 64L * 1024)

    suspend fun identity(access: String): TraktIdentity {
        val request = Request.Builder().url(origin.newBuilder().addPathSegments("users/settings").build())
            .header("Accept", "application/json")
            .header("trakt-api-version", TraktHttp.API_VERSION)
            .header("trakt-api-key", TraktHttp.safe(credentials.clientId))
            .header("Authorization", "Bearer " + TraktHttp.safe(access))
            .get().build()
        val r = http.call(request)
        when (r.code) {
            200 -> Unit
            401 -> throw TraktException(TraktFailure.REAUTHORIZE)
            403 -> throw TraktException(TraktFailure.CONFIGURATION)
            429 -> throw TraktException(TraktFailure.RATE_LIMITED, r.retryAfterSeconds())
            in 500..599 -> throw TraktException(TraktFailure.SERVICE)
            else -> throw TraktException(TraktFailure.INVALID_RESPONSE)
        }
        var uuid: String? = null
        var username: String? = null
        TraktJson.parse(r.body) { j ->
            j.fields { f ->
                if (f != "user") return@fields j.skipValue()
                j.fields { g ->
                    when (g) {
                        "username" -> username = j.text(256)
                        "ids" -> j.fields { h -> if (h == "uuid") uuid = j.text(256)?.takeIf { v -> v.none(Char::isWhitespace) } else j.skipValue() }
                        else -> j.skipValue()
                    }
                }
            }
        }
        return TraktIdentity(uuid ?: throw TraktException(TraktFailure.INVALID_RESPONSE), username ?: "")
    }
}
