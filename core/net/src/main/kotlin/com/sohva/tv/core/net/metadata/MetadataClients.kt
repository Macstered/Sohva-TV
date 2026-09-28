package com.sohva.tv.core.net.metadata

import com.squareup.moshi.JsonReader
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.metadata.MediaType
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer

/** A TMDB credential (META-FR-14): 32 hex characters are a v3 key sent as `api_key`, anything else a v4 token. */
class TmdbCredential private constructor(val apiKey: String?, val bearer: String?) {
    override fun toString(): String = "TmdbCredential(<redacted>)"

    companion object {
        private val HEX32 = Regex("[A-Fa-f0-9]{32}")

        fun of(raw: String?): TmdbCredential? {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return null
            return if (HEX32.matches(v)) TmdbCredential(v, null) else TmdbCredential(null, v)
        }
    }
}

/**
 * TMDB (spec 41 §7.1): the credential check, searches, film and series details, and episodes.
 * Parsing runs on the caller's thread, which is never the main thread.
 */
class TmdbClient(private val http: MetadataHttp, private val base: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl()) {
    /** `GET /configuration` with [credential]: success or the failure's [AppException] (META-FR-06). */
    suspend fun check(credential: TmdbCredential) {
        get("configuration", credential)
    }

    /** Films, series or both for a programme (META-FR-33): at most 12 results. */
    suspend fun search(type: MediaType, query: String, language: String, credential: TmdbCredential): List<MetadataRecord> {
        val path = when (type) {
            MediaType.MOVIE -> "search/movie"
            MediaType.SERIES, MediaType.EPISODE -> "search/tv"
            MediaType.PROGRAMME -> "search/multi"
        }
        val json = get(path, credential, "query" to query.take(160), "language" to language, "include_adult" to "false", "page" to "1")
        return list(obj(json)["results"]).asSequence().mapNotNull { item ->
            val o = obj(item)
            val kind = when (type) {
                MediaType.MOVIE -> MediaType.MOVIE
                MediaType.SERIES, MediaType.EPISODE -> MediaType.SERIES
                MediaType.PROGRAMME -> when (o["media_type"]) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> return@mapNotNull null
                }
            }
            record(o, kind)
        }.take(MAX_RESULTS).toList()
    }

    /** Film details with credits and similar titles (META-FR-37). */
    suspend fun movie(id: String, language: String, credential: TmdbCredential): MetadataRecord? {
        val o = obj(get("movie/$id", credential, "language" to language, "append_to_response" to "credits,similar"))
        val base = record(o, MediaType.MOVIE) ?: return null
        val similar = list(obj(o["similar"])["results"]).mapNotNull { s ->
            val so = obj(s)
            val sid = id(so["id"]) ?: return@mapNotNull null
            if (sid == base.externalId) return@mapNotNull null
            val title = text(so["title"]) ?: return@mapNotNull null
            SimilarRef(sid, title, listOfNotNull(text(so["original_title"])?.takeIf { it != title }), year(so["release_date"]), Artwork.tmdbPath(so["poster_path"] as? String))
        }.take(MAX_SIMILAR)
        return base.copy(
            runtimeMinutes = int(o["runtime"])?.takeIf { it > 0 },
            rating = rating(o["vote_average"]),
            genreIds = genreIds(o),
            cast = cast(o),
            similar = similar,
            attributionUrl = "https://www.themoviedb.org/movie/${base.externalId}",
            detailsLoaded = true,
            imdb = text(o["imdb_id"]).orEmpty(),
        )
    }

    /** Series details with credits, the IMDb id and the seasons aired (META-FR-34, spec 40 VOD-FR-112, -113). */
    suspend fun series(id: String, language: String, credential: TmdbCredential): MetadataRecord? {
        val o = obj(get("tv/$id", credential, "language" to language, "append_to_response" to "credits,external_ids"))
        val base = record(o, MediaType.SERIES) ?: return null
        return base.copy(
            runtimeMinutes = list(o["episode_run_time"]).firstNotNullOfOrNull { int(it)?.takeIf { m -> m > 0 } },
            rating = rating(o["vote_average"]),
            genreIds = genreIds(o),
            cast = cast(o),
            attributionUrl = "https://www.themoviedb.org/tv/${base.externalId}",
            detailsLoaded = true,
            imdb = text(obj(o["external_ids"])["imdb_id"]).orEmpty(),
            airedSeasons = int(obj(o["last_episode_to_air"])["season_number"])?.coerceAtLeast(0) ?: 0,
        )
    }

    /** One episode of [show] (META-FR-35); its poster and fallbacks come from the show. */
    suspend fun episode(show: MetadataRecord, season: Int, episode: Int, language: String, credential: TmdbCredential): MetadataRecord? {
        val o = obj(get("tv/${show.externalId}/season/$season/episode/$episode", credential, "language" to language))
        val s = int(o["season_number"]) ?: season
        val e = int(o["episode_number"]) ?: episode
        return MetadataRecord(
            provider = MetadataProvider.TMDB,
            externalId = "${show.externalId}:$s:$e",
            type = MediaType.EPISODE,
            title = text(o["name"]) ?: show.title,
            overview = text(o["overview"]) ?: show.overview,
            poster = show.poster,
            backdrop = Artwork.tmdbPath(o["still_path"] as? String) ?: show.backdrop,
            year = show.year,
            season = s,
            episode = e,
            runtimeMinutes = int(o["runtime"])?.takeIf { it > 0 },
            rating = rating(o["vote_average"]),
            attributionUrl = "https://www.themoviedb.org/tv/${show.externalId}/season/$s/episode/$e",
            detailsLoaded = true,
        )
    }

    private fun record(o: Map<*, *>, type: MediaType): MetadataRecord? {
        val id = id(o["id"]) ?: return null
        val title = text(o["title"]) ?: text(o["name"]) ?: return null
        val original = text(o["original_title"]) ?: text(o["original_name"])
        return MetadataRecord(
            provider = MetadataProvider.TMDB,
            externalId = id,
            type = type,
            title = title,
            alternativeTitles = listOfNotNull(original?.takeIf { it != title }),
            overview = text(o["overview"]),
            poster = Artwork.tmdbPath(o["poster_path"] as? String),
            backdrop = Artwork.tmdbPath(o["backdrop_path"] as? String),
            year = year(o["release_date"]) ?: year(o["first_air_date"]),
            // Search results carry `vote_average` too: the guide's rating chip uses it (spec 41 Q1).
            rating = rating(o["vote_average"]),
            genreIds = genreIds(o),
            popularity = (o["popularity"] as? Number)?.toDouble(),
            attributionUrl = "https://www.themoviedb.org/${if (type == MediaType.MOVIE) "movie" else "tv"}/$id",
        )
    }

    private fun genreIds(o: Map<*, *>): List<Int> {
        val ids = list(o["genre_ids"]).mapNotNull { int(it) }
        return ids.ifEmpty { list(o["genres"]).mapNotNull { int(obj(it)["id"]) } }
    }

    private fun cast(o: Map<*, *>): List<CastMember> = list(obj(o["credits"])["cast"]).mapNotNull { c ->
        val co = obj(c)
        val name = text(co["name"]) ?: return@mapNotNull null
        CastMember(name, text(co["character"]), Artwork.tmdbPath(co["profile_path"] as? String))
    }.take(MAX_CAST)

    private suspend fun get(path: String, credential: TmdbCredential, vararg query: Pair<String, String>): Any? {
        val url = base.newBuilder().addPathSegments(path).apply {
            credential.apiKey?.let { addQueryParameter("api_key", it) }
            for ((k, v) in query) addQueryParameter(k, v)
        }.build()
        return parse(http.get(MetadataProvider.TMDB, url, credential.bearer), MetadataProvider.TMDB)
    }

    private companion object {
        const val MAX_RESULTS = 12
        const val MAX_CAST = 8
        const val MAX_SIMILAR = 20
    }
}

