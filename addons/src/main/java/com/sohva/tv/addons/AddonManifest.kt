package com.sohva.tv.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

data class AddonResource(val name: String, val types: List<String>, val idPrefixes: List<String>?) {
    fun matches(type: String, id: String): Boolean = type in types &&
        (idPrefixes == null || idPrefixes.any(id::startsWith))
}

data class CatalogExtra(val name: String, val required: Boolean, val options: List<String>, val optionsLimit: Int)

data class AddonCatalog(
    val id: String,
    val type: String,
    val name: String,
    val extras: List<CatalogExtra>,
    val pageSize: Int?,
    val showInHome: Boolean,
) {
    fun validateExtras(values: Map<String, String>) {
        if (values.keys.any { key -> extras.none { it.name == key } } ||
            extras.any { it.required && values[it.name].isNullOrBlank() } ||
            values["skip"]?.let { it.toIntOrNull()?.let { number -> number < 0 } ?: true } == true
        ) fail(AddonFailure.INVALID_REQUEST)
    }

    /** Count raw provider items, before UI deduplication. Never assume a 100-item page. */
    fun nextSkip(currentSkip: Int, receivedCount: Int): Int? {
        if (currentSkip < 0 || receivedCount < 0) fail(AddonFailure.INVALID_REQUEST)
        if (extras.none { it.name == "skip" } || receivedCount == 0 || (pageSize != null && receivedCount < pageSize)) return null
        val next = currentSkip.toLong() + receivedCount
        return next.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
}

class AddonManifest(
    val id: String,
    val version: String,
    val name: String,
    val resources: List<AddonResource>,
    val catalogs: List<AddonCatalog>,
    val configurationRequired: Boolean,
    val configurable: Boolean,
) {
    fun supports(resource: String, type: String, id: String): Boolean = when (resource) {
        // Catalog IDs are not media IDs: global idPrefixes do not filter them.
        "catalog" -> resources.any { it.name == "catalog" } && catalogs.any { it.type == type && it.id == id }
        else -> resources.any { it.name == resource && it.matches(type, id) }
    }
    override fun toString(): String = "AddonManifest([redacted])"
}

object AddonManifestParser {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun parse(text: String): AddonManifest {
        try {
            validateEnvelope(text)
            val root = Json.parseToJsonElement(text) as? JsonObject ?: fail(AddonFailure.INVALID_MANIFEST)
            val types = root.strings("types") ?: fail(AddonFailure.INVALID_MANIFEST)
            val prefixes = root.strings("idPrefixes")
            val rawResources = root["resources"] as? JsonArray ?: fail(AddonFailure.INVALID_MANIFEST)
            if (rawResources.size > 32) fail(AddonFailure.INVALID_MANIFEST)
            val resources = rawResources.map { value ->
                when (value) {
                    is JsonPrimitive -> AddonResource(value.string(), types, prefixes)
                    is JsonObject -> AddonResource(value.required("name"), value.strings("types") ?: types, value.strings("idPrefixes"))
                    else -> fail(AddonFailure.INVALID_MANIFEST)
                }
            }
            val rawCatalogs = root["catalogs"]?.let { it as? JsonArray ?: fail(AddonFailure.INVALID_MANIFEST) } ?: JsonArray(emptyList())
            if (rawCatalogs.size > 512) fail(AddonFailure.INVALID_MANIFEST)
            val catalogs = rawCatalogs.map { item ->
                val obj = item as? JsonObject ?: fail(AddonFailure.INVALID_MANIFEST)
                val id = obj.required("id")
                val extras = obj["extra"]?.let { raw ->
                    val array = raw as? JsonArray ?: fail(AddonFailure.INVALID_MANIFEST)
                    if (array.size > 32) fail(AddonFailure.INVALID_MANIFEST)
                    array.map { extra ->
                        val fields = extra as? JsonObject ?: fail(AddonFailure.INVALID_MANIFEST)
                        CatalogExtra(fields.required("name"), fields.bool("isRequired"), fields.strings("options").orEmpty(), fields.positiveInt("optionsLimit") ?: 1)
                    }
                } ?: (obj.strings("extraSupported").orEmpty() + obj.strings("extraRequired").orEmpty()).distinct().map {
                    CatalogExtra(it, it in obj.strings("extraRequired").orEmpty(), emptyList(), 1)
                }
                if (extras.map { it.name }.distinct().size != extras.size) fail(AddonFailure.INVALID_MANIFEST)
                AddonCatalog(id, obj.required("type"), obj.optional("name") ?: id, extras, obj.positiveInt("pageSize"), obj.bool("showInHome", true))
            }
            if (catalogs.map { it.type to it.id }.distinct().size != catalogs.size) fail(AddonFailure.INVALID_MANIFEST)
            val hints = root["behaviorHints"]?.let { it as? JsonObject ?: fail(AddonFailure.INVALID_MANIFEST) }
            return AddonManifest(root.required("id"), root.required("version"), root.required("name"), resources, catalogs,
                hints?.bool("configurationRequired") ?: false, hints?.bool("configurable") ?: false)
        } catch (error: AddonException) {
            throw error
        } catch (_: Exception) {
            fail(AddonFailure.INVALID_MANIFEST)
        }
    }

    internal fun validateEnvelope(text: String) {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) fail(AddonFailure.RESPONSE_TOO_LARGE)
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { ch ->
            if (quoted) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 64) fail(AddonFailure.INVALID_MANIFEST)
                '}', ']' -> depth--
            }
        }
    }
}

private fun JsonPrimitive.string(): String {
    if (!isString || content.isBlank() || content.length > 8192) fail(AddonFailure.INVALID_MANIFEST)
    return content
}
private fun JsonObject.required(key: String): String = (get(key) as? JsonPrimitive)?.string() ?: fail(AddonFailure.INVALID_MANIFEST)
private fun JsonObject.optional(key: String): String? = get(key)?.let { (it as? JsonPrimitive)?.string() ?: fail(AddonFailure.INVALID_MANIFEST) }
private fun JsonObject.strings(key: String): List<String>? = get(key)?.let { raw ->
    val array = raw as? JsonArray ?: fail(AddonFailure.INVALID_MANIFEST)
    if (array.size > 2048) fail(AddonFailure.INVALID_MANIFEST)
    array.map { (it as? JsonPrimitive)?.string() ?: fail(AddonFailure.INVALID_MANIFEST) }
}
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean = get(key)?.let {
    (it as? JsonPrimitive)?.booleanOrNull ?: fail(AddonFailure.INVALID_MANIFEST)
} ?: default
private fun JsonObject.positiveInt(key: String): Int? = get(key)?.let {
    (it as? JsonPrimitive)?.intOrNull?.takeIf { number -> number in 1..10_000 } ?: fail(AddonFailure.INVALID_MANIFEST)
}
