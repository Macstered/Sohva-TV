package com.sohva.tv.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class AddonStreamKind { HTTP, TORRENT, EXTERNAL, UNSUPPORTED }

/** Resolved URLs/headers can be credentials. Keep in memory only; never log these objects. */
class AddonSubtitle(val id: String, val language: String, val url: String) {
    override fun toString() = "AddonSubtitle([redacted])"
}

class AddonStream(
    val name: String, val description: String?, val kind: AddonStreamKind,
    val url: String?, val requestHeaders: Map<String, String>, val subtitles: List<AddonSubtitle>,
    val videoHash: String?, val videoSize: Long?, val filename: String?,
) {
    /** Only these three protocol extras belong in a subtitle request, never stream auth headers. */
    fun subtitleExtras(): Map<String, String> = buildMap {
        videoHash?.let { put("videoHash", it) }
        videoSize?.let { put("videoSize", it.toString()) }
        filename?.let { put("filename", it) }
    }
    override fun toString() = "AddonStream([redacted])"
}

object AddonSourceParser {
    fun streams(text: String): List<AddonStream> = parse(text, "streams", 500) { it.stream() }
    fun subtitles(text: String): List<AddonSubtitle> = parse(text, "subtitles", 1000) { it.subtitle() }
        .distinctBy { listOf(it.id, it.language, it.url) }

    private fun <T> parse(text: String, field: String, limit: Int, item: (JsonObject) -> T?): List<T> {
        try {
            AddonManifestParser.validateEnvelope(text)
            val root = Json.parseToJsonElement(text) as? JsonObject ?: fail(AddonFailure.INVALID_RESPONSE)
            val values = root[field] as? JsonArray ?: fail(AddonFailure.INVALID_RESPONSE)
            if (values.size > limit) fail(AddonFailure.RESPONSE_TOO_LARGE)
            return values.mapNotNull { (it as? JsonObject)?.let(item) }
        } catch (error: AddonException) {
            throw AddonException(if (error.failure == AddonFailure.RESPONSE_TOO_LARGE) error.failure else AddonFailure.INVALID_RESPONSE)
        } catch (_: Exception) { fail(AddonFailure.INVALID_RESPONSE) }
    }

    private fun JsonObject.stream(): AddonStream? {
        val rawUrl = text("url", 16_384)
        val url = rawUrl?.safeHttp()
        val hintsValue = get("behaviorHints")?.takeUnless { it == JsonNull }
        val hints = hintsValue as? JsonObject ?: if (hintsValue != null) return null else null
        val proxyValue = hints?.get("proxyHeaders")?.takeUnless { it == JsonNull }
        val proxy = proxyValue as? JsonObject ?: if (proxyValue != null) return null else null
        val headerValue = proxy?.get("request")?.takeUnless { it == JsonNull }
        val headers = headerValue as? JsonObject ?: if (headerValue != null) return null else null
        // Reject the entire stream if required headers are unsafe: silently dropping auth is incorrect.
        val requestHeaders = headers?.safeHeaders() ?: if (headers != null) return null else emptyMap()
        val kind = when {
            url != null -> AddonStreamKind.HTTP
            text("infoHash") != null -> AddonStreamKind.TORRENT
            text("externalUrl", 16_384) != null -> AddonStreamKind.EXTERNAL
            rawUrl != null || text("ytId") != null || get("nzbUrl") != null || get("rarUrls") != null || get("zipUrls") != null -> AddonStreamKind.UNSUPPORTED
            else -> return null
        }
        val inline = (get("subtitles") as? JsonArray).orEmpty()
        if (inline.size > 1000) return null
        return AddonStream(text("name", 512) ?: "Stream", text("description", 8192) ?: text("title", 8192), kind,
            url, requestHeaders, inline.mapNotNull { (it as? JsonObject)?.subtitle() },
            hints?.text("videoHash", 128)?.takeIf { it.matches(Regex("[a-fA-F0-9]{16}")) },
            (hints?.get("videoSize") as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 },
            hints?.text("filename", 1024)?.takeIf { value -> value.none { it.isISOControl() } })
    }
    private fun JsonObject.subtitle(): AddonSubtitle? {
        val id = text("id", 1024) ?: return null
        val lang = text("lang", 128) ?: return null
        val url = text("url", 16_384)?.safeHttp() ?: return null
        return AddonSubtitle(id, lang, url)
    }
    private fun JsonObject.safeHeaders(): Map<String, String>? {
        if (size > 32) return null
        val names = mutableSetOf<String>()
        val result = linkedMapOf<String, String>()
        for ((key, value) in this) {
            val name = key.lowercase(java.util.Locale.ROOT)
            if (!names.add(name) || key.length > 128 || !key.matches(Regex("[!#$%&'*+.^_`|~0-9a-zA-Z-]+")) ||
                name in setOf("host", "connection", "content-length", "transfer-encoding", "te", "trailer", "upgrade", "keep-alive", "proxy-authorization", "proxy-authenticate") || name.startsWith("sec-")) return null
            val string = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            if (string.length > 8192 || string.any { it.code !in 32..126 }) return null
            result[key] = string
        }
        return result
    }
    private fun JsonObject.text(key: String, limit: Int = 8192) = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() && it.length <= limit }
    private fun String.safeHttp(): String? {
        if (any { it.isISOControl() } || '\\' in this) return null
        return toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }
            // Stremio's local streaming/subtitle bridge does not exist inside Sohva TV.
            ?.takeUnless { it.host == "localhost" || it.host.endsWith(".localhost") || it.host == "::1" || it.host.startsWith("127.") || it.host == "0.0.0.0" }
            ?.toString()
    }
}