/** TVmaze (spec 41 §7.2): show search and episodes by number; English only, no key, no genres. */
class TvmazeClient(private val http: MetadataHttp, private val base: HttpUrl = "https://api.tvmaze.com/".toHttpUrl()) {
    suspend fun search(query: String): List<MetadataRecord> {
        val url = base.newBuilder().addPathSegments("search/shows").addQueryParameter("q", query.take(160)).build()
        return list(parse(http.get(MetadataProvider.TVMAZE, url), MetadataProvider.TVMAZE)).mapNotNull { item ->
            val show = obj(obj(item)["show"])
            val id = id(show["id"]) ?: return@mapNotNull null
            MetadataRecord(
                provider = MetadataProvider.TVMAZE,
                externalId = id,
                // TVmaze knows only shows: they answer series, episode and programme lookups alike.
                type = MediaType.SERIES,
                title = text(show["name"]) ?: return@mapNotNull null,
                overview = cleanHtml(show["summary"] as? String),
                poster = Artwork.httpsOrNull(obj(show["image"])["medium"] as? String),
                backdrop = Artwork.httpsOrNull(obj(show["image"])["original"] as? String),
                year = year(show["premiered"]),
                attributionUrl = Artwork.httpsOrNull(show["url"] as? String) ?: "https://www.tvmaze.com/shows/$id",
                imdb = text(obj(show["externals"])["imdb"]).orEmpty(),
            )
        }.take(12)
    }

