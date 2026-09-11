package com.sohva.tv.addons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Fixed official origins. Test endpoint injection is module-internal, never configured by an addon. */
class StremioCopyClient internal constructor(
    private val linkBase: HttpUrl,
    private val accountBase: HttpUrl,
    timeoutMillis: Long = 15_000,
    private val maxBytes: Int = 2 * 1024 * 1024,
) : StremioCopyApi {
    constructor() : this("https://link.stremio.com/".toHttpUrl(), "https://api.strem.io/".toHttpUrl())
    init { require(timeoutMillis in 100..60_000 && maxBytes in 1..2 * 1024 * 1024) }
    private val http = OkHttpClient.Builder()
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .build()

    override suspend fun createLink(): StremioImportLink {
        val body = request(Request.Builder().url(linkBase.newBuilder().addPathSegments("api/create")
            .addQueryParameter("type", "Create").build()))
        rejectError(body)
        // The core wrapper uses result; older link service responses used top-level fields.
        val result = if ("result" in body) body["result"] as? JsonObject ?: fail(AddonFailure.INVALID_RESPONSE) else body
        val code = result.stringValue("code") ?: fail(AddonFailure.INVALID_RESPONSE)
        val url = result.stringValue("link") ?: fail(AddonFailure.INVALID_RESPONSE)
        // Never display a provider-controlled login URL or download the returned remote QR image.
        return StremioImportLink(code, url)
    }

    override suspend fun readAddons(link: StremioImportLink, recheckAccess: suspend () -> Unit): AddonCopyList? {
        recheckAccess()
        val body = request(Request.Builder().url(linkBase.newBuilder().addPathSegments("api/read")
            .addQueryParameter("type", "Read").addQueryParameter("code", link.code).build()))
        // The live link service returns error 101 while a newly created code awaits
        // authorization (verified without an account). This is only a waiting state
        // on /api/read, never on create/login/collection. Contradictory result/error
        // envelopes and every other error still fail closed; messages are not trusted.
        val error = body["error"] as? JsonObject
        if (error?.get("code") == JsonPrimitive(101) &&
            (body["result"] == null || body["result"] == JsonNull)) return null
        rejectError(body)
        val value = body["result"]
        if (value == JsonNull) return null
        val result = value as? JsonObject ?: fail(AddonFailure.INVALID_RESPONSE)
        if (result["success"] == JsonPrimitive(false) && result["authKey"] == null) return null
        val token = secret(result, "authKey")
        recheckAccess()
        // Device authorization yields a login token. Exchange only with the fixed account origin.
        // Both credentials are local variables: no fields, preferences, saved state or disk cache.
        val login = post("loginWithToken", buildJsonObject {
            put("type", "LoginWithToken"); put("token", token)
        })
        val authKey = secret(login, "authKey")
        recheckAccess()
        val collection = post("addonCollectionGet", buildJsonObject {
            put("type", "AddonCollectionGet"); put("authKey", authKey); put("update", false)
        })
        recheckAccess()
        return AddonCopyExport.stremio(collection)
    }

    private fun secret(value: JsonObject, field: String): String = value.stringValue(field)
        ?.takeIf { it.isNotBlank() && it.length <= 16_384 && it.none(Char::isISOControl) }
        ?: fail(AddonFailure.INVALID_RESPONSE)

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val response = request(Request.Builder().url(accountBase.newBuilder().addPathSegments("api/$path").build())
            .post(body.toString().toRequestBody("application/json".toMediaType())))
        rejectError(response)
        return response["result"] as? JsonObject ?: fail(AddonFailure.INVALID_RESPONSE)
    }

    private fun rejectError(body: JsonObject) {
        // Unknown service errors fail closed; never print their messages or keep polling indefinitely.
        if (body["error"] != null && body["error"] != JsonNull) fail(AddonFailure.INVALID_RESPONSE)
    }

    private suspend fun request(builder: Request.Builder): JsonObject = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(builder.header("Accept", "application/json").header("Cache-Control", "no-store").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(AddonException(
                    if (e is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK)))
            }
            override fun onResponse(call: Call, response: Response) {
                val outcome = try {
                    response.use {
                        if (continuation.isCancelled) return
                        if (it.code in 300..399) fail(AddonFailure.REDIRECT)
                        if (!it.isSuccessful) throw AddonException(AddonFailure.HTTP_ERROR, it.code)
                        if (it.body.contentLength() > maxBytes) fail(AddonFailure.RESPONSE_TOO_LARGE)
                        val output = ByteArrayOutputStream()
                        it.body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size().toLong() + count > maxBytes) fail(AddonFailure.RESPONSE_TOO_LARGE)
                                output.write(buffer, 0, count)
                            }
                        }
                        Result.success(boundedJson(output.toString(Charsets.UTF_8.name()), maxBytes) as? JsonObject
                            ?: fail(AddonFailure.INVALID_RESPONSE))
                    }
                } catch (error: AddonException) {
                    Result.failure(AddonException(error.failure, error.httpStatus))
                } catch (error: Exception) {
                    Result.failure(AddonException(if (error is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK))
                }
                continuation.resumeWith(outcome)
            }
        })
    }
}
