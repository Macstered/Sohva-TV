package com.sohva.tv.core.net.metadata

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * The metadata providers' HTTP (spec 41 §7.3, §9.4): its own client on the shared connection pool
 * with at most 4 requests (2 per host), connect 10 s, read 20 s; JSON accepted; bodies read up to
 * 2 MiB and refused past it. A request URL can carry a TMDB v3 key, so no URL is ever logged.
 * Cancelling the caller cancels the call.
 */
class MetadataHttp(base: OkHttpClient) {
    internal val client: OkHttpClient = base.newBuilder()
        .apply { interceptors().clear() }
        .dispatcher(Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 2
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * GET [url] from [provider] and return the body. TVmaze's 429 is retried once after its
     * `Retry-After` (1–5 s, 2 when absent); every other failure is an [AppException].
     */
    suspend fun get(provider: MetadataProvider, url: HttpUrl, bearer: String? = null): Buffer {
        val first = fetch(provider, url, bearer)
        if (first is Result.Body) return first.buffer
        val retry = first as Result.TooMany
        if (provider != MetadataProvider.TVMAZE) throw AppException(AppError.MetadataHttp(provider.displayName, 429))
        delay(retry.afterSeconds * 1_000L)
        return when (val second = fetch(provider, url, bearer)) {
            is Result.Body -> second.buffer
            is Result.TooMany -> throw AppException(AppError.MetadataHttp(provider.displayName, 429))
        }
    }

    private sealed interface Result {
        class Body(val buffer: Buffer) : Result
        class TooMany(val afterSeconds: Int) : Result
    }

    private suspend fun fetch(provider: MetadataProvider, url: HttpUrl, bearer: String?): Result = coroutineScope {
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
        val call = client.newCall(request)
        val canceller = launch(Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (response.code == 429) {
                    val after = response.header("Retry-After")?.trim()?.toIntOrNull()?.coerceIn(1, 5) ?: 2
                    return@coroutineScope Result.TooMany(after)
                }
                if (!response.isSuccessful) throw AppException(AppError.MetadataHttp(provider.displayName, response.code))
                val source = response.body.source()
                val buffer = Buffer()
                while (buffer.size <= MAX_BODY) {
                    if (source.read(buffer, 8_192) == -1L) break
                }
                if (buffer.size > MAX_BODY) throw AppException(AppError.MetadataTooLarge(provider.displayName))
                Result.Body(buffer)
            }
        } catch (e: IOException) {
            ensureActive()
            throw AppException(AppError.TransportFailed(null), e)
        } finally {
            canceller.cancel()
        }
    }

    companion object {
        const val MAX_BODY: Long = 2L * 1024 * 1024
        const val USER_AGENT: String = "SohvaTV/0.1 (Android TV; personal use)"
    }
}
