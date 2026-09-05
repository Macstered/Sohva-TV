package com.streammate.tv.iptv.xtream

import com.streammate.tv.core.error.ResourceArgument
import com.streammate.tv.core.error.localizedTransportFailure
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.network.IptvSourceUrlPolicy
import com.streammate.tv.core.security.SecretRedactor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

data class XtreamAccount(
    val username: String,
    val status: String?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val serverTimeZoneId: String? = null,
)

data class XtreamLiveChannel(
    val id: String,
    val name: String,
    val categoryName: String?,
    val epgChannelId: String?,
    val logoUrl: String?,
    val streamUrl: String,
    val streamId: String = id.removePrefix("xtream-"),
    val archiveDurationDays: Int? = null,
    val serverTimeZoneId: String? = null,
    val playlistOrder: Int = Int.MAX_VALUE,
    val categoryId: String? = null,
) {
    override fun toString(): String =
        "XtreamLiveChannel(id=$id, name=$name, categoryName=$categoryName, streamUrl=<redacted>)"
}

data class XtreamMovie(
    val streamId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val streamUrl: String,
    val containerExtension: String,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    val categoryId: String? = null,
) {
    override fun toString(): String =
        "XtreamMovie(streamId=$streamId, name=$name, categoryName=$categoryName, streamUrl=<redacted>)"
}

data class XtreamSeries(
    val seriesId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    val categoryId: String? = null,
)

data class XtreamEpisode(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val streamUrl: String,
    val containerExtension: String,
    val plot: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String? = null,
) {
    override fun toString(): String =
        "XtreamEpisode(episodeId=$episodeId, seasonNumber=$seasonNumber, " +
            "episodeNumber=$episodeNumber, name=$name, streamUrl=<redacted>)"
}

interface XtreamSource {
    suspend fun authenticate(source: IptvSourceConfiguration): XtreamAccount
    suspend fun liveChannels(source: IptvSourceConfiguration): List<XtreamLiveChannel>
    fun xmlTvUrl(source: IptvSourceConfiguration): String
}

interface XtreamCatalogueSource {
    suspend fun movies(source: IptvSourceConfiguration): List<XtreamMovie>
    suspend fun series(source: IptvSourceConfiguration): List<XtreamSeries>
    suspend fun seriesEpisodes(source: IptvSourceConfiguration, seriesId: String): List<XtreamEpisode>

    /**
     * The movie list in chunks as it streams in, so a provider with a
     * hundred thousand films never has to fit in memory at once. Returns the
     * count. The default reads everything first; the real client streams.
     */
    suspend fun streamMovies(
        source: IptvSourceConfiguration,
        chunkSize: Int,
        consume: suspend (List<XtreamMovie>) -> Unit,
    ): Int {
        val all = movies(source)
        all.chunked(chunkSize).forEach { consume(it) }
        return all.size
    }

    suspend fun streamSeries(
        source: IptvSourceConfiguration,
        chunkSize: Int,
        consume: suspend (List<XtreamSeries>) -> Unit,
    ): Int {
        val all = series(source)
        all.chunked(chunkSize).forEach { consume(it) }
        return all.size
    }
}

