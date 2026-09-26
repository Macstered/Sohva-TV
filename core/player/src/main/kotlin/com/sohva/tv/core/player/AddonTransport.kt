package com.sohva.tv.core.player

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The media transport of addon streams (spec 50 ADDON-FR-95): GET only, connect 15 s, read 20 s,
 * no whole-call deadline (progressive video). Redirects are followed here, by hand: at most five
 * hops, never from HTTPS to HTTP, and a new request per hop that carries the stream's own headers
 * only back to the origin they were given for; `Authorization`, `Cookie` and `Referer` never cross
 * to another origin, and nothing a previous hop set is inherited. Media3's own request headers
 * (range, encoding) travel on every hop. Failures carry no URL.
 */
object AddonTransport {
    /** Marks a request for this transport; set by the stream registry, removed before sending. */
    const val MARKER: String = "X-Sohva-Addon"

    /** Names the stream's own header names, so a hop to another origin can drop them. */
    const val STREAM_HEADERS: String = "X-Sohva-Addon-Headers"

    private const val MAX_HOPS = 5
    private val NEVER_ACROSS = setOf("authorization", "cookie", "referer")

    fun client(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(Redirects)
        .build()

    /** Sends marked requests through [addon], everything else through [provider]. */
    fun route(provider: Call.Factory, addon: Call.Factory): Call.Factory = Call.Factory { request ->
        if (request.header(MARKER) != null) addon.newCall(request) else provider.newCall(request)
    }

    private object Redirects : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val first = chain.request()
            val streamNames = first.header(STREAM_HEADERS).orEmpty().split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
            val origin = origin(first.url)
            var request = first.newBuilder().removeHeader(MARKER).removeHeader(STREAM_HEADERS).build()
            if (request.method != "GET") throw IOException("Addon media requests are GET only")
            var hops = 0
            while (true) {
                val response = chain.proceed(request)
                if (response.code !in REDIRECTS) return response
                val location = response.header("Location")
                val next = location?.let { request.url.resolve(it) }
                response.close()
                if (next == null) throw IOException("A redirect without a target")
                if (++hops > MAX_HOPS) throw IOException("Too many redirects")
                if (request.url.isHttps && !next.isHttps) throw IOException("A redirect from HTTPS to HTTP")
                request = hop(first, next, sameOrigin = origin(next) == origin, streamNames)
            }
        }

        /** A fresh request for [url]: Media3's headers, and the stream's only back at their own origin. */
        private fun hop(first: Request, url: HttpUrl, sameOrigin: Boolean, streamNames: Set<String>): Request {
            val builder = Request.Builder().url(url).get()
            for ((name, value) in first.headers) {
                val lower = name.lowercase(Locale.ROOT)
                if (lower == MARKER.lowercase(Locale.ROOT) || lower == STREAM_HEADERS.lowercase(Locale.ROOT)) continue
                if (!sameOrigin && (lower in streamNames || lower in NEVER_ACROSS)) continue
                builder.addHeader(name, value)
            }
            return builder.build()
        }

        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }

    /** Scheme, host and port: the same origin in the web's sense. */
    fun origin(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
}
