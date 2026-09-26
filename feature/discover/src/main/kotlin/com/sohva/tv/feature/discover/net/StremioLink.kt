package com.sohva.tv.feature.discover.net

import com.sohva.tv.feature.discover.data.ImportList
import com.sohva.tv.feature.discover.protocol.AddonJson
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.forFields
import com.sohva.tv.feature.discover.protocol.forItems
import com.sohva.tv.feature.discover.protocol.intOrNull
import com.sohva.tv.feature.discover.protocol.textOrNull
import com.sohva.tv.feature.discover.protocol.boolOrNull
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/** Why a Stremio copy ended (spec 50 ADDON-FR-49); the account's own messages are never shown. */
enum class StremioProblem { TIMEOUT, TOO_MANY, ACCESS_DENIED, OTHER }

class StremioException(val problem: StremioProblem) : Exception(problem.name)

/** The link to show as a QR (FR-47 step 2). */
class StremioPending(val code: String, val link: String) {
    override fun toString(): String = "StremioPending"
}

/**
 * Stremio's link and account API (spec 50 §4.7.2, §7.2) on fixed official origins only, with its
 * own client: 15 s timeouts, no redirects or retries, at most 2 MiB, `Accept: application/json`,
 * `Cache-Control: no-store`. Tokens stay in local variables; nothing is stored, logged or sent
 * anywhere else, and no logout or revoke call is made.
 */