@OptIn(ExperimentalSerializationApi::class)
class XtreamClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxResponseBytes: Long = MAX_XTREAM_RESPONSE_BYTES,
) : XtreamSource, XtreamCatalogueSource {
    init {
        require(maxResponseBytes > 0) { "Xtream response limit must be positive" }
    }

    override suspend fun authenticate(source: IptvSourceConfiguration): XtreamAccount {
        val credentials = source.credentials()
        val root = requestJson(apiUrl(credentials)) as? JsonObject
            ?: throw XtreamException(CoreR.string.error_xtream_no_user_info)
        val userInfo = root["user_info"] as? JsonObject
            ?: throw XtreamException(CoreR.string.error_xtream_no_user_info)
        val authenticated = userInfo["auth"].asBoolean()
        if (!authenticated) throw XtreamException(CoreR.string.error_xtream_auth_failed)
        return XtreamAccount(
            username = userInfo["username"].asString() ?: credentials.username,
            status = userInfo["status"].asString(),
            maxConnections = userInfo["max_connections"].asInt(),
            activeConnections = userInfo["active_cons"].asInt(),
            serverTimeZoneId = (root["server_info"] as? JsonObject)?.get("timezone").asString(),
        )
    }

    override suspend fun liveChannels(source: IptvSourceConfiguration): List<XtreamLiveChannel> {
        val credentials = source.credentials()
        val account = authenticate(source)
        val categoryNames = requestJsonArray(apiUrl(credentials, "get_live_categories")) { element ->
            val category = element as? JsonObject ?: return@requestJsonArray null
            val id = category["category_id"].asString() ?: return@requestJsonArray null
            id to category["category_name"].asString().orEmpty()
        }
            .toMap()
        return requestJsonArray(apiUrl(credentials, "get_live_streams")) { element ->
            element.toLiveChannel(credentials, categoryNames, account.serverTimeZoneId)
        }.mapIndexed { index, channel ->
            if (channel.playlistOrder == Int.MAX_VALUE) channel.copy(playlistOrder = index) else channel
        }
    }

    override fun xmlTvUrl(source: IptvSourceConfiguration): String {
        val credentials = source.credentials()
        return endpointUrl(credentials, "xmltv.php")
            .newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .build()
            .toString()
    }

    override suspend fun movies(source: IptvSourceConfiguration): List<XtreamMovie> {
        val credentials = source.credentials()
        authenticate(source)
        val categoryNames = categories(credentials, "get_vod_categories")
        return requestJsonArray(apiUrl(credentials, "get_vod_streams")) { element ->
            element.toMovie(credentials, categoryNames)
        }
    }

    override suspend fun series(source: IptvSourceConfiguration): List<XtreamSeries> {
        val credentials = source.credentials()
        authenticate(source)
        val categoryNames = categories(credentials, "get_series_categories")
        return requestJsonArray(apiUrl(credentials, "get_series")) { element ->
            element.toSeries(categoryNames)
        }
    }

    override suspend fun streamMovies(
        source: IptvSourceConfiguration,
        chunkSize: Int,
        consume: suspend (List<XtreamMovie>) -> Unit,
    ): Int {
        val credentials = source.credentials()
        authenticate(source)
        val categoryNames = categories(credentials, "get_vod_categories")
        return requestJsonArrayChunked(apiUrl(credentials, "get_vod_streams"), chunkSize, consume) { element ->
            element.toMovie(credentials, categoryNames)
        }
    }

    override suspend fun streamSeries(
        source: IptvSourceConfiguration,
        chunkSize: Int,
        consume: suspend (List<XtreamSeries>) -> Unit,
    ): Int {
        val credentials = source.credentials()
        authenticate(source)
        val categoryNames = categories(credentials, "get_series_categories")
        return requestJsonArrayChunked(apiUrl(credentials, "get_series"), chunkSize, consume) { element ->
            element.toSeries(categoryNames)
        }
    }

    override suspend fun seriesEpisodes(
        source: IptvSourceConfiguration,
        seriesId: String,
    ): List<XtreamEpisode> {
        require(seriesId.matches(ID_PATTERN)) { CoreR.string.error_series_id_invalid }
        val credentials = source.credentials()
        val root = requestJson(apiUrl(credentials, "get_series_info", "series_id" to seriesId)) as? JsonObject
            ?: return emptyList()
        val episodes = root["episodes"] as? JsonObject ?: return emptyList()
        return episodes.entries
            .flatMap { (seasonKey, value) ->
                val seasonNumber = seasonKey.toIntOrNull() ?: return@flatMap emptyList()
                val season = value as? JsonArray ?: return@flatMap emptyList()
                season.mapNotNull { it.toEpisode(credentials, seasonNumber) }
            }
            .sortedWith(compareBy(XtreamEpisode::seasonNumber, XtreamEpisode::episodeNumber))
    }

    private suspend fun requestJson(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        withResponseStream(url) { input ->
            runCatching { json.decodeFromStream(JsonElement.serializer(), input) }
                .getOrElse(::invalidResponse)
        }
    }

    private suspend fun <T : Any> requestJsonArray(
        url: HttpUrl,
        transform: (JsonElement) -> T?,
    ): List<T> = withContext(Dispatchers.IO) {
        withResponseStream(url) { input ->
            runCatching {
                json.decodeToSequence(
                    input,
                    JsonElement.serializer(),
                    DecodeSequenceMode.ARRAY_WRAPPED,
                ).mapNotNull(transform).toList()
            }.getOrElse(::invalidResponse)
        }
    }

    /**
     * Like [requestJsonArray], but hands the items over in chunks while the
     * body is still arriving, and never holds the whole list. The consumer is
     * called on the IO dispatcher with the response open.
     */
    private suspend fun <T : Any> requestJsonArrayChunked(
        url: HttpUrl,
        chunkSize: Int,
        consume: suspend (List<T>) -> Unit,
        transform: (JsonElement) -> T?,
    ): Int = withContext(Dispatchers.IO) {
        withResponseStream(url) { input ->
            var count = 0
            val chunk = ArrayList<T>(chunkSize)
            runCatching {
                json.decodeToSequence(input, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
                    .mapNotNull(transform)
                    .forEach { item ->
                        chunk += item
                        count += 1
                        if (chunk.size >= chunkSize) {
                            runBlocking { consume(chunk.toList()) }
                            chunk.clear()
                        }
                    }
                if (chunk.isNotEmpty()) runBlocking { consume(chunk.toList()) }
            }.getOrElse(::invalidResponse)
            count
        }
    }

    private fun <T> withResponseStream(url: HttpUrl, block: (java.io.InputStream) -> T): T {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw localizedTransportFailure(error, ::XtreamException)
        }
        return response.use { checked ->
            checked.requireSuccess()
            val body = checked.body
            if (body.contentLength() > maxResponseBytes) {
                throw XtreamException(CoreR.string.error_xtream_response_too_large)
            }
            LimitedInputStream(body.byteStream(), maxResponseBytes).use(block)
        }
    }

    private fun invalidResponse(error: Throwable): Nothing {
        val payloadTooLarge = generateSequence(error as Throwable?) { it.cause }
            .any { it is PayloadTooLargeException }
        val message = if (payloadTooLarge) {
            CoreR.string.error_xtream_response_too_large
        } else {
            CoreR.string.error_xtream_response_invalid
        }
        throw XtreamException(message, cause = error)
    }

    private fun Response.requireSuccess() {
        if (!isSuccessful) throw XtreamException(CoreR.string.error_xtream_http, listOf(code))
    }

    private suspend fun categories(credentials: XtreamCredentials, action: String): Map<String, String> =
        requestJsonArray(apiUrl(credentials, action)) { element ->
            val category = element as? JsonObject ?: return@requestJsonArray null
            val id = category["category_id"].asString() ?: return@requestJsonArray null
            id to category["category_name"].asString().orEmpty()
        }
            .toMap()

    private fun apiUrl(
        credentials: XtreamCredentials,
        action: String? = null,
        extraQuery: Pair<String, String>? = null,
    ): HttpUrl =
        endpointUrl(credentials, "player_api.php")
            .newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply { action?.let { addQueryParameter("action", it) } }
            .apply { extraQuery?.let { (name, value) -> addQueryParameter(name, value) } }
            .build()

    private fun endpointUrl(credentials: XtreamCredentials, endpoint: String): HttpUrl =
        credentials.baseUrl.newBuilder()
            .addPathSegment(endpoint)
            .build()

    private fun JsonElement.toLiveChannel(
        credentials: XtreamCredentials,
        categoryNames: Map<String, String>,
        serverTimeZoneId: String?,
    ): XtreamLiveChannel? {
        val stream = this as? JsonObject ?: return null
        val streamId = stream["stream_id"].asString() ?: return null
        val name = stream["name"].asString()?.takeIf(String::isNotBlank) ?: return null
        val categoryId = stream["category_id"].asString()
        val extension = stream["container_extension"].asString()
            .safeExtension("ts")
        val streamUrl = credentials.baseUrl.newBuilder()
            .addPathSegment("live")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$streamId.$extension")
            .build()
            .toString()
        return XtreamLiveChannel(
            id = "xtream-$streamId",
            name = name,
            categoryName = categoryId?.let(categoryNames::get),
            categoryId = categoryId,
            epgChannelId = stream["epg_channel_id"].asString()?.takeIf(String::isNotBlank),
            logoUrl = stream["stream_icon"].asString()?.takeIf(String::isNotBlank),
            streamUrl = streamUrl,
            streamId = streamId,
            archiveDurationDays = if (stream["tv_archive"].asBoolean()) {
                stream["tv_archive_duration"].asInt()?.coerceIn(1, MAX_ARCHIVE_DAYS)
            } else {
                null
            },
            serverTimeZoneId = serverTimeZoneId,
            playlistOrder = stream["num"].asInt() ?: Int.MAX_VALUE,
        )
    }

    private fun JsonElement.toMovie(
        credentials: XtreamCredentials,
        categoryNames: Map<String, String>,
    ): XtreamMovie? {
        val movie = this as? JsonObject ?: return null
        val streamId = movie["stream_id"].asString() ?: return null
        val name = movie["name"].asString()?.takeIf(String::isNotBlank) ?: return null
        val extension = movie["container_extension"].asString().safeExtension("mp4")
        val streamUrl = credentials.baseUrl.newBuilder()
            .addPathSegment("movie")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$streamId.$extension")
            .build()
            .toString()
        return XtreamMovie(
            streamId = streamId,
            name = name,
            categoryName = movie["category_id"].asString()?.let(categoryNames::get),
            categoryId = movie["category_id"].asString(),
            posterUrl = movie["stream_icon"].asString()?.takeIf(String::isNotBlank),
            streamUrl = streamUrl,
            containerExtension = extension,
            year = movie["year"].asInt() ?: movie["release_date"].asString()?.take(4)?.toIntOrNull(),
            rating = movie["rating"].asString(),
            plot = movie["plot"].asString()?.takeIf(String::isNotBlank),
        )
    }

    private fun JsonElement.toSeries(categoryNames: Map<String, String>): XtreamSeries? {
        val series = this as? JsonObject ?: return null
        val seriesId = series["series_id"].asString() ?: return null
        val name = series["name"].asString()?.takeIf(String::isNotBlank) ?: return null
        return XtreamSeries(
            seriesId = seriesId,
            name = name,
            categoryName = series["category_id"].asString()?.let(categoryNames::get),
            categoryId = series["category_id"].asString(),
            posterUrl = series["cover"].asString()?.takeIf(String::isNotBlank),
            backdropUrl = series["backdrop_path"]?.let { backdrop ->
                runCatching { backdrop.jsonArray.firstOrNull().asString() }.getOrNull()
                    ?: backdrop.asString()
            }?.takeIf(String::isNotBlank),
            year = series["year"].asInt() ?: series["releaseDate"].asString()?.take(4)?.toIntOrNull(),
            rating = series["rating"].asString(),
            plot = series["plot"].asString()?.takeIf(String::isNotBlank),
        )
    }

    private fun JsonElement.toEpisode(credentials: XtreamCredentials, seasonNumber: Int): XtreamEpisode? {
        val episode = this as? JsonObject ?: return null
        val episodeId = episode["id"].asString() ?: return null
        val episodeNumber = episode["episode_num"].asInt() ?: return null
        val extension = episode["container_extension"].asString().safeExtension("mp4")
        val info = episode["info"] as? JsonObject
        val rawTitle = episode["title"].asString()?.takeIf(String::isNotBlank)
        return XtreamEpisode(
            episodeId = episodeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            name = xtreamEpisodeDisplayTitle(rawTitle, seasonNumber, episodeNumber),
            streamUrl = credentials.baseUrl.newBuilder()
                .addPathSegment("series")
                .addPathSegment(credentials.username)
                .addPathSegment(credentials.password)
                .addPathSegment("$episodeId.$extension")
                .build()
                .toString(),
            containerExtension = extension,
            plot = info?.get("plot").asString()?.takeIf(String::isNotBlank),
            durationSeconds = info?.get("duration_secs").asInt(),
            thumbnailUrl = sequenceOf("movie_image", "cover_big", "cover")
                .mapNotNull { field -> info?.get(field).asString()?.takeIf(String::isNotBlank) }
                .firstOrNull(),
        )
    }

    private fun IptvSourceConfiguration.credentials(): XtreamCredentials {
        if (type != IptvSourceType.XTREAM) {
            throw XtreamException(CoreR.string.error_source_not_xtream)
        }
        val base = xtreamBaseUrl?.takeIf(String::isNotBlank)
            ?: throw XtreamException(CoreR.string.error_xtream_url_missing)
        val username = xtreamUsername?.takeIf(String::isNotBlank)
            ?: throw XtreamException(CoreR.string.error_xtream_username_missing)
        val password = xtreamPassword?.takeIf(String::isNotBlank)
            ?: throw XtreamException(CoreR.string.error_xtream_password_missing)
        val normalizedBase = runCatching { IptvSourceUrlPolicy.normalize(base, ResourceArgument(CoreR.string.error_label_xtream_server)) }
            .getOrElse { error ->
                throw XtreamException(
                    (error as? LocalizedException)?.messageResource
                        ?: CoreR.string.error_xtream_url_invalid,
                    (error as? LocalizedException)?.messageArguments.orEmpty(),
                    cause = error,
                )
            }
        return XtreamCredentials(normalizedBase.ensureTrailingSlash().toHttpUrl(), username, password)
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    private fun String?.safeExtension(fallback: String): String = this
        ?.lowercase()
        ?.takeIf { it.matches(EXTENSION_PATTERN) }
        ?: fallback

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
        val EXTENSION_PATTERN = Regex("[a-z0-9]{1,8}")
        const val MAX_ARCHIVE_DAYS = 365
        // Arrays are streamed element by element, so this guards against a
        // runaway body rather than sizing memory. A 56,000-channel provider's
        // movie list passed 64 MB and was refused outright.
        const val MAX_XTREAM_RESPONSE_BYTES = 1024L * 1024L * 1024L
    }
}

