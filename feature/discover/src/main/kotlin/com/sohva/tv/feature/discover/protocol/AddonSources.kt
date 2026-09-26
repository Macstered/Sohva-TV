package com.sohva.tv.feature.discover.protocol

import com.squareup.moshi.JsonReader
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer

/** How a stream would be reached (ADDON-FR-21); only [HTTP] ever plays. */
enum class StreamKind { HTTP, TORRENT, EXTERNAL, UNSUPPORTED }

/** A subtitle offered by an addon or inline in a stream (ADDON-FR-23). */
data class AddonSubtitle(val id: String, val lang: String, val url: String) {
    override fun toString(): String = "AddonSubtitle($lang)"
}

/**
 * One stream (ADDON-FR-21, -22). [url] and [headers] are secrets of the viewer's configuration:
 * they go to the player's media transport only, never to logs, the screen or saved state.
 */
data class AddonStream(
    val kind: StreamKind,
    val name: String,
    val description: String?,
    val url: String?,
    val headers: Map<String, String>,
    val videoHash: String?,
    val videoSize: Long?,
    val filename: String?,
    val subtitles: List<AddonSubtitle>,
) {
    override fun toString(): String = "AddonStream($kind)"
}

object AddonSourceParser {
    private const val MAX_STREAMS = 500
    private const val MAX_SUBTITLES = 1_000
    private const val MAX_HEADERS = 32
    private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9a-zA-Z-]+")
    private val FORBIDDEN = setOf(
        "host", "connection", "content-length", "transfer-encoding", "te", "trailer", "upgrade", "keep-alive",
        "proxy-authorization", "proxy-authenticate",
    )
    private val HASH = Regex("[0-9a-fA-F]{16}")

    fun streams(body: Buffer): List<AddonStream> = AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
        val out = ArrayList<AddonStream>()
        var found = false
        r.forFields { f ->
            if (f != "streams") {
                r.skipValue()
                return@forFields
            }
            found = true
            var count = 0
            if (!r.forItems {
                    if (++count > MAX_STREAMS) fail(AddonFailure.RESPONSE_TOO_LARGE)
                    stream(r)?.let(out::add)
                }
            ) {
                fail(AddonFailure.INVALID_RESPONSE)
            }
        }
        if (!found) fail(AddonFailure.INVALID_RESPONSE)
        out
    }

    fun subtitles(body: Buffer): List<AddonSubtitle> = AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
        var out: List<AddonSubtitle>? = null
        r.forFields { f -> if (f == "subtitles") out = subtitleList(r) ?: fail(AddonFailure.INVALID_RESPONSE) else r.skipValue() }
        out ?: fail(AddonFailure.INVALID_RESPONSE)
    }

    private fun subtitleList(r: JsonReader): List<AddonSubtitle>? {
        val out = LinkedHashSet<AddonSubtitle>()
        var count = 0
        if (!r.forItems {
                if (++count > MAX_SUBTITLES) fail(AddonFailure.RESPONSE_TOO_LARGE)
                var id: String? = null
                var lang: String? = null
                var url: String? = null
                r.forFields { f ->
                    when (f) {
                        "id" -> id = r.textOrNull(1_024)
                        "lang" -> lang = r.textOrNull(128)
                        "url" -> url = safeUrl(r.textOrNull(8_192))?.takeUnless(::localHost)
                        else -> r.skipValue()
                    }
                }
                if (id != null && lang != null && url != null) out += AddonSubtitle(id, lang, url)
            }
        ) {
            return null
        }
        return out.toList()
    }

    /** One stream, or null when it is malformed or its headers are unsafe (the whole stream is dropped, FR-22). */
    private fun stream(r: JsonReader): AddonStream? {
        var url: String? = null
        var infoHash = false
        var external = false
        var other = false
        var name: String? = null
        var description: String? = null
        var title: String? = null
        var headers: Map<String, String> = emptyMap()
        var unsafe = false
        var videoHash: String? = null
        var videoSize: Long? = null
        var filename: String? = null
        var subtitles: List<AddonSubtitle> = emptyList()
        var unsafeUrl = false
        if (!r.forFields { f ->
                when (f) {
                    "url" -> {
                        val raw = r.textOrNull(8_192)
                        val safe = safeUrl(raw)?.takeUnless(::localHost)
                        if (safe != null) url = safe else if (raw != null) unsafeUrl = true
                    }
                    "infoHash" -> infoHash = r.textOrNull(256) != null
                    "externalUrl" -> external = r.textOrNull(8_192) != null
                    "ytId", "nzbUrl", "rarUrls", "zipUrls" -> {
                        r.skipValue()
                        other = true
                    }
                    "name" -> name = r.textOrNull(512)
                    "description" -> description = r.textOrNull(8_192)
                    "title" -> title = r.textOrNull(8_192)
                    "subtitles" -> subtitles = subtitleList(r).orEmpty()
                    "behaviorHints" -> {
                        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) {
                            r.skipValue()
                            unsafe = true
                            return@forFields
                        }
                        r.forFields { h ->
                            when (h) {
                                "proxyHeaders" -> {
                                    if (r.peek() != JsonReader.Token.BEGIN_OBJECT) {
                                        r.skipValue()
                                        unsafe = true
                                        return@forFields
                                    }
                                    r.forFields { p ->
                                        if (p != "request") {
                                            r.skipValue()
                                            return@forFields
                                        }
                                        val parsed = headers(r)
                                        if (parsed == null) unsafe = true else headers = parsed
                                    }
                                }
                                "videoHash" -> videoHash = r.textOrNull(16)?.takeIf { HASH.matches(it) }
                                "videoSize" -> videoSize = r.longOrNull()?.takeIf { it >= 0 }
                                "filename" -> filename = r.textOrNull(1_024)?.takeIf { v -> v.none { it.isISOControl() } }
                                else -> r.skipValue()
                            }
                        }
                    }
                    else -> r.skipValue()
                }
            }
        ) {
            return null
        }
        if (unsafe) return null
        val kind = when {
            url != null -> StreamKind.HTTP
            infoHash -> StreamKind.TORRENT
            external -> StreamKind.EXTERNAL
            unsafeUrl || other -> StreamKind.UNSUPPORTED
            else -> return null
        }
        return AddonStream(
            kind, name ?: "Stream", description ?: title, url.takeIf { kind == StreamKind.HTTP },
            if (kind == StreamKind.HTTP) headers else emptyMap(), videoHash, videoSize, filename, subtitles,
        )
    }

    /** FR-22: ≤ 32 token-named, unique, not hop-by-hop or `sec-*`, printable ASCII values ≤ 8,192; else null. */
    private fun headers(r: JsonReader): Map<String, String>? {
        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) {
            r.skipValue()
            return null
        }
        val out = LinkedHashMap<String, String>()
        val seen = HashSet<String>()
        var bad = false
        r.forFields { name ->
            val value = if (r.peek() == JsonReader.Token.STRING) r.nextString() else {
                r.skipValue()
                bad = true
                return@forFields
            }
            val lower = name.lowercase(Locale.ROOT)
            if (out.size >= MAX_HEADERS || name.length > 128 || !TOKEN.matches(name) || lower in FORBIDDEN || lower.startsWith("sec-") ||
                !seen.add(lower) || value.length > 8_192 || value.any { it.code !in 0x20..0x7E }
            ) {
                bad = true
            } else {
                out[name] = value
            }
        }
        return out.takeUnless { bad }
    }

    /** Stremio's local streaming bridge does not exist in Sohva (FR-21). */
    private fun localHost(value: String): Boolean {
        val host = value.toHttpUrlOrNull()?.host?.lowercase(Locale.ROOT) ?: return true
        return host == "localhost" || host.endsWith(".localhost") || host == "::1" || host == "[::1]" || host.startsWith("127.") || host == "0.0.0.0"
    }
}
