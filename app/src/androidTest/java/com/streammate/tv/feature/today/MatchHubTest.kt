package com.streammate.tv.feature.today

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import com.streammate.tv.core.model.FootballIncidentKind
import com.streammate.tv.core.model.FootballIncident
import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.MatchCandidateSource
import com.streammate.tv.matching.ManualMatchDecision
import com.streammate.tv.testing.awaitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The match hub used to be a Dialog, which brought its own window and with it
 * free focus containment and a free Back button. Now that it is part of the
 * screen, both are ours to get right - and on a remote there is no way to click
 * outside, so getting them wrong strands the viewer.
 */
@RunWith(AndroidJUnit4::class)
class MatchHubTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var played = mutableListOf<String>()

    private fun showHubScreen(streams: List<EventChannelMatch> = MATCHES) {
        composeRule.activity.setContent {
            StreamMateTheme {
                TodayScreen(
                    uiState = TodayUiState(
                        events = listOf(EVENT),
                        matches = mapOf(EVENT.id to streams),
                        followedSports = setOf(SportType.FOOTBALL),
                        eventDetails = mapOf(
                            EVENT.id to EventDetailsUiState(incidents = INCIDENTS, isLoaded = true),
                        ),
                    ),
                    onRefresh = {},
                    onLoadDetails = {},
                    onRefreshDetails = {},
                    onMatchDecision = { _, _, _ -> },
                    onGuide = {},
                    onSettings = {},
                    onPlay = { played += it },
                )
            }
        }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("event-${EVENT.id}").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openHub(streams: List<EventChannelMatch> = MATCHES) {
        showHubScreen(streams)
        composeRule.onNodeWithTag("event-${EVENT.id}").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("streams-panel").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun openingTheHubPutsFocusOnTheFirstStream() {
        openHub()

        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag(WATCH_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(WATCH_TAG).assertIsFocused()
    }

    @Test
    fun focusCannotEscapeBackToTheListBehindTheHub() {
        openHub()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag(WATCH_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        // Push in every direction, hard. Nothing may land on the match list.
        listOf(Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown, Key.DirectionRight).forEach { key ->
            repeat(4) {
                composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
                composeRule.waitForIdle()
            }
        }

        val strandedOnTheList = composeRule.onAllNodesWithTag("event-${EVENT.id}")
            .fetchSemanticsNodes()
            .any { node -> node.config.getOrNull(FocusedKey) == true }
        assertTrue("focus escaped the hub and landed back on the match list", !strandedOnTheList)
    }

    @Test
    fun backClosesTheHub() {
        openHub()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("streams-panel").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun watchingAStreamReportsTheChannelBehindIt() {
        openHub()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag(WATCH_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(WATCH_TAG).performClick()

        composeRule.runOnIdle { assertEquals(listOf("channel-1"), played) }
    }

    @Test
    fun theIncidentListScrollsPastWhatFitsOnScreen() {
        openHub()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("incident-list").fetchSemanticsNodes().isNotEmpty()
        }

        // Nothing in this list is actionable, so nothing in it was focusable and
        // the remote had no way to move it. Reaching it is a left press away
        // from the streams, and then down must actually move it.
        composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("incident-list").assertIsFocused()

        val before = firstIncidentTop()
        repeat(3) {
            composeRule.onNodeWithTag("incident-list").performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.waitForIdle()
        }

        assertTrue(
            "the incident list did not move, so anything past the visible few is unreachable",
            firstIncidentTop() < before - 1f,
        )
    }

    private fun firstIncidentTop(): Float = composeRule
        .onNodeWithTag("incident-list")
        .fetchSemanticsNode()
        .children
        .first()
        .positionInRoot
        .y

    @Test
    fun aHubWithNothingWatchableStillPutsFocusSomewhere() {
        // The dialog this replaced handed focus to whatever it found. A match
        // whose channels are only possible has no Watch button, and pinning
        // focus to Watch left the hub open with nothing highlighted and the
        // remote doing nothing at all.
        openHub(listOf(POSSIBLE_MATCH))

        composeRule.awaitUntil { focusedTagOrNull() != null }
        composeRule.onNodeWithTag("match-confirm-channel-2").assertIsFocused()
    }

    @Test
    fun confirmingAChannelKeepsFocusInsideTheHubOnItsNewWatchButton() {
        var streams by mutableStateOf(listOf(POSSIBLE_MATCH))
        composeRule.activity.setContent {
            StreamMateTheme {
                TodayScreen(
                    uiState = TodayUiState(
                        events = listOf(EVENT),
                        matches = mapOf(EVENT.id to streams),
                        followedSports = setOf(SportType.FOOTBALL),
                    ),
                    onRefresh = {},
                    onLoadDetails = {},
                    onRefreshDetails = {},
                    onMatchDecision = { _, channelId, decision ->
                        streams = streams.map { match ->
                            if (match.channelId != channelId) match else match.copy(
                                confidence = if (decision == ManualMatchDecision.CONFIRMED) {
                                    ChannelMatchConfidence.AVAILABLE
                                } else {
                                    match.confidence
                                },
                                manualDecision = decision,
                            )
                        }
                    },
                    onGuide = {},
                    onSettings = {},
                    onPlay = {},
                )
            }
        }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("event-${EVENT.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("event-${EVENT.id}").performClick()
        composeRule.awaitUntil { focusedTagOrNull() == "match-confirm-channel-2" }

        composeRule.onNodeWithTag("match-confirm-channel-2").performClick()

        composeRule.awaitUntil { focusedTagOrNull() == "match-watch-channel-2" }
        composeRule.onNodeWithTag("match-watch-channel-2").assertIsFocused()
    }

    @Test
    fun aHubWithNoStreamsAtAllFallsBackToClose() {
        openHub(emptyList())

        composeRule.awaitUntil { focusedTagOrNull() != null }
        composeRule.onNodeWithTag("match-close").assertIsFocused()
    }

    @Test
    fun theFirstStreamRowIsNotClippedWhenTheHubOpens() {
        // Focus lands on a control near the bottom of the first row, and the
        // scrollable brings that control into view. If the row is taller than
        // the room the panel has, the top of it is scrolled away - the channel
        // name and its status, which is the part worth reading.
        openHub(List(4) { index -> POSSIBLE_MATCH.copy(channelId = "channel-$index") })
        composeRule.awaitUntil { focusedTagOrNull() != null }

        val list = composeRule.onNodeWithTag("streams-list").fetchSemanticsNode()
        val firstRowTop = list.children.first().positionInRoot.y

        assertTrue(
            "the first stream row is clipped: its top is ${list.positionInRoot.y - firstRowTop}px " +
                "above the list",
            firstRowTop >= list.positionInRoot.y - 0.5f,
        )
    }

    private fun focusedTagOrNull(): String? = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private companion object {
        val FocusedKey = androidx.compose.ui.semantics.SemanticsProperties.Focused

        const val WATCH_TAG = "match-watch-channel-1"

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
            detailsAvailable = true,
        )

        // Comfortably more than fits the panel, which is the case that used to
        // hide everything past the fifth.
        val INCIDENTS = (1..20).map { minute ->
            FootballIncident(
                id = "incident-$minute",
                eventId = "event-1",
                elapsedMinutes = minute * 4,
                extraMinutes = null,
                kind = FootballIncidentKind.GOAL,
                detail = "Goal $minute",
                comments = null,
                teamName = if (minute % 2 == 0) "Home FC" else "Away United",
                actorName = "Player $minute",
                relatedName = null,
            )
        }

        val POSSIBLE_MATCH = EventChannelMatch(
            eventId = "event-1",
            channelId = "channel-2",
            channelName = "Sport 2 HD",
            programmeId = "programme-2",
            programmeTitle = "Home FC v Away United",
            programmeStartEpochMillis = 0,
            startOffsetMinutes = 4,
            confidence = ChannelMatchConfidence.POSSIBLE,
            score = 40,
            manualDecision = null,
            source = MatchCandidateSource.M3U_CHANNEL_NAME,
            hasExplicitStartTime = false,
        )

        val MATCHES = listOf(
            EventChannelMatch(
                eventId = EVENT.id,
                channelId = "channel-1",
                channelName = "Sport 1 FHD FI",
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
