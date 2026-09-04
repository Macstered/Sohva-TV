package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.security.MetadataSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class TvmazeMetadataProvider(
    private val httpClient: OkHttpClient,
    private val apiRoot: String = DEFAULT_API_ROOT,
) : MetadataProvider {
    override val id: String = "tvmaze"
    override val attributionName: String = "TVmaze"
    override val positiveCacheMillis: Long = 24L * 60 * 60 * 1_000

    override fun enabled(settings: MetadataSettings): Boolean = settings.tvmazeEnabled

    override fun supports(mediaType: MetadataMediaType): Boolean = mediaType != MetadataMediaType.MOVIE

    override suspend fun search(
        lookup: MetadataLookup,
        settings: MetadataSettings,
    ): List<MetadataCandidate> {
        val url = "$apiRoot/search/shows".toHttpUrl().newBuilder()
            .addQueryParameter("q", lookup.title.take(MAX_QUERY_LENGTH))
            .build()
        val results = httpClient.getMetadataJson(
            request = request(url.toString()),
            providerName = attributionName,
            retryRateLimit = true,
        ).jsonArray
        val shows = results.asSequence()
            .mapNotNull { result ->
                parseShow(result.jsonObject["show"] as? JsonObject ?: return@mapNotNull null)
            }
            .take(MAX_CANDIDATES)
            .toList()
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
            val detailsUrl = "$apiRoot/shows/${show.externalId}/episodebynumber"
                .toHttpUrl().newBuilder()
                .addQueryParameter("season", season.toString())
                .addQueryParameter("number", episode.toString())
                .build()
            val details = httpClient.getMetadataJson(
                request = request(detailsUrl.toString()),
                providerName = attributionName,
                retryRateLimit = true,
            ).jsonObject
            val image = details["image"] as? JsonObject
            MetadataCandidate(
                externalId = details.integer("id")?.toString()
                    ?: "${show.externalId}:$season:$episode",
                mediaType = MetadataMediaType.EPISODE,
                matchingTitle = show.matchingTitle,
                displayTitle = details.string("name") ?: show.displayTitle,
                overview = stripHtml(details.string("summary")) ?: show.overview,
                posterUrl = show.posterUrl,
                backdropUrl = image?.string("original")?.httpsUrlOrNull() ?: show.backdropUrl,
                year = show.year,
                seasonNumber = details.integer("season") ?: season,
                episodeNumber = details.integer("number") ?: episode,
                attributionUrl = details.string("url")?.httpsUrlOrNull() ?: show.attributionUrl,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return listOfNotNull(candidate)
    }

    private fun parseShow(value: JsonObject): MetadataCandidate? {
        val id = value.integer("id")?.toString() ?: return null
        val title = value.string("name") ?: return null
        val image = value["image"] as? JsonObject
        return MetadataCandidate(
            externalId = id,
            mediaType = MetadataMediaType.SERIES,
            matchingTitle = title,
            displayTitle = title,
            overview = stripHtml(value.string("summary")),
            posterUrl = image?.string("medium")?.httpsUrlOrNull(),
            backdropUrl = image?.string("original")?.httpsUrlOrNull(),
            year = value.string("premiered")?.take(4)?.toIntOrNull(),
            attributionUrl = value.string("url")?.httpsUrlOrNull() ?: "https://www.tvmaze.com/shows/$id",
        )
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", USER_AGENT)
        .build()

    private fun stripHtml(value: String?): String? = value
        ?.replace(HTML_TAG, " ")
        ?.replace("&nbsp;", " ")
        ?.replace("&amp;", "&")
        ?.replace("&quot;", "\"")
        ?.replace("&#39;", "'")
        ?.replace(MULTIPLE_SPACES, " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private companion object {
        const val DEFAULT_API_ROOT = "https://api.tvmaze.com"
        const val USER_AGENT = "SohvaTV/0.1 (Android TV; personal use)"
        const val MAX_QUERY_LENGTH = 160
        const val MAX_CANDIDATES = 12
        val HTML_TAG = Regex("<[^>]+>")
        val MULTIPLE_SPACES = Regex("\\s+")
    }
}
