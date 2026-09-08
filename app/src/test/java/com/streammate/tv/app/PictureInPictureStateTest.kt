package com.streammate.tv.app

import org.junit.Assert.assertFalse
import org.junit.Test

class PictureInPictureStateTest {
    @Test
    fun `nothing enters the corner unless a stream is on screen`() {
        val state = PictureInPictureState()
        assertFalse(state.shouldEnter())
        assertFalse(state.active)
    }

    /**
     * A TV launcher need not offer any way to reach a picture-in-picture
     * window - Projectivy on the Shield does not - so the corner is something
     * a viewer opts into rather than meets by surprise.
     */
    @Test
    fun `the corner is off until a viewer asks for it`() {
        assertFalse(AppPreferences().pictureInPictureEnabled)
    }
}
