package com.sohva.tv.feature.trakt.protocol

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `api.trakt.tv` (spec 51 §7): scrobbles and the read side. 30 s per call, 8 MiB per body, at
 * most three reads at a time app-wide, answers parsed on [parse] (never the caller's thread).
 * Titles and ids are never logged.
 */
class TraktApiClient(
    base: OkHttpClient,
    private val credentials: TraktCredentials,
    private val parse: CoroutineDispatcher,
    private val origin: HttpUrl = ORIGIN,
    private val pageCap: Int = PAGE_CAP,
    gate: TraktGate = TraktGate(),
) {
    private val http = TraktHttp(base, TIMEOUT_S, MAX_BYTES, gate)

    // ---- Scrobbles (FR-18) ----

    /** 201 and 409 are success; anything else throws with its failure. */
    suspend fun scrobble(access: String, item: TraktItem, action: ScrobbleAction, progress: Double) {
        val body = TraktJson.write {
            beginObject()
            when (item) {
                is TraktItem.Movie -> {
                    name("movie").beginObject().name("ids")
                    ids(item.ids)
                    endObject()
                }
                is TraktItem.Episode -> {
                    name("show").beginObject().name("ids")
                    ids(item.show)
                    endObject()
                    name("episode").beginObject().name("season").value(item.season.toLong()).name("number").value(item.number.toLong()).endObject()
                }
            }
            name("progress").value(progress.coerceIn(0.0, 100.0))
            endObject()
        }
        val r = http.call(request(access, "scrobble/${action.path}").post(body.toRequestBody(JSON)).build())
        when (r.code) {
            200, 201, 409 -> Unit
            else -> fail(r)
        }
    }

    private fun JsonWriter.ids(ids: TraktIds) {
        beginObject()
        ids.trakt?.let { name("trakt").value(it) }
        ids.tmdb?.let { name("tmdb").value(it) }
        ids.imdb?.let { name("imdb").value(it) }
        ids.tvdb?.let { name("tvdb").value(it) }
        endObject()
    }

    // ---- Read side (FR-22, -25, -26) ----

    suspend fun lastActivities(access: String): LastActivities {
        val r = read(access, "sync/last_activities")
        return withContext(parse) {
            var mw = 0L
            var mp = 0L
            var ew = 0L
            var ep = 0L
            TraktJson.parse(r.body) { j ->
                j.fields { f ->
                    when (f) {
                        "movies" -> j.fields { g ->
                            when (g) {
                                "watched_at" -> mw = TraktJson.time(j.text(64))
                                "paused_at" -> mp = TraktJson.time(j.text(64))
                                else -> j.skipValue()
                            }
                        }
                        "episodes" -> j.fields { g ->
                            when (g) {
                                "watched_at" -> ew = TraktJson.time(j.text(64))
                                "paused_at" -> ep = TraktJson.time(j.text(64))
                                else -> j.skipValue()
                            }
                        }
                        else -> j.skipValue()
                    }
                }
            }
            LastActivities(mw, mp, ew, ep)
        }
    }

    /** `sync/playback?limit=500`: paused movies and episodes. */
    suspend fun playback(access: String): List<PausedItem> {
        val r = read(access, "sync/playback", mapOf("limit" to "500"))
        return withContext(parse) {
            val out = ArrayList<PausedItem>()
            TraktJson.parse(r.body) { j ->
                j.items {
                    var type: String? = null
                    var progress = 0.0
                    var pausedAt = 0L
                    var movie: TraktIds? = null
                    var show: TraktIds? = null
                    var season: Int? = null
                    var number: Int? = null
                    j.fields { f ->
                        when (f) {
                            "type" -> type = j.text(16)
                            "progress" -> progress = j.double() ?: 0.0
                            "paused_at" -> pausedAt = TraktJson.time(j.text(64))
                            "movie" -> j.fields { g -> if (g == "ids") movie = j.ids() else j.skipValue() }
                            "show" -> j.fields { g -> if (g == "ids") show = j.ids() else j.skipValue() }
                            "episode" -> j.fields { g ->
                                when (g) {
                                    "season" -> season = j.long()?.toInt()
                                    "number" -> number = j.long()?.toInt()
                                    else -> j.skipValue()
                                }
                            }
                            else -> j.skipValue()
                        }
                    }
                    val item = when (type) {
                        "movie" -> movie?.takeIf { it.any }?.let { TraktItem.Movie(it) }
                        "episode" -> show?.takeIf { it.any && (season ?: -1) >= 0 && (number ?: -1) >= 0 }?.let { TraktItem.Episode(it, season!!, number!!) }
                        else -> null
                    }
                    if (item != null) out += PausedItem(item, progress.coerceIn(0.0, 100.0), pausedAt)
                }
            }
            out
        }
    }

    /** `sync/watched/movies`, 250 per page (FR-23). */
    suspend fun watchedMovies(access: String): List<WatchedMovie> = paged(access, "sync/watched/movies", MOVIES_PER_PAGE, emptyMap()) { j ->
        val out = ArrayList<WatchedMovie>()
        j.items {
            var ids: TraktIds? = null
            var plays = 0
            var last = 0L
            j.fields { f ->
                when (f) {
                    "plays" -> plays = j.long()?.toInt() ?: 0
                    "last_watched_at" -> last = TraktJson.time(j.text(64))
                    "movie" -> j.fields { g -> if (g == "ids") ids = j.ids() else j.skipValue() }
                    else -> j.skipValue()
                }
            }
            ids?.takeIf { it.any }?.let { out += WatchedMovie(it, plays, last) }
        }
        out
    }

    /** `sync/watched/shows?extended=progress`, 100 per page (FR-23): seasons come only with `extended=progress`. */
    suspend fun watchedShows(access: String): List<WatchedShow> = paged(access, "sync/watched/shows", SHOWS_PER_PAGE, mapOf("extended" to "progress")) { j ->
        val out = ArrayList<WatchedShow>()
        j.items {
            var ids: TraktIds? = null
            var last = 0L
            val episodes = ArrayList<WatchedEpisode>()
            j.fields { f ->
                when (f) {
                    "last_watched_at" -> last = TraktJson.time(j.text(64))
                    "show" -> j.fields { g -> if (g == "ids") ids = j.ids() else j.skipValue() }
                    "seasons" -> j.items {
                        var season: Int? = null
                        val numbers = ArrayList<Triple<Int, Int, Long>>()
                        j.fields { g ->
                            when (g) {
                                "number" -> season = j.long()?.toInt()
                                "episodes" -> j.items {
                                    var number: Int? = null
                                    var plays = 0
                                    var at = 0L
                                    j.fields { h ->
                                        when (h) {
                                            "number" -> number = j.long()?.toInt()
                                            "plays" -> plays = j.long()?.toInt() ?: 0
                                            "last_watched_at" -> at = TraktJson.time(j.text(64))
                                            else -> j.skipValue()
                                        }
                                    }
                                    number?.takeIf { it >= 0 }?.let { numbers += Triple(it, plays, at) }
                                }
                                else -> j.skipValue()
                            }
                        }
                        val s = season?.takeIf { it >= 0 } ?: return@items
                        numbers.forEach { (n, p, a) -> episodes += WatchedEpisode(s, n, p, a) }
                    }
                    else -> j.skipValue()
                }
            }
            ids?.takeIf { it.any }?.let { out += WatchedShow(it, last, episodes) }
        }
        out
    }

    /** Watch next (FR-25): the next episode, or null when the show is not found (404). */
    suspend fun showProgress(access: String, traktId: Long): ShowProgress? {
        val r = readOrNotFound(access, "shows/$traktId/progress/watched", mapOf("hidden" to "false", "specials" to "false", "count_specials" to "false")) ?: return null
        return withContext(parse) {
            var season: Int? = null
            var number: Int? = null
            var title: String? = null
            var last = 0L
            TraktJson.parse(r.body) { j ->
                j.fields { f ->
                    when (f) {
                        "last_watched_at" -> last = TraktJson.time(j.text(64))
                        "next_episode" -> j.fields { g ->
                            when (g) {
                                "season" -> season = j.long()?.toInt()
                                "number" -> number = j.long()?.toInt()
                                "title" -> title = j.text(200)
                                else -> j.skipValue()
                            }
                        }
                        else -> j.skipValue()
                    }
                }
            }
            ShowProgress(season, number, title, last)
        }
    }

    /** A show's summary with images (FR-25), or null when not found. */
    suspend fun showSummary(access: String, traktId: Long): TraktTitle? {
        val r = readOrNotFound(access, "shows/$traktId", mapOf("extended" to "full,images")) ?: return null
        return withContext(parse) { TraktJson.parse(r.body) { j -> title(j, TraktKind.SHOW) } }
    }

    /** Recommended for you (FR-26): up to [limit] titles of one kind. */
    suspend fun recommendations(access: String, kind: TraktKind, limit: Int = 10): List<TraktTitle> {
        val path = if (kind == TraktKind.MOVIE) "recommendations/movies" else "recommendations/shows"
        val r = read(access, path, mapOf("limit" to limit.toString(), "extended" to "full,images"))
        return withContext(parse) {
            val out = ArrayList<TraktTitle>()
            TraktJson.parse(r.body) { j -> j.items { title(j, kind)?.let(out::add) } }
            out
        }
    }

    /**
     * One list for a Home row (spec 02 HOME-FR-94): its first [limit] titles of one kind, as Trakt
     * orders them. [access] is null for the public charts, which are read with the app's key alone.
     * Each item is the title itself (popular) or wraps it (trending, anticipated, box office, watchlist).
     */
    suspend fun list(access: String?, path: String, kind: TraktKind, limit: Int): List<TraktTitle> {
        val r = read(access, path, mapOf("limit" to limit.toString(), "page" to "1", "extended" to "full,images"))
        return withContext(parse) {
            val out = ArrayList<TraktTitle>()
            TraktJson.parse(r.body) { j -> j.items { if (out.size < limit) title(j, kind)?.let(out::add) else j.skipValue() } }
            out
        }
    }

    /**
     * A public list's summary (spec 02 HOME-FR-99), read without a sign-in: by number or by its
     * owner's address. Null when Trakt does not know it (404); a private list answers 401 and throws
     * [TraktFailure.REAUTHORIZE], which the caller reads as "private".
     */
    suspend fun listSummary(ref: TraktListRef): TraktListInfo? {
        val path = when (ref) {
            is TraktListRef.ById -> "lists/${ref.id}"
            is TraktListRef.ByUser -> "users/${ref.user}/lists/${ref.list}"
            is TraktListRef.Search -> return null
        }
        val r = readOrNotFound(null, path, emptyMap()) ?: return null
        return withContext(parse) { TraktJson.parse(r.body) { j -> listInfo(j) } }
    }

    /** Trakt's list search by name (HOME-FR-99): at most [limit] public lists, best first. */
    suspend fun searchLists(query: String, limit: Int = 10): List<TraktListInfo> {
        val r = read(null, "search/list", mapOf("query" to query, "limit" to limit.toString(), "page" to "1"))
        return withContext(parse) {
            val out = ArrayList<TraktListInfo>()
            TraktJson.parse(r.body) { j ->
                j.items {
                    var info: TraktListInfo? = null
                    j.fields { f -> if (f == "list") info = listInfo(j) else j.skipValue() }
                    info?.let(out::add)
                }
            }
            out.take(limit)
        }
    }

    private fun listInfo(j: JsonReader): TraktListInfo? {
        var id: Long? = null
        var name: String? = null
        var owner: String? = null
        var items = 0
        var likes = 0
        var private = false
        j.fields { f ->
            when (f) {
                "name" -> name = j.text(200)
                "item_count" -> items = j.long()?.toInt() ?: 0
                "likes" -> likes = j.long()?.toInt() ?: 0
                "privacy" -> private = j.text(16) == "private"
                "ids" -> j.fields { g -> if (g == "trakt") id = j.long() else j.skipValue() }
                "user" -> j.fields { g -> if (g == "username") owner = j.text(100) else j.skipValue() }
                else -> j.skipValue()
            }
        }
        if (private) return null
        return TraktListInfo(id?.takeIf { it > 0 } ?: return null, name?.takeIf { it.isNotBlank() } ?: return null, owner, items, likes)
    }

    /** FR-27: the object itself or wrapped under `movie`/`show`; an id and a title are required. */
    private fun title(j: JsonReader, kind: TraktKind): TraktTitle? {
        var ids: TraktIds? = null
        var title: String? = null
        var year: Int? = null
        var overview: String? = null
        var poster: String? = null
        var fanart: String? = null
        var wrapped: TraktTitle? = null
        j.fields { f ->
            when (f) {
                "movie" -> wrapped = title(j, TraktKind.MOVIE)
                "show" -> wrapped = title(j, TraktKind.SHOW)
                "ids" -> ids = j.ids()
                "title" -> title = j.text(200)
                "year" -> year = j.long()?.toInt()
                "overview" -> overview = j.text(1_000)
                "images" -> j.fields { g ->
                    when (g) {
                        "poster" -> poster = firstImage(j)
                        "fanart" -> fanart = firstImage(j)
                        else -> j.skipValue()
                    }
                }
                else -> j.skipValue()
            }
        }
        wrapped?.let { return it }
        val i = ids?.takeIf { it.any } ?: return null
        return TraktTitle(kind, i, title ?: return null, year, overview, poster, fanart)
    }

    /** Trakt gives host/path without a scheme; `https://` is prepended when it does not start with `http`. */
    private fun firstImage(j: JsonReader): String? {
        var first: String? = null
        j.items {
            val v = j.text(2_048)
            if (first == null && v != null) first = if (v.startsWith("http")) v else "https://$v"
        }
        return first
    }

    // ---- Transport ----

    private suspend fun <T> paged(access: String, path: String, perPage: Int, extra: Map<String, String>, page: (JsonReader) -> List<T>): List<T> {
        val out = ArrayList<T>()
        var n = 1
        while (true) {
            if (n > pageCap) throw TraktException(TraktFailure.INVALID_RESPONSE)
            val r = read(access, path, extra + mapOf("page" to n.toString(), "limit" to perPage.toString()))
            out += withContext(parse) { TraktJson.parse(r.body) { j -> page(j) } }
            val count = r.header("X-Pagination-Page-Count")?.trim()?.toIntOrNull() ?: 1
            if (n >= count) break
            n++
        }
        return out
    }

    private suspend fun read(access: String?, path: String, query: Map<String, String> = emptyMap()): TraktResponse =
        readOrNotFound(access, path, query) ?: throw TraktException(TraktFailure.NOT_FOUND)

    private suspend fun readOrNotFound(access: String?, path: String, query: Map<String, String>): TraktResponse? = reads.withPermit {
        val url = origin.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val r = http.call(request(access, url).get().build())
        when (r.code) {
            200 -> r
            404 -> null
            else -> fail(r)
        }
    }

    private fun request(access: String, path: String): Request.Builder = request(access, origin.newBuilder().addPathSegments(path).build())

    /** A null [access] is a public read: no Authorization header at all (HOME-FR-94). */
    private fun request(access: String?, url: HttpUrl): Request.Builder {
        if (!credentials.configured) throw TraktException(TraktFailure.CONFIGURATION)
        return Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("trakt-api-version", TraktHttp.API_VERSION)
            .header("trakt-api-key", TraktHttp.safe(credentials.clientId))
            .apply { if (access != null) header("Authorization", "Bearer " + TraktHttp.safe(access)) }
    }

    /** §4.12. */
    private fun fail(r: TraktResponse): Nothing = throw when (r.code) {
        401 -> TraktException(TraktFailure.REAUTHORIZE)
        403 -> TraktException(TraktFailure.CONFIGURATION)
        429 -> TraktException(TraktFailure.RATE_LIMITED, r.retryAfterSeconds())
        in 500..599 -> TraktException(TraktFailure.SERVICE)
        else -> TraktException(TraktFailure.REJECTED)
    }

    companion object {
        val ORIGIN: HttpUrl = "https://api.trakt.tv/".toHttpUrl()
        private const val TIMEOUT_S = 30L
        private const val MAX_BYTES = 8L * 1024 * 1024
        const val MOVIES_PER_PAGE: Int = 250
        const val SHOWS_PER_PAGE: Int = 100

        /** Protects memory: 200 pages is 50,000 movies or 20,000 shows. */
        const val PAGE_CAP: Int = 200
        private val JSON = "application/json".toMediaType()

        /** At most three Trakt reads at a time, app-wide (FR-22 step 3). */
        private val reads = Semaphore(3)
    }
}
