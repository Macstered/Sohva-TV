package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataSearchResult
import com.streammate.tv.testing.awaitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The picker itself: the list a person chooses from when the matcher would not.
 *
 * The two candidates are the case that defeats the matcher - the same name,
 * different years - so the year beside each row is the thing being tested as
 * much as the choosing is.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueMatchPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var chosen: MetadataSearchResult? = null
    private var cleared = false
    private var dismissed = false

    @Test
    fun theQueryStartsFromTheNameTheProviderGave() {
        showPicker()
        composeRule.awaitUntil { resultsShowing() }

        // Searched on the way in, so the common case needs no typing at all.
        composeRule.onNodeWithTag("match-result-603").assertIsDisplayed()
        composeRule.onNodeWithTag("match-result-604").assertIsDisplayed()
    }

    @Test
    fun choosingOneSettlesItAndClosesThePicker() {
        showPicker()
        composeRule.awaitUntil { resultsShowing() }

        composeRule.onNodeWithTag("match-result-604").performClick()
        composeRule.awaitUntil { dismissed }

        assertEquals("604", chosen?.externalId)
        assertTrue("the picker stayed open after a choice", dismissed)
    }

    /** Nothing to undo on a title nobody has chosen for. */
    @Test
    fun undoingIsOfferedOnlyWhereAChoiceWasMade() {
        val pinned = mutableStateOf(false)
        showPicker(pinnedState = pinned)
        composeRule.awaitUntil { resultsShowing() }

        assertTrue(
            "undo was offered on a title nobody has chosen for",
            composeRule.onAllNodesWithTag("match-picker-clear").fetchSemanticsNodes().isEmpty(),
        )

        composeRule.runOnUiThread { pinned.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("match-picker-clear").assertIsDisplayed()
    }

    @Test
    fun undoingHandsTheTitleBackToTheMatcher() {
        showPicker(pinned = true)
        composeRule.awaitUntil { resultsShowing() }

        composeRule.onNodeWithTag("match-picker-clear").performClick()
        composeRule.awaitUntil { dismissed }

        assertTrue(cleared)
    }

    @Test
    fun aSearchThatFindsNothingSaysSo() {
        showPicker(results = emptyList())
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("match-picker-note").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("match-picker-note").assertIsDisplayed()
    }

    private fun showPicker(
        pinned: Boolean = false,
        results: List<MetadataSearchResult> = CANDIDATES,
        pinnedState: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(pinned),
    ) {
        val open = mutableStateOf(true)
        composeRule.setContent {
            StreamMateTheme {
                if (open.value) {
                    CatalogueMatchPicker(
                        initialQuery = "The Matrix",
                        lookup = LOOKUP,
                        pinned = pinnedState.value,
                        onSearch = { results },
                        onChoose = { chosen = it },
                        onClear = { cleared = true },
                        onDismiss = {
                            dismissed = true
                            open.value = false
                        },
                    )
                }
            }
        }
    }

    private fun resultsShowing(): Boolean =
        composeRule.onAllNodesWithTag("match-result-603").fetchSemanticsNodes().isNotEmpty()

    private companion object {
        val LOOKUP = MetadataLookup(
            mediaType = MetadataMediaType.MOVIE,
            title = "The Matrix",
            year = 1999,
        )

        // The same name twice is exactly what the matcher refuses to choose
        // between, and exactly what a person settles at a glance.
        val CANDIDATES = listOf(
            MetadataSearchResult(
                externalId = "603",
                mediaType = MetadataMediaType.MOVIE,
                title = "The Matrix",
                year = 1999,
                overview = "A hacker learns what the world really is.",
                posterUrl = null,
            ),
            MetadataSearchResult(
                externalId = "604",
                mediaType = MetadataMediaType.MOVIE,
                title = "The Matrix",
                year = 2003,
                overview = "A remake nobody asked for.",
                posterUrl = null,
            ),
        )
    }
}
