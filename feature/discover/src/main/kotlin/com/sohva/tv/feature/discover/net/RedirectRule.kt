package com.sohva.tv.feature.discover.net

import okhttp3.HttpUrl

/**
 * Which redirects an addon JSON request follows (decision "Addon redirects"; spec 50 §8 names the
 * need: OpenSubtitles Pro answers with a cross-origin 302). At most [MAX_HOPS]; each target must be
 * HTTPS (an HTTP request may only move within its own host, which only device tests' local servers
 * use), and never an address literal on the loopback, private or link-local networks or
 * `localhost`, so an addon cannot point the TV at the household's own devices. Nothing but the
 * `Accept` header is ever sent, so no secret travels with a redirect.
 */
object RedirectRule {
    const val MAX_HOPS: Int = 3
    val STATUSES: Set<Int> = setOf(301, 302, 303, 307, 308)

    /** The next URL, or null when the rule refuses it. */
    fun next(from: HttpUrl, location: String?): HttpUrl? {
        val to = location?.let(from::resolve) ?: return null
        val sameHost = to.host == from.host
        if (!to.isHttps && !(sameHost && !from.isHttps)) return null
        if (!sameHost && local(to.host)) return null
        return to
    }

    private fun local(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".localhost")) return true
        val v4 = h.split('.').takeIf { it.size == 4 }?.map { it.toIntOrNull() ?: return false }
        if (v4 != null) {
            val (a, b) = v4
            return a == 127 || a == 10 || a == 0 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        // IPv6 literals: loopback, unique local (fc00::/7), link-local (fe80::/10).
        if (':' in h) return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")
        return false
    }
}
