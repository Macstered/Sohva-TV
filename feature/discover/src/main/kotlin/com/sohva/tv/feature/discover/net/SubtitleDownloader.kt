package com.sohva.tv.feature.discover.net

import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * Subtitle files (spec 50 ADDON-FR-101): only for a choice, on their own client with the media
 * transport's rules but never a stream header — GET only, at most five redirects followed by hand
 * and never from HTTPS to HTTP, a 20 s deadline for the whole call — and at most 4 MiB after
 * decompression. Nothing is cached or written to storage.
 */
class SubtitleDownloader(base: OkHttpClient) {
    private val client = base.newBuilder()
        .callTimeout(CALL_S, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun get(start: HttpUrl): ByteArray {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            when (val hop = call(url)) {
                is Hop.Body -> return hop.bytes
                is Hop.Redirect -> {
                    val next = hop.location?.let { url.resolve(it) } ?: throw AddonException(AddonFailure.REDIRECT, hop.code)
                    if (url.isHttps && !next.isHttps) throw AddonException(AddonFailure.REDIRECT, hop.code)
                    url = next
                }
            }
        }
        throw AddonException(AddonFailure.REDIRECT)
    }

    /** One hop's outcome, read completely on OkHttp's thread. */
    private sealed interface Hop {
        class Redirect(val location: String?, val code: Int) : Hop

        class Body(val bytes: ByteArray) : Hop
    }

    /**
     * One request. The body is read on OkHttp's callback thread, so a caller on the main thread
     * never reads the socket (AGENTS.md §4 rule 1; a TLS read there crashes the app).
     */
    private suspend fun call(url: HttpUrl): Hop {
        val call = client.newCall(Request.Builder().url(url).get().build())
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        cont.resumeWithException(AddonException(if (e is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val hop = runCatching {
                            response.use { r ->
                                when {
                                    r.code in REDIRECTS -> Hop.Redirect(r.header("Location"), r.code)
                                    r.code !in 200..299 -> throw AddonException(AddonFailure.HTTP_ERROR, r.code)
                                    else -> Hop.Body(read(r))
                                }
                            }
                        }
                        hop.fold({ cont.resume(it) { _, _, _ -> } }, { cont.resumeWithException(it) })
                    }
                },
            )
        }
    }

    private fun read(response: Response): ByteArray {
        val body = response.body
        if (body.contentLength() > MAX_BYTES) throw AddonException(AddonFailure.RESPONSE_TOO_LARGE)
        val out = Buffer()
        try {
            val source = body.source()
            while (source.read(out, CHUNK) != -1L) {
                if (out.size > MAX_BYTES) throw AddonException(AddonFailure.RESPONSE_TOO_LARGE)
            }
        } catch (e: InterruptedIOException) {
            throw AddonException(AddonFailure.TIMEOUT)
        } catch (e: IOException) {
            throw AddonException(AddonFailure.NETWORK)
        }
        return out.readByteArray()
    }

    companion object {
        const val MAX_BYTES: Long = 4L * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val CALL_S = 20L
        private const val CHUNK = 8_192L
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        fun parse(url: String): HttpUrl? = url.toHttpUrlOrNull()
    }
}
