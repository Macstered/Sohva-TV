package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.DisplayRate
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.InfoLine
import com.sohva.tv.core.model.player.LiveFigures
import com.sohva.tv.core.model.player.PictureShape
import com.sohva.tv.core.model.player.PlaybackCause
import com.sohva.tv.core.model.player.PressHoldResolver
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.player.SkipLadder
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.StreamContainer
import com.sohva.tv.core.model.player.StreamStats
import com.sohva.tv.core.model.player.TrackLanguage
import com.sohva.tv.core.model.player.UserAgents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 30 §11 and spec 31 §11 unit tests for the pure parts of the player. */
class PlaybackRulesTest {
    @Test
    fun reconnectDelays() {
        assertEquals(listOf(2_000L, 4_000L, 6_000L), (1..3).map { ReconnectPolicy.STANDARD.delayBefore(it) })
        assertNull(ReconnectPolicy.STANDARD.delayBefore(4))
        assertEquals(listOf(2L, 4L, 8L, 16L, 30L, 30L, 30L, 30L), (1..8).map { ReconnectPolicy.PERSISTENT.delayBefore(it)!! / 1000 })
        assertNull(ReconnectPolicy.PERSISTENT.delayBefore(9))
        assertNull(ReconnectPolicy.STANDARD.delayBefore(0))
        assertEquals(ReconnectPolicy.STANDARD, ReconnectPolicy.fromStored("unknown"))
    }

    @Test
    fun skipLadderClimbsEveryThreePressesAndResets() {
        val ladder = SkipLadder(SkipStep.TEN_SECONDS)
        val distances = (0 until 10).map { ladder.skip(1, it * 500L) / 1000 }
        assertEquals(listOf(10L, 10, 10, 30, 30, 30, 60, 60, 60, 120), distances)
        assertEquals(10_000L, ladder.skip(1, 20_000))
        assertEquals(10_000L, ladder.skip(-1, 20_100))
        assertEquals(30_000L, SkipLadder(SkipStep.THIRTY_SECONDS).skip(1, 0))
        assertEquals("10 s", SkipLadder.label(10_000))
        assertEquals("1 min", SkipLadder.label(60_000))
        assertEquals("−2 min", SkipLadder.label(120_000, signed = true, negative = true))
        assertEquals("+30 s", SkipLadder.label(30_000, signed = true))
    }

    @Test
    fun containerFromTheAddress() {
        assertEquals(StreamContainer.HLS, StreamContainer.of("http://provider.example/live/1.M3U8?token=x#y"))
        assertEquals(StreamContainer.DASH, StreamContainer.of("https://provider.example/a.mpd/"))
        assertEquals(StreamContainer.SMOOTH, StreamContainer.of("https://provider.example/a.ism/Manifest"))
        assertEquals(StreamContainer.PROGRESSIVE, StreamContainer.of("http://provider.example/live/u/p/1.ts"))
        assertEquals(StreamContainer.PROGRESSIVE, StreamContainer.of("http://provider.example/live/u/p/1"))
    }

    @Test
    fun displayRatePick() {
        val rates = listOf(50f, 60f, 24f, 23.976f)
        assertEquals(50f, DisplayRate.pick(50f, rates))
        assertEquals(23.976f, DisplayRate.pick(23.976f, rates))
        assertEquals(24f, DisplayRate.pick(23.976f, listOf(24f, 48f, 60f)))
        assertEquals(50f, DisplayRate.pick(25f, listOf(50f, 60f)))
        assertNull(DisplayRate.pick(50f, listOf(60f)))
        assertNull(DisplayRate.pick(0f, rates))
        assertNull(DisplayRate.pick(50f, emptyList()))
    }

    @Test
    fun liveFigures() {
        assertEquals(0.25f, LiveFigures.fraction(0, 4_000, 1_000)!!, 0.0001f)
        assertNull(LiveFigures.fraction(0, 4_000, 4_000))
        assertNull(LiveFigures.fraction(10, 10, 10))
        assertEquals(24, LiveFigures.percent(0.249f))
        assertEquals(46, LiveFigures.minutesLeft(46 * 60_000L, 1))
        assertEquals(1, LiveFigures.minutesLeft(60_001, 60_000))
        assertNull(LiveFigures.minutesLeft(5, 5))
    }

