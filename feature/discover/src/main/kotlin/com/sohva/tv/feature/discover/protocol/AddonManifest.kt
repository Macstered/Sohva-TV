package com.sohva.tv.feature.discover.protocol

import com.squareup.moshi.JsonReader
import okio.Buffer

/** One extra a catalog accepts (ADDON-FR-12): `genre`, `search`, `skip`, … */
data class CatalogExtra(val name: String, val required: Boolean, val options: List<String>)

/** A catalog of a manifest (ADDON-FR-12). */
data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extras: List<CatalogExtra>,
    val pageSize: Int?,
    val showInHome: Boolean,
) {
    fun extra(name: String): CatalogExtra? = extras.firstOrNull { it.name == name }

    /** Required extras other than `skip`: such a catalog needs a choice before it can load. */
    val requiredChoices: List<CatalogExtra> get() = extras.filter { it.required && it.name != SKIP }

    /** ADDON-FR-58: on the landing when shown in home and no choice is required. */
    val onLanding: Boolean get() = showInHome && requiredChoices.isEmpty()

    val pages: Boolean get() = extra(SKIP) != null

    /** ADDON-FR-113: plain text search with nothing else required. */
    val searchable: Boolean get() = extra(SEARCH) != null && extras.none { it.required && it.name != SEARCH && it.name != SKIP }

    companion object {
        const val SKIP: String = "skip"
        const val SEARCH: String = "search"
    }
}

/** A resource an addon serves, with the types and id prefixes it applies to (ADDON-FR-11). */
data class AddonResource(val name: String, val types: List<String>, val idPrefixes: List<String>)

/**
 * A Stremio addon manifest (spec 50 §4.3), parsed strictly: any malformed required field makes
 * the whole manifest invalid, unknown fields are ignored.
 */
