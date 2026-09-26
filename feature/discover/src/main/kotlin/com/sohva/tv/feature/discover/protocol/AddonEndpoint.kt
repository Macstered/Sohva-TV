package com.sohva.tv.feature.discover.protocol

import java.security.MessageDigest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A configured addon URL, normalised (spec 50 ADDON-FR-06…08). The path and query carry the
 * viewer's configuration (often credentials), so [toString] is redacted and the URL itself is
 * handed only to the client and the encrypted store.
 */
class AddonEndpoint private constructor(val manifestUrl: HttpUrl) {
    /** SHA-256 hex of the full normalised URL: installations are unique per profile and this (FR-07). */
    val fingerprint: String = Hashes.sha256Hex(manifestUrl.toString())

    /**
     * `<base>/<resource>/<type>/<id>[/<extras>].json` (FR-08): extras sorted by key, each value
     * encoded as a query value so `/ & + =` stay inside it.
     */
    fun resource(resource: String, type: String, id: String, extras: Map<String, String> = emptyMap()): HttpUrl {
        if (resource !in RESOURCES) fail(AddonFailure.INVALID_REQUEST)
        checkSegment(type, MAX_TYPE)
        checkSegment(id, MAX_ID)
        if (extras.size > MAX_EXTRAS) fail(AddonFailure.INVALID_REQUEST)
        extras.forEach { (k, v) -> if (k.isEmpty() || k.length > MAX_EXTRA_KEY || v.length > MAX_EXTRA_VALUE) fail(AddonFailure.INVALID_REQUEST) }
        // The configuration segments are kept exactly as encoded (FR-06).
        val segments = manifestUrl.encodedPathSegments.dropLast(1)
        val builder = manifestUrl.newBuilder().encodedPath("/")
        segments.filter { it.isNotEmpty() }.forEach { builder.addEncodedPathSegment(it) }
        builder.addPathSegment(resource).addPathSegment(type)
        if (extras.isEmpty()) {
            builder.addPathSegment("$id.json")
        } else {
            builder.addPathSegment(id)
            val encoded = extras.toSortedMap().entries.joinToString("&") { (k, v) -> "${encodeValue(k)}=${encodeValue(v)}" }
            builder.addEncodedPathSegment("$encoded.json")
        }
        return builder.build()
    }

    override fun equals(other: Any?): Boolean = other is AddonEndpoint && other.fingerprint == fingerprint

    override fun hashCode(): Int = fingerprint.hashCode()

    override fun toString(): String = "AddonEndpoint(<redacted>)"

    companion object {
        private val RESOURCES = setOf("catalog", "meta", "stream", "subtitles", "addon_catalog")
        private const val MAX_INPUT = 16_384
        private const val MAX_TYPE = 256
        private const val MAX_ID = 2_048
        private const val MAX_EXTRAS = 32
        private const val MAX_EXTRA_KEY = 128
        private const val MAX_EXTRA_VALUE = 8_192
        private val UUID_BASE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val FILE_NAME = Regex(".*\\.[a-zA-Z]{1,8}")

        /**
         * FR-06: trimmed; `stremio://` becomes `https://`; plain HTTP only with [allowHttp] (no UI
         * sets it, decision "Spec 50 open questions" Q1); `manifest.json` appended to a directory,
         * an AIOMetadata `/stremio/<UUID>` base, or, where [baseAllowed], any last segment that is
         * not `configure`, `install` or a file name.
         */
        fun parse(input: String, baseAllowed: Boolean, allowHttp: Boolean = false): AddonEndpoint {
            val text = input.trim()
            if (text.isEmpty() || text.length > MAX_INPUT || text.any { it.isISOControl() || it == '\\' }) fail(AddonFailure.INVALID_URL)
            val rewritten = if (text.startsWith("stremio://", ignoreCase = true)) "https://" + text.substring("stremio://".length) else text
            if (rewritten.contains('#')) fail(AddonFailure.INVALID_URL)
            val url = rewritten.toHttpUrlOrNull() ?: fail(AddonFailure.INVALID_URL)
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) fail(AddonFailure.INVALID_URL)
            if (!url.isHttps && !allowHttp) fail(AddonFailure.INSECURE_URL)
            val segments = url.pathSegments
            val last = segments.last()
            val manifest = when {
                last == "manifest.json" -> url
                last.isEmpty() -> url.newBuilder().setPathSegment(segments.size - 1, "manifest.json").build()
                segments.size >= 2 && segments[segments.size - 2] == "stremio" && UUID_BASE.matches(last) -> url.newBuilder().addPathSegment("manifest.json").build()
                baseAllowed && last != "configure" && last != "install" && !FILE_NAME.matches(last) -> url.newBuilder().addPathSegment("manifest.json").build()
                else -> fail(AddonFailure.INVALID_URL)
            }
            return AddonEndpoint(manifest)
        }

        private fun checkSegment(value: String, max: Int) {
            if (value.isEmpty() || value.length > max || value == "." || value == "..") fail(AddonFailure.INVALID_REQUEST)
        }

        /** A query-value encoding inside one path segment: everything but unreserved characters is escaped. */
        private fun encodeValue(value: String): String = buildString {
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                if (c.toChar().let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' || it == '~' }) {
                    append(c.toChar())
                } else {
                    append('%').append(HEX[c shr 4]).append(HEX[c and 15])
                }
            }
        }

        private const val HEX = "0123456789ABCDEF"
    }
}

/** SHA-256 hex helpers; [parts] joins opaque ids as `<length>:<part>` so no separator can collide (spec 50 §6). */
object Hashes {
    fun sha256Hex(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    fun parts(vararg parts: String): String = sha256Hex(parts.joinToString("") { "${it.length}:$it" })

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) { bytes.forEach { b -> append(digits[(b.toInt() shr 4) and 15]).append(digits[b.toInt() and 15]) } }
    }
}
