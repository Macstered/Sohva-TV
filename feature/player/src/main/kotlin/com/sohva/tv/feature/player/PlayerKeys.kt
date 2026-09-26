package com.sohva.tv.feature.player

import android.view.KeyEvent
import com.sohva.tv.core.model.player.CleanScreen
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteDispatcher
import com.sohva.tv.core.model.player.RemoteMapping

/**
 * The player's keys (spec 31 §4.6, spec 30 §4.3) for live TV and catch-up: the channel list's own keys,
 * and the clean screen through the shared [RemoteDispatcher], which lives for the whole player
 * session (REMOTE-FR-09) so a held channel key zaps exactly once. Returns whether the event is
 * consumed; an unconsumed Back travels the window's Back route (spec 30 §3.2).
 */
class PlayerKeys internal constructor(private val model: PlayerModel, mapping: RemoteMapping = RemoteMapping.DEFAULTS) : CleanScreen {
    private val dispatcher = RemoteDispatcher(mapping)

    override val live: Boolean get() = model.live

    override val boxVisible: Boolean get() = model.boxVisible.value

    /** Applies from the next key press (REMOTE-FR-36). */
    fun useMapping(mapping: RemoteMapping) {
        dispatcher.mapping = mapping
    }

    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (model.channels.open.value) {
            dispatcher.reset()
            // The list owns its keys, down and up alike, so exactly one layer closes per Back (PLAY-FR-04).
            return if (down) listKey(event.keyCode) else event.keyCode in LIST_KEYS
        }
        return dispatcher.onKey(event.keyCode, down, event.repeatCount, this)
    }

    override fun reveal(focusPlayPause: Boolean) {
        if (live) model.reveal() else model.transport.show(focusPlayPause)
    }

    override fun focusBox() = model.focusBox()

    override fun showControls() = model.transport.show(focusPlay = true)

    override fun dial(digit: Int) = model.dial.digit(digit)

    /** Performs [action]; false when it does not apply here or has nothing to act on (REMOTE-FR-23..24). */
    override fun perform(action: RemoteAction): Boolean {
        if (!action.appliesTo(live)) return false
        val playing = model.playing.value?.channel?.key
        return when (action) {
            RemoteAction.NEXT_CHANNEL -> model.channels.step(+1)
            RemoteAction.PREVIOUS_CHANNEL -> model.channels.step(-1)
            RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL -> model.switchToPrevious()
            RemoteAction.OPEN_CHANNEL_BROWSER -> model.channels.openList()
            RemoteAction.OPEN_GROUP_BROWSER -> model.channels.openList(withGroups = true)
            // Live shows the box; catch-up the controls with Play/Pause focused (REMOTE-FR-10).
            RemoteAction.PROGRAMME_INFO -> {
                if (live) model.reveal() else showControls()
                true
            }
            RemoteAction.TOGGLE_STATS -> {
                model.toggleStats()
                true
            }
            RemoteAction.SCORE_TICKER -> model.toggleTicker()
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
            RemoteAction.LEAVE_PLAYER -> model.currentKey()?.let { model.navigation.leave(it); true } ?: false
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
            RemoteAction.PLAY_PAUSE -> {
                model.transport.togglePlay()
                true
            }
            RemoteAction.SEEK_BACK -> {
                model.transport.skip(back = true)
                true
            }
            RemoteAction.SEEK_FORWARD -> {
                model.transport.skip(back = false)
                true
            }
            RemoteAction.RESTART -> {
                model.transport.restart()
                true
            }
            RemoteAction.SHOW_CONTROLS -> {
                showControls()
                true
            }
            RemoteAction.NOTHING -> false
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
}
