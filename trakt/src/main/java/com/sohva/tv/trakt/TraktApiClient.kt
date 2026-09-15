package com.sohva.tv.trakt

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Authenticated Trakt API calls: scrobbling out, playback and watched state in. */
class TraktApiClient internal constructor(private val clientId: String, private val origin: HttpUrl) {
    constructor(clientId: String) : this(clientId, "https://api.trakt.tv/".toHttpUrl())

    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun scrobble(tokens: TraktTokens, item: TraktItem, action: TraktScrobbleAction, progress: Double): TraktScrobbleResult {
        require(progress.isFinite() && progress in 0.0..100.0) { "Progress must be a percentage" }
        val body = buildJsonObject {
            when (item) {
                is TraktItem.Movie -> put("movie", buildJsonObject { put("ids", item.ids.json()) })
                is TraktItem.Episode -> {
                    put("show", buildJsonObject { put("ids", item.ids.json()) })
                    put("episode", buildJsonObject { put("season", item.season); put("number", item.number) })
                }
            }
            put("progress", progress)
        }
        val response = call(tokens, "scrobble/${action.path}", body)
        // 409: Trakt already recorded this exact watch moments ago. Treat as done.
        if (response.code == 409) return TraktScrobbleResult("scrobble", progress)
        if (response.code != 201) response.fail()
        return parse {
            val json = response.json().jsonObject
            fun name(key: String) = (json[key] as? JsonObject)?.get("title")?.let { it as? JsonPrimitive }?.contentOrNull?.take(120)
            val episode = (json["episode"] as? JsonObject)
            val recorded = when (item) {
                is TraktItem.Movie -> name("movie")
                is TraktItem.Episode -> listOfNotNull(name("show"),
                    episode?.let { "S${it["season"]?.jsonPrimitive?.contentOrNull}E${it["number"]?.jsonPrimitive?.contentOrNull}" },
                    name("episode")).joinToString(" · ").ifEmpty { null }
            }
            TraktScrobbleResult(json.getValue("action").jsonPrimitive.content, json.getValue("progress").jsonPrimitive.double, recorded)
        }
    }

    suspend fun playback(tokens: TraktTokens): List<TraktPlaybackItem> {
        val response = call(tokens, "sync/playback?limit=500")
        if (response.code != 200) response.fail()
        return parse {
            response.json().jsonArray.mapNotNull { entry ->
                val obj = entry.jsonObject
                val item = obj.item() ?: return@mapNotNull null
                TraktPlaybackItem(
                    id = obj.getValue("id").jsonPrimitive.long,
                    item = item,
                    progress = obj.getValue("progress").jsonPrimitive.double.coerceIn(0.0, 100.0),
                    pausedAtMillis = obj.time("paused_at"),
                    title = obj.title(),
                )
            }
        }
    }

    suspend fun watchedMovies(tokens: TraktTokens): List<TraktWatchedMovie> {
        val response = call(tokens, "sync/watched/movies")
        if (response.code != 200) response.fail()
        return parse {
            response.json().jsonArray.map { entry ->
                val obj = entry.jsonObject
                TraktWatchedMovie(obj.getValue("movie").jsonObject.ids(), obj.getValue("plays").jsonPrimitive.int, obj.time("last_watched_at"))
            }
        }
    }

    suspend fun watchedShows(tokens: TraktTokens): List<TraktWatchedShow> {
        val response = call(tokens, "sync/watched/shows")
        if (response.code != 200) response.fail()
        return parse {
            response.json().jsonArray.map { entry ->
                val obj = entry.jsonObject
                val episodes = obj["seasons"]?.jsonArray.orEmpty().flatMap { season ->
                    val number = season.jsonObject.getValue("number").jsonPrimitive.int
                    season.jsonObject["episodes"]?.jsonArray.orEmpty().map { episode ->
                        val e = episode.jsonObject
                        TraktWatchedEpisode(number, e.getValue("number").jsonPrimitive.int, e.getValue("plays").jsonPrimitive.int, e.time("last_watched_at"))
                    }
                }
                val show = obj.getValue("show").jsonObject
                TraktWatchedShow(show.ids(), episodes, (show["title"] as? JsonPrimitive)?.contentOrNull?.take(200),
                    (show["year"] as? JsonPrimitive)?.intOrNull, obj.time("last_watched_at"))
            }
        }
    }

    /** Trakt's picks for the account, with pictures; [kind] is `movies` or `shows`. */
    suspend fun recommendations(tokens: TraktTokens, kind: String, limit: Int): List<TraktTitle> {
        require(kind == "movies" || kind == "shows")
        val response = call(tokens, "recommendations/$kind?limit=$limit&extended=full,images")
        if (response.code != 200) response.fail()
        return parse { response.json().jsonArray.mapNotNull { it.jsonObject.title(if (kind == "movies") "movie" else "show") } }
    }

    /** One show's summary with pictures, by any id Trakt accepts in the path. */
    suspend fun showSummary(tokens: TraktTokens, id: String): TraktTitle? {
        val response = call(tokens, "shows/${id.encodePath()}?extended=full,images")
        if (response.code == 404) return null
        if (response.code != 200) response.fail()
        return parse { response.json().jsonObject.title("show") }
    }

