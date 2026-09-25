package com.sohva.tv.feature.player

import android.view.KeyEvent
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.PressHoldResolver
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping

/**
 * The clean screen's key dispatch (spec 31 §4.6, spec 30 §4.3) for live playback. One resolver for
 * the whole player session (REMOTE-FR-09), so a held channel key zaps exactly once. Returns whether
 * the event is consumed; an unconsumed Back travels the window's Back route (spec 30 §3.2).
 */
class PlayerKeys internal constructor(private val model: PlayerModel) {
    private val resolver = PressHoldResolver()
    private val mapping = RemoteMapping.DEFAULTS
    private var boxAtDown = false
    private val live = true

    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        if (model.channels.open.value) {
            resolver.reset()
            // The list owns its keys, down and up alike, so exactly one layer closes per Back (PLAY-FR-04).
            return if (down) listKey(code) else code in LIST_KEYS
        }
        digit(code)?.let { d ->
            // Live: the first key-down appends; every event is consumed (REMOTE-FR-18).
            if (down && event.repeatCount == 0) model.dial.digit(d)
            return true
        }
        val button = RemoteButton.of(code)
        if (button == null) {
            // Outside the grid: reveal on the first down, leave the key to Android (REMOTE-FR-19).
            if (down && event.repeatCount == 0) model.reveal()
            return false
        }
        if (button == RemoteButton.BACK) return backKey(event)
        if (down) {
            if (event.repeatCount == 0) boxAtDown = model.boxVisible.value
            val result = resolver.down(button, event.repeatCount)
            if (result is PressHoldResolver.Result.Hold) perform(mapping.action(button, Gesture.HOLD))
            return true
        }
        when (val result = resolver.up(button)) {
            is PressHoldResolver.Result.Press -> press(result.button)
            else -> Unit
        }
        return true
    }

    /** Back hold = the mapped hold action when it applies; a press goes through the window (REMOTE-FR-20). */
    private fun backKey(event: KeyEvent): Boolean {
        val hold = mapping.action(RemoteButton.BACK, Gesture.HOLD)
        if (!hold.appliesTo(live) || hold == RemoteAction.NOTHING) {
            resolver.reset()
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                resolver.down(RemoteButton.BACK, 0)
                return false
            }
            if (resolver.down(RemoteButton.BACK, event.repeatCount) is PressHoldResolver.Result.Hold) perform(hold)
            return true
        }
        val holding = resolver.isHolding(RemoteButton.BACK)
        resolver.up(RemoteButton.BACK)
        return holding
    }

    private fun press(button: RemoteButton) {
        // Up/Down step into an open box first (REMOTE-FR-22).
        if ((button == RemoteButton.UP || button == RemoteButton.DOWN) && boxAtDown) {
            model.focusBox()
            return
        }
        if (!perform(mapping.action(button, Gesture.PRESS))) model.reveal()
    }

    /** Performs [action]; false when it does not apply here or has nothing to act on (REMOTE-FR-23..24). */
    fun perform(action: RemoteAction): Boolean {
        if (!action.appliesTo(live)) return false
        val playing = model.playing.value?.channel?.key
        return when (action) {
            RemoteAction.NEXT_CHANNEL -> model.channels.step(+1)
            RemoteAction.PREVIOUS_CHANNEL -> model.channels.step(-1)
            RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL -> model.switchToPrevious()
            RemoteAction.OPEN_CHANNEL_BROWSER -> model.channels.openList()
            RemoteAction.OPEN_GROUP_BROWSER -> model.channels.openList(withGroups = true)
            RemoteAction.PROGRAMME_INFO -> {
                model.reveal()
                true
            }
            RemoteAction.TOGGLE_STATS -> {
                model.toggleStats()
                true
            }
            // The score ticker arrives with Sohva Sport (M8): nothing to act on yet.
            RemoteAction.SCORE_TICKER -> false
            RemoteAction.GUIDE_AT_CHANNEL -> playing?.let { model.navigation.guideAt(it); true } ?: false
            RemoteAction.QUICK_ACTIONS -> {
                model.openQuickActions()
                true
            }
            RemoteAction.AUDIO_PICKER -> {
                model.openPicker(Picker.AUDIO)
                true
            }
            RemoteAction.NEXT_AUDIO_TRACK -> model.nextAudio()
            RemoteAction.SUBTITLE_PICKER -> {
                model.openPicker(Picker.SUBTITLES)
                true
            }
            RemoteAction.TOGGLE_SUBTITLES -> model.toggleSubtitles()
            RemoteAction.CYCLE_PICTURE_SHAPE -> {
                model.cycleShape()
                true
            }
            RemoteAction.LEAVE_PLAYER -> playing?.let { model.navigation.leave(it); true } ?: false
            RemoteAction.GO_HOME -> {
                model.navigation.home()
                true
            }
            RemoteAction.GO_GUIDE -> {
                model.navigation.guide()
                true
            }
            RemoteAction.GO_SPORT -> {
                model.navigation.sport()
                true
            }
            // Timeshift actions (catch-up M3, films M4) never apply to live; NOTHING never acts.
            else -> false
        }
    }

    /** Keys while the channel list is open, acted on key-down (PLAY-FR-52). */
    private fun listKey(code: Int): Boolean {
        val channels = model.channels
        val inGroups = channels.groups.value != null
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (inGroups) channels.moveGroup(-1) else channels.move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (inGroups) channels.moveGroup(+1) else channels.move(+1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (inGroups) channels.chooseGroup() else channels.choose()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!inGroups) channels.openGroups()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> if (inGroups) channels.closeGroups() else channels.close()
            else -> return false
        }
        return true
    }

    private companion object {
        val LIST_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        )
    }

    private fun digit(code: Int): Int? = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }
}
