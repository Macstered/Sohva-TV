package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.security.MetadataSettings

enum class MetadataMediaType(val wireValue: String) {
    MOVIE("movie"),
    SERIES("series"),
    EPISODE("episode"),
    PROGRAMME("programme"),
}

data class MetadataLookup(
    val mediaType: MetadataMediaType,
    val title: String,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val language: String = "fi-FI",
)

data class MetadataCastMember(
    val name: String,
    val character: String?,
    val profileUrl: String?,
)

data class MetadataMovieReference(
    val externalId: String,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val year: Int?,
    val posterUrl: String?,
)

data class EnrichedMetadata(
    val provider: String,
    val externalId: String,
    val mediaType: MetadataMediaType,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val runtimeMinutes: Int? = null,
    val rating: String? = null,
    val cast: List<MetadataCastMember> = emptyList(),
    /** What this is, in the library's own vocabulary. Empty until enriched. */
    val genres: List<CatalogueGenre> = emptyList(),
    val detailsLoaded: Boolean = false,
    val similarMovies: List<MetadataMovieReference> = emptyList(),
    val attributionName: String,
    val attributionUrl: String,
    val confidence: Double,
)

data class CatalogueMetadataOverride(
    val contentKey: String,
    val providerPosterUrl: String?,
    val replacementPosterUrl: String?,
    val replaceProviderPoster: Boolean,
    val replacementTitle: String,
    /** The TMDB record this title turned out to be, where anything matched. */
    val externalId: String? = null,
    /** Which genre vocabulary this title was last sorted under. */
    val genresVersion: Int = 0,
)

/** A compact active-catalogue row used only to synchronize durable metadata work. */
data class CatalogueMetadataCandidate(
    val contentKey: String,
    val mediaType: MetadataMediaType,
    val title: String,
    val year: Int?,
    val providerPosterUrl: String?,
    val genresVersion: Int,
)

/** One bounded unit read from the durable metadata queue. */
data class CatalogueMetadataWork(
    val contentKey: String,
    val mediaType: MetadataMediaType,
    val title: String,
    val year: Int?,
    val providerPosterUrl: String?,
    val targetGenresVersion: Int,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
)

/** A background lookup either settled a title or should be tried again later. */
sealed interface CatalogueEnrichmentResult {
    data class Matched(val metadata: EnrichedMetadata) : CatalogueEnrichmentResult
    data object NoMatch : CatalogueEnrichmentResult
    data object Retry : CatalogueEnrichmentResult
}

data class CatalogueEnrichmentUpdate(
    val work: CatalogueMetadataWork,
    val result: CatalogueEnrichmentResult,
)

/**
 * One row of the list offered when a match is being chosen by hand.
 *
 * Carries the three things that tell two films of the same name apart: the
 * year, the artwork, and what it is about.
 */
data class MetadataSearchResult(
    val externalId: String,
    val mediaType: MetadataMediaType,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
)

internal data class MetadataCandidate(
    val externalId: String,
    val mediaType: MetadataMediaType,
    val matchingTitle: String,
    val alternativeTitles: List<String> = emptyList(),
    val displayTitle: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val runtimeMinutes: Int? = null,
    val rating: String? = null,
    val cast: List<MetadataCastMember> = emptyList(),
    val genres: List<CatalogueGenre> = emptyList(),
    val detailsLoaded: Boolean = false,
    val similarMovies: List<MetadataMovieReference> = emptyList(),
    val attributionUrl: String,
    val providerPopularity: Double? = null,
)

internal interface MetadataProvider {
    val id: String
    val attributionName: String
    val positiveCacheMillis: Long

    fun enabled(settings: MetadataSettings): Boolean

    fun supports(mediaType: MetadataMediaType): Boolean

    suspend fun search(
        lookup: MetadataLookup,
        settings: MetadataSettings,
    ): List<MetadataCandidate>
}
