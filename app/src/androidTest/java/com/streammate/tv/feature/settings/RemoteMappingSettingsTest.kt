package com.streammate.tv.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.app.MainActivity
import com.streammate.tv.testing.awaitUntil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The remote grid: cells open the action list, a choice lands back on the cell, reset restores. */
@RunWith(AndroidJUnit4::class)
class RemoteMappingSettingsTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)

    @Before
    fun openRemoteSection() {
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("home-live").performClick()
        composeRule.onNodeWithTag("guide-empty-settings").performClick()
        composeRule.onNodeWithTag("settings-section-remote").performClick()
    }

    @Test
    fun theGridShowsTheDefaultsAndKeepsBackPressOutOfIt() {
        scrollTo("settings-remote-slot-up-press")
        composeRule.onNodeWithTag("settings-remote-slot-up-press").assert(hasText("Channel list"))
        composeRule.onNodeWithTag("settings-remote-slot-up-hold").assert(hasText("Next channel"))
        scrollTo("settings-remote-slot-back-hold")
        composeRule.onNodeWithTag("settings-remote-slot-back-hold").assert(hasText("Switch to previous channel"))
        composeRule.onNodeWithTag("settings-remote-slot-back-press").assertDoesNotExist()
        scrollTo("settings-remote-slot-menu-press")
        composeRule.onNodeWithTag("settings-remote-slot-menu-press").assert(hasText("Quick actions"))
    }

    @Test
    fun choosingAnActionAssignsItAndResetPutsTheDefaultBack() {
        scrollTo("settings-remote-slot-up-press")
        composeRule.onNodeWithTag("settings-remote-slot-up-press").performClick()
        composeRule.onNodeWithTag("settings-remote-action-open_channel_browser").assertIsDisplayed()
        scrollTo("settings-remote-action-go_home")
        composeRule.onNodeWithTag("settings-remote-action-go_home").performClick()

        scrollTo("settings-remote-slot-up-press")
        composeRule.onNodeWithTag("settings-remote-slot-up-press").assert(hasText("Home"))
        // Only the edited cell changed.
        composeRule.onNodeWithTag("settings-remote-slot-up-hold").assert(hasText("Next channel"))

        scrollTo("settings-remote-reset")
        composeRule.onNodeWithTag("settings-remote-reset").performClick()
        composeRule.onNodeWithTag("settings-remote-reset-confirm").performClick()
        scrollTo("settings-remote-slot-up-press")
        composeRule.onNodeWithTag("settings-remote-slot-up-press").assert(hasText("Channel list"))
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    }
}
