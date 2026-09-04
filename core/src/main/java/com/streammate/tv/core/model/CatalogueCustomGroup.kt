package com.streammate.tv.core.model

/**
 * A row of the genre rail that somebody defined for themselves.
 *
 * The vocabulary TMDB supplies is broad and impersonal - a household thinks in
 * terms of "the children's films" or "the Nordic dramas", neither of which is a
 * genre. A group is a name over a handful of genres, narrowed by year or by
 * rating where that is what makes it the thing it is.
 *
 * Deliberately not a query language. Everything here is a plain field a person
 * can set from a television remote, and anything that needed more expression
 * than this would be easier to find by searching for it.
 */
data class CatalogueCustomGroup(
    /** Stable across renames, so a group keeps its place when it is edited. */
    val id: String,
    val name: String,
    val genres: Set<CatalogueGenre> = emptySet(),
    /** Inclusive. Null at either end means unbounded in that direction. */
    val fromYear: Int? = null,
    val toYear: Int? = null,
    /** Out of ten, as TMDB scores. Null keeps everything. */
    val minRating: Double? = null,
) {
    /**
     * A group with nothing in it would collect the whole library, which is
     * never what anyone meant by writing a name over it.
     */
    val isUsable: Boolean
        get() = name.isNotBlank() &&
            (genres.isNotEmpty() || fromYear != null || toYear != null || minRating != null)
}

/**
 * Whether one title belongs in [group].
 *
 * Every condition set has to hold: a group of children's films from the
 * eighties is both, not either. A condition left unset is not a condition.
 *
 * A title with no genres never matches a group that names one. It sits in the
 * unsorted row until the metadata pass reaches it, or until somebody chooses a
 * match for it by hand - putting it in a group on the strength of its year
 * alone would be a guess wearing a label.
 */
fun catalogueGroupMatches(
    group: CatalogueCustomGroup,
    genres: Set<CatalogueGenre>,
    year: Int?,
    rating: Double?,
): Boolean {
    if (!group.isUsable) return false
    if (group.genres.isNotEmpty() && group.genres.none { it in genres }) return false
    group.fromYear?.let { if (year == null || year < it) return false }
    group.toYear?.let { if (year == null || year > it) return false }
    group.minRating?.let { if (rating == null || rating < it) return false }
    return true
}
