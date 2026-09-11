package com.sohva.tv.addons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/** Dedicated media client. Configured headers belong ONLY to the initial media origin. */
object AddonMediaTransport {
    fun client(originUrl: String, headers: Map<String, String> = emptyMap()): OkHttpClient {
        val origin = validUrl(originUrl)
        // Validate even manually-created models, not only parser output.
        val safeHeaders = okhttp3.Headers.Builder().apply {
            if (headers.size > 32) throw IOException("Invalid media headers")
            if (headers.keys.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size != headers.size) throw IOException("Invalid media headers")
            headers.forEach { (name, value) ->
                if (name.lowercase(java.util.Locale.ROOT) in setOf("host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-authenticate", "te", "trailer", "upgrade", "keep-alive") ||
                    name.startsWith("sec-", true) || name.length > 128 || value.length > 8192 || value.any { it.code !in 32..126 }) throw IOException("Invalid media headers")
                try { add(name, value) } catch (_: IllegalArgumentException) { throw IOException("Invalid media headers") }
            }
        }.build()
        return OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // No whole-call deadline for a long progressive video response.
            .addInterceptor { chain ->
                var request = chain.request()
                if (request.method != "GET") throw IOException("Unsupported media request")
                var hops = 0
                while (true) {
                    val target = validUrl(request.url.toString())
                    if (origin.isHttps && !target.isHttps) throw IOException("Media HTTPS downgrade blocked")
                    val builder = request.newBuilder()
                    // Never inherit a header from the manifest API or a previous redirect.
                    safeHeaders.names().forEach(builder::removeHeader)
                    builder.removeHeader("Authorization").removeHeader("Cookie").removeHeader("Referer")
                    if (sameOrigin(origin, target)) safeHeaders.forEach { (key, value) -> builder.header(key, value) }
                    val response = chain.proceed(builder.build())
                    if (response.code !in setOf(301, 302, 303, 307, 308)) return@addInterceptor response
                    val next = response.header("Location")?.let(target::resolve)
                    response.close()
                    if (++hops > 5 || next == null || (target.isHttps && !next.isHttps)) throw IOException("Media redirect blocked")
                    validUrl(next.toString())
                    request = request.newBuilder().url(next).build()
                }
                @Suppress("UNREACHABLE_CODE") throw IOException("Media redirect failed")
            }.build()
    }
    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
    private fun validUrl(value: String): HttpUrl {
        if (value.length > 16_384 || value.any(Char::isISOControl) || '\\' in value) throw IOException("Invalid media address")
        val url = value.toHttpUrlOrNull() ?: throw IOException("Unsupported media transport")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) throw IOException("Invalid media address")
        return url
    }
}

class AddonSubtitleData(val bytes: ByteArray, val mimeType: String, val language: String) {
    override fun toString() = "AddonSubtitleData([redacted])"
}

/** Explicit selection only, no stream headers, bounded decoded content, no disk files or ZIP extraction. */
class AddonSubtitleLoader {
    suspend fun load(subtitle: AddonSubtitle): AddonSubtitleData = suspendCancellableCoroutine { continuation ->
        try {
            val http = AddonMediaTransport.client(subtitle.url).newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
            val call = http.newCall(okhttp3.Request.Builder().url(subtitle.url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    continuation.resumeWith(Result.failure(AddonException(AddonFailure.NETWORK)))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val result = try { response.use {
                    if (!response.isSuccessful) throw IOException("Subtitle request failed")
                    if (response.body.contentLength() > MAX_BYTES) fail(AddonFailure.RESPONSE_TOO_LARGE)
                    val data = ByteArrayOutputStream()
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (data.size() + count > MAX_BYTES) fail(AddonFailure.RESPONSE_TOO_LARGE)
                            data.write(buffer, 0, count)
                        }
                    }
                    Result.success(decode(data.toByteArray(), subtitle.language))
                    } } catch (error: AddonException) { Result.failure(AddonException(error.failure)) }
                    catch (_: Exception) { Result.failure(AddonException(AddonFailure.NETWORK)) }
                    continuation.resumeWith(result)
                }
            })
        } catch (_: Exception) { continuation.resumeWith(Result.failure(AddonException(AddonFailure.NETWORK))) }
    }
    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        fun decode(bytes: ByteArray, language: String): AddonSubtitleData {
            if (bytes.size > MAX_BYTES) fail(AddonFailure.RESPONSE_TOO_LARGE)
            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
            val mime = when {
                text.startsWith("WEBVTT") -> "text/vtt"
                text.startsWith("[Script Info]", true) && text.contains("[Events]", true) -> "text/x-ssa"
                Regex("(?m)^\\d{2,}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->").containsMatchIn(text) -> "application/x-subrip"
                else -> fail(AddonFailure.INVALID_RESPONSE)
            }
            return AddonSubtitleData(text.toByteArray(Charsets.UTF_8), mime, language)
        }
    }
}