    suspend fun episode(show: MetadataRecord, season: Int, number: Int): MetadataRecord? {
        val url = base.newBuilder().addPathSegments("shows/${show.externalId}/episodebynumber")
            .addQueryParameter("season", season.toString()).addQueryParameter("number", number.toString()).build()
        val o = obj(parse(http.get(MetadataProvider.TVMAZE, url), MetadataProvider.TVMAZE))
        val s = int(o["season"]) ?: season
        val e = int(o["number"]) ?: number
        return MetadataRecord(
            provider = MetadataProvider.TVMAZE,
            externalId = id(o["id"]) ?: "${show.externalId}:$s:$e",
            type = MediaType.EPISODE,
            title = text(o["name"]) ?: show.title,
            overview = cleanHtml(o["summary"] as? String) ?: show.overview,
            poster = show.poster,
            backdrop = Artwork.httpsOrNull(obj(o["image"])["original"] as? String) ?: show.backdrop,
            year = show.year,
            season = s,
            episode = e,
            attributionUrl = Artwork.httpsOrNull(o["url"] as? String) ?: show.attributionUrl,
            detailsLoaded = true,
        )
    }

    companion object {
        private val TAGS = Regex("<[^>]*>")
        private val SPACE = Regex("\\s+")

        /** Tags → space, the four entities, whitespace collapsed, blank → none (META-FR-36). */
        fun cleanHtml(html: String?): String? {
            if (html == null) return null
            val text = TAGS.replace(html, " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            return SPACE.replace(text, " ").trim().take(MAX_TEXT).ifEmpty { null }
        }
    }
}

private const val MAX_TEXT = 8_000

private fun parse(body: Buffer, provider: MetadataProvider): Any? = try {
    JsonReader.of(body).use { it.readJsonValue() }
} catch (e: Exception) {
    // An answer that is not JSON counts as a failed request: the worker retries it later.
    throw AppException(AppError.TransportFailed("${provider.displayName}: unreadable response"), e)
}

private fun obj(v: Any?): Map<*, *> = v as? Map<*, *> ?: emptyMap<Any, Any>()

private fun list(v: Any?): List<*> = v as? List<*> ?: emptyList<Any>()

private fun text(v: Any?): String? = (v as? String)?.trim()?.take(MAX_TEXT)?.ifEmpty { null }

private fun int(v: Any?): Int? = when (v) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull()
    else -> null
}

/** Ids arrive as numbers (read as doubles) or strings. */
private fun id(v: Any?): String? = when (v) {
    is Number -> v.toLong().toString()
    is String -> v.trim().ifEmpty { null }
    else -> null
}

/** The first four characters of a date, as a year. */
private fun year(v: Any?): Int? = (v as? String)?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1870..2200 }

/** One decimal in the US locale, only above 0 (META-FR-40). */
private fun rating(v: Any?): String? = (v as? Number)?.toDouble()?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }
