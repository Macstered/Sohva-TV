package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.ActionScope
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.PressHoldResolver
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.player.RemoteSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 31 §11 unit tests: defaults, the fixed Back press, the stored form and the legacy setting. */
class RemoteMappingTest {
    private val defaults = RemoteMapping.DEFAULTS

    @Test
    fun defaultsAreTheTableOfRemoteFr13() {
        val encoded = defaults.encode()
        assertEquals(17, encoded.size)
        assertEquals(RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, defaults.action(RemoteButton.BACK, Gesture.HOLD))
        assertEquals(RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, defaults.action(RemoteButton.LEFT, Gesture.HOLD))
        assertEquals(RemoteAction.GUIDE_AT_CHANNEL, defaults.action(RemoteButton.RIGHT, Gesture.HOLD))
        assertEquals(RemoteAction.PREVIOUS_CHANNEL, defaults.action(RemoteButton.CHANNEL_UP, Gesture.PRESS))
        assertEquals(RemoteAction.NEXT_CHANNEL, defaults.action(RemoteButton.CHANNEL_DOWN, Gesture.PRESS))
        for (b in listOf(RemoteButton.CHANNEL_UP, RemoteButton.CHANNEL_DOWN, RemoteButton.INFO, RemoteButton.AUDIO, RemoteButton.CAPTIONS, RemoteButton.MENU)) {
            assertEquals(b.name, RemoteAction.NOTHING, defaults.action(b, Gesture.HOLD))
        }
    }

    @Test
    fun backPressIsFixed() {
        assertEquals(23, RemoteSlots.MAPPABLE.size)
        assertFalse(RemoteSlots.MAPPABLE.contains(RemoteButton.BACK to Gesture.PRESS))
        assertSame(defaults, defaults.with(RemoteButton.BACK, Gesture.PRESS, RemoteAction.GO_HOME))
        assertEquals(RemoteAction.NOTHING, RemoteMapping.decode(setOf("BACK.PRESS=GO_HOME")).action(RemoteButton.BACK, Gesture.PRESS))
        assertTrue(RemoteMapping.decode(setOf("BACK.PRESS=GO_HOME")).encode().isEmpty())
    }

    @Test
    fun encodeAndDecodeRoundTripAndNothingIsNeverEncoded() {
        val edited = defaults.with(RemoteButton.UP, Gesture.PRESS, RemoteAction.GO_HOME).with(RemoteButton.OK, Gesture.HOLD, RemoteAction.NOTHING)
        val encoded = edited.encode()
        assertTrue("UP.PRESS=GO_HOME" in encoded)
        assertTrue(encoded.none { it.startsWith("OK.HOLD") })
        assertEquals(edited, RemoteMapping.decode(encoded))
    }

    @Test
    fun malformedAndUnknownEntriesAreDroppedAndEmptyMeansNothing() {
        val mapping = RemoteMapping.decode(setOf("UP.PRESS=GO_HOME", "UP.PRESS", "UP=GO_HOME", "UP.SWIPE=GO_HOME", "ZOOM.PRESS=GO_HOME", "DOWN.PRESS=FLY", "A=B=C"))
        assertEquals(setOf("UP.PRESS=GO_HOME"), mapping.encode())
        val empty = RemoteMapping.decode(emptySet())
        assertTrue(RemoteSlots.MAPPABLE.all { (b, g) -> empty.action(b, g) == RemoteAction.NOTHING })
    }

    @Test
    fun theLegacySettingCountsOnlyWhenNothingIsStored() {
        val legacy = RemoteMapping.decode(null, RemoteMapping.LEGACY_CHANNEL_KEYS_ONLY)
        assertEquals(RemoteAction.NOTHING, legacy.action(RemoteButton.UP, Gesture.PRESS))
        assertEquals(RemoteAction.NOTHING, legacy.action(RemoteButton.DOWN, Gesture.PRESS))
        assertEquals(RemoteAction.NEXT_CHANNEL, legacy.action(RemoteButton.UP, Gesture.HOLD))
        assertEquals(defaults, RemoteMapping.decode(null, "DPAD_AND_CHANNEL_KEYS"))
        assertEquals(defaults, RemoteMapping.decode(null))
        assertEquals(defaults, RemoteMapping.decode(defaults.encode(), RemoteMapping.LEGACY_CHANNEL_KEYS_ONLY))
    }

    @Test
    fun scopes() {
        for (a in listOf(RemoteAction.NEXT_CHANNEL, RemoteAction.PREVIOUS_CHANNEL, RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, RemoteAction.OPEN_CHANNEL_BROWSER, RemoteAction.OPEN_GROUP_BROWSER)) {
            assertEquals(ActionScope.LIVE, a.scope)
        }
        for (a in listOf(RemoteAction.PLAY_PAUSE, RemoteAction.SEEK_BACK, RemoteAction.SEEK_FORWARD, RemoteAction.RESTART, RemoteAction.SHOW_CONTROLS)) {
            assertEquals(ActionScope.TIMESHIFT, a.scope)
        }
        for (a in listOf(RemoteAction.AUDIO_PICKER, RemoteAction.NEXT_AUDIO_TRACK, RemoteAction.SUBTITLE_PICKER, RemoteAction.TOGGLE_SUBTITLES, RemoteAction.CYCLE_PICTURE_SHAPE)) {
            assertTrue(a.appliesTo(live = true) && a.appliesTo(live = false))
        }
    }

    @Test
    fun theResolverAllocatesNoResultPerEvent() {
        val resolver = PressHoldResolver()
        resolver.down(RemoteButton.OK, 0)
        val first = resolver.up(RemoteButton.OK)
        resolver.down(RemoteButton.OK, 0)
        assertSame(first, resolver.up(RemoteButton.OK))
        resolver.down(RemoteButton.UP, 0)
        val hold = resolver.down(RemoteButton.UP, 1)
        resolver.up(RemoteButton.UP)
        resolver.down(RemoteButton.UP, 0)
        assertSame(hold, resolver.down(RemoteButton.UP, 1))
    }
}
