package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.CleanScreen
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteDispatcher
import com.sohva.tv.core.model.player.RemoteMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 31 §11 "Dispatcher (pure, with a fake player)". */
class RemoteDispatcherTest {
    private class FakeScreen(override val live: Boolean, override var boxVisible: Boolean = false) : CleanScreen {
        val log = ArrayList<String>()
        var acts = true

        override fun perform(action: RemoteAction): Boolean {
            log += "perform $action"
            return acts
        }

        override fun reveal(focusPlayPause: Boolean) {
            log += if (focusPlayPause) "reveal play" else "reveal"
        }

        override fun focusBox() {
            log += "focusBox"
        }

        override fun showControls() {
            log += "controls"
        }

        override fun dial(digit: Int) {
            log += "dial $digit"
        }
    }

    private val dispatcher = RemoteDispatcher()

    private fun tap(screen: CleanScreen, code: Int): Pair<Boolean, Boolean> =
        dispatcher.onKey(code, down = true, repeatCount = 0, screen = screen) to dispatcher.onKey(code, down = false, repeatCount = 0, screen = screen)

    private fun hold(screen: CleanScreen, code: Int, repeats: Int = 5): List<Boolean> = buildList {
        add(dispatcher.onKey(code, true, 0, screen))
        for (r in 1..repeats) add(dispatcher.onKey(code, true, r, screen))
        add(dispatcher.onKey(code, false, 0, screen))
    }

    @Test
    fun liveLeftPressShowsTheChrome() {
        val screen = FakeScreen(live = true)
        assertEquals(true to true, tap(screen, LEFT))
        assertEquals(listOf("reveal"), screen.log)
    }

    @Test
    fun timeshiftUpPressFocusesTheControlsWithoutAskingTheMapping() {
        dispatcher.mapping = RemoteMapping.DEFAULTS.with(RemoteButton.UP, Gesture.PRESS, RemoteAction.GO_HOME)
        val screen = FakeScreen(live = false)
        tap(screen, UP)
        assertEquals(listOf("controls"), screen.log)
    }

    @Test
    fun liveUpPressStepsIntoAnOpenBoxJudgedAtKeyDown() {
        val screen = FakeScreen(live = true, boxVisible = true)
        dispatcher.onKey(UP, true, 0, screen)
        screen.boxVisible = false
        dispatcher.onKey(UP, false, 0, screen)
        assertEquals(listOf("focusBox"), screen.log)
    }

    @Test
    fun liveUpPressOpensTheChannelListAndAHoldZapsOnce() {
        val screen = FakeScreen(live = true)
        tap(screen, UP)
        assertEquals(listOf("perform OPEN_CHANNEL_BROWSER"), screen.log)
        screen.log.clear()
        assertTrue(hold(screen, UP, repeats = 20).all { it })
        assertEquals(listOf("perform NEXT_CHANNEL"), screen.log)
    }

    @Test
    fun okHoldOpensQuickActionsAndItsReleaseActivatesNothing() {
        val screen = FakeScreen(live = true)
        hold(screen, OK)
        assertEquals(listOf("perform QUICK_ACTIONS"), screen.log)
    }

    @Test
    fun backWithAHoldThatDoesNotApplyIsNotConsumedAtAll() {
        val screen = FakeScreen(live = false)
        assertEquals(listOf(false, false, false, false), hold(screen, BACK, repeats = 2))
        assertTrue(screen.log.isEmpty())
    }

    @Test
    fun liveBackHoldZapsBackAndSwallowsTheRelease() {
        val screen = FakeScreen(live = true)
        assertEquals(listOf(false, true, true, true), hold(screen, BACK, repeats = 2))
        assertEquals(listOf("perform SWITCH_TO_PREVIOUS_CHANNEL"), screen.log)
        // A short Back is left to the window.
        assertEquals(false to false, tap(screen, BACK))
    }

    @Test
    fun backHoldMappedToNothingIsAnOrdinaryBack() {
        dispatcher.mapping = RemoteMapping.DEFAULTS.with(RemoteButton.BACK, Gesture.HOLD, RemoteAction.NOTHING)
        val screen = FakeScreen(live = true)
        assertEquals(listOf(false, false, false), hold(screen, BACK, repeats = 1))
    }

    @Test
    fun digitsDialOnLiveAndAreLeftAloneInTimeshift() {
        val live = FakeScreen(live = true)
        assertEquals(true to true, tap(live, 8))
        assertEquals(true to true, tap(live, 150))
        assertEquals(listOf("dial 1", "dial 6"), live.log)
        val vod = FakeScreen(live = false)
        assertEquals(false to false, tap(vod, 8))
        assertTrue(vod.log.isEmpty())
    }

    @Test
    fun unknownKeysRevealOnTheFirstDownAndAreNotConsumed() {
        val screen = FakeScreen(live = true)
        assertFalse(dispatcher.onKey(MEDIA_PLAY_PAUSE, true, 0, screen))
        assertFalse(dispatcher.onKey(MEDIA_PLAY_PAUSE, true, 1, screen))
        assertFalse(dispatcher.onKey(MEDIA_PLAY_PAUSE, false, 0, screen))
        assertEquals(listOf("reveal"), screen.log)
    }

    @Test
    fun timeshiftFallbacks() {
        val screen = FakeScreen(live = false)
        tap(screen, CHANNEL_UP)
        tap(screen, LEFT)
        screen.acts = false
        tap(screen, OK)
        assertEquals(listOf("reveal", "perform SEEK_BACK", "perform PROGRAMME_INFO", "reveal play"), screen.log)
    }

    @Test
    fun aSecondKeyAbandonsTheFirst() {
        val screen = FakeScreen(live = true)
        dispatcher.onKey(UP, true, 0, screen)
        dispatcher.onKey(OK, true, 0, screen)
        dispatcher.onKey(UP, false, 0, screen)
        dispatcher.onKey(OK, false, 0, screen)
        assertEquals(listOf("perform PROGRAMME_INFO"), screen.log)
    }

    private companion object {
        const val UP = 19
        const val LEFT = 21
        const val OK = 23
        const val BACK = 4
        const val CHANNEL_UP = 166
        const val MEDIA_PLAY_PAUSE = 85
    }
}
