package com.sohva.tv.core.model.vod

/**
 * A group of the viewer's own (spec 40 VOD-FR-11, spec 42 ORG-32): genres, a year range and a
 * minimum rating. [id] is stable across renames. A title belongs when every set condition holds;
 * a title without a year fails a year bound, one without a rating fails the minimum, and one
 * without a genre never matches a group that names genres.
 */
data class CustomGroup(
    val id: String,
    val name: String,
    val genres: Set<Genre>,
    val fromYear: Int?,
    val toYear: Int?,
    val minRating: Double?,
) {
    /** A name over nothing would quietly collect the whole library. */
    val isUsable: Boolean
        get() = name.isNotBlank() && (genres.isNotEmpty() || fromYear != null || toYear != null || minRating != null)

    fun matches(genre: Genre?, year: Int?, rating: Double?): Boolean {
        if (!isUsable) return false
        if (genres.isNotEmpty() && (genre == null || genre !in genres)) return false
        if (fromYear != null && (year == null || year < fromYear)) return false
        if (toYear != null && (year == null || year > toYear)) return false
        if (minRating != null && (rating == null || rating < minRating)) return false
        return true
    }

    /**
     * The minimum as the stored `rating_x10` (the provider's rating in tenths, rounded), for the
     * wall's index walk; a hundredth below the minimum can round into it.
     */
    val minRatingTenths: Int? get() = minRating?.let { kotlin.math.ceil(it * 10 - 1e-9).toInt() }

    companion object {
        const val MAX: Int = 24
    }
}
