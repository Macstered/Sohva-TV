package com.sohva.tv.core.sync.metadata

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.sohva.tv.core.model.metadata.Lookup
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.text.StableIds
import com.sohva.tv.core.net.metadata.CastMember
import com.sohva.tv.core.net.metadata.MetadataProvider
import com.sohva.tv.core.net.metadata.MetadataRecord
import com.sohva.tv.core.net.metadata.SimilarRef
import okio.Buffer

/** A lookup as a screen or the worker asks it, before sanitising (spec 41 META-FR-16). */
data class MetadataRequest(
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** The title's content key, when one exists: its pin answers first (META-FR-76). */
    val contentKey: String? = null,
)

/** A sanitised lookup with its cache key (META-FR-16, -17). */
data class SanitisedLookup(val lookup: Lookup, val language: String, val key: String)

internal object Lookups {
    private val LANGUAGE = Regex("[a-z]{2}(?:-[A-Z]{2})?")

    /** Null when the lookup is refused: a title that cleans to fewer than 2 characters. */
    fun sanitise(request: MetadataRequest, language: String): SanitisedLookup? {
        val title = TitleCleaner.searchTitle(request.title).take(160)
        val normalized = TitleCleaner.normalizeTitle(title)
        if (normalized.length < 2) return null
        val year = (request.year ?: TitleCleaner.yearFromTitle(request.title))?.takeIf { it in 1870..2200 }
        val season = request.season?.takeIf { it in 0..10_000 }
        val episode = request.episode?.takeIf { it in 0..100_000 }
        val lang = language.takeIf { LANGUAGE.matches(it) } ?: "en-US"
        val key = StableIds().sha256Hex(
            listOf(KEY_VERSION, request.type.wire, normalized, year?.toString().orEmpty(), season?.toString().orEmpty(), episode?.toString().orEmpty(), lang.lowercase())
                .joinToString(SEPARATOR),
        )
        return SanitisedLookup(Lookup(request.type, title, year, season, episode), lang, key)
    }

    /** The key version covers the normaliser as well as the layout (META-FR-18). */
    private val KEY_VERSION = "4.${TitleCleaner.NORMALISER_VERSION}"
    private const val SEPARATOR = "\u001F"
}

/** A [MetadataRecord] as the cache stores it. */
internal object RecordCodec {
    fun encode(r: MetadataRecord): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { w ->
            w.beginObject()
            w.name("provider").value(r.provider.id)
            w.name("id").value(r.externalId)
            w.name("type").value(r.type.wire)
            w.name("title").value(r.title)
            w.name("alt").beginArray().also { r.alternativeTitles.forEach(it::value) }.endArray()
            w.name("overview").value(r.overview)
            w.name("poster").value(r.poster)
            w.name("backdrop").value(r.backdrop)
            w.name("year").value(r.year)
            w.name("season").value(r.season)
            w.name("episode").value(r.episode)
            w.name("runtime").value(r.runtimeMinutes)
            w.name("rating").value(r.rating)
            w.name("genres").beginArray().also { r.genreIds.forEach { id -> it.value(id) } }.endArray()
            w.name("cast").beginArray()
            for (c in r.cast) w.beginObject().name("name").value(c.name).name("character").value(c.character).name("profile").value(c.profile).endObject()
            w.endArray()
            w.name("similar").beginArray()
            for (s in r.similar) {
                w.beginObject().name("id").value(s.externalId).name("title").value(s.title)
                w.name("alt").beginArray().also { s.alternativeTitles.forEach(it::value) }.endArray()
                w.name("year").value(s.year).name("poster").value(s.poster).endObject()
            }
            w.endArray()
            w.name("popularity").value(r.popularity)
            w.name("attribution").value(r.attributionUrl)
            w.name("details").value(r.detailsLoaded)
            w.endObject()
        }
        return buffer.readUtf8()
    }

    fun decode(json: String): MetadataRecord? = runCatching {
        val o = JsonReader.of(Buffer().writeUtf8(json)).readJsonValue() as Map<*, *>
        fun str(k: String) = o[k] as? String
        fun int(k: String) = (o[k] as? Number)?.toInt()
        fun strings(v: Any?) = (v as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        MetadataRecord(
            provider = MetadataProvider.of(str("provider")) ?: return null,
            externalId = str("id") ?: return null,
            type = MediaType.entries.firstOrNull { it.wire == str("type") } ?: return null,
            title = str("title") ?: return null,
            alternativeTitles = strings(o["alt"]),
            overview = str("overview"),
            poster = str("poster"),
            backdrop = str("backdrop"),
            year = int("year"),
            season = int("season"),
            episode = int("episode"),
            runtimeMinutes = int("runtime"),
            rating = str("rating"),
            genreIds = (o["genres"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
            cast = (o["cast"] as? List<*>)?.mapNotNull { c ->
                val m = c as? Map<*, *> ?: return@mapNotNull null
                CastMember(m["name"] as? String ?: return@mapNotNull null, m["character"] as? String, m["profile"] as? String)
            }.orEmpty(),
            similar = (o["similar"] as? List<*>)?.mapNotNull { s ->
                val m = s as? Map<*, *> ?: return@mapNotNull null
                SimilarRef(
                    m["id"] as? String ?: return@mapNotNull null,
                    m["title"] as? String ?: return@mapNotNull null,
                    strings(m["alt"]),
                    (m["year"] as? Number)?.toInt(),
                    m["poster"] as? String,
                )
            }.orEmpty(),
            popularity = (o["popularity"] as? Number)?.toDouble(),
            attributionUrl = str("attribution"),
            detailsLoaded = o["details"] == true,
        )
    }.getOrNull()
}
