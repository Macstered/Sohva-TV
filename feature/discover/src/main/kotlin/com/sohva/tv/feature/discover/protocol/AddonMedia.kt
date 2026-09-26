package com.sohva.tv.feature.discover.protocol

import com.squareup.moshi.JsonReader
import okio.Buffer

/** A title as a catalog shows it (ADDON-FR-17). Catalog previews never carry videos or cast. */
data class MetaPreview(
    val type: String,
    val id: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val imdbRating: String? = null,
    val defaultVideoId: String? = null,
) {
    /** ADDON-FR-20: a default video, or a movie (the title id is the video). */
    val singleVideo: Boolean get() = defaultVideoId != null || type == "movie"

    val videoId: String? get() = defaultVideoId ?: id.takeIf { type == "movie" }

    override fun toString(): String = "MetaPreview($type:$id)"
}

/** A page of a catalog: at most the requested models, and the raw count for paging (FR-16). */
data class CatalogPage(val items: List<MetaPreview>, val receivedCount: Int)

/** One video of a series (ADDON-FR-18); ids are opaque and never rewritten (FR-20). */
data class AddonVideo(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val overview: String?,
    val thumbnail: String?,
) {
    override fun toString(): String = "AddonVideo($id)"
}

data class CastMember(val name: String, val photo: String?, val character: String?)

/** A title's details (ADDON-FR-18, -19). */
data class MetaDetails(val preview: MetaPreview, val videos: List<AddonVideo>, val cast: List<CastMember>) {
    override fun toString(): String = "MetaDetails(${preview.type}:${preview.id}, videos=${videos.size})"
}

object AddonMediaParser {
    private const val MAX_METAS = 1_000
    private const val MAX_VIDEOS = 10_000
    private const val MAX_DESCRIPTION = 32_768
    private const val MAX_NAME = 8_192
    private const val MAX_GENRES = 12
    private const val MAX_CAST_SCANNED = 240
    private const val MAX_CAST = 60
    private val RATING = Regex("\\d+(\\.\\d+)?")

