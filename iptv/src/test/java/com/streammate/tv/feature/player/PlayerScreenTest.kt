package com.streammate.tv.feature.player

import android.view.KeyEvent
import androidx.media3.common.Player
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenTest {
    @Test
    fun liveChannelsDoNotShowTransportControls() {
        assertFalse(
            shouldShowTransportControls(
                catchupStartEpochMillis = null,
                catchupStopEpochMillis = null,
                vodContentKey = null,
            ),
        )
    }

    @Test
    fun catchupShowsTransportControls() {
        assertTrue(
            shouldShowTransportControls(
                catchupStartEpochMillis = 1_000L,
                catchupStopEpochMillis = 2_000L,
                vodContentKey = null,
            ),
        )
    }

    @Test
    fun videoOnDemandShowsTransportControls() {
        assertTrue(
            shouldShowTransportControls(
                catchupStartEpochMillis = null,
                catchupStopEpochMillis = null,
                vodContentKey = "movie:example",
            ),
        )
    }

    @Test
    fun playbackEndNotificationIsVodOnlyAndOneShot() {
        assertTrue(shouldNotifyVodPlaybackEnded(Player.STATE_ENDED, "vod:episode:source:1", false))
        assertFalse(shouldNotifyVodPlaybackEnded(Player.STATE_READY, "vod:episode:source:1", false))
        assertFalse(shouldNotifyVodPlaybackEnded(Player.STATE_ENDED, null, false))
        assertFalse(shouldNotifyVodPlaybackEnded(Player.STATE_ENDED, "vod:episode:source:1", true))
    }

    @Test
    fun liveProgrammeHelpersSelectCurrentNextAndProgress() {
        val current = programme("current", 1_000L, 2_000L)
        val next = programme("next", 2_000L, 3_000L)
        val channel = timelineChannel(listOf(current, next))

        assertEquals(current, channel.currentProgrammeAt(1_500L))
        assertEquals(next, channel.nextProgrammeAfter(current, 1_500L))
        assertEquals(0.5f, current.progressAt(1_500L) ?: -1f, 0.001f)
        assertNull(current.progressAt(3_000L))
    }

    @Test
    fun trackLanguagesNormalizeProviderAndRegionalCodes() {
        assertEquals("fi", normalizeTrackLanguage("fin"))
        assertEquals("fi", normalizeTrackLanguage("fi-FI"))
        assertEquals("en", normalizeTrackLanguage("eng"))
        assertEquals("sv", normalizeTrackLanguage("swe"))
        assertEquals("no", normalizeTrackLanguage("nob"))
        assertEquals("de", normalizeTrackLanguage("deu"))
        assertNull(normalizeTrackLanguage(null))
    }

    @Test
    fun primaryAudioTurnsAutomaticVodSubtitlesOff() {
        assertTrue(
            shouldDisableAutomaticVodSubtitles(
                primaryAudioLanguage = "fi",
                availableAudioLanguages = listOf("eng", "fin"),
            ),
        )
    }

    @Test
    fun nonPrimaryOrSingleForeignAudioKeepsPreferredVodSubtitlesEnabled() {
        assertFalse(
            shouldDisableAutomaticVodSubtitles(
                primaryAudioLanguage = "fi",
                availableAudioLanguages = listOf("eng"),
            ),
        )
        assertFalse(
            shouldDisableAutomaticVodSubtitles(
                primaryAudioLanguage = null,
                availableAudioLanguages = listOf("fin"),
            ),
        )
    }

    @Test
    fun backDismissesVisiblePlayerLayersBeforeExiting() {
        assertEquals(
            PlayerBackAction.DISMISS_TRACK_PICKER,
            playerBackAction(
                trackPickerVisible = true,
                channelGroupBrowserVisible = true,
                channelBrowserVisible = true,
                chromeVisible = true,
            ),
        )
        assertEquals(
            PlayerBackAction.DISMISS_CHANNEL_GROUP_BROWSER,
            playerBackAction(
                trackPickerVisible = false,
                channelGroupBrowserVisible = true,
                channelBrowserVisible = true,
                chromeVisible = true,
            ),
        )
        assertEquals(
            PlayerBackAction.DISMISS_CHANNEL_BROWSER,
            playerBackAction(
                trackPickerVisible = false,
                channelGroupBrowserVisible = false,
                channelBrowserVisible = true,
                chromeVisible = true,
            ),
        )
        assertEquals(
            PlayerBackAction.DISMISS_CHROME,
            playerBackAction(
                trackPickerVisible = false,
                channelGroupBrowserVisible = false,
                channelBrowserVisible = false,
                chromeVisible = true,
            ),
        )
        assertEquals(
            PlayerBackAction.EXIT_PLAYER,
            playerBackAction(
                trackPickerVisible = false,
                channelGroupBrowserVisible = false,
                channelBrowserVisible = false,
                chromeVisible = false,
            ),
        )
    }

    @Test
    fun backDoesNotRevealHiddenPlayerChromeBeforeExit() {
        assertFalse(playerKeyRevealsChrome(KeyEvent.KEYCODE_BACK))
        assertTrue(playerKeyRevealsChrome(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(playerKeyRevealsChrome(KeyEvent.KEYCODE_DPAD_UP))
    }

    private fun programme(id: String, start: Long, stop: Long) = GuideTimelineProgramme(
        id = id,
        title = id,
        subtitle = null,
        description = null,
        categories = emptyList(),
        startEpochMillis = start,
        stopEpochMillis = stop,
    )

    @Test
    fun theChannelBrowserOffersOnlyTheGroupBeingWatched() {
        val channels = listOf(
            guideChannel("a", "Urheilu"),
            guideChannel("b", "Uutiset"),
            guideChannel("c", "Urheilu"),
            guideChannel("d", null),
        )

        assertEquals(
            listOf("a", "c"),
            playerBrowserChannels(channels, "a").map { it.id },
        )
    }

    @Test
    fun aChannelWithNoGroupOfItsOwnKeepsTheWholeList() {
        val channels = listOf(
            guideChannel("a", "Urheilu"),
            guideChannel("b", null),
        )

        assertEquals(
            listOf("a", "b"),
            playerBrowserChannels(channels, "b").map { it.id },
        )
    }

    @Test
    fun channelBrowserGroupsKeepProviderOrderAndSkipUngroupedChannels() {
        val channels = listOf(
            guideChannel("a", "Urheilu"),
            guideChannel("b", "Uutiset"),
            guideChannel("c", "Urheilu"),
            guideChannel("d", null),
        )

        assertEquals(listOf("Urheilu", "Uutiset"), playerBrowserGroups(channels))
        assertEquals(
            listOf("b"),
            playerBrowserChannelsForGroup(channels, "Uutiset").map { it.id },
        )
    }

    @Test
    fun dismissingATemporaryGroupCanReturnToThePlayingChannelsGroup() {
        val channels = listOf(
            guideChannel("sport-1", "Urheilu"),
            guideChannel("news-1", "Uutiset"),
            guideChannel("sport-2", "Urheilu"),
        )

        val playingGroup = playerBrowserGroup(channels, "sport-1")
        assertEquals("Urheilu", playingGroup)
        assertEquals(
            listOf("sport-1", "sport-2"),
            playerBrowserChannelsForGroup(channels, playingGroup).map { it.id },
        )
    }

    @Test
    fun theBrowserHighlightTravelsToTheMiddleBeforeTheListMoves() {
        // seven rows on screen, so the middle is the fourth
        assertEquals(0, playerBrowserScrollTarget(selectedIndex = 0, rowsOnScreen = 7))
        assertEquals(0, playerBrowserScrollTarget(selectedIndex = 3, rowsOnScreen = 7))
        // past the middle the list starts carrying it
        assertEquals(1, playerBrowserScrollTarget(selectedIndex = 4, rowsOnScreen = 7))
        assertEquals(7, playerBrowserScrollTarget(selectedIndex = 10, rowsOnScreen = 7))
    }

    private fun guideChannel(id: String, group: String?) = com.streammate.tv.iptv.repository.GuideChannel(
        sourceId = "src",
        sourceName = "Source",
        sourcePriority = 0,
        id = id,
        name = id.uppercase(),
        groupTitle = group,
        logoUrl = null,
        playlistOrder = 0,
        currentProgrammeTitle = null,
        currentProgrammeSubtitle = null,
        programmeStartEpochMillis = null,
        programmeStopEpochMillis = null,
    )

    private fun timelineChannel(programmes: List<GuideTimelineProgramme>) = GuideTimelineChannel(
        sourceId = "source",
        sourceName = "Source",
        sourcePriority = 0,
        id = "channel",
        name = "Channel",
        groupTitle = null,
        logoUrl = null,
        playlistOrder = 0,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        programmes = programmes,
    )
}