class StremioLink(base: OkHttpClient, private val linkBase: HttpUrl = LINK, private val apiBase: HttpUrl = API) {
    private val client = base.newBuilder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /** Step 1: a code of 4–128 ASCII letters and digits, and exactly `https://link.stremio.com/<code>`. */
    suspend fun create(): StremioPending = guard {
        val body = get(linkBase.newBuilder().addPathSegments("api/create").addQueryParameter("type", "Create").build())
        var code: String? = null
        var link: String? = null
        AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
            r.forFields { f ->
                when (f) {
                    "result" -> if (r.peek() == JsonReader.Token.BEGIN_OBJECT) {
                        r.forFields { g ->
                            when (g) {
                                "code" -> code = r.textOrNull(256)
                                "link" -> link = r.textOrNull(512)
                                else -> r.skipValue()
                            }
                        }
                    } else {
                        r.skipValue()
                    }
                    // Older responses carry them at the top level.
                    "code" -> code = code ?: r.textOrNull(256)
                    "link" -> link = link ?: r.textOrNull(512)
                    else -> r.skipValue()
                }
            }
        }
        val c = code?.takeIf { CODE.matches(it) } ?: throw StremioException(StremioProblem.OTHER)
        val expected = "https://link.stremio.com/$c"
        if (link != expected) throw StremioException(StremioProblem.OTHER)
        StremioPending(c, expected)
    }

    /** Step 3: the authorization key once granted, null while still waiting. */
    suspend fun read(code: String): String? = guard {
        val body = get(linkBase.newBuilder().addPathSegments("api/read").addQueryParameter("type", "Read").addQueryParameter("code", code).build())
        var errorCode: Int? = null
        var hasError = false
        var hasResult = false
        var resultNull = false
        var authKey: String? = null
        var success: Boolean? = null
        AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
            r.forFields { f ->
                when (f) {
                    "error" -> {
                        hasError = true
                        if (r.peek() == JsonReader.Token.BEGIN_OBJECT) r.forFields { g -> if (g == "code") errorCode = r.intOrNull() else r.skipValue() } else r.skipValue()
                    }
                    "result" -> {
                        hasResult = true
                        when (r.peek()) {
                            JsonReader.Token.NULL -> {
                                r.skipValue()
                                resultNull = true
                            }
                            JsonReader.Token.BEGIN_OBJECT -> r.forFields { g ->
                                when (g) {
                                    "authKey" -> authKey = r.textOrNull(1_024)
                                    "success" -> success = r.boolOrNull()
                                    else -> r.skipValue()
                                }
                            }
                            else -> r.skipValue()
                        }
                    }
                    else -> r.skipValue()
                }
            }
        }
        when {
            hasError && errorCode == WAITING && (!hasResult || resultNull) -> null
            hasError -> throw StremioException(StremioProblem.OTHER)
            !hasResult -> throw StremioException(StremioProblem.OTHER)
            resultNull -> null
            authKey != null -> authKey
            success == false -> null
            else -> throw StremioException(StremioProblem.OTHER)
        }
    }

    /** Step 4: the account's addon URLs (`transportUrl`), at most 32. */
    suspend fun addons(linkToken: String): List<String> = guard {
        val authKey = resultText(post("api/loginWithToken", json("type" to "LoginWithToken", "token" to linkToken)), "authKey")
            ?: throw StremioException(StremioProblem.OTHER)
        val body = post("api/addonCollectionGet", json("type" to "AddonCollectionGet", "authKey" to authKey, "update" to false))
        val urls = ArrayList<String>()
        AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
            r.forFields { f ->
                if (f != "result" || r.peek() != JsonReader.Token.BEGIN_OBJECT) return@forFields r.skipValue()
                r.forFields { g ->
                    if (g != "addons" || r.peek() != JsonReader.Token.BEGIN_ARRAY) return@forFields r.skipValue()
                    r.forItems {
                        if (urls.size > ImportList.MAX_LINES) throw StremioException(StremioProblem.TOO_MANY)
                        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) return@forItems r.skipValue()
                        r.forFields { h -> if (h == "transportUrl") r.textOrNull(16_384)?.let(urls::add) else r.skipValue() }
                    }
                }
            }
        }
        if (urls.size > ImportList.MAX_LINES) throw StremioException(StremioProblem.TOO_MANY)
        urls
    }

    /** Malformed or oversized answers become the flow's own failures (FR-49). */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AddonException) {
        throw StremioException(if (e.failure == AddonFailure.RESPONSE_TOO_LARGE) StremioProblem.TOO_MANY else StremioProblem.OTHER)
    }

    private fun resultText(body: Buffer, field: String): String? {
        var value: String? = null
        AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
            r.forFields { f ->
                if (f == "result" && r.peek() == JsonReader.Token.BEGIN_OBJECT) r.forFields { g -> if (g == field) value = r.textOrNull(1_024) else r.skipValue() } else r.skipValue()
            }
        }
        return value
    }

    /** A request body of string and boolean fields. */
    private fun json(vararg fields: Pair<String, Any>): String {
        val out = Buffer()
        com.squareup.moshi.JsonWriter.of(out).use { w ->
            w.beginObject()
            fields.forEach { (k, v) ->
                w.name(k)
                if (v is Boolean) w.value(v) else w.value(v.toString())
            }
            w.endObject()
        }
        return out.readUtf8()
    }

    private suspend fun get(url: HttpUrl): Buffer = call(request(url).get().build())

    private suspend fun post(path: String, json: String): Buffer {
        val body = json.toRequestBody(JSON)
        return call(request(apiBase.newBuilder().addPathSegments(path).build()).post(body).build())
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("Accept", "application/json").header("Cache-Control", "no-store")

    private suspend fun call(request: Request): Buffer {
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(StremioException(StremioProblem.OTHER))

                        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }
                    },
                )
        }
        return response.use { r ->
            if (r.code !in 200..299) throw StremioException(StremioProblem.OTHER)
            val out = Buffer()
            try {
                val source = r.body.source()
                while (source.read(out, CHUNK) != -1L) {
                    if (out.size > MAX_BYTES) throw StremioException(StremioProblem.TOO_MANY)
                }
            } catch (e: IOException) {
                throw StremioException(StremioProblem.OTHER)
            }
            out
        }
    }

    companion object {
        val LINK: HttpUrl = "https://link.stremio.com/".toHttpUrl()
        val API: HttpUrl = "https://api.strem.io/".toHttpUrl()
        private val JSON = "application/json".toMediaType()
        private val CODE = Regex("[A-Za-z0-9]{4,128}")
        private const val WAITING = 101
        private const val TIMEOUT_S = 15L
        private const val MAX_BYTES = 2L * 1024 * 1024
        private const val CHUNK = 8_192L
    }
}
