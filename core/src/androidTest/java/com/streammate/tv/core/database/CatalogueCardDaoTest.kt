package com.streammate.tv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueCardDaoTest {
    private lateinit var database: StreamMateDatabase
    private lateinit var dao: CatalogueDao

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        dao = database.catalogueDao()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity(
                sourceId = "source",
                name = "Playlist",
                type = "xtream",
                enabled = true,
                connectionLimit = 1,
                priority = 0,
                updatedAtEpochMillis = 1,
            ),
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun categoryWallUsesNormalizedKeyAndFetchesDetailsOnlyOnDemand() = runBlocking {
        dao.upsertMovies(
            listOf(
                movie("1", "Arrival", "  Sci-Fi  ", "large detail payload"),
                movie("2", "Heat", "Crime", "another detail payload"),
            ),
        )
        dao.activateCatalogueSnapshot("source", "snapshot", 2, 1)

        val card = dao.observeMovieCardsByCategory("sci-fi").first().single()
        assertEquals("Arrival", card.name)
        assertEquals("  Sci-Fi  ", card.categoryName)
        assertEquals("large detail payload", dao.activeMovie("source", "1")?.plot)
    }

    @Test
    fun categoryCountsGroupCaseAndWhitespaceVariantsByIndexedKey() = runBlocking {
        dao.upsertMovies(
            listOf(
                movie("1", "Arrival", " Sci-Fi ", null),
                movie("2", "Dune", "SCI-FI", null),
            ),
        )
        dao.activateCatalogueSnapshot("source", "snapshot", 2, 1)

        val categories = dao.observeMovieCategories().first()
        assertEquals(1, categories.size)
        assertEquals(2, categories.single().itemCount)
    }

    @Test
    fun genreWallReturnsOnlyMatchingCardsWithSelectedGenreAndEmbeddedMetadata() = runBlocking {
        dao.upsertMovies(
            listOf(
                movie("1", "Arrival", "Sci-Fi", null),
                movie("2", "Heat", "Crime", null),
            ),
        )
        dao.activateCatalogueSnapshot("source", "snapshot", 2, 1)

        val metadataDao = database.metadataDao()
        val arrivalKey = "vod:movie:source:1"
        metadataDao.replaceGenres(
            arrivalKey,
            listOf(
                CatalogueGenreEntity(arrivalKey, "science_fiction"),
                CatalogueGenreEntity(arrivalKey, "drama"),
            ),
        )
        metadataDao.replaceGenres(
            "vod:movie:source:2",
            listOf(CatalogueGenreEntity("vod:movie:source:2", "crime")),
        )
        metadataDao.upsertCatalogueMetadataOverride(
            CatalogueMetadataOverrideEntity(
                contentKey = arrivalKey,
                providerPosterUrl = "https://example.com/1.jpg",
                replacementPosterUrl = "https://image.example/arrival.jpg",
                replaceProviderPoster = true,
                replacementTitle = "Arrival (2016)",
                externalId = "tmdb:329865",
                genresVersion = 3,
                updatedAtEpochMillis = 2,
            ),
        )

        val counts = dao.observeMovieGenreCounts().first().associate { it.genre to it.itemCount }
        assertEquals(1, counts["science_fiction"])
        // Only the first recognised genre is persisted, even for old multi-genre input.
        assertEquals(null, counts["drama"])
        assertEquals(emptyList<VodMovieCardRow>(), dao.observeMovieCardsInGenre("drama").first())
        assertEquals(1, counts["crime"])

        val card = dao.observeMovieCardsInGenre("science_fiction").first().single()
        assertEquals("1", card.movieId)
        assertEquals("science_fiction", card.genresCsv)
        assertEquals("https://image.example/arrival.jpg", card.replacementPosterUrl)
        assertEquals(true, card.replaceProviderPoster)
        assertEquals("Arrival (2016)", card.replacementTitle)
        assertEquals("tmdb:329865", card.externalId)
        assertEquals(3, card.metadataGenresVersion)
    }

    @Test
    fun searchProjectionStaysInItsCategoryAndMatchesLocalizedReplacementTitle() = runBlocking {
        dao.upsertMovies(
            listOf(
                movie("1", "Arrival", "Sci-Fi", null),
                movie("2", "Heat", "Crime", null),
            ),
        )
        dao.activateCatalogueSnapshot("source", "snapshot", 2, 1)
        database.metadataDao().upsertCatalogueMetadataOverride(
            CatalogueMetadataOverrideEntity(
                contentKey = "vod:movie:source:2",
                providerPosterUrl = "https://example.com/2.jpg",
                replacementPosterUrl = null,
                replaceProviderPoster = false,
                replacementTitle = "Lämpö",
                externalId = "tmdb:949",
                genresVersion = 3,
                updatedAtEpochMillis = 2,
            ),
        )

        assertEquals(
            listOf("Arrival"),
            dao.observeMovieCardsByCategoryMatching("sci-fi", "rriv").first().map { it.name },
        )
        assertEquals(
            emptyList<VodMovieCardRow>(),
            dao.observeMovieCardsByCategoryMatching("sci-fi", "Heat").first(),
        )
        assertEquals(
            listOf("Heat"),
            dao.observeMovieCardsMatching("Lämpö").first().map { it.name },
        )
    }

    @Test
    fun generationTracksEnabledProvidersAndTheirActiveSnapshots() = runBlocking {
        dao.activateCatalogueSnapshot("source", "snapshot-1", 12, 100)

        val first = dao.observeCatalogueGenerations().first().single()
        assertEquals("source", first.sourceId)
        assertEquals("snapshot-1", first.activeSnapshotId)
        assertEquals(100, first.updatedAtEpochMillis)
        assertEquals(12, first.itemCount)

        dao.activateCatalogueSnapshot("source", "snapshot-2", 15, 200)
        val second = dao.observeCatalogueGenerations().first().single()
        assertEquals("snapshot-2", second.activeSnapshotId)
        assertEquals(15, second.itemCount)

        database.guideDao().upsertSourceState(
            IptvSourceStateEntity(
                sourceId = "source",
                name = "Playlist",
                type = "xtream",
                enabled = false,
                connectionLimit = 1,
                priority = 0,
                updatedAtEpochMillis = 300,
            ),
        )
        assertEquals(emptyList<CatalogueGenerationRow>(), dao.observeCatalogueGenerations().first())
    }

    private fun movie(
        id: String,
        name: String,
        category: String,
        plot: String?,
    ) = VodMovieEntity(
        sourceId = "source",
        snapshotId = "snapshot",
        movieId = id,
        name = name,
        normalizedName = name.lowercase(),
        categoryName = category,
        posterUrl = "https://example.com/$id.jpg",
        encryptedStreamUrl = "encrypted-$id",
        year = 2020,
        rating = "8.0",
        plot = plot,
    )
}