    @Test
    fun infoLine() {
        val full = StreamStats(1920, 1080, 50f, "video/avc", "audio/mp4a-latm", 2, 5_400_000, "text/vtt", 3.24f)
        assertEquals("1920×1080 50p", InfoLine.resolution(full))
        assertEquals("AVC · MP4A-LATM 2.0", InfoLine.codecs(full))
        assertEquals("5.4 Mb/s", InfoLine.bitrate(full))
        assertEquals("3.2 s", InfoLine.buffer(full))
        assertEquals("1280×720", InfoLine.resolution(StreamStats(1280, 720)))
        assertNull(InfoLine.resolution(StreamStats()))
        assertNull(InfoLine.codecs(StreamStats()))
        assertEquals("MP4A-LATM", InfoLine.codecs(StreamStats(audioMime = "audio/mp4a-latm")))
        assertNull(InfoLine.bitrate(StreamStats(videoBitrate = 0)))
        assertEquals("0.0 s", InfoLine.buffer(StreamStats(bufferedSeconds = -1f)))
        assertEquals("5.1", InfoLine.layout(6))
        assertEquals("3 ch", InfoLine.layout(3))
    }

    @Test
    fun remoteDefaultsAndResolver() {
        val m = RemoteMapping.DEFAULTS
        assertEquals(RemoteAction.PREVIOUS_CHANNEL, m.action(RemoteButton.CHANNEL_UP, Gesture.PRESS))
        assertEquals(RemoteAction.NEXT_CHANNEL, m.action(RemoteButton.CHANNEL_DOWN, Gesture.PRESS))
        assertEquals(RemoteAction.NOTHING, m.action(RemoteButton.BACK, Gesture.PRESS))
        assertEquals(RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, m.action(RemoteButton.BACK, Gesture.HOLD))
        assertEquals(RemoteAction.NOTHING, m.action(RemoteButton.MENU, Gesture.HOLD))
        assertFalse(RemoteAction.SEEK_BACK.appliesTo(live = true))
        assertEquals(RemoteButton.OK, RemoteButton.of(160))
        assertNull(RemoteButton.of(7))

        val r = PressHoldResolver()
        assertEquals(PressHoldResolver.Result.None, r.down(RemoteButton.OK, 0))
        assertEquals(PressHoldResolver.Result.Press(RemoteButton.OK), r.up(RemoteButton.OK))
        r.down(RemoteButton.UP, 0)
        assertEquals(PressHoldResolver.Result.Hold(RemoteButton.UP), r.down(RemoteButton.UP, 1))
        assertEquals(PressHoldResolver.Result.None, r.down(RemoteButton.UP, 2))
        assertTrue(r.isHolding(RemoteButton.UP))
        assertEquals(PressHoldResolver.Result.None, r.up(RemoteButton.UP))
        // A second key abandons the first: its release does nothing.
        r.down(RemoteButton.LEFT, 0)
        r.down(RemoteButton.RIGHT, 0)
        assertEquals(PressHoldResolver.Result.None, r.up(RemoteButton.LEFT))
        assertEquals(PressHoldResolver.Result.Press(RemoteButton.RIGHT), r.up(RemoteButton.RIGHT))
    }

    @Test
    fun smallTables() {
        assertEquals(PictureShape.FILL, PictureShape.FIT.next())
        assertEquals(PictureShape.FIT, PictureShape.ZOOM.next())
        assertEquals(15_000, BufferProfile.LOW_LATENCY.maxBufferMs)
        assertEquals(32 * 1024 * 1024, BufferProfile.STABILITY.targetBytes(lowMemory = true))
        assertEquals("fi", TrackLanguage.normalize("FIN"))
        assertEquals("no", TrackLanguage.normalize("nob"))
        assertEquals("en", TrackLanguage.normalize("en-GB"))
        assertEquals("pt", TrackLanguage.normalize("pt_BR"))
        assertNull(TrackLanguage.normalize(" "))
        assertEquals(PlaybackCause.REFUSED, PlaybackCause.ofHttpStatus(403))
        assertEquals(PlaybackCause.GONE, PlaybackCause.ofHttpStatus(410))
        assertEquals(PlaybackCause.SERVER, PlaybackCause.ofHttpStatus(503))
        assertEquals("Sohva TV/? (Android TV 11)", UserAgents.sohva(null, "11"))
    }
}
