package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.model.CatalogueGenre

/**
 * TMDB's genre ids, folded into the one list the library is grouped by.
 *
 * TMDB keeps two lists and they overlap without matching. Film has *Action*,
 * *Adventure*, *Science Fiction* and *Fantasy* as four separate genres;
 * television has *Action & Adventure* and *Sci-Fi & Fantasy* as two combined
 * ones, and adds *Kids*, *News*, *Reality*, *Soap* and *Talk* which film has no
 * equivalent for. Grouping a mixed library by the provider's own ids would put
 * two rows a viewer reads as the same thing side by side in the rail.
 *
 * A title belongs to one rail only. The first recognised genre in TMDB's
 * response is treated as the primary genre. For a combined television id, the
 * first half of the viewer-facing pair is the primary one.
 *
 * The ids are TMDB's own and are stable; the names beside them are only here so
 * that the table can be read. What the viewer sees comes from string resources,
 * not from these, so it arrives translated and without a request.
 */
internal object TmdbGenres {

    fun of(mediaType: MetadataMediaType, ids: List<Int>): List<CatalogueGenre> {
        val table = when (mediaType) {
            MetadataMediaType.MOVIE -> MOVIE
            MetadataMediaType.SERIES, MetadataMediaType.EPISODE -> TELEVISION
            // A multi search can return either, and the result carries its own
            // media type by then; look in both rather than guess.
            MetadataMediaType.PROGRAMME -> MOVIE + TELEVISION
        }
        return ids.asSequence()
            .flatMap { id -> table[id].orEmpty() }
            .firstOrNull()
            ?.let(::listOf)
            .orEmpty()
    }

    private val MOVIE: Map<Int, List<CatalogueGenre>> = mapOf(
        28 to listOf(CatalogueGenre.ACTION),
        12 to listOf(CatalogueGenre.ADVENTURE),
        16 to listOf(CatalogueGenre.ANIMATION),
        35 to listOf(CatalogueGenre.COMEDY),
        80 to listOf(CatalogueGenre.CRIME),
        99 to listOf(CatalogueGenre.DOCUMENTARY),
        18 to listOf(CatalogueGenre.DRAMA),
        10751 to listOf(CatalogueGenre.FAMILY),
        14 to listOf(CatalogueGenre.FANTASY),
        36 to listOf(CatalogueGenre.HISTORY),
        27 to listOf(CatalogueGenre.HORROR),
        10402 to listOf(CatalogueGenre.MUSIC),
        9648 to listOf(CatalogueGenre.MYSTERY),
        10749 to listOf(CatalogueGenre.ROMANCE),
        878 to listOf(CatalogueGenre.SCIENCE_FICTION),
        53 to listOf(CatalogueGenre.THRILLER),
        10752 to listOf(CatalogueGenre.WAR),
        37 to listOf(CatalogueGenre.WESTERN),
        // 10770 "TV Movie" is deliberately absent. It says where something was
        // first shown rather than what it is, and a row of it in the rail would
        // collect an assortment with nothing in common.
    )

    private val TELEVISION: Map<Int, List<CatalogueGenre>> = mapOf(
        10759 to listOf(CatalogueGenre.ACTION, CatalogueGenre.ADVENTURE),
        16 to listOf(CatalogueGenre.ANIMATION),
        35 to listOf(CatalogueGenre.COMEDY),
        80 to listOf(CatalogueGenre.CRIME),
        99 to listOf(CatalogueGenre.DOCUMENTARY),
        18 to listOf(CatalogueGenre.DRAMA),
        10751 to listOf(CatalogueGenre.FAMILY),
        // Kids and Family are one row to a viewer, and a library with both would
        // split the children's films across two.
        10762 to listOf(CatalogueGenre.FAMILY),
        9648 to listOf(CatalogueGenre.MYSTERY),
        10763 to listOf(CatalogueGenre.NEWS),
        10764 to listOf(CatalogueGenre.REALITY),
        10765 to listOf(CatalogueGenre.SCIENCE_FICTION, CatalogueGenre.FANTASY),
        10766 to listOf(CatalogueGenre.SOAP),
        10767 to listOf(CatalogueGenre.TALK),
        10768 to listOf(CatalogueGenre.WAR),
        37 to listOf(CatalogueGenre.WESTERN),
    )

    private operator fun Map<Int, List<CatalogueGenre>>.plus(
        other: Map<Int, List<CatalogueGenre>>,
    ): Map<Int, List<CatalogueGenre>> = buildMap {
        putAll(this@plus)
        other.forEach { (id, genres) ->
            put(id, (this[id].orEmpty() + genres).distinct())
        }
    }
}
