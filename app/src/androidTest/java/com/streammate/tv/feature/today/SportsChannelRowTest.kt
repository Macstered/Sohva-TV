package com.streammate.tv.feature.today

import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.MatchCandidateSource
import com.streammate.tv.testing.awaitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sports channel row has to be reachable.
 *
 * It was drawn as a painted row with nothing focusable in it, so the section
 * could be read from across the room and never opened: pressing down from the
 * matches above simply went nowhere.
 */
@RunWith(AndroidJUnit4::class)
class SportsChannelRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var played: String? = null

    @Test
    fun theChannelRowCanBeFocused() {
        showScreen()

        composeRule.onNodeWithTag(CHANNEL_TAG).assertIsDisplayed()

        // Down from the match above should land in this section rather than
        // passing over it as though it were a picture. Clicking the match
        // would open the hub, which is a different journey.
        pressDownUntilOnTheChannel()

        assertTrue(
            "the sports channel section could not be reached with the D-pad",
            focusedTags().any { it == CHANNEL_TAG },
        )
    }

    @Test
    fun pressingAChannelPutsItOn() {
        showScreen()

        composeRule.onNodeWithTag(CHANNEL_TAG).performClick()

        assertEquals("channel-1", played)
    }

    private fun pressDownUntilOnTheChannel() {
        repeat(4) {
            if (focusedTags().any { tag -> tag == CHANNEL_TAG }) return
            composeRule.onAllNodes(isFocused()).onFirst().performKeyInput {
                pressKey(Key.DirectionDown)
            }
            composeRule.waitForIdle()
        }
    }

    private fun focusedTags(): List<String> = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .mapNotNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
        }

    private fun showScreen() {
        composeRule.activity.setContent {
            StreamMateTheme {
                TodayScreen(
                    uiState = TodayUiState(
                        events = listOf(EVENT),
                        matches = mapOf(EVENT.id to MATCHES),
                        followedSports = setOf(SportType.FOOTBALL),
                    ),
                    onRefresh = {},
                    onLoadDetails = {},
                    onRefreshDetails = {},
                    onMatchDecision = { _, _, _ -> },
                    onGuide = {},
                    onSettings = {},
                    onPlay = { played = it },
                )
            }
        }
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(CHANNEL_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val CHANNEL_TAG = "sports-channel-channel-1"

        val EVENT = TodayEvent(
            id = "event-1",
            sport = SportType.FOOTBALL,
            competition = "Test League",
            home = "Home FC",
            away = "Away United",
            startMinuteOfDay = 20 * 60,
            startLabel = "20:00",
            status = TodayEventStatus.LIVE,
            statusLabel = "62′",
            score = "1 - 0",
            matchingChannels = 1,
        )

        // An available match is what puts a channel in the section below.
        val MATCHES = listOf(
            EventChannelMatch(
                eventId = "event-1",
                channelId = "channel-1",
                channelName = "Urheilukanava",
                programmeId = "programme-1",
                programmeTitle = "Home FC v Away United",
                programmeStartEpochMillis = 0,
                startOffsetMinutes = 0,
                confidence = ChannelMatchConfidence.AVAILABLE,
                score = 100,
                manualDecision = null,
                source = MatchCandidateSource.XMLTV_PROGRAMME,
                hasExplicitStartTime = true,
            ),
        )
    }
}
