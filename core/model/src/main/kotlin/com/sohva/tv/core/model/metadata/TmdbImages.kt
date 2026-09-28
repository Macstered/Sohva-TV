package com.sohva.tv.core.model.metadata

/**
 * Artwork addresses from what metadata stores (spec 41 §9.5): a TMDB image path (`/abc.jpg`) is
 * sized per use; a full https address (TVmaze) is used as it is. Shared by the screens and the
 * metadata clients, so each screen asks for the smallest image that covers what it draws.
 */
object TmdbImages {
    const val POSTER_SMALL: String = "w185"
    const val POSTER_WALL: String = "w342"

    /** The smallest TMDB backdrop at least 356 px wide: a Continue watching card at xhdpi (spec 02 §9). */
    const val CARD_BACKDROP: String = "w780"

    fun url(image: String?, size: String): String? = when {
        image.isNullOrBlank() -> null
        image.startsWith("/") -> "https://image.tmdb.org/t/p/$size$image"
        else -> image
    }

    /** A wall poster drawn [widthPx] wide: `w185` covers 720p boxes, `w342` everything larger. */
    fun wallPoster(image: String?, widthPx: Int): String? = url(image, if (widthPx <= 185) POSTER_SMALL else POSTER_WALL)
}
