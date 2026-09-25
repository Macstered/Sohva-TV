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

/** Mappable (button, gesture) pairs; (BACK, PRESS) is fixed and never a slot (REMOTE-FR-02). */
object RemoteSlots {
    const val COUNT: Int = 24

    fun index(button: RemoteButton, gesture: Gesture): Int = button.ordinal * 2 + gesture.ordinal

    fun isFixed(button: RemoteButton, gesture: Gesture): Boolean = button == RemoteButton.BACK && gesture == Gesture.PRESS

    /** The 23 mappable slots in grid order: button order, Press before Hold. */
    val MAPPABLE: List<Pair<RemoteButton, Gesture>> =
        RemoteButton.entries.flatMap { b -> Gesture.entries.map { b to it } }.filterNot { (b, g) -> isFixed(b, g) }

    /** `<BUTTON>.<GESTURE>` (REMOTE-FR-03). */
    fun name(button: RemoteButton, gesture: Gesture): String = "${button.name}.${gesture.name}"
}

/**
 * The player's button mapping, decoded once into an array indexed by slot (spec 31 §9): a key
 * event reads an array entry, never a map keyed by pairs. (BACK, PRESS) always reads NOTHING here;
 * the player gives it its fixed meaning.
 */
class RemoteMapping private constructor(private val slots: Array<RemoteAction>) {
    fun action(button: RemoteButton, gesture: Gesture): RemoteAction =
        if (RemoteSlots.isFixed(button, gesture)) RemoteAction.NOTHING else slots[RemoteSlots.index(button, gesture)]

    /** A copy with one slot changed; the fixed slot is ignored (REMOTE-FR-33). */
    fun with(button: RemoteButton, gesture: Gesture, action: RemoteAction): RemoteMapping {
        if (RemoteSlots.isFixed(button, gesture)) return this
        val copy = slots.copyOf()
        copy[RemoteSlots.index(button, gesture)] = action
        return RemoteMapping(copy)
    }

    /** The stored form (REMOTE-FR-30): one `SLOT=ACTION` per slot that is not NOTHING. */
    fun encode(): Set<String> = RemoteSlots.MAPPABLE.mapNotNullTo(LinkedHashSet()) { (b, g) ->
        action(b, g).takeIf { it != RemoteAction.NOTHING }?.let { "${RemoteSlots.name(b, g)}=${it.name}" }
    }

    override fun equals(other: Any?): Boolean = other is RemoteMapping && slots.contentEquals(other.slots)

    override fun hashCode(): Int = slots.contentHashCode()

    companion object {
        /** Beta 23's "CH+/CH− only" choice of the old channel-key setting (REMOTE-FR-32). */
        const val LEGACY_CHANNEL_KEYS_ONLY: String = "CHANNEL_KEYS_ONLY"

        private fun empty(): Array<RemoteAction> = Array(RemoteSlots.COUNT) { RemoteAction.NOTHING }

        /** REMOTE-FR-13: seventeen slots; the other six are NOTHING. */
        val DEFAULTS: RemoteMapping = RemoteMapping(empty())
            .with(RemoteButton.UP, Gesture.PRESS, RemoteAction.OPEN_CHANNEL_BROWSER)
            .with(RemoteButton.UP, Gesture.HOLD, RemoteAction.NEXT_CHANNEL)
            .with(RemoteButton.DOWN, Gesture.PRESS, RemoteAction.OPEN_CHANNEL_BROWSER)
            .with(RemoteButton.DOWN, Gesture.HOLD, RemoteAction.PREVIOUS_CHANNEL)
            .with(RemoteButton.LEFT, Gesture.PRESS, RemoteAction.SEEK_BACK)
            .with(RemoteButton.LEFT, Gesture.HOLD, RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL)
            .with(RemoteButton.RIGHT, Gesture.PRESS, RemoteAction.SEEK_FORWARD)
            .with(RemoteButton.RIGHT, Gesture.HOLD, RemoteAction.GUIDE_AT_CHANNEL)
            .with(RemoteButton.OK, Gesture.PRESS, RemoteAction.PROGRAMME_INFO)
            .with(RemoteButton.OK, Gesture.HOLD, RemoteAction.QUICK_ACTIONS)
            .with(RemoteButton.BACK, Gesture.HOLD, RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL)
            // CH+ steps to the channel above in the list, CH− to the one below (REMOTE-FR-13).
            .with(RemoteButton.CHANNEL_UP, Gesture.PRESS, RemoteAction.PREVIOUS_CHANNEL)
            .with(RemoteButton.CHANNEL_DOWN, Gesture.PRESS, RemoteAction.NEXT_CHANNEL)
            .with(RemoteButton.INFO, Gesture.PRESS, RemoteAction.TOGGLE_STATS)
            .with(RemoteButton.AUDIO, Gesture.PRESS, RemoteAction.AUDIO_PICKER)
            .with(RemoteButton.CAPTIONS, Gesture.PRESS, RemoteAction.SUBTITLE_PICKER)
            .with(RemoteButton.MENU, Gesture.PRESS, RemoteAction.QUICK_ACTIONS)

        /**
         * The mapping from storage (REMOTE-FR-31, -32). [stored] null means never written, so the
         * legacy setting decides; an empty set means every slot is NOTHING. Malformed entries,
         * unknown names and the fixed slot are dropped, so an older build survives a newer one's.
         */
        fun decode(stored: Set<String>?, legacyMode: String? = null): RemoteMapping {
            if (stored == null) {
                return if (legacyMode == LEGACY_CHANNEL_KEYS_ONLY) {
                    DEFAULTS.with(RemoteButton.UP, Gesture.PRESS, RemoteAction.NOTHING).with(RemoteButton.DOWN, Gesture.PRESS, RemoteAction.NOTHING)
                } else {
                    DEFAULTS
                }
            }
            val slots = empty()
            for (entry in stored) {
                val parts = entry.split('=')
                if (parts.size != 2) continue
                val slot = parts[0].split('.')
                if (slot.size != 2) continue
                val button = RemoteButton.entries.firstOrNull { it.name == slot[0] } ?: continue
                val gesture = Gesture.entries.firstOrNull { it.name == slot[1] } ?: continue
                val action = RemoteAction.entries.firstOrNull { it.name == parts[1] } ?: continue
                if (RemoteSlots.isFixed(button, gesture)) continue
                slots[RemoteSlots.index(button, gesture)] = action
            }
            return RemoteMapping(slots)
        }
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
            return HOLDS[button.ordinal]
        }
        return Result.None
    }

    fun up(button: RemoteButton): Result {
        if (button != pending) return Result.None
        val wasHeld = held
        pending = null
        held = false
        return if (wasHeld) Result.None else PRESSES[button.ordinal]
    }

    /** True while a hold fired for [button] and it is still down: its release must be swallowed. */
    fun isHolding(button: RemoteButton): Boolean = held && pending == button

    fun reset() {
        pending = null
        held = false
    }

    private companion object {
        // One result per button, made once: a key event allocates nothing (spec 31 §9).
        val PRESSES: Array<Result> = Array(RemoteButton.entries.size) { Result.Press(RemoteButton.entries[it]) }
        val HOLDS: Array<Result> = Array(RemoteButton.entries.size) { Result.Hold(RemoteButton.entries[it]) }
    }
}
