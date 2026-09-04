package com.streammate.tv.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.streammate.tv.app.MainActivity
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.testing.awaitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class HomeScreenTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Outside the compose rule so persisted state is reset before the activity
    // launches, not after it has already rendered.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)

    @Before
    fun waitForLandingPage() {
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-hero-primary").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * The destinations used to be six tiles across the middle of the screen and
     * the first of them took focus. They are a rail down the edge now, and the
     * thing worth pointing at on arrival is whatever the hero offers to play -
     * so focus lands there instead.
     */
    @Test
    fun theHeroActionReceivesInitialFocus() {
        composeRule.onNodeWithTag("home-hero-primary").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun theRailShowsEveryDestinationWithoutCatchup() {
        DESTINATIONS.forEach { id ->
            composeRule.onNodeWithTag("home-$id").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("home-nav-home").assertIsDisplayed()
        composeRule.onAllNodesWithTag("home-catch-up").assertCountEquals(0)
    }

    /**
     * Left off the hero reaches the rail, and the rail walks top to bottom.
     *
     * Which rail entry the sideways move lands on depends on where the hero's
     * button happens to sit against the column, so the walk is anchored by
     * pressing up first: above Live TV is the Home marker, which is a label
     * rather than a control, so focus has nowhere further to go.
     */
    @Test
    fun theRailIsReachableFromTheHeroAndWalksWithTheDpad() {
        pressOnFocused(Key.DirectionLeft)
        composeRule.waitForIdle()
        assertTrue(
            "left off the hero landed on ${focusedTag()} rather than in the rail",
            focusedTag() in DESTINATIONS.map { "home-$it" },
        )

        repeat(DESTINATIONS.size) {
            pressOnFocused(Key.DirectionUp)
            composeRule.waitForIdle()
        }
        assertEquals("home-live", focusedTag())

        (1 until DESTINATIONS.size).forEach { step ->
            pressOnFocused(Key.DirectionDown)
            composeRule.waitForIdle()
            assertEquals("home-${DESTINATIONS[step]}", focusedTag())
        }
    }

    @Test
    fun emptyGuideSettingsReceivesInitialFocus() {
        composeRule.onNodeWithTag("home-live").performClick()

        composeRule.onNodeWithTag("guide-empty-settings")
            .assertIsDisplayed()
            .assertIsFocused()
    }

    /**
     * The rail is drawn over the rows rather than beside them, and it widens
     * when it takes focus, so by the time you want to leave it the card you
     * came from is underneath it. A search to the right finds nothing and you
     * are stuck on the menu.
     */
    @Test
    fun rightOutOfTheRailReturnsToTheRows() {
        pressOnFocused(Key.DirectionLeft)
        composeRule.waitForIdle()
        assertTrue("expected to be in the rail, was on ${focusedTag()}", focusedTag()?.startsWith("home-") == true)

        pressOnFocused(Key.DirectionRight)
        composeRule.waitForIdle()

        assertEquals("home-hero-primary", focusedTag())
    }

    /**
     * Back in the rail used to fall through to the activity and close the app,
     * which is a long way to go for "I did not mean to open the menu".
     */
    @Test
    fun backOutOfTheRailReturnsToTheRowsRatherThanLeavingTheApp() {
        pressOnFocused(Key.DirectionLeft)
        composeRule.waitForIdle()
        assertTrue("expected to be in the rail, was on ${focusedTag()}", focusedTag()?.startsWith("home-") == true)

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals("home-hero-primary", focusedTag())
        assertTrue("the activity was finishing", !composeRule.activity.isFinishing)
    }

    private fun pressOnFocused(key: Key) {
        composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
    }

    private fun focusedTag(): String? = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .firstNotNullOfOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
        }

    private companion object {
        val DESTINATIONS = listOf("live", "sportmate", "movies", "series", "search", "settings")
    }
}
