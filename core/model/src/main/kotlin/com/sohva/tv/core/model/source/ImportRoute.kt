package com.sohva.tv.core.model.source

import java.net.URI
import java.net.URLDecoder

/** An Xtream account: normalised server (no trailing slash) and credentials. */
data class XtreamAccount(val baseUrl: String, val username: String, val password: String) {
    override fun toString(): String = "XtreamAccount(credentials=<redacted>)"
}

/**
 * How a source is imported (spec 10 SRC-FR-73..74): the one decision every caller uses. An M3U
 * address of the Xtream `get.php` form is imported through the Xtream API.
 */
sealed interface ImportRoute {
    /** A plain M3U playlist, with the XMLTV guide address if any. */
    data class M3u(val playlistUrl: String, val guideUrl: String?) : ImportRoute {
        override fun toString(): String = "M3u(<redacted>)"
    }

    /** Through the Xtream API; [explicitGuideUrl] is an XMLTV address that overrides `xmltv.php`. */
    data class Xtream(val account: XtreamAccount, val explicitGuideUrl: String?) : ImportRoute {
        override fun toString(): String = "Xtream(<redacted>)"
    }

    companion object {
        fun of(config: SourceConfig): ImportRoute? {
            val s = config.secrets
            return when (config.source.type) {
                SourceType.XTREAM -> {
                    val base = s.xtreamBaseUrl ?: return null
                    Xtream(XtreamAccount(base.trimEnd('/'), s.xtreamUsername.orEmpty(), s.xtreamPassword.orEmpty()), null)
                }
                SourceType.M3U -> {
                    val url = s.m3uUrl ?: return null
                    val derived = xtreamFromGetPhp(url)
                    if (derived != null) Xtream(derived, s.xmlTvUrl) else M3u(url, s.xmlTvUrl)
                }
            }
        }

        /**
         * An address ending `/get.php` with `username` and `password` query parameters (last segment
         * and parameter names in any case, values URL-decoded) → server = scheme://host[:port]/parent
         * path. Missing username or password → null.
         */
        fun xtreamFromGetPhp(url: String): XtreamAccount? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val path = uri.rawPath ?: return null
            val last = path.substringAfterLast('/')
            if (!last.equals("get.php", ignoreCase = true)) return null
            val params = (uri.rawQuery ?: return null).split('&').mapNotNull { pair ->
                val name = pair.substringBefore('=').lowercase()
                val value = pair.substringAfter('=', "")
                runCatching { name to URLDecoder.decode(value, "UTF-8") }.getOrNull()
            }.toMap()
            val user = params["username"]?.takeIf { it.isNotBlank() } ?: return null
            val password = params["password"]?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val parent = path.substringBeforeLast('/')
            return XtreamAccount("${uri.scheme}://${uri.host}$port$parent", user, password)
        }
    }
}
