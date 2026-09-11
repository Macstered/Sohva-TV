package com.sohva.tv.addons

import java.security.MessageDigest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Opaque in logs. Only deliberate transport/export/storage code should unwrap the URL. */
class AddonEndpoint private constructor(internal val url: HttpUrl) {
    val host: String get() = url.host
    val fingerprint: String get() = sha256(url.toString())
    fun exportConfiguredUrl(): String = url.toString()
    override fun toString(): String = "AddonEndpoint([redacted])"

    fun resourceUrl(resource: String, type: String, id: String, extras: Map<String, String> = emptyMap()): String {
        if (resource !in setOf("catalog", "meta", "stream", "subtitles", "addon_catalog") ||
            type.isBlank() || id.isBlank() || type.length > 256 || id.length > 2048 ||
            type in setOf(".", "..") || id in setOf(".", "..") || extras.size > 32 ||
            extras.any { (key, value) -> key.isBlank() || key.length > 128 || value.length > 8192 }
        ) fail(AddonFailure.INVALID_REQUEST)
        val builder = url.newBuilder().removePathSegment(url.pathSegments.lastIndex)
            .addPathSegment(resource).addPathSegment(type).addPathSegment(id)
        if (extras.isNotEmpty()) {
            // Encode each value before joining the Stremio extra path component.
            // A slash, ampersand, plus or '=' in search text must stay inside its value.
            val query = HttpUrl.Builder().scheme("https").host("encoding.invalid")
            extras.toSortedMap().forEach { (key, value) -> query.addQueryParameter(key, value) }
            builder.addEncodedPathSegment(query.build().encodedQuery!!)
        }
        val last = builder.build().encodedPathSegments.last()
        return builder.setEncodedPathSegment(builder.build().pathSegments.lastIndex, "$last.json").build().toString()
    }

    companion object {
        fun parse(input: String, allowInsecureHttp: Boolean = false, allowBaseUrl: Boolean = false): AddonEndpoint {
            val trimmed = input.trim()
            if (trimmed.length > 16_384 || trimmed.any { it.isISOControl() } || '\\' in trimmed) fail(AddonFailure.INVALID_URL)
            val normalized = if (trimmed.startsWith("stremio://", ignoreCase = true)) {
                "https://" + trimmed.substringAfter("://")
            } else trimmed
            val parsed = normalized.toHttpUrlOrNull() ?: fail(AddonFailure.INVALID_URL)
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.fragment != null) fail(AddonFailure.INVALID_URL)
            if (!parsed.isHttps && !allowInsecureHttp) fail(AddonFailure.INSECURE_URL)
            val finalUrl = if (parsed.pathSegments.last() == "manifest.json") parsed else {
                // AIOMetadata's configured base is /stremio/<UUID>, sometimes copied
                // without its trailing slash. Support that exact route shape, not
                // arbitrary filenames or configuration pages.
                val aioBase = parsed.pathSegments.size >= 2 &&
                    parsed.pathSegments[parsed.pathSegments.lastIndex - 1] == "stremio" &&
                    parsed.pathSegments.last().matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                val explicitBase = allowBaseUrl && parsed.pathSegments.last() !in setOf("configure", "install") &&
                    !parsed.pathSegments.last().matches(Regex(".*\\.[a-zA-Z]{1,8}"))
                if (parsed.pathSegments.last().isNotEmpty() && !aioBase && !explicitBase) fail(AddonFailure.INVALID_URL)
                parsed.newBuilder().addPathSegment("manifest.json").build()
            }
            return AddonEndpoint(finalUrl)
        }
    }
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
