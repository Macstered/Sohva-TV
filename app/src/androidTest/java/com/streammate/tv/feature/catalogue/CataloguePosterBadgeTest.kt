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
 * Providers write picture quality into the title, which is where it goes to
 * die: the grid gives a title one line and truncates it, so the very thing that
 * separates two copies of the same film was the part cut off. Floating it over
 * the artwork keeps it readable.
 */
@RunWith(AndroidJUnit4::class)
class CataloguePosterBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()

    @Before
    fun createLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, NOW),
        )
        database.catalogueDao().upsertMovies(
            listOf(
                movie(1, "Deep Water 4K UHD"),
                movie(2, "Night Signal HDR10"),
                movie(3, "Quiet Harbour"),
            ),
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", 3, NOW)
    }

    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        database.close()
    }

    private fun movie(index: Int, name: String) = VodMovieEntity(
        sourceId = "source",
        snapshotId = "catalogue-1",
        movieId = index.toString(),
        name = name,
        normalizedName = name.lowercase(),
        categoryName = "Drama",
        posterUrl = null,
        encryptedStreamUrl = "encrypted-$index",
        year = null,
        rating = null,
        plot = null,
    )

    private fun showLibrary() {
        composeRule.setContent {
            StreamMateTheme {
                browser.Content(database = database)
            }
        }
        // Substring: the card merges the poster initials, the title and the
        // badge into one node, so an exact match on the title alone misses it.
        composeRule.awaitUntil {
            composeRule.onAllNodesWithText("Deep Water", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun qualityWrittenIntoATitleIsShownOnTheArtwork() {
        showLibrary()

        assertEquals(1, badgeCount("4K UHD"))
        assertEquals(1, badgeCount("HDR10"))
    }

    @Test
    fun aTitleWithNothingToSayCarriesNoBadge() {
        // Three films, two of which say something about quality. A badge on the
        // third would be an invention.
        showLibrary()

        assertEquals(2, badgeCount("4K UHD") + badgeCount("HDR10"))
    }

    /**
     * Exact match on purpose. "4K UHD" is also a substring of the title it was
     * read from, so a substring count would find the badge twice and pass for
     * the wrong reason.
     */
    private fun badgeCount(label: String) = composeRule
        .onAllNodesWithText(label, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    private companion object {
        const val NOW = 1_000L
    }
}
