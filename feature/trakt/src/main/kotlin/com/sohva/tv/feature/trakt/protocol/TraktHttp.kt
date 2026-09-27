package com.sohva.tv.feature.trakt.protocol

import java.io.IOException
import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** A response kept to its body limit, with the headers the clients read. */
class TraktResponse(val code: Int, val body: Buffer, private val headers: okhttp3.Headers) {
    fun header(name: String): String? = headers[name]

    /** `Retry-After` in whole seconds, rounded up; seconds or an HTTP date (spec 51 §11). */
    fun retryAfterSeconds(now: Long = System.currentTimeMillis()): Long? = TraktHttp.retryAfter(header("Retry-After"), now)
}

/**
 * The rules every Trakt client follows (spec 51 FR-05): on the shared OkHttp base but no redirects
 * (a redirect never forwards a code, secret or bearer token), no retry, no cache, its own timeout
 * per call and body limit. Bodies and raw errors are never logged.
 */
internal class TraktHttp(base: OkHttpClient, timeoutSeconds: Long, private val maxBytes: Long) {
    private val client = base.newBuilder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cache(null)
        .apply { interceptors().clear() }
        .build()

    suspend fun call(request: Request): TraktResponse {
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(TraktException(TraktFailure.NETWORK))

                    override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }
                },
            )
        }
        return response.use { r ->
            val body = r.body
            if (body.contentLength() > maxBytes) throw TraktException(TraktFailure.INVALID_RESPONSE)
            val out = Buffer()
            try {
                val source = body.source()
                while (source.read(out, CHUNK) != -1L) {
                    if (out.size > maxBytes) throw TraktException(TraktFailure.INVALID_RESPONSE)
                }
            } catch (e: InterruptedIOException) {
                throw TraktException(TraktFailure.NETWORK)
            } catch (e: IOException) {
                throw TraktException(TraktFailure.NETWORK)
            }
            TraktResponse(r.code, out, r.headers)
        }
    }

    companion object {
        const val API_VERSION: String = "2"
        private const val CHUNK = 8_192L
        const val MAX_RETRY_AFTER: Long = 3_600
        private val DEL = Char(0x7F)

        /** Seconds, or an HTTP date turned into seconds from [now]; rounded up; null when absent or unreadable. */
        fun retryAfter(value: String?, now: Long): Long? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            v.toLongOrNull()?.let { return it.coerceAtLeast(0) }
            v.toDoubleOrNull()?.let { return kotlin.math.ceil(it).toLong().coerceAtLeast(0) }
            return runCatching {
                val at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                ((at - now + 999) / 1000).coerceAtLeast(0)
            }.getOrNull()
        }

        /** Header values from the build or Trakt: no control characters, so none can inject a header. */
        fun safe(value: String): String {
            if (value.isEmpty() || value.any { it < ' ' || it == DEL }) throw TraktException(TraktFailure.CONFIGURATION)
            return value
        }
    }
}
