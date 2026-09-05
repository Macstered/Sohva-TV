package com.streammate.tv.app

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMappingTest {
    @Test
    fun `defaults reproduce what the buttons did before mapping existed`() {
        val defaults = RemoteMappings.DEFAULTS
        assertEquals(RemoteAction.OPEN_CHANNEL_BROWSER, defaults[RemoteSlot(RemoteButton.UP, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.SEEK_FORWARD, defaults[RemoteSlot(RemoteButton.RIGHT, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.QUICK_ACTIONS, defaults[RemoteSlot(RemoteButton.OK, RemoteGesture.HOLD)])
        assertEquals(RemoteAction.PREVIOUS_CHANNEL, defaults[RemoteSlot(RemoteButton.CHANNEL_UP, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.TOGGLE_STATS, defaults[RemoteSlot(RemoteButton.INFO, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.NOTHING, defaults[RemoteSlot(RemoteButton.INFO, RemoteGesture.HOLD)])
    }

    @Test
    fun `zap back sits on the holds nobody could reach before`() {
        val defaults = RemoteMappings.DEFAULTS
        assertEquals(RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, defaults[RemoteSlot(RemoteButton.BACK, RemoteGesture.HOLD)])
        assertEquals(RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL, defaults[RemoteSlot(RemoteButton.LEFT, RemoteGesture.HOLD)])
        assertEquals(RemoteAction.GUIDE_AT_CHANNEL, defaults[RemoteSlot(RemoteButton.RIGHT, RemoteGesture.HOLD)])
    }

    @Test
    fun `back press is fixed and cannot be mapped or stored`() {
        val backPress = RemoteSlot(RemoteButton.BACK, RemoteGesture.PRESS)
        assertTrue(backPress.fixed)
        assertFalse(backPress in RemoteSlot.MAPPABLE)
        val mappings = RemoteMappings.DEFAULTS.with(backPress, RemoteAction.GO_HOME)
        assertEquals(RemoteAction.NOTHING, mappings[backPress])
        assertTrue(RemoteMappings.decode(setOf("BACK.PRESS=GO_HOME")).actions.isEmpty())
    }

    @Test
    fun `mappings survive a round trip through their stored form`() {
        val edited = RemoteMappings.DEFAULTS
            .with(RemoteSlot(RemoteButton.UP, RemoteGesture.PRESS), RemoteAction.NEXT_CHANNEL)
            .with(RemoteSlot(RemoteButton.MENU, RemoteGesture.HOLD), RemoteAction.GO_SPORT)
            .with(RemoteSlot(RemoteButton.OK, RemoteGesture.HOLD), RemoteAction.NOTHING)

        val restored = RemoteMappings.decode(edited.encode())

        assertEquals(edited, restored)
        assertEquals(RemoteAction.NOTHING, restored[RemoteSlot(RemoteButton.OK, RemoteGesture.HOLD)])
        assertFalse(edited.encode().any { it.contains("NOTHING") })
    }

    @Test
    fun `unknown slots and actions from a newer build are dropped, not fatal`() {
        val restored = RemoteMappings.decode(
            setOf("UP.PRESS=NEXT_CHANNEL", "WHEEL.PRESS=NEXT_CHANNEL", "DOWN.HOLD=TELEPORT", "garbage"),
        )
        assertEquals(mapOf(RemoteSlot(RemoteButton.UP, RemoteGesture.PRESS) to RemoteAction.NEXT_CHANNEL), restored.actions)
    }

    @Test
    fun `the legacy channel key setting decides the defaults until something is stored`() {
        val channelKeysOnly = RemoteMappings.fromStored(null, RemoteChannelKeyMode.CHANNEL_KEYS_ONLY)
        assertEquals(RemoteAction.NOTHING, channelKeysOnly[RemoteSlot(RemoteButton.UP, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.NOTHING, channelKeysOnly[RemoteSlot(RemoteButton.DOWN, RemoteGesture.PRESS)])
        assertEquals(RemoteAction.NEXT_CHANNEL, channelKeysOnly[RemoteSlot(RemoteButton.UP, RemoteGesture.HOLD)])

        assertEquals(RemoteMappings.DEFAULTS, RemoteMappings.fromStored(null, RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS))
        // Once anything is stored, the legacy setting no longer has a say.
        assertEquals(
            RemoteMappings(mapOf(RemoteSlot(RemoteButton.UP, RemoteGesture.PRESS) to RemoteAction.GO_HOME)),
            RemoteMappings.fromStored(setOf("UP.PRESS=GO_HOME"), RemoteChannelKeyMode.CHANNEL_KEYS_ONLY),
        )
    }

    @Test
    fun `actions know where they apply`() {
        assertTrue(RemoteAction.NEXT_CHANNEL.appliesTo(live = true))
        assertFalse(RemoteAction.NEXT_CHANNEL.appliesTo(live = false))
        assertFalse(RemoteAction.SEEK_BACK.appliesTo(live = true))
        assertTrue(RemoteAction.SEEK_BACK.appliesTo(live = false))
        assertTrue(RemoteAction.AUDIO_PICKER.appliesTo(live = true))
        assertTrue(RemoteAction.AUDIO_PICKER.appliesTo(live = false))
    }
}

class RemoteKeyGestureResolverTest {
    private val resolver = RemoteKeyGestureResolver()

    @Test
    fun `a tap is a press, delivered on release`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_UP, RemoteKeyAction.DOWN, 0))
        assertEquals(
            RemoteKeyGesture.Press(RemoteButton.UP),
            resolver.resolve(KeyEvent.KEYCODE_DPAD_UP, RemoteKeyAction.UP, 0),
        )
    }

    @Test
    fun `a held key is a hold on its first repeat and nothing on release`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyAction.DOWN, 0))
        assertEquals(
            RemoteKeyGesture.Hold(RemoteButton.OK),
            resolver.resolve(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyAction.DOWN, 1),
        )
        assertTrue(resolver.isHolding(KeyEvent.KEYCODE_DPAD_CENTER))
        // Later repeats are swallowed, and so is the release.
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyAction.DOWN, 2))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyAction.DOWN, 7))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyAction.UP, 0))
        assertFalse(resolver.isHolding(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `enter and the centre key are the same OK button`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_ENTER, RemoteKeyAction.DOWN, 0))
        assertEquals(
            RemoteKeyGesture.Press(RemoteButton.OK),
            resolver.resolve(KeyEvent.KEYCODE_ENTER, RemoteKeyAction.UP, 0),
        )
    }

    @Test
    fun `a second key down abandons the first`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_LEFT, RemoteKeyAction.DOWN, 0))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, RemoteKeyAction.DOWN, 0))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_LEFT, RemoteKeyAction.UP, 0))
        assertEquals(
            RemoteKeyGesture.Press(RemoteButton.RIGHT),
            resolver.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, RemoteKeyAction.UP, 0),
        )
    }

    @Test
    fun `a release with nothing pending, and keys outside the grid, are ignored`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_UP, RemoteKeyAction.UP, 0))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_VOLUME_UP, RemoteKeyAction.DOWN, 0))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_VOLUME_UP, RemoteKeyAction.UP, 0))
        assertNull(resolver.resolve(KeyEvent.KEYCODE_BACK, RemoteKeyAction.DOWN, 3))
    }

    @Test
    fun `reset forgets a key that went down before an overlay took over`() {
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_DOWN, RemoteKeyAction.DOWN, 0))
        resolver.reset()
        assertNull(resolver.resolve(KeyEvent.KEYCODE_DPAD_DOWN, RemoteKeyAction.UP, 0))
    }
}
