package com.streammate.tv.feature.catalogue

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.CataloguePreferredCopy
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
 * One setting deciding which copy the wall stands on, so the common case needs
 * no choosing at all. The two copies below are the same film under two names,
 * and the card shows whichever one it settled on - which is what makes the
 * choice readable from outside.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueCopyPreferenceWallTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()
    private lateinit var preferences: AppPreferencesRepository

    @Before
    fun createLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = AppPreferencesRepository(context)
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        listOf("xtream", "m3u").forEach { sourceId ->
            database.guideDao().upsertSourceState(
                IptvSourceStateEntity(sourceId, sourceId, "XTREAM", true, 1, 0, NOW),
            )
        }
        database.catalogueDao().upsertMovies(listOf(movie("xtream", "1", FINNISH_COPY)))
        database.catalogueDao().upsertMovies(listOf(movie("m3u", "2", SUBTITLED_COPY)))
        database.catalogueDao().activateCatalogueSnapshot("xtream", "catalogue-xtream", 1, NOW)
        database.catalogueDao().activateCatalogueSnapshot("m3u", "catalogue-m3u", 1, NOW)
    }

    /**
     * Preferences outlive a test, so this one puts back what it changed. A
     * setting left behind is a failure in whichever test runs next.
     */
    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        runBlocking { preferences.setPreferredCatalogueCopy(CataloguePreferredCopy.NONE) }
        database.close()
    }

    @Test
    fun withNoPreferenceTheWallStandsWhereItAlwaysDid() {
        showLibrary(CataloguePreferredCopy.NONE)

        assertEquals(1, cardCount(FINNISH_COPY))
        assertEquals(0, cardCount(SUBTITLED_COPY))
    }

    @Test
    fun askingForSubtitlesStandsTheWallOnTheSubtitledCopy() {
        showLibrary(CataloguePreferredCopy.FINNISH_SUBTITLES)

        assertEquals(1, cardCount(SUBTITLED_COPY))
        assertEquals(0, cardCount(FINNISH_COPY))
    }

    @Test
    fun askingForTheLargestPictureStandsTheWallOnTheFourKCopy() {
        showLibrary(CataloguePreferredCopy.LARGEST_PICTURE)

        assertEquals(1, cardCount(FINNISH_COPY))
        assertEquals(0, cardCount(SUBTITLED_COPY))
    }

    private fun movie(sourceId: String, movieId: String, name: String) = VodMovieEntity(
        sourceId = sourceId,
        snapshotId = "catalogue-$sourceId",
        movieId = movieId,
        name = name,
        normalizedName = name.lowercase(),
        categoryName = "Drama",
        posterUrl = null,
        encryptedStreamUrl = "encrypted-$sourceId-$movieId",
        year = 1999,
        rating = null,
        plot = null,
    )

    private fun showLibrary(preferred: CataloguePreferredCopy) {
        runBlocking { preferences.setPreferredCatalogueCopy(preferred) }
        composeRule.setContent {
            StreamMateTheme {
                browser.Content(database = database, preferredCopy = preferred)
            }
        }
        // The preference arrives from storage a moment after the wall does, so
        // the card can start on one copy and settle on the other.
        composeRule.awaitUntil { cardCount(expectedCopy(preferred)) == 1 }
    }

    private fun expectedCopy(preferred: CataloguePreferredCopy) =
        if (preferred == CataloguePreferredCopy.FINNISH_SUBTITLES) SUBTITLED_COPY else FINNISH_COPY

    /**
     * The provider's own name, which is what an unmatched card shows - and the
     * only thing on screen that says which copy the wall settled on.
     */
    private fun cardCount(title: String) = composeRule
        .onAllNodesWithText(title, substring = true)
        .fetchSemanticsNodes()
        .size

    private companion object {
        const val NOW = 1_000L
        const val FINNISH_COPY = "FIN | The Matrix (1999) 4K"
        const val SUBTITLED_COPY = "The Matrix 1999 [MULTI-SUBS] 1080p"
    }
}
