package com.streammate.tv.feature.catalogue.v2

import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.metadata.CatalogueMetadataOverride
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogueBrowseDeriverTest {
    @Test
    fun movieCopiesFoldOffThePersistedExternalIdAndPreferenceChoosesPrimary() = runTest {
        val first = movie(
            key = "first",
            title = "FIN | The Matrix (1999) 4K",
            poster = null,
        )
        val second = movie(
            key = "second",
            title = "The Matrix 1999 [MULTI-SUBS] 1080p HDR10",
            poster = "poster",
        )
        val deriver = CatalogueBrowseDeriver(
            preferredCopy = CataloguePreferredCopy.FINNISH_SUBTITLES,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = deriver.derive(
            CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.PlaylistGroup("Movies")),
            listOf(first, second),
        )

        val film = result.entries.single()
        assertEquals("second", film.contentKey)
        assertEquals("poster", film.providerPosterUrl)
        assertEquals(2, film.copyCount)
        assertEquals("second", result.primaryContentKeyByCopy["first"])
        assertEquals(listOf("HDR10", "4K UHD"), film.copyQualityTags)
    }

    @Test
    fun seriesRemainIndividualEntries() = runTest {
        val entries = listOf(
            CatalogueBrowseEntry(
                contentKey = "series:a:1",
                target = CatalogueBrowseTarget.Series("a", "1"),
                providerTitle = "Series",
                playlistGroup = "Shows",
                providerPosterUrl = null,
                year = 2020,
                rating = null,
                genres = emptySet(),
                metadataOverride = null,
            ),
        )
        val deriver = CatalogueBrowseDeriver(
            preferredCopy = CataloguePreferredCopy.NONE,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = deriver.derive(
            CatalogueBrowseRequest(CatalogueMode.SERIES, CatalogueBrowsePartition.PlaylistGroup("Shows")),
            entries,
        )

        assertSame(entries, result.entries)
        assertEquals(emptyMap<String, String>(), result.primaryContentKeyByCopy)
    }

    private fun movie(key: String, title: String, poster: String?) = CatalogueBrowseEntry(
        contentKey = key,
        target = CatalogueBrowseTarget.Movie("source-$key", key),
        providerTitle = title,
        playlistGroup = "Movies",
        providerPosterUrl = poster,
        year = 1999,
        rating = null,
        genres = emptySet(),
        metadataOverride = CatalogueMetadataOverride(
            contentKey = key,
            providerPosterUrl = poster,
            replacementPosterUrl = null,
            replaceProviderPoster = false,
            replacementTitle = "The Matrix",
            externalId = "tmdb:603",
            genresVersion = 1,
        ),
    )
}
