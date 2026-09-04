package com.streammate.tv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.core.model.OrganizationKey
import com.streammate.tv.core.model.organizationGroupKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The saved-filter rules are evaluated by Room, identically for films and series. */
@RunWith(AndroidJUnit4::class)
class CatalogueCustomFilterDaoTest {
    private lateinit var database: StreamMateDatabase
    private val dao get() = database.catalogueDao()

    @Before
    fun createLibrary() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        database.guideDao().upsertSourceState(source("source"))
        seed(FIXTURES)
        dao.activateCatalogueSnapshot("source", "active", FIXTURES.size * 2, 1)
    }

    @After
    fun closeLibrary() = database.close()

    @Test
    fun genreUnionYearBoundsAndRatingMustAllMatchForBothLibraries() = runBlocking {
        assertBoth(
            expected = listOf("a", "b"),
            genres = listOf("action", "comedy"), fromYear = 1980, toYear = 1989, minRating = 7.5,
        )
        // A title with no genre is eligible when the saved filter has only year rules.
        assertBoth(listOf("j"), fromYear = 1985, toYear = 1985, search = "Unsorted")
        assertBoth(emptyList(), genres = listOf("action"), fromYear = 1985, toYear = 1985, search = "Unsorted")
    }

    @Test
    fun unknownValuesAreNotTreatedAsZeroAndNumericPrefixesStayCompatible() = runBlocking {
        assertBoth(
            expected = listOf("a", "c", "d", "e", "f", "k", "l"),
            genres = listOf("action"), minRating = 0.0,
        )
        // "7e1" is a provider string starting with 7, not a scientific-notation score of 70.
        assertBoth(emptyList(), minRating = 8.0, search = "Exponent")
        assertBoth(listOf("l"), minRating = 7.0, search = "Exponent")
        assertBoth(listOf("e"), genres = listOf("action"), search = "Unknown year")
        assertBoth(emptyList(), genres = listOf("action"), fromYear = 1900, search = "Unknown year")
    }

    @Test
    fun searchMatchesProviderOrReplacementTitleWithoutEscapingTheFilter() = runBlocking {
        for (key in listOf("vod:movie:source:a", "series:source:a")) {
            database.metadataDao().upsertCatalogueMetadataOverride(CatalogueMetadataOverrideEntity(
                contentKey = key, providerPosterUrl = null,
                replacementPosterUrl = "https://example.invalid/poster.jpg", replaceProviderPoster = true,
                replacementTitle = "Localized arrival", externalId = "tmdb:1",
                genresVersion = 3, updatedAtEpochMillis = 2,
            ))
        }
        assertBoth(listOf("a"), genres = listOf("action"), search = "LOCALIZED")
        assertBoth(listOf("a"), genres = listOf("action"), search = "start")
        assertBoth(emptyList(), genres = listOf("drama"), search = "Localized")
        val movie = dao.observeMovieCardsInCustomGroup(listOf("action"), false, 1980, 1980, null, "").first().single()
        val series = dao.observeSeriesCardsInCustomGroup(listOf("action"), false, 1980, 1980, null, "").first().single()
        assertEquals("Localized arrival", movie.replacementTitle)
        assertEquals(movie.replacementPosterUrl, series.replacementPosterUrl)
        assertEquals("action", movie.genresCsv)
        assertEquals("action", series.genresCsv)
    }

    @Test
    fun disabledSourcesInactiveSnapshotsAndOrganizationHidesApplyToCustomFilters() = runBlocking {
        database.guideDao().upsertSourceState(source("disabled").copy(enabled = false))
        seed(listOf(FIXTURES.first().copy(id = "disabled")), sourceId = "disabled")
        dao.activateCatalogueSnapshot("disabled", "active", 2, 1)
        seed(listOf(FIXTURES.first().copy(id = "old")), snapshotId = "inactive")

        assertBoth(listOf("a", "b"), fromYear = 1980, toYear = 1989, minRating = 7.5, genres = listOf("action", "comedy"))
        for ((room, key) in listOf(LibraryRoom.MOVIES to "vod:movie:source:a", LibraryRoom.SERIES to "series:source:a")) {
            database.organizationDao().change(listOf(OrganizationChange(
                OrganizationKey(room, itemKey = key), enabled = false, changeEnabled = true,
            )))
        }
        assertBoth(listOf("b"), fromYear = 1980, toYear = 1989, minRating = 7.5, genres = listOf("action", "comedy"))
        database.organizationDao().change(listOf(LibraryRoom.MOVIES, LibraryRoom.SERIES).map { room ->
            OrganizationChange(
                OrganizationKey(room, "source", organizationGroupKey("Provider")),
                enabled = false, changeEnabled = true,
            )
        })
        assertBoth(emptyList(), minRating = 0.0)
        assertTrue(dao.activeMovie("source", "a") != null)
    }

    private suspend fun assertBoth(
        expected: List<String>,
        genres: List<String> = emptyList(),
        fromYear: Int? = null,
        toYear: Int? = null,
        minRating: Double? = null,
        search: String = "",
    ) {
        assertEquals("Movies", expected, dao.observeMovieCardsInCustomGroup(
            genres, genres.isEmpty(), fromYear, toYear, minRating, search,
        ).first().map { it.movieId }.sorted())
        assertEquals("Series", expected, dao.observeSeriesCardsInCustomGroup(
            genres, genres.isEmpty(), fromYear, toYear, minRating, search,
        ).first().map { it.seriesId }.sorted())
    }

    private suspend fun seed(rows: List<Fixture>, sourceId: String = "source", snapshotId: String = "active") {
        dao.upsertMovies(rows.map { row -> VodMovieEntity(
            sourceId = sourceId, snapshotId = snapshotId, movieId = row.id,
            name = row.title, normalizedName = row.title.lowercase(), categoryName = "Provider",
            posterUrl = null, encryptedStreamUrl = "encrypted", year = row.year, rating = row.rating, plot = null,
        ) })
        dao.upsertSeries(rows.map { row -> VodSeriesEntity(
            sourceId = sourceId, snapshotId = snapshotId, seriesId = row.id,
            name = row.title, normalizedName = row.title.lowercase(), categoryName = "Provider",
            posterUrl = null, backdropUrl = null, year = row.year, rating = row.rating, plot = null,
        ) })
        for (row in rows) {
            for (key in listOf("vod:movie:$sourceId:${row.id}", "series:$sourceId:${row.id}")) {
                database.metadataDao().replaceGenres(key, row.genre?.let { listOf(CatalogueGenreEntity(key, it)) }.orEmpty())
            }
        }
    }

    private fun source(id: String) = IptvSourceStateEntity(id, id, "xtream", true, 1, 0, 1)

    private data class Fixture(val id: String, val title: String, val year: Int?, val rating: String?, val genre: String? = "action")

    private companion object {
        val FIXTURES = listOf(
            Fixture("a", "A start", 1980, " 7,5 "),
            Fixture("b", "B end", 1989, "8.0/10", "comedy"),
            Fixture("c", "C old", 1979, "9.0"),
            Fixture("d", "D new", 1990, "9.0"),
            Fixture("e", "E Unknown year", null, "9.0"),
            Fixture("f", "F weak", 1985, "6.9"),
            Fixture("g", "G unknown score", 1985, "NR"),
            Fixture("h", "H null score", 1985, null),
            Fixture("i", "I drama", 1985, "8.5", "drama"),
            Fixture("j", "J Unsorted", 1985, "8.0", null),
            Fixture("k", "K zero", 1985, "0.0"),
            Fixture("l", "L Exponent", 1985, "7e1"),
        )
    }
}
