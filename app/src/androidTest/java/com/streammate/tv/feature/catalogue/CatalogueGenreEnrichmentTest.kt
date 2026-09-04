package com.streammate.tv.feature.catalogue

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.EnrichedMetadata
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settling what a title is has to leave the library able to group it.
 *
 * The metadata pass is the only thing that does this today, and a match chosen
 * by hand will do it later; both go through the same call, so the genres are
 * written there rather than by either caller.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueGenreEnrichmentTest {

    private lateinit var database: StreamMateDatabase
    private lateinit var repository: MetadataRepository

    @Before
    fun createRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        repository = MetadataRepository(
            dao = database.metadataDao(),
            settingsStore = SecretSettingsStore(context, TestCipher),
            httpClient = OkHttpClient(),
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun rememberingWhatATitleIsAlsoSortsIt() = runBlocking {
        repository.rememberCatalogueMetadataOverride(
            contentKey = "vod:movie:source:1",
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.ACTION, CatalogueGenre.SCIENCE_FICTION),
        )

        assertEquals(
            listOf("action"),
            database.metadataDao().genres("vod:movie:source:1"),
        )
    }

    /**
     * The stamp is what stops the metadata pass looking at this title again,
     * so it has to be written at the same time as the genres themselves.
     */
    @Test
    fun aSortedTitleCarriesTheVocabularyItWasSortedUnder() = runBlocking {
        repository.rememberCatalogueMetadataOverride(
            contentKey = "vod:movie:source:1",
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.DRAMA),
        )

        val override = repository.observeCatalogueMetadataOverrides().first()["vod:movie:source:1"]
        assertEquals(CatalogueGenre.VERSION, override?.genresVersion)
    }

    @Test
    fun matchingATitleToSomethingElseDropsTheGenresOfWhatItWas() = runBlocking {
        val contentKey = "vod:movie:source:1"
        repository.rememberCatalogueMetadataOverride(
            contentKey = contentKey,
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.HORROR),
        )

        repository.rememberCatalogueMetadataOverride(
            contentKey = contentKey,
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.COMEDY, CatalogueGenre.ROMANCE),
        )

        assertEquals(
            listOf("comedy"),
            database.metadataDao().genres(contentKey),
        )
    }

    /**
     * A title TMDB matched but gave no genres for is still finished. It sorts
     * nowhere and belongs in the uncategorised bucket, and the pass must not
     * keep coming back to it.
     */
    @Test
    fun aMatchWithNoGenresIsStillSettled() = runBlocking {
        repository.rememberCatalogueMetadataOverride(
            contentKey = "vod:movie:source:1",
            providerPosterUrl = null,
            metadata = metadata(),
        )

        assertEquals(emptyList<String>(), database.metadataDao().genres("vod:movie:source:1"))
        val override = repository.observeCatalogueMetadataOverrides().first()["vod:movie:source:1"]
        assertEquals(CatalogueGenre.VERSION, override?.genresVersion)
    }

    // Nothing here reaches the network, so the store only has to be openable.
    private object TestCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "test:$plainText"
        override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
    }

    /**
     * Which record a title is, is what lets two copies of one film from two
     * playlists be recognised as the same film.
     */
    @Test
    fun rememberingWhatATitleIsRecordsWhichRecordItIs() = runBlocking {
        repository.rememberCatalogueMetadataOverride(
            contentKey = "vod:movie:source:1",
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.ACTION),
        )

        val override = repository.observeCatalogueMetadataOverrides().first()["vod:movie:source:1"]
        assertEquals("603", override?.externalId)
    }

    @Test
    fun detailsBackfillOnlyRepairsAMissingCataloguePoster() = runBlocking {
        val contentKey = "vod:movie:source:1"
        repository.rememberCatalogueMetadataOverride(
            contentKey = contentKey,
            providerPosterUrl = null,
            metadata = metadata(CatalogueGenre.ACTION),
        )

        repository.backfillMissingCataloguePoster(
            contentKey = contentKey,
            providerPosterUrl = null,
            metadata = metadata(
                CatalogueGenre.ACTION,
                posterUrl = "https://image.tmdb.org/first.jpg",
            ),
        )
        // A later details visit must not churn the completed row or silently
        // replace artwork that has already been settled.
        repository.backfillMissingCataloguePoster(
            contentKey = contentKey,
            providerPosterUrl = null,
            metadata = metadata(
                CatalogueGenre.DRAMA,
                posterUrl = "https://image.tmdb.org/second.jpg",
                title = "A different record",
            ),
        )

        val override = database.metadataDao().catalogueMetadataOverride(contentKey)
        assertEquals("https://image.tmdb.org/first.jpg", override?.replacementPosterUrl)
        assertEquals(true, override?.replaceProviderPoster)
        assertEquals("The Matrix", override?.replacementTitle)
        assertEquals(listOf("action"), database.metadataDao().genres(contentKey))
    }

    private fun metadata(
        vararg genres: CatalogueGenre,
        posterUrl: String? = null,
        title: String = "The Matrix",
    ) = EnrichedMetadata(
        provider = "tmdb",
        externalId = "603",
        mediaType = MetadataMediaType.MOVIE,
        title = title,
        overview = null,
        posterUrl = posterUrl,
        backdropUrl = null,
        year = 1999,
        seasonNumber = null,
        episodeNumber = null,
        genres = genres.toList(),
        attributionName = "TMDB",
        attributionUrl = "https://www.themoviedb.org/movie/603",
        confidence = 0.99,
    )
}
