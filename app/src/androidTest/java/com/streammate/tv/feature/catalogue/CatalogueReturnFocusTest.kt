package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserSession
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowsePartition
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Focus regressions exercise the shipping browser, including a real Room load and detour. */
class CatalogueReturnFocusTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var database: StreamMateDatabase
    private val browser = CatalogueBrowserFixture()
    private val session = CatalogueBrowserSession(CatalogueMode.MOVIES)
    private val detailsOpen = mutableStateOf(false)

    @Before
    fun createLibrary() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
    }

    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        database.close()
    }

    private suspend fun seedMovies() {
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, 1_000),
        )
        database.catalogueDao().upsertMovies((1..30).map { index ->
            VodMovieEntity(
                sourceId = "source", snapshotId = "catalogue-1", movieId = index.toString(),
                name = titleOf(index), normalizedName = titleOf(index).lowercase(),
                categoryName = "Drama", posterUrl = null, encryptedStreamUrl = "encrypted-" + index,
                year = 2024, rating = null, plot = null,
            )
        })
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", 30, 1_000)
    }

    @Test
    fun leftReachesTheRailFromTheFirstColumn() = runBlocking {
        seedMovies()
        showLibrary()
        awaitWall()
        focusTitle(titleOf(1))
        pressLeftUntilRail()
    }

    @Test
    fun leftReachesTheRailOnALibraryThatArrivedAfterTheScreenDid() = runBlocking {
        showLibrary()
        composeRule.waitForIdle()
        seedMovies()
        awaitWall()
        focusTitle(titleOf(1))
        pressLeftUntilRail()
    }

    @Test
    fun leftStillReachesTheRailAfterComingBackFromATitle() = runBlocking {
        seedMovies()
        showLibrary()
        awaitWall()
        openAndReturn(titleOf(1))
        pressLeftUntilRail()
    }

    @Test
    fun comingBackFromATitleRestoresTheSameScrolledWallItem() = runBlocking {
        seedMovies()
        showLibrary()
        awaitWall()
        composeRule.onNodeWithTag("catalogue-v2-wall-current").performScrollToIndex(12)
        openAndReturn(titleOf(13))
        composeRule.onNodeWithText(titleOf(13)).assertIsFocused()
        pressLeftUntilRail()
    }

    @Test
    fun leftFromTheSecondColumnMovesOneCardWithoutLeavingTheWall() = runBlocking<Unit> {
        seedMovies()
        showLibrary()
        awaitWall()
        focusTitle(titleOf(2))
        composeRule.onNodeWithText(titleOf(2)).performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText(titleOf(1)).assertIsFocused()
    }

    @Test
    fun returningFromGroupManagementRestoresTheOptionsButton() = runBlocking {
        seedMovies()
        showLibrary()
        awaitWall()
        composeRule.onNodeWithTag("catalogue-v2-options").performClick()
        composeRule.onNodeWithTag("catalogue-v2-category-edit").performClick()
        composeRule.onNodeWithTag("test-back").performClick()
        composeRule.awaitUntil {
            runCatching { composeRule.onNodeWithTag("catalogue-v2-options").assertIsFocused() }.isSuccess
        }
    }

    private fun showLibrary() {
        composeRule.setContent {
            StreamMateTheme {
                if (detailsOpen.value) {
                    TvActionButton("Back to library", onClick = { detailsOpen.value = false }, testTag = "test-back")
                } else {
                    browser.Content(
                        database, session = session,
                        initialPartition = CatalogueBrowsePartition.PlaylistGroup("Drama"),
                        onManageGroups = { detailsOpen.value = true },
                        onOpenEntry = { detailsOpen.value = true },
                    )
                }
            }
        }
    }

    private fun openAndReturn(title: String) {
        focusTitle(title)
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("test-back").performClick()
        composeRule.awaitUntil {
            runCatching { composeRule.onNodeWithText(title).assertIsFocused() }.isSuccess
        }
    }

    private fun focusTitle(title: String) {
        composeRule.onNodeWithText(title).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    private fun awaitWall() {
        composeRule.awaitUntil { composeRule.onAllNodesWithText(titleOf(1)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pressLeftUntilRail() {
        repeat(8) {
            if (focusIsOnRail()) return
            composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.waitForIdle()
        }
        assertTrue("Left did not reach the group rail", focusIsOnRail())
    }

    private fun focusIsOnRail(): Boolean = composeRule.onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .any { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
            tag == "catalogue-v2-group-${"Drama".hashCode()}"
        }

    private fun titleOf(index: Int) = "Elokuva %02d".format(index)
}
