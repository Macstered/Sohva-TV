package com.sohva.tv.addons

import java.io.InputStream
import kotlinx.serialization.json.*

/** Only configured addon URLs leave an account/export response. Never log this object. */
class AddonCopyList internal constructor(private val urls: List<String>) {
    val count: Int get() = urls.size
    fun asUrlList(): String = urls.joinToString("\n")
    override fun toString() = "AddonCopyList(count=$count, [redacted])"
}

/** Version-scoped URL copy, not a general backup importer or account/history synchronizer. */
object AddonCopyExport {
    fun readNuvio(input: InputStream): AddonCopyList = nuvio(AddonImportText.readUtf8(input))

    fun nuvio(text: String): AddonCopyList {
        val root = boundedJson(text, AddonImportText.MAX_BYTES)
        val entries = when (root) {
            is JsonArray -> root
            is JsonObject -> root["addons"] as? JsonArray
            else -> null
        } ?: fail(AddonFailure.INVALID_RESPONSE)
        return urls(entries, "url")
    }

    internal fun stremio(result: JsonObject): AddonCopyList =
        urls(result["addons"] as? JsonArray ?: fail(AddonFailure.INVALID_RESPONSE), "transportUrl")

    private fun urls(entries: JsonArray, field: String): AddonCopyList {
        if (entries.size > AddonImportText.MAX_ENTRIES) fail(AddonFailure.RESPONSE_TOO_LARGE)
        // Keep a positional failure for unsupported entries; do not silently discard/reorder.
        val urls = entries.map { entry ->
            val value = (entry as? JsonObject)?.stringValue(field)
            value?.takeIf { it.isNotBlank() && it.length <= 16_384 && it.none(Char::isISOControl) }
                ?: "unsupported-import-entry"
        }
        if (urls.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 } > AddonImportText.MAX_BYTES) {
            fail(AddonFailure.RESPONSE_TOO_LARGE)
        }
        return AddonCopyList(urls)
    }
}

internal fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Bound bytes and nesting before entering the recursive JSON parser; redact parser exceptions. */
internal fun boundedJson(text: String, maxBytes: Int): JsonElement {
    if (text.length > maxBytes || text.toByteArray(Charsets.UTF_8).size > maxBytes) fail(AddonFailure.RESPONSE_TOO_LARGE)
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in text) {
        if (quoted) {
            if (escaped) escaped = false else when (char) { '\\' -> escaped = true; '"' -> quoted = false }
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> { depth++; if (depth > 64) fail(AddonFailure.INVALID_RESPONSE) }
            '}', ']' -> depth--
        }
    }
    return try { Json.parseToJsonElement(text) } catch (_: Exception) { fail(AddonFailure.INVALID_RESPONSE) }
}
