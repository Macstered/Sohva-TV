package com.streammate.tv.feature.catalogue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.CatalogueGenreEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Room projections feeding the production browser's two independent rails. */
class CatalogueGenreRailTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()

    @Before
    fun createLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, NOW),
        )
        database.catalogueDao().upsertMovies(TITLES.mapIndexed { index, title ->
            VodMovieEntity(
                sourceId = "source", snapshotId = "catalogue-1", movieId = (index + 1).toString(),
                name = title, normalizedName = title.lowercase(), categoryName = "VOD",
                posterUrl = null, encryptedStreamUrl = "encrypted-" + index,
                year = 2020, rating = null, plot = null,
            )
        })
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", TITLES.size, NOW)
        // The real write path accepts old multi-genre input but keeps only the primary.
        database.metadataDao().replaceGenres(contentKey(1), listOf(
            CatalogueGenreEntity(contentKey(1), "action"),
            CatalogueGenreEntity(contentKey(1), "thriller"),
        ))
        database.metadataDao().replaceGenres(contentKey(2), listOf(CatalogueGenreEntity(contentKey(2), "action")))
        database.metadataDao().replaceGenres(contentKey(3), listOf(CatalogueGenreEntity(contentKey(3), "comedy")))
    }

    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        database.close()
    }

    @Test
    fun providerGroupsAndGenresNeverMixInTheSameRail() {
        showLibrary()
        composeRule.onNodeWithTag(providerTag()).assertIsDisplayed()
        assertFalse(rowExists("action"))
        openGenres()
        composeRule.onNodeWithTag(genreTag("action")).assertIsDisplayed()
        composeRule.onNodeWithTag(genreTag("comedy")).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(providerTag()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun choosingAGenreLeavesOnlyTheTitlesInIt() {
        showLibrary()
        openGenres()
        selectGenre("comedy")
        composeRule.awaitUntil { titleShowing(TITLES[2]) && !titleShowing(TITLES[0]) }
        assertFalse(titleShowing(TITLES[1]))
    }

    @Test
    fun aTitleAppearsOnlyInItsPrimaryGenre() {
        showLibrary()
        openGenres()
        selectGenre("action")
        composeRule.awaitUntil { titleShowing(TITLES[0]) }
        assertFalse("the secondary genre must not create another group", rowExists("thriller"))
        selectGenre("comedy")
        composeRule.awaitUntil { titleShowing(TITLES[2]) }
        assertFalse(titleShowing(TITLES[0]))
    }

    @Test
    fun titlesTheMetadataPassHasNotReachedRemainFindable() {
        showLibrary()
        openGenres()
        selectGenre("unsorted")
        composeRule.awaitUntil { titleShowing(TITLES[3]) && !titleShowing(TITLES[0]) }
    }

    @Test
    fun anEnrichmentWriteMovesATitleFromUnsortedIntoItsPrimaryGenre() = runBlocking {
        showLibrary()
        openGenres()
        selectGenre("unsorted")
        composeRule.awaitUntil { titleShowing(TITLES[3]) }
        database.metadataDao().replaceGenres(contentKey(4), listOf(CatalogueGenreEntity(contentKey(4), "comedy")))
        composeRule.awaitUntil { !rowExists("unsorted") }
        selectGenre("comedy")
        composeRule.awaitUntil { titleShowing(TITLES[2]) && titleShowing(TITLES[3]) }
    }

    @Test
    fun returningToProviderGroupsRestoresTheWholeGroup() {
        showLibrary()
        openGenres()
        selectGenre("comedy")
        composeRule.awaitUntil { titleShowing(TITLES[2]) && !titleShowing(TITLES[0]) }
        composeRule.onNodeWithTag("catalogue-v2-grouping-playlist").performClick()
        composeRule.awaitUntil { TITLES.all(::titleShowing) }
        assertFalse(rowExists("action"))
    }

    private fun showLibrary() {
        composeRule.setContent { StreamMateTheme { browser.Content(database) } }
        composeRule.awaitUntil { TITLES.all(::titleShowing) }
    }

    private fun openGenres() {
        composeRule.onNodeWithTag("catalogue-v2-grouping-genre").performClick()
        composeRule.awaitUntil { rowExists("comedy") }
    }

    private fun selectGenre(genre: String) {
        composeRule.onNodeWithTag(genreTag(genre)).performClick()
    }

    private fun rowExists(genre: String): Boolean =
        composeRule.onAllNodesWithTag(genreTag(genre)).fetchSemanticsNodes().isNotEmpty()

    private fun titleShowing(title: String): Boolean =
        composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun genreTag(genre: String) = "catalogue-v2-genre-genre:" + genre
    private fun providerTag() = "catalogue-v2-group-" + "VOD".hashCode()

    private companion object {
        const val NOW = 1_000L
        val TITLES = listOf("Ajojahti", "Takaa-ajo", "Naurava kissa", "Tuntematon nauha")
        fun contentKey(index: Int) = "vod:movie:source:" + index
    }
}
