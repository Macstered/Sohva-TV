package com.sohva.tv.feature.discover.net

import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** What a response allows the cache to do (ADDON-FR-27). */
data class CacheHints(val maxAgeSeconds: Long?, val noStore: Boolean, val staleAllowed: Boolean) {
    companion object {
        val NONE: CacheHints = CacheHints(null, noStore = false, staleAllowed = true)
    }
}

/** A body of at most 2 MiB (after decompression) and its cache hints. */
class AddonAnswer(val body: Buffer, val hints: CacheHints)

/**
 * The one client for addon JSON (spec 50 §4.5), built on the app's shared OkHttp base so pools and
 * threads are shared (plan/03 §4.12) but with its own rules: at most four requests app-wide in
 * FIFO order, 15 s connect, read and call timeouts, redirects never followed, no retry, `Accept:
 * application/json`. Failures carry only their kind and status, never the URL (FR-28).
 */
class AddonClient(base: OkHttpClient, private val maxBytes: Long = MAX_BYTES) {
    private val client = base.newBuilder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    // kotlinx's Semaphore resumes waiters in order: a slow shelf never starves an older request.
    private val permits = Semaphore(PERMITS)

    suspend fun get(url: HttpUrl): AddonAnswer = permits.withPermit {
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        val call = client.newCall(request)
        suspendCancellableCoroutine { cont ->
            // Cancelling the caller cancels the call; the permit is freed as withPermit returns (FR-28).
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        cont.resumeWithException(AddonException(if (e is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching { read(response) }
                        result.fold({ cont.resume(it) { _, _, _ -> } }, { cont.resumeWithException(it) })
                    }
                },
            )
        }
    }

    private fun read(response: Response): AddonAnswer = response.use {
        val status = it.code
        if (status in 300..399) throw AddonException(AddonFailure.REDIRECT, status)
        if (status !in 200..299) throw AddonException(AddonFailure.HTTP_ERROR, status)
        val body = it.body
        if (body.contentLength() > maxBytes) throw AddonException(AddonFailure.RESPONSE_TOO_LARGE)
        val out = Buffer()
        try {
            val source = body.source()
            // 8 KiB at a time, failing as soon as the limit passes (FR-26).
            while (source.read(out, CHUNK) != -1L) {
                if (out.size > maxBytes) throw AddonException(AddonFailure.RESPONSE_TOO_LARGE)
            }
        } catch (e: InterruptedIOException) {
            throw AddonException(AddonFailure.TIMEOUT)
        } catch (e: IOException) {
            throw AddonException(AddonFailure.NETWORK)
        }
        val cc = it.cacheControl
        val maxAge = when {
            cc.noCache -> 0L
            cc.maxAgeSeconds >= 0 -> cc.maxAgeSeconds.toLong()
            else -> null
        }
        AddonAnswer(out, CacheHints(maxAge, cc.noStore, staleAllowed = !cc.mustRevalidate && !cc.noCache))
    }

    companion object {
        const val MAX_BYTES: Long = 2L * 1024 * 1024
        private const val PERMITS = 4
        private const val TIMEOUT_S = 15L
        private const val CHUNK = 8_192L
    }
}
