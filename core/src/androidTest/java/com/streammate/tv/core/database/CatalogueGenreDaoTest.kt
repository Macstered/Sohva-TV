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

/**
 * The table the genre rail is built from.
 *
 * A title belongs only to its primary genre, so counts stay proportional to the
 * actual library and a rematch moves the title rather than duplicating it.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueGenreDaoTest {

    private lateinit var database: StreamMateDatabase
    private lateinit var dao: MetadataDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        dao = database.metadataDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun aTitleIsStoredOnlyUnderItsPrimaryGenre() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "action", "thriller", "science_fiction"))

        assertEquals(listOf("action"), dao.genres("movie:1"))
    }

    @Test
    fun theCountsAddUpToTheNumberOfSortedTitles() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "action", "thriller"))
        dao.replaceGenres("movie:2", rows("movie:2", "action"))

        val counts = dao.observeGenreCounts().first().associate { it.genre to it.itemCount }

        assertEquals(mapOf("action" to 2), counts)
        assertEquals(2, counts.values.sum())
    }

    @Test
    fun rematchingATitleDropsWhatItUsedToBe() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "horror"))

        dao.replaceGenres("movie:1", rows("movie:1", "comedy"))

        assertEquals(listOf("comedy"), dao.genres("movie:1"))
        assertEquals(
            listOf("comedy"),
            dao.observeGenreCounts().first().map { it.genre },
        )
    }

    @Test
    fun aTitleThatMatchedNothingLeavesNoRowsBehind() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "drama"))

        dao.replaceGenres("movie:1", emptyList())

        assertEquals(emptyList<String>(), dao.genres("movie:1"))
        assertEquals(emptyList<CatalogueGenreCount>(), dao.observeGenreCounts().first())
    }

    @Test
    fun storingTheSameGenreTwiceKeepsOneRow() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "action") + rows("movie:1", "action"))

        assertEquals(listOf("action"), dao.genres("movie:1"))
    }

    @Test
    fun aGenreListsTheTitlesInIt() = runBlocking {
        dao.replaceGenres("movie:1", rows("movie:1", "drama", "action"))
        dao.replaceGenres("movie:2", rows("movie:2", "drama"))
        dao.replaceGenres("movie:3", rows("movie:3", "action"))

        assertEquals(
            listOf("movie:1", "movie:2"),
            dao.observeContentKeysInGenre("drama").first().sorted(),
        )
        assertEquals(listOf("movie:3"), dao.observeContentKeysInGenre("action").first())
    }

    @Test
    fun metadataWorkReadsOnlyAnEligibleBoundedPage() = runBlocking {
        dao.upsertCatalogueMetadataWork(
            listOf(
                work("a", CatalogueMetadataWorkEntity.STATE_PENDING, nextAttemptAt = 0),
                work("b", CatalogueMetadataWorkEntity.STATE_RETRY, nextAttemptAt = 2_000),
                work("c", CatalogueMetadataWorkEntity.STATE_COMPLETE, nextAttemptAt = 0),
                work("d", CatalogueMetadataWorkEntity.STATE_PENDING, nextAttemptAt = 0),
            ),
        )

        assertEquals(
            listOf("a"),
            dao.nextCatalogueMetadataWork(nowEpochMillis = 1_000, limit = 1)
                .map(CatalogueMetadataWorkEntity::contentKey),
        )
        assertEquals(
            listOf("a", "b", "d"),
            dao.nextCatalogueMetadataWork(nowEpochMillis = 3_000, limit = 10)
                .map(CatalogueMetadataWorkEntity::contentKey),
        )
    }

    @Test
    fun aWorkerPagePublishesGenresOverridesAndQueueStateTogether() = runBlocking {
        val initial = work("movie:1", CatalogueMetadataWorkEntity.STATE_PENDING, nextAttemptAt = 0)
        dao.upsertCatalogueMetadataWork(listOf(initial))

        dao.applyCatalogueMetadataBatch(
            replacedGenreContentKeys = listOf("movie:1"),
            genreRows = rows("movie:1", "action", "thriller"),
            overrides = listOf(
                CatalogueMetadataOverrideEntity(
                    contentKey = "movie:1",
                    providerPosterUrl = null,
                    replacementPosterUrl = null,
                    replaceProviderPoster = false,
                    replacementTitle = "Movie",
                    externalId = "1",
                    genresVersion = 1,
                    updatedAtEpochMillis = 10,
                ),
            ),
            workRows = listOf(initial.copy(state = CatalogueMetadataWorkEntity.STATE_COMPLETE)),
        )

        assertEquals(listOf("action"), dao.genres("movie:1"))
        assertEquals(
            "Movie",
            dao.catalogueMetadataOverride("movie:1")?.replacementTitle,
        )
        assertEquals(
            CatalogueMetadataWorkEntity.STATE_COMPLETE,
            dao.catalogueMetadataWork().single().state,
        )
    }

    private fun rows(contentKey: String, vararg genres: String) =
        genres.map { CatalogueGenreEntity(contentKey = contentKey, genre = it) }

    private fun work(
        contentKey: String,
        state: String,
        nextAttemptAt: Long,
    ) = CatalogueMetadataWorkEntity(
        contentKey = contentKey,
        mediaType = "movie",
        title = "Movie $contentKey",
        year = 2020,
        providerPosterUrl = null,
        targetGenresVersion = 1,
        state = state,
        attemptCount = 0,
        nextAttemptAtEpochMillis = nextAttemptAt,
        updatedAtEpochMillis = 1,
    )
}
