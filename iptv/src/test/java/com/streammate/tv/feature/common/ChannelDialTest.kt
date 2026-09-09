package com.streammate.tv.feature.common

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelDialTest {
    // Four channels: no number of its own at 1, own numbers 40 and 12, none at 4.
    private val shown = listOf<Int?>(null, 40, 12, null)

    @Test
    fun `a channel's own number wins, and a position counts only where nothing else shows there`() {
        assertEquals(2, ChannelDial.indexFor(shown, 12))
        assertEquals(1, ChannelDial.indexFor(shown, 40))
        assertEquals(0, ChannelDial.indexFor(shown, 1))
        assertEquals(3, ChannelDial.indexFor(shown, 4))
        // Position 2 shows 40, not 2, so dialling 2 finds nothing.
        assertNull(ChannelDial.indexFor(shown, 2))
        assertNull(ChannelDial.indexFor(shown, 9))
        assertNull(ChannelDial.indexFor(emptyList(), 1))
    }

    @Test
    fun `digits come from the number row and the keypad, and from nothing else`() {
        assertEquals(0, ChannelDial.digitOf(KeyEvent.KEYCODE_0))
        assertEquals(7, ChannelDial.digitOf(KeyEvent.KEYCODE_7))
        assertEquals(3, ChannelDial.digitOf(KeyEvent.KEYCODE_NUMPAD_3))
        assertNull(ChannelDial.digitOf(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(ChannelDial.digitOf(KeyEvent.KEYCODE_ENTER))
    }
}
