package com.sohva.tv.core.net.metadata

import com.sohva.tv.core.model.metadata.Candidate
import com.sohva.tv.core.model.metadata.MediaType

/** The two metadata providers, consulted in this order (spec 41 META-FR-13). */
enum class MetadataProvider(val id: String, val displayName: String, val home: String) {
    TMDB("tmdb", "TMDB", "https://www.themoviedb.org"),
    TVMAZE("tvmaze", "TVmaze", "https://www.tvmaze.com"),
    ;

    fun supports(type: MediaType): Boolean = this == TMDB || type != MediaType.MOVIE

    companion object {
        fun of(id: String?): MetadataProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Artwork as the provider gives it: a TMDB image path (`/abc.jpg`), sized per use with [url], or a
 * full https address (TVmaze). Paths are stored rather than sized addresses so each screen asks
 * for the smallest image that covers what it draws (spec 41 §9.5).
 */
object Artwork {
    const val POSTER_WALL: String = "w342"
    const val POSTER_SMALL: String = "w185"
    const val THUMB: String = "w92"
    const val STILL: String = "w300"
    const val BACKDROP: String = "w780"
    const val PROFILE: String = "w185"

    fun url(image: String?, size: String): String? = when {
        image.isNullOrBlank() -> null
        image.startsWith("/") -> "https://image.tmdb.org/t/p/$size$image"
        else -> image
    }

    /** Only https artwork, at most 2,048 characters (META-FR-38). */
    internal fun httpsOrNull(url: String?): String? =
        url?.trim()?.takeIf { it.length <= 2_048 && it.regionMatches(0, "https://", 0, 8, ignoreCase = true) }

    /** A TMDB path as given (`/…`), else null. */
    internal fun tmdbPath(path: String?): String? = path?.trim()?.takeIf { it.startsWith("/") && it.length <= 2_048 }
}

/** A cast member of a details record (META-FR-37): at most 8 per title. */
data class CastMember(val name: String, val character: String?, val profile: String?)

/** A similar film TMDB names (META-FR-37): at most 20, the film itself excluded. */
data class SimilarRef(val externalId: String, val title: String, val alternativeTitles: List<String>, val year: Int?, val poster: String?)

/**
 * What a provider knows about one title: search results carry the first half; details add
 * runtime, rating, cast and similar titles. Text fields are trimmed and cut to 8,000 characters.
 */
data class MetadataRecord(
    val provider: MetadataProvider,
    val externalId: String,
    val type: MediaType,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val overview: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val runtimeMinutes: Int? = null,
    /** TMDB `vote_average` with one decimal ("8.2"), only above 0 (META-FR-40). */
    val rating: String? = null,
    val genreIds: List<Int> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val similar: List<SimilarRef> = emptyList(),
    val popularity: Double? = null,
    val attributionUrl: String? = null,
    val detailsLoaded: Boolean = false,
) {
    fun candidate(): Candidate = Candidate(externalId, type, title, alternativeTitles, year, season, episode, popularity)
}
