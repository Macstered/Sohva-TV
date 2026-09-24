package com.sohva.tv.core.net.http

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.player.UserAgents
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okio.BufferedSource
import okio.buffer

/** What a provider request fetches; decides which errors the viewer reads. */
enum class ProviderRequest {
    /** An M3U playlist or an XMLTV guide (spec 10 SRC-FR-48). */
    SOURCE,

    /** The Xtream `player_api.php` API. */
    XTREAM,
    ;

    internal fun httpError(status: Int): AppError =
        if (this == XTREAM) AppError.XtreamHttp(status) else AppError.HttpStatus(status)

    internal val tooLarge: AppError
        get() = if (this == XTREAM) AppError.XtreamResponseTooLarge else AppError.SourceResponseTooLarge
}

/**
 * Every request to an IPTV provider: playlists, guides and the Xtream API (spec 10 §4.9). Bodies
 * are streamed to the caller's reader and never held whole; a body is decompressed when it starts
 * with the gzip magic bytes and stops at [maxBodyBytes] (a runaway guard, not a size limit
 * providers reach). Failures leave as [AppException] with the viewer's sentence.
 *
 * The call is blocking and runs on the caller's thread, which for imports is the background-priority
 * parse thread (SRC-L-01). Cancelling the coroutine cancels the call, which closes the socket and
 * unblocks a read that would otherwise wait out the 90-second timeout.
 */
class ProviderHttp(
    private val client: OkHttpClient,
    private val log: DiagnosticsLog,
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
) {
    /** Fetches [url] and hands the body to [read]; the body is closed when [read] returns. */
    suspend fun <T> get(url: String, request: ProviderRequest, read: (BufferedSource) -> T): T {
        val httpUrl = url.trim().toHttpUrlOrNull() ?: throw AppException(AppError.SourceUrlMalformed)
        return get(httpUrl, request, read)
    }

    suspend fun <T> get(url: HttpUrl, request: ProviderRequest, read: (BufferedSource) -> T): T = coroutineScope {
        val call = client.newCall(Request.Builder().url(url).get().build())
        // Unconfined: runs on the cancelling thread, because this one is blocked in the read.
        val canceller = launch(Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            execute(call, url, request, read)
        } catch (e: IOException) {
            ensureActive()
            throw transportFailure(e, url)
        } finally {
            canceller.cancel()
        }
    }

    private fun <T> execute(call: Call, url: HttpUrl, request: ProviderRequest, read: (BufferedSource) -> T): T {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                log.info(EVENT, "GET ${Redactor.redact(url.toString())}: HTTP ${response.code}")
                throw AppException(request.httpError(response.code))
            }
            val body = response.body
            if (body.contentLength() > maxBodyBytes) throw AppException(request.tooLarge)
            val raw = CappedSource(body.source(), maxBodyBytes).buffer()
            try {
                return read(BodySources.decompressed(raw, maxBodyBytes))
            } catch (_: BodyTooLargeException) {
                throw AppException(request.tooLarge)
            }
        }
    }

    private fun transportFailure(e: IOException, url: HttpUrl): AppException {
        log.info(EVENT, "GET ${Redactor.redact(url.toString())}: no response")
        return AppException(AppError.TransportFailed(transportDetail(e, url.host)), e)
    }

    companion object {
        /** The runaway guard on every provider body (plan/09 M1: 1 GiB). */
        const val MAX_BODY_BYTES: Long = 1L shl 30
        private const val EVENT = "http"
        private val ipv4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")

        /**
         * The provider client of SRC-FR-44: HTTP/1.1 only, connect 20 s, read 90 s, redirects
         * followed including HTTPS to HTTP, the app's agent on every request (SRC-FR-45).
         */
        fun client(userAgent: String): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
            .build()

        /** `Sohva TV/<version> (Android TV <release>)`. */
        fun userAgent(versionName: String, androidRelease: String): String = UserAgents.sohva(versionName, androidRelease)

        /** The exception text without addresses, host names or credentials (SRC-FR-48). */
        internal fun transportDetail(e: IOException, host: String): String? {
            val text = Redactor.redact(e.message) ?: return null
            return ipv4.replace(text.replace(host, Redactor.REDACTED, ignoreCase = true), Redactor.REDACTED)
        }
    }
}