    /**
     * `{"metas":[…]}` (FR-16): at most 1,000 entries (else too large), malformed ones skipped,
     * duplicates by (type, id) dropped, at most [itemLimit] built; the rest is skipped but counted.
     */
    fun catalog(body: Buffer, itemLimit: Int): CatalogPage = AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
        val items = ArrayList<MetaPreview>()
        val seen = HashSet<Pair<String, String>>()
        var received = -1
        r.forFields { f ->
            if (f != "metas") {
                r.skipValue()
                return@forFields
            }
            received = 0
            if (!r.forItems {
                    received++
                    if (received > MAX_METAS) fail(AddonFailure.RESPONSE_TOO_LARGE)
                    if (items.size >= itemLimit) {
                        r.skipValue()
                    } else {
                        val m = preview(r)
                        if (m != null && seen.add(m.type to m.id)) items += m
                    }
                }
            ) {
                fail(AddonFailure.INVALID_RESPONSE)
            }
        }
        if (received < 0) fail(AddonFailure.INVALID_RESPONSE)
        CatalogPage(items, received)
    }

    /** `{"meta":{…}}` (FR-18). */
    fun meta(body: Buffer): MetaDetails = AddonJson.parse(body, AddonFailure.INVALID_RESPONSE) { r ->
        var details: MetaDetails? = null
        r.forFields { f -> if (f == "meta") details = details(r) ?: fail(AddonFailure.INVALID_RESPONSE) else r.skipValue() }
        details ?: fail(AddonFailure.INVALID_RESPONSE)
    }

    private class Fields {
        var type: String? = null
        var id: String? = null
        var name: String? = null
        var poster: String? = null
        var background: String? = null
        var logo: String? = null
        var description: String? = null
        var releaseInfo: String? = null
        var genres: List<String> = emptyList()
        var runtime: String? = null
        var rating: String? = null
        var defaultVideoId: String? = null

        fun preview(): MetaPreview? {
            return MetaPreview(
                type ?: return null, id ?: return null, name ?: return null, poster, background, logo, description,
                releaseInfo, genres, runtime, rating, defaultVideoId,
            )
        }
    }

    /** One preview field; false when [field] is not a preview field (the caller handles or skips it). */
    private fun previewField(r: JsonReader, field: String, into: Fields): Boolean {
        when (field) {
            "type" -> into.type = r.textOrNull(256)
            "id" -> into.id = r.textOrNull(2_048)
            "name" -> into.name = r.textOrNull(MAX_NAME)
            "poster" -> into.poster = safeUrl(r.textOrNull(MAX_NAME))
            "background" -> into.background = safeUrl(r.textOrNull(MAX_NAME))
            "logo" -> into.logo = safeUrl(r.textOrNull(MAX_NAME))
            "description" -> into.description = r.textOrNull(MAX_DESCRIPTION)
            "releaseInfo" -> into.releaseInfo = r.textOrNumber(256)
            "genres" -> {
                val g = ArrayList<String>()
                r.forItems { if (g.size < MAX_GENRES) r.textOrNull(128)?.let(g::add) else r.skipValue() }
                into.genres = g
            }
            "runtime" -> into.runtime = r.textOrNull(128)
            "imdbRating" -> into.rating = r.textOrNumber(16)?.takeIf { RATING.matches(it) && (it.toDoubleOrNull() ?: 99.0) <= 10.0 }
            "behaviorHints" -> r.forFields { h -> if (h == "defaultVideoId") into.defaultVideoId = r.textOrNull(2_048) else r.skipValue() }
            else -> return false
        }
        return true
    }

    private fun preview(r: JsonReader): MetaPreview? {
        val fields = Fields()
        if (!r.forFields { f -> if (!previewField(r, f, fields)) r.skipValue() }) return null
        return fields.preview()
    }

    private fun details(r: JsonReader): MetaDetails? {
        val fields = Fields()
        var videos: List<AddonVideo> = emptyList()
        val cast = CastMerge()
        if (!r.forFields { f ->
                if (previewField(r, f, fields)) return@forFields
                when (f) {
                    "videos" -> videos = videos(r)
                    "app_extras" -> r.forFields { e -> if (e == "cast") cast.people(r, 0) else r.skipValue() }
                    "credits_cast" -> cast.people(r, 1)
                    "cast" -> cast.people(r, 2)
                    "links" -> cast.links(r)
                    else -> r.skipValue()
                }
            }
        ) {
            return null
        }
        return MetaDetails(fields.preview() ?: return null, videos, cast.result())
    }

    private fun videos(r: JsonReader): List<AddonVideo> {
        val out = ArrayList<AddonVideo>()
        val seen = HashSet<String>()
        r.forItems {
            if (out.size >= MAX_VIDEOS) {
                r.skipValue()
                return@forItems
            }
            var id: String? = null
            var title: String? = null
            var name: String? = null
            var season: Int? = null
            var episode: Int? = null
            var overview: String? = null
            var thumbnail: String? = null
            r.forFields { f ->
                when (f) {
                    "id" -> id = r.textOrNull(2_048)
                    "title" -> title = r.textOrNull(MAX_NAME)
                    "name" -> name = r.textOrNull(MAX_NAME)
                    "season" -> season = r.intOrNull()?.takeIf { it >= 0 }
                    "episode", "number" -> episode = r.intOrNull()?.takeIf { it >= 0 } ?: episode
                    "overview", "description" -> overview = r.textOrNull(MAX_DESCRIPTION) ?: overview
                    "thumbnail" -> thumbnail = safeUrl(r.textOrNull(MAX_NAME))
                    else -> r.skipValue()
                }
            }
            val videoId = id ?: return@forItems
            if (seen.add(videoId)) out += AddonVideo(videoId, title ?: name ?: videoId, season, episode, overview, thumbnail)
        }
        return out
    }

    /**
     * FR-19: people from four sources merged in a fixed order whatever the JSON field order (rich
     * extras, credits, cast, actor links); at most 240 scanned per source and 60 kept, merged by
     * lower-cased name; a later source fills a missing photo or character. Link URLs are never
     * followed.
     */
    private class CastMerge {
        private val sources = Array(4) { ArrayList<CastMember>() }

        fun people(r: JsonReader, source: Int) {
            var scanned = 0
            r.forItems {
                if (scanned++ >= MAX_CAST_SCANNED) {
                    r.skipValue()
                    return@forItems
                }
                when (r.peek()) {
                    JsonReader.Token.STRING -> r.textOrNull(256)?.let { sources[source] += CastMember(it, null, null) }
                    JsonReader.Token.BEGIN_OBJECT -> {
                        var name: String? = null
                        var photo: String? = null
                        var character: String? = null
                        r.forFields { f ->
                            when (f) {
                                "name" -> name = r.textOrNull(256)
                                "photo", "profile_path" -> photo = safeUrl(r.textOrNull(MAX_NAME)) ?: photo
                                "character" -> character = r.textOrNull(512)
                                else -> r.skipValue()
                            }
                        }
                        name?.let { sources[source] += CastMember(it, photo, character) }
                    }
                    else -> r.skipValue()
                }
            }
        }

        fun links(r: JsonReader) {
            var scanned = 0
            r.forItems {
                if (scanned++ >= MAX_CAST_SCANNED) {
                    r.skipValue()
                    return@forItems
                }
                var name: String? = null
                var category: String? = null
                r.forFields { f ->
                    when (f) {
                        "name" -> name = r.textOrNull(256)
                        "category" -> category = r.textOrNull(64)?.lowercase()
                        else -> r.skipValue()
                    }
                }
                if (category == "actor" || category == "actors" || category == "cast") name?.let { sources[LINKS] += CastMember(it, null, null) }
            }
        }

        fun result(): List<CastMember> {
            val people = LinkedHashMap<String, CastMember>()
            for (source in sources) {
                for (p in source) {
                    val key = p.name.lowercase()
                    val old = people[key]
                    if (old == null) {
                        if (people.size < MAX_CAST) people[key] = p
                    } else {
                        people[key] = old.copy(photo = old.photo ?: p.photo, character = old.character ?: p.character)
                    }
                }
            }
            return people.values.toList()
        }

        private companion object {
            const val LINKS = 3
        }
    }
}
