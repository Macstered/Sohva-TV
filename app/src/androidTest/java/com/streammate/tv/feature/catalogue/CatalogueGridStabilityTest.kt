package com.streammate.tv.feature.catalogue

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.testing.awaitUntil
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library grid must not move while focus travels along a row.
 *
 * Moving between titles used to shift the whole grid by six density pixels on
 * every press, which reads as a rendering bug rather than as navigation.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueGridStabilityTest {

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
        // Enough titles to fill more than one row, so a row can sit against the
        // bottom edge exactly as it does on a real library.
        database.catalogueDao().upsertMovies(
            (1..ITEM_COUNT).map { index ->
                val title = titleOf(index)
                VodMovieEntity(
                    sourceId = "source",
                    snapshotId = "catalogue-1",
                    movieId = index.toString(),
                    name = title,
                    normalizedName = title.lowercase(),
                    categoryName = "Drama",
                    posterUrl = null,
                    encryptedStreamUrl = "encrypted-$index",
                    // Half carry a year, half do not, so the cards are not all
                    // the same height - the grid still must not move.
                    year = if (index % 2 == 0) 2020 + index % 5 else null,
                    rating = null,
                    plot = null,
                )
            },
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", ITEM_COUNT, NOW)
    }

    @After
    fun closeLibrary() {
        browser.dispose(composeRule)
        database.close()
    }

    @Test
    fun movingFocusAlongARowLeavesTheGridWhereItWas() {
        composeRule.setContent {
            StreamMateTheme {
                browser.Content(database = database)
            }
        }

        composeRule.awaitUntil {
            composeRule.onAllNodesWithText(titleOf(1)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(titleOf(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocusOn(titleOf(1))

        // Scroll into the library the way someone browsing it would, so a row
        // sits against the bottom edge rather than the grid resting at the top.
        // The drift only shows once the grid has somewhere to scroll to.
        repeat(ROWS_DOWN) {
            pressOnFocused(Key.DirectionDown)
            composeRule.waitForIdle()
        }

        // Several moves in both directions. One press could land on a stable
        // spot by luck; a run of them in both directions cannot.
        listOf(
            Key.DirectionRight,
            Key.DirectionRight,
            Key.DirectionLeft,
            Key.DirectionLeft,
            Key.DirectionRight,
        ).forEachIndexed { step, key ->
            assertGridHoldsStillWhileFocusMoves(step, key)
        }
    }

    /**
     * Presses [key] and checks that every card taking no part in the move stays
     * exactly where it was. The card losing focus and the card gaining it are
     * both excluded: their own lift animation moves them, which is intended.
     */
    private fun assertGridHoldsStillWhileFocusMoves(step: Int, key: Key) {
        val before = visibleTitleTops()
        val movedFrom = requireNotNull(focusedTitleOrNull()) { "focus is on ${describeFocus()}" }

        pressOnFocused(key)
        composeRule.waitForIdle()
        composeRule.awaitUntil { focusedTitleOrNull() != movedFrom }
        val movedTo = requireNotNull(focusedTitleOrNull()) { "focus is on ${describeFocus()}" }
        val after = visibleTitleTops()

        val drifted = before
            .filterKeys { it != movedFrom && it != movedTo && it in after }
            .mapNotNull { (title, y) ->
                val moved = after.getValue(title) - y
                if (abs(moved) > 0.5f) "$title moved ${moved}px" else null
            }
        assertTrue(
            "step $step ($key, $movedFrom -> $movedTo) shifted the grid: $drifted",
            drifted.isEmpty(),
        )
    }

    private fun visibleTitleTops(): Map<String, Float> = (1..ITEM_COUNT).mapNotNull { index ->
        val title = titleOf(index)
        composeRule.onAllNodesWithText(title, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.let { title to it.positionInRoot.y }
    }.toMap()

    private fun describeFocus(): String = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .joinToString(" | ") { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("/")
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            "text=$text tag=$tag"
        }
        .ifBlank { "nothing" }

    private fun pressOnFocused(key: Key) {
        composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
    }

    private fun awaitFocusOn(title: String) {
        composeRule.awaitUntil {
            composeRule.onAllNodes(hasText(title) and isFocused())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun focusedTitleOrNull(): String? = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .firstNotNullOfOrNull { node ->
            // A card merges the poster's initials, the title and the year into
            // one node, so pick out the element that is the title.
            node.config.getOrNull(SemanticsProperties.Text)
                ?.map(AnnotatedString::text)
                ?.firstOrNull { it.startsWith(TITLE_PREFIX) }
        }

    private fun titleOf(index: Int) = "$TITLE_PREFIX %02d".format(index)

    private companion object {
        const val NOW = 1_000L
        const val ITEM_COUNT = 24
        const val TITLE_PREFIX = "Catalogue Title"
        const val ROWS_DOWN = 3
    }
}