    /** The account's next episode for a show, or none when it is finished. */
    suspend fun showProgress(tokens: TraktTokens, id: String): TraktShowProgress? {
        val response = call(tokens, "shows/${id.encodePath()}/progress/watched?hidden=false&specials=false&count_specials=false")
        if (response.code == 404) return null
        if (response.code != 200) response.fail()
        return parse {
            val json = response.json().jsonObject
            val next = json["next_episode"] as? JsonObject
            TraktShowProgress(next?.get("season")?.jsonPrimitive?.intOrNull, next?.get("number")?.jsonPrimitive?.intOrNull,
                (next?.get("title") as? JsonPrimitive)?.contentOrNull?.take(200), json.time("last_watched_at"))
        }
    }

    suspend fun lastActivities(tokens: TraktTokens): TraktActivities {
        val response = call(tokens, "sync/last_activities")
        if (response.code != 200) response.fail()
        return parse {
            val json = response.json().jsonObject
            val movies = json.getValue("movies").jsonObject
            val episodes = json.getValue("episodes").jsonObject
            TraktActivities(movies.time("watched_at"), movies.time("paused_at"), episodes.time("watched_at"), episodes.time("paused_at"))
        }
    }

    /** A movie or show object, on its own or wrapped under [wrapper] as in the recommendation lists. */
    private fun JsonObject.title(wrapper: String): TraktTitle? {
        val obj = (this[wrapper] as? JsonObject) ?: this
        val ids = obj.ids().takeIf { it.any } ?: return null
        val name = (obj["title"] as? JsonPrimitive)?.contentOrNull?.take(200) ?: return null
        val images = obj["images"] as? JsonObject
        fun image(key: String) = (images?.get(key) as? JsonArray)?.firstOrNull()?.let { it as? JsonPrimitive }?.contentOrNull
            ?.let { if (it.startsWith("http")) it else "https://$it" }
        return TraktTitle(if (wrapper == "movie") "movie" else "show", ids, name, (obj["year"] as? JsonPrimitive)?.intOrNull,
            (obj["overview"] as? JsonPrimitive)?.contentOrNull?.take(1000), image("poster"), image("fanart"))
    }
    private fun String.encodePath() = java.net.URLEncoder.encode(this, "UTF-8")

    private fun TraktIds.json() = buildJsonObject {
        trakt?.let { put("trakt", it) }; tmdb?.let { put("tmdb", it) }; imdb?.let { put("imdb", it) }; tvdb?.let { put("tvdb", it) }
    }
    private fun JsonObject.ids(): TraktIds {
        val ids = getValue("ids").jsonObject
        fun long(name: String) = (ids[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        return TraktIds(long("trakt"), long("tmdb"), (ids["imdb"] as? JsonPrimitive)?.contentOrNull, long("tvdb"))
    }
    private fun JsonObject.item(): TraktItem? = when (getValue("type").jsonPrimitive.content) {
        "movie" -> getValue("movie").jsonObject.ids().takeIf { it.any }?.let { TraktItem.Movie(it) }
        "episode" -> {
            val episode = getValue("episode").jsonObject
            getValue("show").jsonObject.ids().takeIf { it.any }?.let {
                TraktItem.Episode(it, episode.getValue("season").jsonPrimitive.int, episode.getValue("number").jsonPrimitive.int)
            }
        }
        else -> null
    }
    private fun JsonObject.title(): String? {
        fun name(obj: JsonObject?) = (obj?.get("title") as? JsonPrimitive)?.contentOrNull?.take(200)
        return when (getValue("type").jsonPrimitive.content) {
            "movie" -> name(this["movie"]?.jsonObject)
            else -> listOfNotNull(name(this["show"]?.jsonObject), name(this["episode"]?.jsonObject)).joinToString(" · ").ifEmpty { null }
        }
    }
    private fun JsonObject.time(name: String): Long =
        (this[name] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private suspend fun call(tokens: TraktTokens, path: String, body: JsonObject? = null): ApiResponse = try {
        val request = Request.Builder().url(origin.resolve(path)!!)
            .header("trakt-api-version", "2").header("trakt-api-key", clientId)
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .let { if (body != null) it.post(body.toString().toRequestBody("application/json".toMediaType())) else it.get() }
            .build()
        client.newCall(request).await()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: TraktException) { throw error }
    catch (_: Exception) { throw TraktException(TraktFailure.NETWORK) }

    private suspend fun Call.await(): ApiResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(TraktException(TraktFailure.NETWORK))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        val source = it.body.source()
                        source.request(MAX_BODY_BYTES + 1)
                        if (source.buffer.size > MAX_BODY_BYTES) throw TraktException(TraktFailure.INVALID_RESPONSE)
                        ApiResponse(it.code, source.readUtf8(), retryAfterSeconds(it.header("Retry-After"), System.currentTimeMillis()))
                    }
                    continuation.resume(value) { _, _, _ -> }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error as? TraktException ?: TraktException(TraktFailure.NETWORK))
                }
            }
        })
    }
    private suspend fun <T> parse(block: () -> T): T = withContext(Dispatchers.Default) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw TraktException(TraktFailure.INVALID_RESPONSE) }
    }

    private class ApiResponse(val code: Int, private val body: String, val retryAfter: Long?) {
        fun json(): JsonElement = Json.parseToJsonElement(body)
        fun fail(): Nothing = throw TraktException(when (code) {
            401 -> TraktFailure.REAUTHORIZE
            403 -> TraktFailure.CONFIGURATION
            429 -> TraktFailure.RATE_LIMITED
            in 500..599 -> TraktFailure.SERVICE
            else -> TraktFailure.INVALID_RESPONSE
        }, retryAfter)
    }
    private companion object { const val MAX_BODY_BYTES = 8 * 1024 * 1024L }
}
