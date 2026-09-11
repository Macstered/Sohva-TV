package com.sohva.tv.addons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Payloads may contain configured artwork/stream URLs. Never print the body. */
class AddonDocument(val body: String, val maxAgeSeconds: Int?, val noStore: Boolean, val staleAllowed: Boolean = true) {
    override fun toString(): String = "AddonDocument([redacted])"
}

class AddonClient(
    concurrency: Int = 4,
    timeoutMillis: Long = 15_000,
    private val maxResponseBytes: Int = AddonManifestParser.MAX_BYTES,
) {
    init {
        require(concurrency in 1..8)
        require(timeoutMillis in 100..60_000)
        require(maxResponseBytes in 1..8 * 1024 * 1024)
    }
    private val permits = Semaphore(concurrency)
    // Dedicated client: never inherit IPTV auth, cookie jars, interceptors or logging.
    // Redirects are explicitly unsupported until a credential-safe redirect policy is implemented.
    private val client = OkHttpClient.Builder()
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun manifest(endpoint: AddonEndpoint): AddonDocument = fetch(endpoint.exportConfiguredUrl())

    suspend fun resource(
        endpoint: AddonEndpoint,
        manifest: AddonManifest,
        resource: String,
        type: String,
        id: String,
        extras: Map<String, String> = emptyMap(),
    ): AddonDocument {
        if (!manifest.supports(resource, type, id)) fail(AddonFailure.UNSUPPORTED_RESOURCE)
        if (resource == "catalog") manifest.catalogs.first { it.id == id && it.type == type }.validateExtras(extras)
        return fetch(endpoint.resourceUrl(resource, type, id, extras))
    }

    private suspend fun fetch(url: String): AddonDocument = permits.withPermit {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(AddonException(
                        if (e is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK,
                    )))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            if (continuation.isCancelled) return
                            if (it.code in 300..399) fail(AddonFailure.REDIRECT)
                            if (!it.isSuccessful) throw AddonException(AddonFailure.HTTP_ERROR, it.code)
                            if (it.body.contentLength() > maxResponseBytes) fail(AddonFailure.RESPONSE_TOO_LARGE)
                            val output = ByteArrayOutputStream()
                            it.body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size().toLong() + count > maxResponseBytes) fail(AddonFailure.RESPONSE_TOO_LARGE)
                                    output.write(buffer, 0, count)
                                }
                            }
                            Result.success(AddonDocument(output.toString(Charsets.UTF_8.name()),
                                if (it.cacheControl.noCache) 0 else it.cacheControl.maxAgeSeconds.takeIf { seconds -> seconds >= 0 },
                                it.cacheControl.noStore, !it.cacheControl.mustRevalidate && !it.cacheControl.noCache))
                        }
                    } catch (error: AddonException) {
                        // Reconstruct to avoid a suppressed close exception carrying a URL.
                        Result.failure(AddonException(error.failure, error.httpStatus))
                    } catch (error: Exception) {
                        Result.failure(AddonException(if (error is InterruptedIOException) AddonFailure.TIMEOUT else AddonFailure.NETWORK))
                    }
                    continuation.resumeWith(result)
                }
            })
        }
    }
}
