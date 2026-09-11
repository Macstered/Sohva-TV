package com.sohva.tv.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Provider identifiers are opaque and case-sensitive; never guess or rewrite an episode ID. */
data class AddonMediaKey(val type: String, val id: String) {
    override fun toString() = "AddonMediaKey([redacted])"
}

class AddonVideo(val id: String, val title: String, val season: Int?, val episode: Int?, val overview: String?, val thumbnail: String? = null) {
    override fun toString() = "AddonVideo([redacted])"
}

class AddonCastMember(val name: String, val photo: String? = null, val character: String? = null) {
    override fun toString() = "AddonCastMember([redacted])"
}

class AddonMedia(
    val key: AddonMediaKey,
    val name: String,
    val poster: String?,
    val posterShape: String,
    val background: String?,
    val description: String?,
    val releaseInfo: String?,
    val videos: List<AddonVideo>,
    val defaultVideoId: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val imdbRating: String? = null,
    val cast: List<AddonCastMember> = emptyList(),
) {
    /** A movie preview is playable even when its catalog addon has no meta resource. */
    fun singleVideoKey(): AddonMediaKey? = when {
        defaultVideoId != null -> AddonMediaKey(key.type, defaultVideoId)
        key.type == "movie" -> key
        else -> null
    }
    override fun toString() = "AddonMedia([redacted])"
}

class AddonCatalogPage(val items: List<AddonMedia>, val receivedCount: Int) {
    override fun toString() = "AddonCatalogPage([redacted])"
}

object AddonMediaParser {
    fun catalog(text: String, itemLimit: Int = 1000): AddonCatalogPage = parse(text) { root ->
        if (itemLimit !in 1..1000) fail(AddonFailure.INVALID_REQUEST)
        val raw = root["metas"] as? JsonArray ?: fail(AddonFailure.INVALID_RESPONSE)
        if (raw.size > 1000) fail(AddonFailure.RESPONSE_TOO_LARGE)
        // A malformed tile does not destroy a usable page. Pagination uses the raw count.
        val items = raw.asSequence().mapNotNull { value ->
            try { (value as? JsonObject)?.media(includeVideos = false) } catch (_: AddonException) { null }
        }.distinctBy { it.key }.take(itemLimit).toList()
        AddonCatalogPage(items, raw.size)
    }

    fun metadata(text: String): AddonMedia = parse(text) { root ->
        (root["meta"] as? JsonObject)?.media(includeVideos = true) ?: fail(AddonFailure.INVALID_RESPONSE)
    }

    private fun <T> parse(text: String, block: (JsonObject) -> T): T {
        try {
            AddonManifestParser.validateEnvelope(text)
            return block(Json.parseToJsonElement(text) as? JsonObject ?: fail(AddonFailure.INVALID_RESPONSE))
        } catch (error: AddonException) {
            throw AddonException(if (error.failure == AddonFailure.RESPONSE_TOO_LARGE) error.failure else AddonFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            fail(AddonFailure.INVALID_RESPONSE)
        }
    }

    private fun JsonObject.media(includeVideos: Boolean): AddonMedia {
        val videos = if (!includeVideos) emptyList() else {
            val raw = get("videos")?.takeUnless { it == JsonNull }?.let {
                it as? JsonArray ?: fail(AddonFailure.INVALID_RESPONSE)
            } ?: JsonArray(emptyList())
            if (raw.size > 10_000) fail(AddonFailure.RESPONSE_TOO_LARGE)
            raw.mapNotNull { value ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                val id = obj.text("id") ?: return@mapNotNull null
                AddonVideo(id, obj.text("title") ?: obj.text("name") ?: id,
                    obj.number("season"), obj.number("episode"), obj.text("overview", 32_768), obj.image("thumbnail"))
            }.distinctBy { it.id }
        }
        return AddonMedia(
            AddonMediaKey(text("type") ?: fail(AddonFailure.INVALID_RESPONSE), text("id") ?: fail(AddonFailure.INVALID_RESPONSE)),
            text("name") ?: fail(AddonFailure.INVALID_RESPONSE), image("poster"),
            text("posterShape")?.takeIf { it in setOf("poster", "square", "landscape") } ?: "poster",
            image("background"), text("description", 32_768), text("releaseInfo"), videos,
            (get("behaviorHints") as? JsonObject)?.text("defaultVideoId"),
            image("logo"), (get("genres") as? JsonArray).orEmpty().take(12).mapNotNull {
                (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content?.takeIf { value -> value.isNotBlank() && value.length <= 128 }
            }, text("runtime", 128), text("imdbRating", 16)?.takeIf { it.toDoubleOrNull()?.let { score -> score.isFinite() && score in 0.0..10.0 } == true },
            // Cast is detail-only; Home previews must not allocate a credits list per tile.
            if (includeVideos) cast() else emptyList(),
        )
    }

    private fun JsonObject.cast(): List<AddonCastMember> {
        val members = LinkedHashMap<String, AddonCastMember>()
        fun add(member: AddonCastMember) {
            val key = member.name.lowercase(Locale.ROOT)
            val previous = members[key]
            if (previous != null) members[key] = AddonCastMember(previous.name,
                previous.photo ?: member.photo, previous.character ?: member.character)
            else if (members.size < 60) members[key] = member
        }
        // AIOMetadata's rich cast first, followed by IMDb-style credits, the standard
        // Stremio name array and actor links. Links supply names only; never follow URLs.
        val arrays = listOf((get("app_extras") as? JsonObject)?.get("cast"), get("credits_cast"), get("cast"))
        for (raw in arrays) for (value in (raw as? JsonArray).orEmpty().take(240)) {
            when (value) {
                is JsonObject -> value.text("name", 256)?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
                    add(AddonCastMember(name, value.image("photo") ?: value.image("profile_path"), value.text("character", 512)?.trim()))
                }
                is JsonPrimitive -> value.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }
                    ?.let { add(AddonCastMember(it)) }
                else -> Unit
            }
        }
        for (link in (get("links") as? JsonArray).orEmpty().take(240)) {
            val obj = link as? JsonObject ?: continue
            if (obj.text("category", 32)?.trim()?.lowercase(Locale.ROOT) !in setOf("actor", "actors", "cast")) continue
            obj.text("name", 256)?.trim()?.takeIf(String::isNotEmpty)?.let { add(AddonCastMember(it)) }
        }
        return members.values.toList()
    }
    private fun JsonObject.text(key: String, limit: Int = 8192): String? = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() && it.length <= limit }
    private fun JsonObject.number(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
    private fun JsonObject.image(key: String): String? = text(key)?.let { value ->
        value.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }?.toString()
    }
}
