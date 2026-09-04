package com.streammate.tv.feature.player

import com.streammate.tv.testing.awaitFocused
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.streammate.tv.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerChromeOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun playerChromeAndKeepAwakeFollowTheirIndependentLifecycles() {
        val keepAwakeActive = mutableStateOf(true)
        lateinit var hostView: View
        var initialKeepScreenOn: Boolean? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                hostView = LocalView.current
                if (initialKeepScreenOn == null) {
                    initialKeepScreenOn = hostView.keepScreenOn
                }
                if (keepAwakeActive.value) {
                    KeepScreenOnEffect()
                }
                PlayerChromeOverlay(channelName = "Testikanava", onBack = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-back").assertIsDisplayed()
        composeRule.onNodeWithText("Testikanava").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(hostView.keepScreenOn) }

        composeRule.runOnUiThread { keepAwakeActive.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(initialKeepScreenOn, hostView.keepScreenOn) }

        composeRule.mainClock.advanceTimeBy(PLAYER_CONTROLS_TIMEOUT_MILLIS + 500L)
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("player-back").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Testikanava").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun playerChromeReappearsAfterRemoteInteractionAndShowsPlaybackActions() {
        val visibilityKey = mutableIntStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                PlayerChromeOverlay(
                    channelName = "Testikanava",
                    onBack = {},
                    visibilityKey = visibilityKey.intValue,
                    aspectModeLabel = "Sovita",
                    onCycleAspectMode = {},
                    audioTrackLabel = "Suomi",
                    onCycleAudioTrack = {},
                    subtitleTrackLabel = "Pois",
                    onCycleSubtitleTrack = {},
                    onOpenExternal = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(PLAYER_CONTROLS_TIMEOUT_MILLIS + 500L)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("player-audio").fetchSemanticsNodes().isEmpty())

        composeRule.runOnUiThread { visibilityKey.intValue += 1 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("player-aspect").assertIsDisplayed()
        composeRule.onNodeWithTag("player-audio").assertIsDisplayed()
        composeRule.onNodeWithTag("player-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("player-external").assertIsDisplayed()
    }

    @Test
    fun vodControlsShowStreamTracksAndFocusPlayPauseOnRemoteEntry() {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                BottomTransportControls(
                    title = "Testielokuva",
                    isPlaying = true,
                    positionMillis = 60_000L,
                    durationMillis = 3_600_000L,
                    visibilityKey = 0,
                    focusRequestKey = 1,
                    aspectModeLabel = "Sovita",
                    audioTrackLabel = "Suomi",
                    subtitleTrackLabel = "Pois",
                    onBack = {},
                    onCycleAspectMode = {},
                    onCycleAudioTrack = {},
                    onCycleSubtitleTrack = {},
                    onRewind = {},
                    onPlayPause = {},
                    onForward = {},
                    onControlsFocusChanged = {},
                    onDismissed = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Testielokuva").assertIsDisplayed()
        composeRule.onNodeWithTag("player-audio").assertIsDisplayed()
        composeRule.onNodeWithTag("player-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("player-play-pause").assertIsFocused()
    }

    @Test
    fun trackPickerShowsEveryChoiceAndFocusesCurrentTrack() {
        // The overlay renders the labels it is handed - these are fixture data,
        // not resources, so they are asserted as given rather than looked up.
        val choices = listOf(
            PlayerTrackChoice("Track one", selected = false),
            PlayerTrackChoice("Track two", selected = true),
            PlayerTrackChoice("Track three", selected = false),
        )
        composeRule.activity.setContent {
            TrackSelectionOverlay(
                title = "Choose subtitles",
                choices = choices,
                onSelect = {},
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("player-track-picker").assertIsDisplayed()
        choices.forEach { choice ->
            composeRule.onNodeWithText(choice.label).assertIsDisplayed()
        }
        composeRule.awaitFocused("player-track-option-1")
    }

}
