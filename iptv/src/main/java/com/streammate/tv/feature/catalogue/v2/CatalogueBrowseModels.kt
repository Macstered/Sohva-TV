package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.catalogue.CatalogueCopy
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.metadata.CatalogueMetadataOverride

/** A database-addressable portion of one catalogue wall. */
sealed interface CatalogueBrowsePartition {
    /** Movies or series with recorded playback, newest first. */
    data object History : CatalogueBrowsePartition

    /** Null means all playlist groups. */
    data class PlaylistGroup(val name: String?) : CatalogueBrowsePartition

    data class Genre(val genre: CatalogueGenre) : CatalogueBrowsePartition

    /** A saved genre/year/rating filter; its id survives edits and renames. */
    data class CustomGroup(val id: String) : CatalogueBrowsePartition

    data object Unsorted : CatalogueBrowsePartition
}

/** One database-addressable row in the genre rail. */
data class CatalogueBrowseFacet(
    val partition: CatalogueBrowsePartition,
    /** Custom filters are counted only when opened, not by scanning every saved filter. */
    val count: Int?,
    val name: String? = null,
)

data class CatalogueBrowseRequest(
    val mode: CatalogueMode,
    val partition: CatalogueBrowsePartition,
    val search: String = "",
)

sealed interface CatalogueBrowseTarget {
    val sourceId: String

    data class Movie(
        override val sourceId: String,
        val movieId: String,
    ) : CatalogueBrowseTarget

    data class Series(
        override val sourceId: String,
        val seriesId: String,
    ) : CatalogueBrowseTarget
}

/**
 * Small wall item. Detail-only fields and stream URLs intentionally do not
 * cross the browsing boundary.
 */
data class CatalogueBrowseEntry(
    override val contentKey: String,
    val target: CatalogueBrowseTarget,
    val providerTitle: String,
    val playlistGroup: String?,
    val providerPosterUrl: String?,
    override val year: Int?,
    val rating: String?,
    val genres: Set<CatalogueGenre>,
    val metadataOverride: CatalogueMetadataOverride?,
    val copyQualityTags: List<String> = emptyList(),
    val copyCount: Int = 1,
) : CatalogueCopy {
    override val title: String get() = providerTitle
}

data class CatalogueDerivedEntries(
    val entries: List<CatalogueBrowseEntry>,
    val primaryContentKeyByCopy: Map<String, String> = emptyMap(),
)
