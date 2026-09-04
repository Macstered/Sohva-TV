package com.streammate.tv.feature.today

import com.streammate.tv.testing.ClearAppStateRule
import org.junit.rules.RuleChain
import com.streammate.tv.testing.awaitUntil
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.streammate.tv.app.MainActivity
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Outside the compose rule so persisted state is reset before the activity
    // launches, not after it has already rendered.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)

    @Before
    fun waitForLandingPage() {
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sohvaSportWordmarkIsNotClippedByTheHeader() {
        openSportMate()
        composeRule.onNodeWithTag("today-brand")
            .assertIsDisplayed()
            .assertTextEquals("Sohva Sport")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue("Brand text layout must be available", getResults(results))
                assertFalse("Sohva Sport is clipped", results.single().hasVisualOverflow)
            }
    }

    @Test
    fun firstMatchReceivesInitialFocusWhenEventsAreAvailable() {
        openSportMate()
        val eventNodes = composeRule.onAllNodes(hasEventTag).fetchSemanticsNodes()
        if (eventNodes.isEmpty()) {
            composeRule.onNodeWithTag("filter-all").assertIsDisplayed().assertIsFocused()
        } else {
            composeRule.onAllNodes(hasEventTag)[0].assertIsDisplayed().assertIsFocused()
        }
    }

    @Test
    fun dpadRightMovesFocusToNextFilter() {
        openSportMate()
        val eventNodes = composeRule.onAllNodes(hasEventTag).fetchSemanticsNodes()
        if (eventNodes.isNotEmpty()) {
            composeRule.onAllNodes(hasEventTag)[0]
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionUp) }
        }
        composeRule.onNodeWithTag("filter-all")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithTag("filter-football").assertIsFocused()
    }

    /**
     * Back out of Settings and the guide is still there.
     *
     * Both ends are named by test tag rather than by the words on a button.
     * The guide no longer carries a title bar with a Settings button in it -
     * source, sorting and settings moved behind one options control so the grid
     * could have the screen - so the route in is the empty guide's own prompt.
     */
    @Test
    fun backReturnsFromSettingsToThePreviousGuideScreen() {
        composeRule.onNodeWithTag("home-live").performClick()
        composeRule.onNodeWithTag("guide-empty-settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings-back").assertIsDisplayed()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()

        composeRule.onNodeWithTag("guide-empty-settings").assertIsDisplayed()
    }

    private fun openSportMate() {
        composeRule.onNodeWithTag("home-sportmate").performClick()
    }

    private val hasEventTag = SemanticsMatcher("has SportMate event tag") { node ->
        node.config.contains(SemanticsProperties.TestTag) &&
            node.config[SemanticsProperties.TestTag].startsWith("event-")
    }

}
