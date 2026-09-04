package com.streammate.tv.feature.catalogue

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodEpisodeEntity
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.CatalogueSearchType
import com.streammate.tv.iptv.metadata.MetadataMovieReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueRepositoryTest {
    private lateinit var database: StreamMateDatabase
    private var now = 1_000L

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, now),
        )
        database.catalogueDao().upsertMovies(
            listOf(
                VodMovieEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    movieId = "42",
                    name = "Test Movie",
                    normalizedName = "test movie",
                    categoryName = "Drama",
                    posterUrl = null,
                    encryptedStreamUrl = "encrypted-movie",
                    year = 2026,
                    rating = "8.0",
                    plot = "Plot",
                ),
            ),
        )
        database.catalogueDao().upsertSeries(
            listOf(
                VodSeriesEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    seriesId = "7",
                    name = "Test Series",
                    normalizedName = "test series",
                    categoryName = "Drama",
                    posterUrl = null,
                    backdropUrl = null,
                    year = 2025,
                    rating = "7.5",
                    plot = "Series plot",
                ),
            ),
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", 2, now)
        database.catalogueDao().replaceSeriesEpisodes(
            "source",
            "7",
            listOf(
                VodEpisodeEntity(
                    sourceId = "source",
                    seriesId = "7",
                    episodeId = "70",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    name = "Pilot",
                    encryptedStreamUrl = "encrypted-episode",
                    plot = null,
                    durationSeconds = 3_600,
                ),
            ),
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun catalogueProgressAndContinueWatchingArePersistent() = runBlocking {
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }
        val movie = repository.observeMovies().first().single()
        val series = repository.observeSeries().first().single()
        val episode = repository.observeEpisodes(series.sourceId, series.seriesId).first().single()

        assertEquals("vod:movie:source:42", movie.contentKey)
        assertEquals("vod:episode:source:70", episode.contentKey)
        assertNotNull(repository.playable(movie.contentKey))
        val searchTypes = repository.search("Test").map { it.type }.toSet()
        assertEquals(
            setOf(CatalogueSearchType.MOVIE, CatalogueSearchType.SERIES, CatalogueSearchType.EPISODE),
            searchTypes,
        )
        assertEquals(
            CatalogueSearchType.EPISODE,
            repository.search("Pilot").single().type,
        )

        repository.updateProgress(movie.contentKey, 300_000, 1_000_000)
        val inProgress = repository.progress(movie.contentKey)
        assertNotNull(inProgress)
        assertFalse(requireNotNull(inProgress).completed)
        assertEquals(movie.contentKey, repository.observeContinueWatching().first().single().contentKey)

        repository.updateProgress(movie.contentKey, 960_000, 1_000_000)
        assertTrue(requireNotNull(repository.progress(movie.contentKey)).completed)
        assertTrue(repository.observeContinueWatching().first().isEmpty())
    }

    @Test
    fun historyKeepsCompletedMoviesAndSeries() = runBlocking {
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress("vod:movie:source:42", 960_000, 1_000_000)
        repository.updateProgress("vod:episode:source:70", 3_590_000, 3_600_000)

        assertEquals(
            listOf("vod:movie:source:42"),
            repository.observeMovieHistoryCards().first().map { it.contentKey },
        )
        assertEquals(
            listOf("7"),
            repository.observeSeriesHistoryCards().first().map { it.seriesId },
        )
    }

    @Test
    fun nextEpisodeCrossesSeasonBoundaryAndResolvesItsSeries() = runBlocking {
        database.catalogueDao().replaceSeriesEpisodes(
            "source",
            "7",
            listOf(
                episode("70", season = 1, number = 1),
                episode("71", season = 1, number = 2),
                episode("80", season = 2, number = 1),
            ),
        )
        val repository = CatalogueRepository(database.catalogueDao())

        assertEquals("7", repository.seriesForEpisode("vod:episode:source:70")?.seriesId)
        assertEquals("vod:episode:source:71", repository.nextEpisode("vod:episode:source:70")?.contentKey)
        assertEquals("vod:episode:source:80", repository.nextEpisode("vod:episode:source:71")?.contentKey)
        assertNull(repository.nextEpisode("vod:episode:source:80"))
    }

    /**
     * The two names below are the shapes two playlists give one film. A person
     * who watched forty minutes of one of them has watched forty minutes of the
     * film, so opening the other carries on rather than starting again.
     */
    @Test
    fun aPositionWrittenAgainstOneCopyIsFoundFromTheOther() = runBlocking {
        seedSecondCopyOfOneFilm()
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress(FIRST_COPY, 400_000, 1_000_000)

        val fromTheOtherCopy = repository.progress(SECOND_COPY)
        assertEquals(400_000L, requireNotNull(fromTheOtherCopy).resumePositionMillis)
    }

    /** A copy played later is where the film is now, whichever was played first. */
    @Test
    fun theCopyPlayedLastIsWhereTheFilmWasLeft() = runBlocking {
        seedSecondCopyOfOneFilm()
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress(FIRST_COPY, 400_000, 1_000_000)
        repository.updateProgress(SECOND_COPY, 700_000, 1_000_000)

        assertEquals(700_000L, requireNotNull(repository.progress(FIRST_COPY)).resumePositionMillis)
    }

    /** A different film keeps its own position, however alike the two look. */
    @Test
    fun aPositionDoesNotLeakBetweenDifferentFilms() = runBlocking {
        seedSecondCopyOfOneFilm()
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress(FIRST_COPY, 400_000, 1_000_000)

        assertNull(repository.progress("vod:movie:source:42"))
    }

    /**
     * The duplicate problem where it is most annoying: the same half-watched
     * film sitting on the home screen twice.
     */
    @Test
    fun oneFilmMakesOneContinueWatchingRow() = runBlocking {
        seedSecondCopyOfOneFilm()
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress(FIRST_COPY, 400_000, 1_000_000)
        repository.updateProgress(SECOND_COPY, 700_000, 1_000_000)

        val resuming = repository.observeContinueWatching().first()
        assertEquals(listOf(SECOND_COPY), resuming.map { it.contentKey })
        assertEquals(700_000L, resuming.single().progress.resumePositionMillis)
    }

    /** Different films still get a row each. */
    @Test
    fun filmsThatAreNotTheSameFilmKeepARowEach() = runBlocking {
        seedSecondCopyOfOneFilm()
        val repository = CatalogueRepository(database.catalogueDao()) { ++now }

        repository.updateProgress(FIRST_COPY, 400_000, 1_000_000)
        repository.updateProgress(SECOND_COPY, 700_000, 1_000_000)
        repository.updateProgress("vod:movie:source:42", 100_000, 1_000_000)

        assertEquals(2, repository.observeContinueWatching().first().size)
    }

    /** Two copies of one film, from two playlists, named as providers name them. */
    private suspend fun seedSecondCopyOfOneFilm() {
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("other", "M3U", "M3U", true, 1, 0, now),
        )
        database.catalogueDao().upsertMovies(
            listOf(
                VodMovieEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    movieId = "99",
                    name = "FIN | The Matrix (1999) 4K",
                    normalizedName = "fin the matrix 1999 4k",
                    categoryName = "4K Movies",
                    posterUrl = null,
                    encryptedStreamUrl = "encrypted-matrix-4k",
                    year = 1999,
                    rating = "8.7",
                    plot = "Plot",
                ),
            ),
        )
        database.catalogueDao().upsertMovies(
            listOf(
                VodMovieEntity(
                    sourceId = "other",
                    snapshotId = "catalogue-other",
                    movieId = "12",
                    name = "The Matrix 1999 [MULTI-SUBS] 1080p",
                    normalizedName = "the matrix 1999 multi subs 1080p",
                    categoryName = "Movies",
                    posterUrl = null,
                    encryptedStreamUrl = "encrypted-matrix-1080p",
                    year = 1999,
                    rating = "8.7",
                    plot = "Plot",
                ),
            ),
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", 3, now)
        database.catalogueDao().activateCatalogueSnapshot("other", "catalogue-other", 1, now)
    }

    private fun episode(id: String, season: Int, number: Int) = VodEpisodeEntity(
        sourceId = "source",
        seriesId = "7",
        episodeId = id,
        seasonNumber = season,
        episodeNumber = number,
        name = "Episode $number",
        encryptedStreamUrl = "encrypted-$id",
        plot = null,
        durationSeconds = 3_600,
    )

    @Test
    fun similarMoviesAreLimitedToExactActiveLibraryMatches() = runBlocking {
        database.catalogueDao().upsertMovies(
            listOf(
                VodMovieEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    movieId = "43",
                    name = "NC - Dune: Part Two [Multi-Sub] (2024)",
                    normalizedName = "nc dune part two multi sub 2024",
                    categoryName = "4K Movies",
                    posterUrl = "https://provider.test/dune-two.jpg",
                    encryptedStreamUrl = "encrypted-dune-two",
                    year = 2024,
                    rating = "8.5",
                    plot = "Plot",
                ),
            ),
        )
        val repository = CatalogueRepository(database.catalogueDao())
        val current = repository.observeMovies().first().first { it.movieId == "42" }

        val matches = repository.availableSimilarMovies(
            currentMovie = current,
            references = listOf(
                MetadataMovieReference("43", "Dune: Part Two", year = 2024, posterUrl = null),
                MetadataMovieReference("44", "Arrival", year = 2016, posterUrl = null),
            ),
        )

        assertEquals(listOf("vod:movie:source:43"), matches.map { it.movie.contentKey })
        assertEquals("Dune: Part Two", matches.single().title)
    }

    private companion object {
        const val FIRST_COPY = "vod:movie:source:99"
        const val SECOND_COPY = "vod:movie:other:12"
    }
}
