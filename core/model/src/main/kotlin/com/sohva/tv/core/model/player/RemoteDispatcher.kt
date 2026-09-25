package com.sohva.tv.core.model.player

/** What the player's clean screen offers the dispatcher (spec 31 §4.6). */
interface CleanScreen {
    /** A live channel; false for catch-up and films ("timeshift"). */
    val live: Boolean

    /** Whether the live information box is on screen now. */
    val boxVisible: Boolean

    /** Performs [action]; false when it does not apply here or has nothing to act on. */
    fun perform(action: RemoteAction): Boolean

    /** Shows the chrome: the live box, or the timeshift controls (Play/Pause focused when [focusPlayPause]). */
    fun reveal(focusPlayPause: Boolean)

    /** Moves focus into the live box's action row. */
    fun focusBox()

    /** Shows the timeshift controls with Play/Pause focused. */
    fun showControls()

    /** Appends a digit to the channel dial (live only). */
    fun dial(digit: Int)
}

/**
 * The clean screen's key dispatch (spec 31 REMOTE-FR-18…27), as pure logic over key codes so it
 * runs in JVM tests with a fake player. One per player session (REMOTE-FR-09): a held key zaps once
 * however many channels it passes. Returns whether the event is consumed; an unconsumed Back
 * travels the window's Back route.
 */
class RemoteDispatcher(var mapping: RemoteMapping = RemoteMapping.DEFAULTS) {
    private val resolver = PressHoldResolver()
    private var boxAtDown = false

    /** Forgets a pending key, when an overlay takes the keys (REMOTE-FR-26). */
    fun reset() = resolver.reset()

    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, screen: CleanScreen): Boolean {
        val digit = digit(keyCode)
        if (digit != null) {
            // Live: the first key-down dials and every event is consumed; timeshift leaves digits alone.
            if (!screen.live) return false
            if (down && repeatCount == 0) screen.dial(digit)
            return true
        }
        val button = RemoteButton.of(keyCode)
        if (button == null) {
            // Outside the grid: reveal on the first down, leave the key to Android (REMOTE-FR-19).
            if (down && repeatCount == 0) screen.reveal(focusPlayPause = false)
            return false
        }
        if (button == RemoteButton.BACK) return back(down, repeatCount, screen)
        if (down) {
            if (repeatCount == 0) boxAtDown = screen.boxVisible
            // A hold acts and owns the rest of the key; no fallback for holds (REMOTE-FR-21).
            if (resolver.down(button, repeatCount) is PressHoldResolver.Result.Hold) screen.perform(mapping.action(button, Gesture.HOLD))
            return true
        }
        if (resolver.up(button) is PressHoldResolver.Result.Press) press(button, screen)
        return true
    }

    /**
     * REMOTE-FR-20. A Back hold that does not apply here, or is mapped to Nothing (Q-06), leaves Back
     * entirely to the window. Otherwise the first down is not consumed (Android tracks Back), a hold
     * acts, and the release after a hold is swallowed so no Back follows it.
     */
    private fun back(down: Boolean, repeatCount: Int, screen: CleanScreen): Boolean {
        val hold = mapping.action(RemoteButton.BACK, Gesture.HOLD)
        if (hold == RemoteAction.NOTHING || !hold.appliesTo(screen.live)) {
            resolver.reset()
            return false
        }
        if (down) {
            if (repeatCount == 0) {
                resolver.down(RemoteButton.BACK, 0)
                return false
            }
            if (resolver.down(RemoteButton.BACK, repeatCount) is PressHoldResolver.Result.Hold) screen.perform(hold)
            return true
        }
        val holding = resolver.isHolding(RemoteButton.BACK)
        resolver.up(RemoteButton.BACK)
        return holding
    }

    private fun press(button: RemoteButton, screen: CleanScreen) {
        // Up/Down step into what is open before the mapping is asked (REMOTE-FR-22).
        if (button == RemoteButton.UP || button == RemoteButton.DOWN) {
            if (boxAtDown && screen.live) {
                screen.focusBox()
                return
            }
            if (!screen.live) {
                screen.showControls()
                return
            }
        }
        val action = mapping.action(button, Gesture.PRESS)
        if (action.appliesTo(screen.live) && screen.perform(action)) return
        // Fallback: the chrome; in timeshift an OK press also focuses Play/Pause (REMOTE-FR-24).
        screen.reveal(focusPlayPause = !screen.live && button == RemoteButton.OK)
    }

    private fun digit(code: Int): Int? = when (code) {
        in KEY_0..KEY_9 -> code - KEY_0
        in NUMPAD_0..NUMPAD_9 -> code - NUMPAD_0
        else -> null
    }

    private companion object {
        // Android key codes, as plain ints so this module stays Android-free.
        const val KEY_0 = 7
        const val KEY_9 = 16
        const val NUMPAD_0 = 144
        const val NUMPAD_9 = 153
    }
}