data class AddonManifest(
    val id: String,
    val version: String,
    val name: String,
    val types: List<String>,
    val resources: List<AddonResource>,
    val catalogs: List<AddonCatalog>,
    val configurationRequired: Boolean,
) {
    fun catalog(type: String, id: String): AddonCatalog? = catalogs.firstOrNull { it.type == type && it.id == id }

    /**
     * ADDON-FR-14: a catalog needs the `catalog` resource and that exact catalog (catalog ids are
     * not media ids, so prefixes never apply); other resources need the type and a matching prefix.
     */
    fun supports(resource: String, type: String, id: String): Boolean {
        if (resource == "catalog") return resources.any { it.name == "catalog" } && catalog(type, id) != null
        return resources.any { r -> r.name == resource && type in r.types && (r.idPrefixes.isEmpty() || r.idPrefixes.any { id.startsWith(it) }) }
    }

    /** ADDON-FR-15: every key declared, every required choice given, `skip` a non-negative integer. */
    fun checkExtras(catalog: AddonCatalog, extras: Map<String, String>) {
        extras.keys.forEach { if (catalog.extra(it) == null) fail(AddonFailure.INVALID_REQUEST) }
        catalog.extras.filter { it.required }.forEach { e ->
            if (e.name == AddonCatalog.SKIP) return@forEach
            if (extras[e.name].isNullOrBlank()) fail(AddonFailure.INVALID_REQUEST)
        }
        extras[AddonCatalog.SKIP]?.let { if ((it.toLongOrNull() ?: -1) < 0) fail(AddonFailure.INVALID_REQUEST) }
    }

    override fun toString(): String = "AddonManifest(id=$id, catalogs=${catalogs.size})"

    companion object {
        private const val MAX_TEXT = 8_192
        private const val MAX_STRINGS = 2_048
        private const val MAX_RESOURCES = 32
        private const val MAX_CATALOGS = 512
        private const val MAX_EXTRAS = 32
        private const val MAX_PAGE = 10_000
        private val INVALID = AddonFailure.INVALID_MANIFEST

        fun parse(body: Buffer): AddonManifest = AddonJson.parse(body, INVALID) { r -> read(r) }

        internal fun read(r: JsonReader): AddonManifest {
            var id: String? = null
            var version: String? = null
            var name: String? = null
            var types: List<String>? = null
            var prefixes: List<String> = emptyList()
            // Resources are resolved after the manifest's own types and prefixes are known.
            var rawResources: List<RawResource>? = null
            var catalogs: List<AddonCatalog> = emptyList()
            var configurationRequired = false
            if (r.peek() != JsonReader.Token.BEGIN_OBJECT) fail(INVALID)
            r.forFields { field ->
                when (field) {
                    "id" -> id = r.requiredText(MAX_TEXT, INVALID)
                    "version" -> version = r.requiredText(MAX_TEXT, INVALID)
                    "name" -> name = r.requiredText(MAX_TEXT, INVALID)
                    "types" -> types = strings(r)
                    "idPrefixes" -> prefixes = strings(r)
                    "resources" -> rawResources = resources(r)
                    "catalogs" -> catalogs = catalogs(r)
                    "behaviorHints" -> {
                        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) fail(INVALID)
                        r.forFields { hint ->
                            if (hint == "configurationRequired") configurationRequired = r.boolOrNull() == true else r.skipValue()
                        }
                    }
                    else -> r.skipValue()
                }
            }
            val manifestTypes = types ?: fail(INVALID)
            val resolved = (rawResources ?: fail(INVALID)).map { raw ->
                if (raw.types == null) {
                    // A string resource inherits both; an object resource only the types (FR-11).
                    AddonResource(raw.name, manifestTypes, if (raw.plain) prefixes else raw.prefixes.orEmpty())
                } else {
                    AddonResource(raw.name, raw.types, if (raw.plain) prefixes else raw.prefixes.orEmpty())
                }
            }
            if (catalogs.map { it.type to it.id }.toSet().size != catalogs.size) fail(INVALID)
            return AddonManifest(id ?: fail(INVALID), version ?: fail(INVALID), name ?: fail(INVALID), manifestTypes, resolved, catalogs, configurationRequired)
        }

        private class RawResource(val name: String, val types: List<String>?, val prefixes: List<String>?, val plain: Boolean)

        private fun resources(r: JsonReader): List<RawResource> {
            val out = ArrayList<RawResource>()
            if (!r.forItems {
                    if (out.size >= MAX_RESOURCES) fail(INVALID)
                    when (r.peek()) {
                        JsonReader.Token.STRING -> out += RawResource(r.requiredText(MAX_TEXT, INVALID), null, null, plain = true)
                        JsonReader.Token.BEGIN_OBJECT -> {
                            var name: String? = null
                            var types: List<String>? = null
                            var prefixes: List<String>? = null
                            r.forFields { f ->
                                when (f) {
                                    "name" -> name = r.requiredText(MAX_TEXT, INVALID)
                                    "types" -> types = strings(r)
                                    "idPrefixes" -> prefixes = strings(r)
                                    else -> r.skipValue()
                                }
                            }
                            out += RawResource(name ?: fail(INVALID), types, prefixes, plain = false)
                        }
                        else -> fail(INVALID)
                    }
                }
            ) {
                fail(INVALID)
            }
            return out
        }

        private fun catalogs(r: JsonReader): List<AddonCatalog> {
            val out = ArrayList<AddonCatalog>()
            if (!r.forItems {
                    if (out.size >= MAX_CATALOGS) fail(INVALID)
                    out += catalog(r)
                }
            ) {
                fail(INVALID)
            }
            return out
        }

        private fun catalog(r: JsonReader): AddonCatalog {
            var type: String? = null
            var id: String? = null
            var name: String? = null
            var extras: List<CatalogExtra>? = null
            var supported: List<String> = emptyList()
            var required: List<String> = emptyList()
            var pageSize: Int? = null
            var showInHome = true
            if (!r.forFields { f ->
                    when (f) {
                        "type" -> type = r.requiredText(MAX_TEXT, INVALID)
                        "id" -> id = r.requiredText(MAX_TEXT, INVALID)
                        "name" -> name = r.requiredText(MAX_TEXT, INVALID)
                        "extra" -> extras = extras(r)
                        "extraSupported" -> supported = strings(r)
                        "extraRequired" -> required = strings(r)
                        "pageSize" -> pageSize = r.intOrNull()?.takeIf { it in 1..MAX_PAGE } ?: fail(INVALID)
                        "showInHome" -> showInHome = r.boolOrNull() ?: fail(INVALID)
                        else -> r.skipValue()
                    }
                }
            ) {
                fail(INVALID)
            }
            // Legacy `extraSupported` / `extraRequired` only when `extra` is absent (FR-12).
            val resolved = extras ?: (supported + required).distinct().map { CatalogExtra(it, it in required, emptyList()) }
            if (resolved.size > MAX_EXTRAS || resolved.map { it.name }.toSet().size != resolved.size) fail(INVALID)
            val catalogId = id ?: fail(INVALID)
            return AddonCatalog(type ?: fail(INVALID), catalogId, name ?: catalogId, resolved, pageSize, showInHome)
        }

        private fun extras(r: JsonReader): List<CatalogExtra> {
            val out = ArrayList<CatalogExtra>()
            if (!r.forItems {
                    if (out.size >= MAX_EXTRAS) fail(INVALID)
                    var name: String? = null
                    var required = false
                    var options: List<String> = emptyList()
                    if (!r.forFields { f ->
                            when (f) {
                                "name" -> name = r.requiredText(128, INVALID)
                                "isRequired" -> required = r.boolOrNull() ?: fail(INVALID)
                                "options" -> options = strings(r)
                                // optionsLimit is parsed by beta 23 but only one value is ever sent (FR-12).
                                else -> r.skipValue()
                            }
                        }
                    ) {
                        fail(INVALID)
                    }
                    out += CatalogExtra(name ?: fail(INVALID), required, options)
                }
            ) {
                fail(INVALID)
            }
            return out
        }

        /** FR-10: at most 2,048 non-blank strings within 8,192 characters each. */
        private fun strings(r: JsonReader): List<String> {
            val out = ArrayList<String>()
            if (!r.forItems {
                    if (out.size >= MAX_STRINGS) fail(INVALID)
                    out += r.requiredText(MAX_TEXT, INVALID)
                }
            ) {
                fail(INVALID)
            }
            return out
        }
    }
}
