package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.security.MetadataSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class TmdbMetadataProvider(
    private val httpClient: OkHttpClient,
    private val apiRoot: String = DEFAULT_API_ROOT,
    private val imageRoot: String = DEFAULT_IMAGE_ROOT,
) : MetadataProvider {
    override val id: String = "tmdb"
    override val attributionName: String = "TMDB"
    override val positiveCacheMillis: Long = 30L * 24 * 60 * 60 * 1_000

    override fun enabled(settings: MetadataSettings): Boolean =
        settings.tmdbEnabled && settings.tmdbReadAccessToken.isNotBlank()

    override fun supports(mediaType: MetadataMediaType): Boolean = true

    suspend fun verifyCredential(credential: String) {
        require(credential.isNotBlank()) { "TMDB credential is missing" }
        httpClient.getMetadataJson(
            request = request("$apiRoot/configuration", credential),
            providerName = attributionName,
        )
    }

    override suspend fun search(
        lookup: MetadataLookup,
        settings: MetadataSettings,
    ): List<MetadataCandidate> {
        val searchType = when (lookup.mediaType) {
            MetadataMediaType.MOVIE -> "movie"
            MetadataMediaType.PROGRAMME -> "multi"
            MetadataMediaType.SERIES, MetadataMediaType.EPISODE -> "tv"
        }
        val url = "$apiRoot/search/$searchType".toHttpUrl().newBuilder()
            .addQueryParameter("query", lookup.title.take(MAX_QUERY_LENGTH))
            .addQueryParameter("language", lookup.language)
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
            .build()
        val results = httpClient.getMetadataJson(
            request = request(url.toString(), settings.tmdbReadAccessToken),
            providerName = attributionName,
        ).jsonObject["results"]?.jsonArray.orEmpty()
        val shows = results.asSequence()
            .mapNotNull { result -> parseSearchResult(result.jsonObject, lookup.mediaType) }
            .take(MAX_CANDIDATES)
            .toList()
        if (lookup.mediaType == MetadataMediaType.SERIES) {
            val selected = MetadataMatcher.choose(lookup, shows)?.candidate ?: return shows
            val detailed = try {
                seriesDetails(selected, lookup.language, settings.tmdbReadAccessToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return shows
            return shows.map { candidate ->
                if (candidate.externalId == detailed.externalId) detailed else candidate
            }
        }
        if (lookup.mediaType != MetadataMediaType.EPISODE) return shows
        val season = lookup.seasonNumber ?: return emptyList()
        val episode = lookup.episodeNumber ?: return emptyList()
        val show = MetadataMatcher.choose(
            lookup.copy(
                mediaType = MetadataMediaType.SERIES,
                seasonNumber = null,
                episodeNumber = null,
            ),
            shows,
        )?.candidate ?: return emptyList()
        val candidate = try {
            val detailsUrl = "$apiRoot/tv/${show.externalId}/season/$season/episode/$episode"
                .toHttpUrl().newBuilder()
                .addQueryParameter("language", lookup.language)
                .build()
            val details = httpClient.getMetadataJson(
                request(detailsUrl.toString(), settings.tmdbReadAccessToken),
                attributionName,
            ).jsonObject
            MetadataCandidate(
                externalId = "${show.externalId}:$season:$episode",
                mediaType = MetadataMediaType.EPISODE,
                matchingTitle = show.matchingTitle,
                alternativeTitles = show.alternativeTitles,
                displayTitle = details.string("name") ?: show.displayTitle,
                overview = details.string("overview") ?: show.overview,
                posterUrl = show.posterUrl,
                backdropUrl = imageUrl(details.string("still_path"), "w780") ?: show.backdropUrl,
                year = show.year,
                seasonNumber = details.integer("season_number") ?: season,
                episodeNumber = details.integer("episode_number") ?: episode,
                runtimeMinutes = details.integer("runtime"),
                rating = formatRating(details.decimal("vote_average")),
                attributionUrl = "https://www.themoviedb.org/tv/${show.externalId}/season/$season/episode/$episode",
                providerPopularity = show.providerPopularity,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return listOfNotNull(candidate)
    }

    /**
     * The record for a match somebody chose, fetched by its id.
     *
     * Nothing is searched and nothing is scored: the question of which title
     * this is has already been answered by a person, and asking the matcher
     * again could only disagree with them.
     */
    internal suspend fun detailsById(
        externalId: String,
        mediaType: MetadataMediaType,
        language: String,
        credential: String,
    ): MetadataCandidate? = when (mediaType) {
        MetadataMediaType.MOVIE -> movieDetails(externalId, language, credential)
        MetadataMediaType.SERIES, MetadataMediaType.EPISODE -> {
            val stub = MetadataCandidate(
                externalId = externalId,
                mediaType = MetadataMediaType.SERIES,
                matchingTitle = "",
                displayTitle = "",
                overview = null,
                posterUrl = null,
                backdropUrl = null,
                year = null,
                attributionUrl = "https://www.themoviedb.org/tv/$externalId",
            )
            seriesDetails(stub, language, credential)
                .let { detailed -> detailed.copy(matchingTitle = detailed.displayTitle) }
        }
        // A programme is not something the library lets anyone pin.
        MetadataMediaType.PROGRAMME -> null
    }

    internal suspend fun movieDetails(
        externalId: String,
        language: String,
        credential: String,
    ): MetadataCandidate {
        val detailsUrl = "$apiRoot/movie/$externalId".toHttpUrl().newBuilder()
            .addQueryParameter("language", language)
            .addQueryParameter("append_to_response", "credits,similar")
            .build()
        val details = httpClient.getMetadataJson(
            request(detailsUrl.toString(), credential),
            attributionName,
        ).jsonObject
        val title = details.string("title") ?: details.string("original_title") ?: externalId
        val originalTitle = details.string("original_title")
        val releaseDate = details.string("release_date")
        val similarMovies = ((details["similar"] as? JsonObject)?.get("results") as? JsonArray)
            .orEmpty()
            .asSequence()
            .mapNotNull(::parseMovieReference)
            .filterNot { it.externalId == externalId }
            .take(MAX_SIMILAR_MOVIES)
            .toList()
        return MetadataCandidate(
            externalId = details.integer("id")?.toString() ?: externalId,
            mediaType = MetadataMediaType.MOVIE,
            matchingTitle = title,
            alternativeTitles = listOfNotNull(originalTitle).filterNot(title::equals),
            displayTitle = title,
            overview = details.string("overview"),
            posterUrl = imageUrl(details.string("poster_path"), "w500"),
            backdropUrl = imageUrl(details.string("backdrop_path"), "w780"),
            year = releaseDate?.take(4)?.toIntOrNull(),
            runtimeMinutes = details.integer("runtime")?.takeIf { it > 0 },
            rating = formatRating(details.decimal("vote_average")),
            cast = parseCast(details),
            genres = TmdbGenres.of(MetadataMediaType.MOVIE, detailGenreIds(details)),
            detailsLoaded = true,
            similarMovies = similarMovies,
            attributionUrl = "https://www.themoviedb.org/movie/$externalId",
        )
    }

    private suspend fun seriesDetails(
        candidate: MetadataCandidate,
        language: String,
        credential: String,
    ): MetadataCandidate {
        val detailsUrl = "$apiRoot/tv/${candidate.externalId}".toHttpUrl().newBuilder()
            .addQueryParameter("language", language)
            .addQueryParameter("append_to_response", "credits")
            .build()
        val details = httpClient.getMetadataJson(
            request(detailsUrl.toString(), credential),
            attributionName,
        ).jsonObject
        val runtime = (details["episode_run_time"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            ?.firstOrNull { it > 0 }
        return candidate.copy(
            displayTitle = details.string("name") ?: candidate.displayTitle,
            overview = details.string("overview") ?: candidate.overview,
            posterUrl = imageUrl(details.string("poster_path"), "w500") ?: candidate.posterUrl,
            backdropUrl = imageUrl(details.string("backdrop_path"), "w780") ?: candidate.backdropUrl,
            runtimeMinutes = runtime,
            rating = formatRating(details.decimal("vote_average")),
            cast = parseCast(details),
            genres = TmdbGenres.of(candidate.mediaType, detailGenreIds(details)),
            detailsLoaded = true,
        )
    }

    /**
     * The detail responses carry whole genre objects where search carries bare
     * ids. Only the id is taken from either: it is stable and the same in every
     * language, and the name the viewer reads comes from string resources.
     */
    private fun detailGenreIds(details: JsonObject): List<Int> =
        (details["genres"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.integer("id") }

    private fun parseCast(details: JsonObject): List<MetadataCastMember> =
        ((details["credits"] as? JsonObject)?.get("cast") as? JsonArray)
            .orEmpty()
            .asSequence()
            .mapNotNull { item ->
                val person = item as? JsonObject ?: return@mapNotNull null
                MetadataCastMember(
                    name = person.string("name") ?: return@mapNotNull null,
                    character = person.string("character"),
                    profileUrl = imageUrl(person.string("profile_path"), "w185"),
                )
            }
            .take(MAX_CAST_MEMBERS)
            .toList()

    private fun parseMovieReference(value: kotlinx.serialization.json.JsonElement): MetadataMovieReference? {
        val movie = value as? JsonObject ?: return null
        val externalId = movie.integer("id")?.toString() ?: return null
        val title = movie.string("title") ?: return null
        val originalTitle = movie.string("original_title")
        return MetadataMovieReference(
            externalId = externalId,
            title = title,
            alternativeTitles = listOfNotNull(originalTitle).filterNot(title::equals),
            year = movie.string("release_date")?.take(4)?.toIntOrNull(),
            posterUrl = imageUrl(movie.string("poster_path"), "w500"),
        )
    }

    private fun parseSearchResult(
        value: JsonObject,
        requestedType: MetadataMediaType,
    ): MetadataCandidate? {
        val resultType = when (requestedType) {
            MetadataMediaType.MOVIE -> MetadataMediaType.MOVIE
            MetadataMediaType.SERIES, MetadataMediaType.EPISODE -> MetadataMediaType.SERIES
            MetadataMediaType.PROGRAMME -> when (value.string("media_type")) {
                "movie" -> MetadataMediaType.MOVIE
                "tv" -> MetadataMediaType.SERIES
                else -> return null
            }
        }
        val externalId = value.integer("id")?.toString() ?: return null
        val movie = resultType == MetadataMediaType.MOVIE
        val title = value.string(if (movie) "title" else "name") ?: return null
        val original = value.string(if (movie) "original_title" else "original_name")
        val releaseDate = value.string(if (movie) "release_date" else "first_air_date")
        val pathType = if (movie) "movie" else "tv"
        return MetadataCandidate(
            externalId = externalId,
            mediaType = resultType,
            matchingTitle = title,
            alternativeTitles = listOfNotNull(original).filterNot(title::equals),
            displayTitle = title,
            overview = value.string("overview"),
            posterUrl = imageUrl(value.string("poster_path"), "w500"),
            backdropUrl = imageUrl(value.string("backdrop_path"), "w780"),
            year = releaseDate?.take(4)?.toIntOrNull(),
            // Search hands back bare ids where the detail call hands back whole
            // objects. Reading them here means a title has its genres from the
            // moment it is matched, without waiting for anyone to open it.
            genres = TmdbGenres.of(
                resultType,
                (value["genre_ids"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.intOrNull },
            ),
            attributionUrl = "https://www.themoviedb.org/$pathType/$externalId",
            providerPopularity = value.decimal("popularity"),
        )
    }

    private fun request(url: String, credential: String): Request {
        val normalizedCredential = credential.trim()
        val builder = Request.Builder()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
        if (V3_API_KEY.matches(normalizedCredential)) {
            builder.url(
                url.toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", normalizedCredential)
                    .build(),
            )
        } else {
            builder
                .url(url)
                .header("Authorization", "Bearer $normalizedCredential")
        }
        return builder.build()
    }

    private fun imageUrl(path: String?, size: String): String? = path
        ?.takeIf { it.startsWith('/') }
        ?.let { "$imageRoot/$size$it" }
        .httpsUrlOrNull()

    private fun formatRating(value: Double?): String? = value
        ?.takeIf { it > 0.0 }
        ?.let { String.format(Locale.US, "%.1f", it) }

    private companion object {
        const val DEFAULT_API_ROOT = "https://api.themoviedb.org/3"
        const val DEFAULT_IMAGE_ROOT = "https://image.tmdb.org/t/p"
        const val USER_AGENT = "SohvaTV/0.1 (Android TV; personal use)"
        const val MAX_QUERY_LENGTH = 160
        const val MAX_CANDIDATES = 12
        const val MAX_CAST_MEMBERS = 8
        const val MAX_SIMILAR_MOVIES = 20
        val V3_API_KEY = Regex("[A-Fa-f0-9]{32}")
    }
}