internal fun xtreamEpisodeDisplayTitle(rawTitle: String?, seasonNumber: Int, episodeNumber: Int): String {
    val fallback = "Jakso $episodeNumber"
    val title = rawTitle?.trim()?.takeIf(String::isNotBlank) ?: return fallback
    val marker = Regex("(?i)S0*$seasonNumber\\s*E0*$episodeNumber(?!\\d)").find(title) ?: return title
    return title.substring(marker.range.last + 1)
        .trim(' ', '-', '–', '—', ':', '.')
        .takeIf(String::isNotBlank)
        ?: fallback
}

private class LimitedInputStream(
    private val delegate: java.io.InputStream,
    private val maxBytes: Long,
) : java.io.InputStream() {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) recordRead(1)
        return value
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val count = delegate.read(target, offset, length)
        if (count > 0) recordRead(count)
        return count
    }

    override fun close() = delegate.close()

    private fun recordRead(count: Int) {
        bytesRead += count
        if (bytesRead > maxBytes) throw PayloadTooLargeException()
    }
}

private class PayloadTooLargeException : IOException("Xtream response exceeded the byte limit")

private data class XtreamCredentials(
    val baseUrl: HttpUrl,
    val username: String,
    val password: String,
)

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asInt(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}

private fun JsonElement?.asBoolean(): Boolean {
    val value = asString()?.lowercase()
    return value == "1" || value == "true"
}

class XtreamException(
    @StringRes messageResource: Int,
    messageArguments: List<Any> = emptyList(),
    logMessage: String? = null,
    cause: Throwable? = null,
) : LocalizedException(messageResource, messageArguments, logMessage, cause)
