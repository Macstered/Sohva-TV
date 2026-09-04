package com.streammate.tv.feature.catalogue

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two playlists carrying the same catalogue put two of everything on the wall.
 * The library here is the smallest thing that can show the difference: one film
 * carried twice, and one carried once.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueCollapsedWallTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()

    @Before
    fun createLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        listOf("xtream", "m3u").forEach { sourceId ->
            database.guideDao().upsertSourceState(
                IptvSourceStateEntity(sourceId, sourceId, "XTREAM", true, 1, 0, NOW),
            )
        }
        database.catalogueDao().upsertMovies(
            listOf(
                // The copy the wall will stand on: alphabetically first, and
                // deliberately the one missing a score.
                movie("xtream", "1", "FIN | The Matrix (1999) 4K", year = 1999, rating = null),
                movie("m3u", "2", "The Matrix 1999 [MULTI-SUBS] HDR10 1080p", year = 1999, rating = "8.7"),
                movie("xtream", "3", "Quiet Harbour", year = 2021, rating = "7.1"),
            ),
        )
        database.catalogueDao().activateCatalogueSnapshot("xtream", "catalogue-xtream", 2, NOW)
        database.catalogueDao().activateCatalogueSnapshot("m3u", "catalogue-m3u", 1, NOW)
    }

    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        database.close()
    }

    @Test
    fun twoCopiesOfOneFilmMakeOnePoster() {
        showLibrary()

        assertEquals(1, cardCount("The Matrix"))
        assertEquals(1, cardCount("Quiet Harbour"))
    }

    /**
     * The card speaks for both copies, so it says 4K because one of them is 4K
     * and HDR10 because the other is. Read off one copy alone the wall would
     * lose half of what the library actually holds.
     */
    @Test
    fun theCardSpeaksForEveryCopyItStandsFor() {
        showLibrary()

        assertEquals(1, cardCount("The Matrix"))
        assertEquals(1, badgeCount("4K UHD"))
        assertEquals(1, badgeCount("HDR10"))
    }

    /**
     * A score missing from one playlist arrives from the other. Both halves are
     * asserted: one card, and that card carrying what only its sibling knew.
     */
    @Test
    fun theCardTakesFromItsCopiesWhateverItLacks() {
        showLibrary()

        assertEquals(1, cardCount("The Matrix"))
        assertEquals(1, metaCount("8.7"))
    }

    @Test
    fun theMarkSaysWhenThereIsMoreThanOneCopy() {
        showLibrary()

        assertEquals(1, badgeCount("×2"))
    }

    /**
     * And says nothing about a film that has only ever had one version - a mark
     * on that one would be an invention.
     */
    @Test
    fun aFilmWithOneCopyCarriesNoMark() {
        showLibrary()

        assertEquals(1, metaCount("×"))
    }

    private fun movie(
        sourceId: String,
        movieId: String,
        name: String,
        year: Int?,
        rating: String?,
    ) = VodMovieEntity(
        sourceId = sourceId,
        snapshotId = "catalogue-$sourceId",
        movieId = movieId,
        name = name,
        normalizedName = name.lowercase(),
        categoryName = "Drama",
        posterUrl = null,
        encryptedStreamUrl = "encrypted-$sourceId-$movieId",
        year = year,
        rating = rating,
        plot = null,
    )

    private fun showLibrary() {
        composeRule.setContent {
            StreamMateTheme {
                browser.Content(database = database)
            }
        }
        composeRule.awaitUntil { cardCount("Quiet Harbour") == 1 }
    }

    /** Merged: one node per card, holding everything the card says. */
    private fun cardCount(title: String) = composeRule
        .onAllNodesWithText(title, substring = true)
        .fetchSemanticsNodes()
        .size

    /**
     * Exact match on purpose. A badge label is usually also a substring of the
     * title it was read from, so a substring count would find it twice and pass
     * for the wrong reason.
     */
    private fun badgeCount(label: String) = composeRule
        .onAllNodesWithText(label, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    /** For text no title contains, where a substring is the only way in. */
    private fun metaCount(text: String) = composeRule
        .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    private companion object {
        const val NOW = 1_000L
    }
}
