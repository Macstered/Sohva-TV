package com.sohva.tv.core.model.player

/** The twelve remote buttons of spec 31 REMOTE-FR-01, in grid order. */
enum class RemoteButton {
    UP, DOWN, LEFT, RIGHT, OK, BACK, CHANNEL_UP, CHANNEL_DOWN, INFO, AUDIO, CAPTIONS, MENU;

    companion object {
        /** Android key codes that count as each button (REMOTE-FR-01); plain ints keep this module Android-free. */
        fun of(keyCode: Int): RemoteButton? = when (keyCode) {
            19 -> UP
            20 -> DOWN
            21 -> LEFT
            22 -> RIGHT
            23, 66, 160 -> OK
            4 -> BACK
            166 -> CHANNEL_UP
            167 -> CHANNEL_DOWN
            165 -> INFO
            222 -> AUDIO
            175 -> CAPTIONS
            82 -> MENU
            else -> null
        }
    }
}

enum class Gesture { PRESS, HOLD }

/** Where an action applies (REMOTE-FR-10, -16). */
enum class ActionScope { ANY, LIVE, TIMESHIFT }

/** The mappable actions of REMOTE-FR-10, in picker order. */
enum class RemoteAction(val scope: ActionScope) {
    NEXT_CHANNEL(ActionScope.LIVE),
    PREVIOUS_CHANNEL(ActionScope.LIVE),
    SWITCH_TO_PREVIOUS_CHANNEL(ActionScope.LIVE),
    OPEN_CHANNEL_BROWSER(ActionScope.LIVE),
    OPEN_GROUP_BROWSER(ActionScope.LIVE),
    PROGRAMME_INFO(ActionScope.ANY),
    TOGGLE_STATS(ActionScope.ANY),
    SCORE_TICKER(ActionScope.ANY),
    GUIDE_AT_CHANNEL(ActionScope.LIVE),
    QUICK_ACTIONS(ActionScope.ANY),
    PLAY_PAUSE(ActionScope.TIMESHIFT),
    SEEK_BACK(ActionScope.TIMESHIFT),
    SEEK_FORWARD(ActionScope.TIMESHIFT),
    RESTART(ActionScope.TIMESHIFT),
    SHOW_CONTROLS(ActionScope.TIMESHIFT),
    AUDIO_PICKER(ActionScope.ANY),
    NEXT_AUDIO_TRACK(ActionScope.ANY),
    SUBTITLE_PICKER(ActionScope.ANY),
    TOGGLE_SUBTITLES(ActionScope.ANY),
    CYCLE_PICTURE_SHAPE(ActionScope.ANY),
    LEAVE_PLAYER(ActionScope.ANY),
    GO_HOME(ActionScope.ANY),
    GO_GUIDE(ActionScope.ANY),
    GO_SPORT(ActionScope.ANY),
    NOTHING(ActionScope.ANY),
    ;

    fun appliesTo(live: Boolean): Boolean = when (scope) {
        ActionScope.ANY -> true
        ActionScope.LIVE -> live
        ActionScope.TIMESHIFT -> !live
    }
}

/**
 * The player's button mapping. M2 ships the defaults of REMOTE-FR-13; the Settings grid that edits
 * them arrives with spec 31's Settings part (M3). (BACK, PRESS) is fixed and never mapped.
 */
class RemoteMapping(private val slots: Map<Pair<RemoteButton, Gesture>, RemoteAction>) {
    fun action(button: RemoteButton, gesture: Gesture): RemoteAction =
        if (button == RemoteButton.BACK && gesture == Gesture.PRESS) RemoteAction.NOTHING
        else slots[button to gesture] ?: RemoteAction.NOTHING

    companion object {
        val DEFAULTS: RemoteMapping = RemoteMapping(
            mapOf(
                (RemoteButton.UP to Gesture.PRESS) to RemoteAction.OPEN_CHANNEL_BROWSER,
                (RemoteButton.UP to Gesture.HOLD) to RemoteAction.NEXT_CHANNEL,
                (RemoteButton.DOWN to Gesture.PRESS) to RemoteAction.OPEN_CHANNEL_BROWSER,
                (RemoteButton.DOWN to Gesture.HOLD) to RemoteAction.PREVIOUS_CHANNEL,
                (RemoteButton.LEFT to Gesture.PRESS) to RemoteAction.SEEK_BACK,
                (RemoteButton.LEFT to Gesture.HOLD) to RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL,
                (RemoteButton.RIGHT to Gesture.PRESS) to RemoteAction.SEEK_FORWARD,
                (RemoteButton.RIGHT to Gesture.HOLD) to RemoteAction.GUIDE_AT_CHANNEL,
                (RemoteButton.OK to Gesture.PRESS) to RemoteAction.PROGRAMME_INFO,
                (RemoteButton.OK to Gesture.HOLD) to RemoteAction.QUICK_ACTIONS,
                (RemoteButton.BACK to Gesture.HOLD) to RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL,
                // CH+ steps to the channel above in the list, CH− to the one below (REMOTE-FR-13).
                (RemoteButton.CHANNEL_UP to Gesture.PRESS) to RemoteAction.PREVIOUS_CHANNEL,
                (RemoteButton.CHANNEL_DOWN to Gesture.PRESS) to RemoteAction.NEXT_CHANNEL,
                (RemoteButton.INFO to Gesture.PRESS) to RemoteAction.TOGGLE_STATS,
                (RemoteButton.AUDIO to Gesture.PRESS) to RemoteAction.AUDIO_PICKER,
                (RemoteButton.CAPTIONS to Gesture.PRESS) to RemoteAction.SUBTITLE_PICKER,
                (RemoteButton.MENU to Gesture.PRESS) to RemoteAction.QUICK_ACTIONS,
            ),
        )
    }
}

/**
 * Press versus hold (REMOTE-FR-05..07): the platform's first auto-repeat is the hold; a press fires
 * on release. Pure logic; the player keeps one for its whole session (REMOTE-FR-09).
 */
class PressHoldResolver {
    private var pending: RemoteButton? = null
    private var held = false

    sealed interface Result {
        data object None : Result

        data class Press(val button: RemoteButton) : Result

        data class Hold(val button: RemoteButton) : Result
    }

    fun down(button: RemoteButton, repeatCount: Int): Result {
        if (repeatCount == 0 || button != pending) {
            pending = button
            held = false
            return Result.None
        }
        if (!held) {
            held = true
            return Result.Hold(button)
        }
        return Result.None
    }

    fun up(button: RemoteButton): Result {
        if (button != pending) return Result.None
        val wasHeld = held
        pending = null
        held = false
        return if (wasHeld) Result.None else Result.Press(button)
    }

    /** True while a hold fired for [button] and it is still down: its release must be swallowed. */
    fun isHolding(button: RemoteButton): Boolean = held && pending == button

    fun reset() {
        pending = null
        held = false
    }
}
