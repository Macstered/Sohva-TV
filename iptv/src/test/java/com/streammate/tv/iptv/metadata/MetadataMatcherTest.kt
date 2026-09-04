package com.streammate.tv.iptv.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMatcherTest {
    @Test
    fun exactTitleAndYearWinsWhenMarginIsClear() {
        val match = MetadataMatcher.choose(
            MetadataLookup(MetadataMediaType.MOVIE, "Dune (2021)", year = 2021),
            listOf(
                candidate("1", "Dune", year = 2021),
                candidate("2", "Dune Warriors", year = 1991),
            ),
        )

        assertEquals("1", match?.candidate?.externalId)
        assertTrue(requireNotNull(match).confidence >= MetadataMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun identicalTopResultsAreRejectedAsAmbiguous() {
        val match = MetadataMatcher.choose(
            MetadataLookup(MetadataMediaType.SERIES, "Top Gear"),
            listOf(
                candidate("1", "Top Gear", type = MetadataMediaType.SERIES),
                candidate("2", "Top Gear", type = MetadataMediaType.SERIES),
            ),
        )

        assertNull(match)
    }

    @Test
    fun tmdbPopularityResolvesExactTitleTie() {
        val match = MetadataMatcher.choose(
            MetadataLookup(MetadataMediaType.MOVIE, "Avatar"),
            listOf(
                candidate("old", "Avatar", year = 1916, popularity = 2.0),
                candidate("canonical", "Avatar", year = 2009, popularity = 84.0),
            ),
        )

        assertEquals("canonical", match?.candidate?.externalId)
    }

    @Test
    fun popularityDoesNotOverrideAmbiguousNearTie() {
        val match = MetadataMatcher.choose(
            MetadataLookup(MetadataMediaType.SERIES, "Foundation"),
            listOf(
                candidate("1", "Foundation", type = MetadataMediaType.SERIES, popularity = 20.0),
                candidate("2", "Foundation", type = MetadataMediaType.SERIES, popularity = 18.0),
            ),
        )

        assertNull(match)
    }

    @Test
    fun weakFuzzyTitleIsRejected() {
        val match = MetadataMatcher.choose(
            MetadataLookup(MetadataMediaType.MOVIE, "The Matrix"),
            listOf(candidate("1", "Matrix Revolutions")),
        )

        assertNull(match)
    }

    @Test
    fun episodeRequiresExactSeasonAndEpisode() {
        val match = MetadataMatcher.choose(
            MetadataLookup(
                MetadataMediaType.EPISODE,
                "The Bear",
                seasonNumber = 2,
                episodeNumber = 6,
            ),
            listOf(
                candidate(
                    id = "1",
                    title = "The Bear",
                    type = MetadataMediaType.EPISODE,
                    season = 2,
                    episode = 7,
                ),
            ),
        )

        assertNull(match)
    }

    @Test
    fun searchTitleRemovesProviderSuffixesButKeepsWords() {
        assertEquals("The Bear", MetadataMatcher.searchTitle("The Bear (2022) S02E06"))
        assertEquals("the bear", MetadataMatcher.normalizeTitle("The Bear (2022) S02E06"))
        assertEquals(2022, MetadataMatcher.yearFromTitle("The Bear (2022)"))
    }

    @Test
    fun searchTitleRemovesBracketedProviderQualityTags() {
        val providerTitle = "The Shadow's Edge [Multi-Sub&Audio] [4K] [2025]"

        assertEquals("The Shadow's Edge", MetadataMatcher.searchTitle(providerTitle))
        assertEquals("the shadow s edge", MetadataMatcher.normalizeTitle(providerTitle))
        assertEquals(2025, MetadataMatcher.yearFromTitle(providerTitle))
    }

    @Test
    fun searchTitleRemovesProviderLanguageAndTrailingQuality() {
        assertEquals("Foundation", MetadataMatcher.searchTitle("[FIN] Foundation - 4K"))
        assertEquals("avatar", MetadataMatcher.normalizeTitle("ENG | Avatar UHD"))
        assertEquals(
            "21 Bridges",
            MetadataMatcher.searchTitle("21 Bridges [Multi-Subs] [2019] [4K]"),
        )
        assertEquals(
            "The Shawshank Redemption",
            MetadataMatcher.searchTitle("The Shawshank Redemption [IMDB] [1994]"),
        )
        assertEquals(
            "Finding Nemo",
            MetadataMatcher.searchTitle("Finding Nemo [KIDS] (Animated) {Multi Audio} [2003]"),
        )
        assertEquals("REC", MetadataMatcher.searchTitle("[REC]"))
    }

    @Test
    fun searchTitleRemovesLeadingProviderTagsAndBareTrailingYear() {
        assertEquals(
            "Avatar: The Last Airbender",
            MetadataMatcher.searchTitle("4K - NC Avatar: The Last Airbender (2024)"),
        )
        assertEquals("DuckTales", MetadataMatcher.searchTitle("NC - DuckTales 2017"))
        assertEquals("The Bear", MetadataMatcher.searchTitle("NORDIC | The Bear 2022"))
        assertEquals("Dune", MetadataMatcher.searchTitle("HDR10+ - Dune 2021"))
        assertEquals(2017, MetadataMatcher.yearFromTitle("NC - DuckTales 2017"))
        assertEquals(2024, MetadataMatcher.yearFromTitle("4K - NC Avatar (2024)"))
        assertEquals("1917", MetadataMatcher.searchTitle("1917"))
    }

    private fun candidate(
        id: String,
        title: String,
        type: MetadataMediaType = MetadataMediaType.MOVIE,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        popularity: Double? = null,
    ) = MetadataCandidate(
        externalId = id,
        mediaType = type,
        matchingTitle = title,
        displayTitle = title,
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        year = year,
        seasonNumber = season,
        episodeNumber = episode,
        attributionUrl = "https://example.com/$id",
        providerPopularity = popularity,
    )
}
