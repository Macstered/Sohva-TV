package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.iptv.metadata.CatalogueMetadataOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueBrowsePresentationTest {
    @Test
    fun persistedReplacementTitleAndPosterWinWhenExplicitlyEnabled() {
        val entry = entry(
            override = CatalogueMetadataOverride(
                contentKey = "movie",
                providerPosterUrl = "provider",
                replacementPosterUrl = "replacement",
                replaceProviderPoster = true,
                replacementTitle = "Clean title",
                externalId = "tmdb:1",
                genresVersion = 1,
            ),
        )

        assertEquals("Clean title", entry.displayTitle())
        assertEquals("replacement", entry.displayPosterUrl())
    }

    @Test
    fun providerPosterRemainsWhenReplacementWasNotChosen() {
        val entry = entry(
            override = CatalogueMetadataOverride(
                contentKey = "movie",
                providerPosterUrl = "provider",
                replacementPosterUrl = "replacement",
                replaceProviderPoster = false,
                replacementTitle = "Clean title",
                externalId = "tmdb:1",
                genresVersion = 1,
            ),
        )

        assertEquals("provider", entry.displayPosterUrl())
    }

    @Test
    fun failedProviderPosterFallsBackToPersistedTmdbPoster() {
        val entry = entry(
            override = CatalogueMetadataOverride(
                contentKey = "movie",
                providerPosterUrl = "provider",
                replacementPosterUrl = "replacement",
                replaceProviderPoster = false,
                replacementTitle = "Clean title",
                externalId = "tmdb:1",
                genresVersion = 1,
            ),
        )

        assertEquals("replacement", entry.displayPosterUrl(providerPosterFailed = true))
    }

    @Test
    fun failedProviderWithoutTmdbArtworkShowsPlaceholderInsteadOfDeadUrl() {
        val entry = entry(
            override = CatalogueMetadataOverride(
                contentKey = "movie",
                providerPosterUrl = "provider",
                replacementPosterUrl = null,
                replaceProviderPoster = false,
                replacementTitle = "Clean title",
                externalId = "tmdb:1",
                genresVersion = 1,
            ),
        )

        assertEquals(null, entry.displayPosterUrl(providerPosterFailed = true))
    }

    private fun entry(override: CatalogueMetadataOverride) = CatalogueBrowseEntry(
        contentKey = "movie",
        target = CatalogueBrowseTarget.Movie("source", "1"),
        providerTitle = "Provider title",
        playlistGroup = "Action",
        providerPosterUrl = "provider",
        year = 2020,
        rating = "8",
        genres = emptySet(),
        metadataOverride = override,
    )
}
