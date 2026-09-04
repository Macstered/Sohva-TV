package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.CatalogueGenreEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A group somebody defined, seen from the library.
 *
 * The seeded titles are the case the feature exists for: the playlist filed all
 * of them under one meaningless group, and what separates them is what they
 * are and when they were made.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueCustomGroupRailTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()
    private lateinit var preferences: AppPreferencesRepository

    @Before
    fun createLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        preferences = AppPreferencesRepository(context)
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, NOW),
        )
        database.catalogueDao().upsertMovies(
            TITLES.mapIndexed { index, (title, year) ->
                VodMovieEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    movieId = (index + 1).toString(),
                    name = title,
                    normalizedName = title.lowercase(),
                    categoryName = "VOD",
                    posterUrl = null,
                    encryptedStreamUrl = "encrypted-$index",
                    year = year,
                    rating = null,
                    plot = null,
                )
            },
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", TITLES.size, NOW)
        database.metadataDao().upsertGenres(
            listOf(
                CatalogueGenreEntity("vod:movie:source:1", "action"),
                CatalogueGenreEntity("vod:movie:source:2", "action"),
                CatalogueGenreEntity("vod:movie:source:3", "family"),
            ),
        )
        preferences.saveCustomCatalogueGroup(EIGHTIES)
    }

    @After
    fun cleanUp() = runBlocking {
        browser.dispose(composeRule)
        preferences.deleteCustomCatalogueGroup(EIGHTIES.id)
        preferences.deleteCustomCatalogueGroup(EMPTY_GROUP.id)
        database.close()
    }

    @Test
    fun aGroupOfYourOwnSitsAboveTheGenres() {
        showLibrary()
        composeRule.awaitUntil(timeoutMillis = 15_000) { titleShowing(TITLES[0].first) }

        composeRule.onNodeWithTag("catalogue-v2-genre-custom:eighties").assertIsDisplayed()
    }

    @Test
    fun choosingItKeepsOnlyWhatItNames() {
        showLibrary()
        composeRule.awaitUntil(timeoutMillis = 15_000) { titleShowing(TITLES[0].first) }

        composeRule.onNodeWithTag("catalogue-v2-genre-custom:eighties").performClick()
        composeRule.awaitUntil { !titleShowing(TITLES[1].first) }

        // Action from the eighties stays; action from the nineties and the
        // eighties family film both go.
        assertTrue("the eighties action film is missing", titleShowing(TITLES[0].first))
        assertTrue("a nineties film crept in", !titleShowing(TITLES[1].first))
        assertTrue("a family film crept in", !titleShowing(TITLES[2].first))
    }

    /**
     * A group can be empty - the rail shows it because somebody made it on
     * purpose - and choosing one must not stand the viewer in front of a blank
     * wall with nothing lit. There is nowhere on the wall for focus to go, so
     * it belongs on the rail, where a different row can be chosen.
     */
    @Test
    fun anEmptyGroupLeavesTheHighlightSomewhereUseful() = runBlocking {
        preferences.saveCustomCatalogueGroup(EMPTY_GROUP)
        showLibrary()
        composeRule.awaitUntil(timeoutMillis = 15_000) { titleShowing(TITLES[0].first) }

        composeRule.onNodeWithTag("catalogue-v2-genre-custom:empty")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performClick()
        composeRule.awaitUntil { !titleShowing(TITLES[0].first) }

        assertTrue(
            "nothing was left highlighted on an empty group; focus was on ${focusedTag()}",
            focusedTag() == "catalogue-v2-genre-custom:empty",
        )
    }

    private fun focusedTag(): String? = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun showLibrary() {
        composeRule.setContent {
            StreamMateTheme {
                val settings by preferences.preferences.collectAsState(initial = AppPreferences())
                browser.Content(database = database, customGroups = settings.customCatalogueGroups)
            }
        }
        composeRule.awaitUntil { titleShowing(TITLES[0].first) }
        composeRule.onNodeWithTag("catalogue-v2-grouping-genre").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("catalogue-v2-genre-custom:eighties").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun titleShowing(title: String): Boolean =
        composeRule.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val NOW = 1_000L

        val TITLES = listOf(
            "Ajojahti" to 1985,
            "Takaa-ajo" to 1995,
            "Naurava kissa" to 1986,
        )

        // Nothing in this library is a western, so this row is empty.
        val EMPTY_GROUP = CatalogueCustomGroup(
            id = "empty",
            name = "Lännenelokuvat",
            genres = setOf(CatalogueGenre.WESTERN),
        )

        val EIGHTIES = CatalogueCustomGroup(
            id = "eighties",
            name = "80-luvun toiminta",
            genres = setOf(CatalogueGenre.ACTION),
            fromYear = 1980,
            toYear = 1989,
        )
    }
}
