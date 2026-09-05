package com.streammate.tv.app

import android.view.KeyEvent

/**
 * The buttons a viewer can map while watching. The D-pad and Back are on every
 * remote; the rest are on some, and sit under their own heading in Settings.
 */
enum class RemoteButton(val keyCodes: Set<Int>, val optional: Boolean = false) {
    UP(setOf(KeyEvent.KEYCODE_DPAD_UP)),
    DOWN(setOf(KeyEvent.KEYCODE_DPAD_DOWN)),
    LEFT(setOf(KeyEvent.KEYCODE_DPAD_LEFT)),
    RIGHT(setOf(KeyEvent.KEYCODE_DPAD_RIGHT)),
    OK(setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)),
    BACK(setOf(KeyEvent.KEYCODE_BACK)),
    CHANNEL_UP(setOf(KeyEvent.KEYCODE_CHANNEL_UP), optional = true),
    CHANNEL_DOWN(setOf(KeyEvent.KEYCODE_CHANNEL_DOWN), optional = true),
    INFO(setOf(KeyEvent.KEYCODE_INFO), optional = true),
    AUDIO(setOf(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK), optional = true),
    CAPTIONS(setOf(KeyEvent.KEYCODE_CAPTIONS), optional = true),
    MENU(setOf(KeyEvent.KEYCODE_MENU), optional = true),
    ;

    companion object {
        fun fromKeyCode(keyCode: Int): RemoteButton? = entries.firstOrNull { keyCode in it.keyCodes }
    }
}

enum class RemoteGesture { PRESS, HOLD }

data class RemoteSlot(val button: RemoteButton, val gesture: RemoteGesture) {
    /** Back press always dismisses and leaves; a remote must keep one way out. */
    val fixed: Boolean get() = button == RemoteButton.BACK && gesture == RemoteGesture.PRESS

    val storedName: String get() = "${button.name}.${gesture.name}"

    companion object {
        /** Every slot, in the order the settings grid shows them. */
        val ALL: List<RemoteSlot> = RemoteButton.entries.flatMap { button ->
            RemoteGesture.entries.map { gesture -> RemoteSlot(button, gesture) }
        }

        val MAPPABLE: List<RemoteSlot> = ALL.filterNot(RemoteSlot::fixed)

        fun fromStoredName(name: String): RemoteSlot? {
            val (button, gesture) = name.split('.').takeIf { it.size == 2 } ?: return null
            return RemoteSlot(
                RemoteButton.entries.firstOrNull { it.name == button } ?: return null,
                RemoteGesture.entries.firstOrNull { it.name == gesture } ?: return null,
            )
        }
    }
}

/** Where an action does something. Mapped elsewhere, it does nothing. */
enum class RemoteActionScope { LIVE, TIMESHIFT, ANY }

enum class RemoteActionGroup { CHANNELS, INFORMATION, PLAYBACK, SOUND_AND_PICTURE, LEAVE, NOTHING }

enum class RemoteAction(val group: RemoteActionGroup, val scope: RemoteActionScope) {
    NOTHING(RemoteActionGroup.NOTHING, RemoteActionScope.ANY),

    NEXT_CHANNEL(RemoteActionGroup.CHANNELS, RemoteActionScope.LIVE),
    PREVIOUS_CHANNEL(RemoteActionGroup.CHANNELS, RemoteActionScope.LIVE),
    SWITCH_TO_PREVIOUS_CHANNEL(RemoteActionGroup.CHANNELS, RemoteActionScope.LIVE),
    OPEN_CHANNEL_BROWSER(RemoteActionGroup.CHANNELS, RemoteActionScope.LIVE),
    OPEN_GROUP_BROWSER(RemoteActionGroup.CHANNELS, RemoteActionScope.LIVE),

    PROGRAMME_INFO(RemoteActionGroup.INFORMATION, RemoteActionScope.ANY),
    TOGGLE_STATS(RemoteActionGroup.INFORMATION, RemoteActionScope.ANY),
    GUIDE_AT_CHANNEL(RemoteActionGroup.INFORMATION, RemoteActionScope.LIVE),
    QUICK_ACTIONS(RemoteActionGroup.INFORMATION, RemoteActionScope.ANY),

    PLAY_PAUSE(RemoteActionGroup.PLAYBACK, RemoteActionScope.TIMESHIFT),
    SEEK_BACK(RemoteActionGroup.PLAYBACK, RemoteActionScope.TIMESHIFT),
    SEEK_FORWARD(RemoteActionGroup.PLAYBACK, RemoteActionScope.TIMESHIFT),
    RESTART(RemoteActionGroup.PLAYBACK, RemoteActionScope.TIMESHIFT),
    SHOW_CONTROLS(RemoteActionGroup.PLAYBACK, RemoteActionScope.TIMESHIFT),

    AUDIO_PICKER(RemoteActionGroup.SOUND_AND_PICTURE, RemoteActionScope.ANY),
    NEXT_AUDIO_TRACK(RemoteActionGroup.SOUND_AND_PICTURE, RemoteActionScope.ANY),
    SUBTITLE_PICKER(RemoteActionGroup.SOUND_AND_PICTURE, RemoteActionScope.ANY),
    TOGGLE_SUBTITLES(RemoteActionGroup.SOUND_AND_PICTURE, RemoteActionScope.ANY),
    CYCLE_PICTURE_SHAPE(RemoteActionGroup.SOUND_AND_PICTURE, RemoteActionScope.ANY),

    LEAVE_PLAYER(RemoteActionGroup.LEAVE, RemoteActionScope.ANY),
    GO_HOME(RemoteActionGroup.LEAVE, RemoteActionScope.ANY),
    GO_GUIDE(RemoteActionGroup.LEAVE, RemoteActionScope.ANY),
    GO_SPORT(RemoteActionGroup.LEAVE, RemoteActionScope.ANY),
    ;

    fun appliesTo(live: Boolean): Boolean = when (scope) {
        RemoteActionScope.ANY -> true
        RemoteActionScope.LIVE -> live
        RemoteActionScope.TIMESHIFT -> !live
    }

    companion object {
        fun fromStoredName(name: String): RemoteAction? = entries.firstOrNull { it.name == name }
    }
}

/**
 * What each slot does. Slots absent from [actions] are [RemoteAction.NOTHING];
 * the fixed Back press is never in it.
 */
data class RemoteMappings(val actions: Map<RemoteSlot, RemoteAction> = emptyMap()) {
    operator fun get(slot: RemoteSlot): RemoteAction = actions[slot] ?: RemoteAction.NOTHING

    fun with(slot: RemoteSlot, action: RemoteAction): RemoteMappings {
        if (slot.fixed) return this
        return RemoteMappings(
            if (action == RemoteAction.NOTHING) actions - slot else actions + (slot to action),
        )
    }

    /** `BUTTON.GESTURE=ACTION` entries; order does not matter. */
    fun encode(): Set<String> = actions
        .filterKeys { !it.fixed }
        .filterValues { it != RemoteAction.NOTHING }
        .map { (slot, action) -> "${slot.storedName}=${action.name}" }
        .toSet()

    companion object {
        private fun slot(button: RemoteButton, gesture: RemoteGesture) = RemoteSlot(button, gesture)

        /**
         * Reproduces what the buttons did before mapping existed, and puts the
         * two things nobody could reach - zap-back and the guide - on holds.
         */
        val DEFAULTS: RemoteMappings = RemoteMappings(
            mapOf(
                slot(RemoteButton.UP, RemoteGesture.PRESS) to RemoteAction.OPEN_CHANNEL_BROWSER,
                slot(RemoteButton.UP, RemoteGesture.HOLD) to RemoteAction.NEXT_CHANNEL,
                slot(RemoteButton.DOWN, RemoteGesture.PRESS) to RemoteAction.OPEN_CHANNEL_BROWSER,
                slot(RemoteButton.DOWN, RemoteGesture.HOLD) to RemoteAction.PREVIOUS_CHANNEL,
                slot(RemoteButton.LEFT, RemoteGesture.PRESS) to RemoteAction.SEEK_BACK,
                slot(RemoteButton.LEFT, RemoteGesture.HOLD) to RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL,
                slot(RemoteButton.RIGHT, RemoteGesture.PRESS) to RemoteAction.SEEK_FORWARD,
                slot(RemoteButton.RIGHT, RemoteGesture.HOLD) to RemoteAction.GUIDE_AT_CHANNEL,
                slot(RemoteButton.OK, RemoteGesture.PRESS) to RemoteAction.PROGRAMME_INFO,
                slot(RemoteButton.OK, RemoteGesture.HOLD) to RemoteAction.QUICK_ACTIONS,
                slot(RemoteButton.BACK, RemoteGesture.HOLD) to RemoteAction.SWITCH_TO_PREVIOUS_CHANNEL,
                slot(RemoteButton.CHANNEL_UP, RemoteGesture.PRESS) to RemoteAction.PREVIOUS_CHANNEL,
                slot(RemoteButton.CHANNEL_DOWN, RemoteGesture.PRESS) to RemoteAction.NEXT_CHANNEL,
                slot(RemoteButton.INFO, RemoteGesture.PRESS) to RemoteAction.TOGGLE_STATS,
                slot(RemoteButton.AUDIO, RemoteGesture.PRESS) to RemoteAction.AUDIO_PICKER,
                slot(RemoteButton.CAPTIONS, RemoteGesture.PRESS) to RemoteAction.SUBTITLE_PICKER,
                slot(RemoteButton.MENU, RemoteGesture.PRESS) to RemoteAction.QUICK_ACTIONS,
            ),
        )

        /**
         * The setting that mapping replaces: "CH+/CH- only" meant Up and Down
         * did nothing on the clean screen.
         */
        fun migrated(legacyMode: RemoteChannelKeyMode): RemoteMappings = when (legacyMode) {
            RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS -> DEFAULTS
            RemoteChannelKeyMode.CHANNEL_KEYS_ONLY -> DEFAULTS
                .with(slot(RemoteButton.UP, RemoteGesture.PRESS), RemoteAction.NOTHING)
                .with(slot(RemoteButton.DOWN, RemoteGesture.PRESS), RemoteAction.NOTHING)
        }

        /** Unknown slots and actions are dropped, so an older build survives a newer mapping. */
        fun decode(stored: Set<String>): RemoteMappings = RemoteMappings(
            stored.mapNotNull { entry ->
                val (slotName, actionName) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val slot = RemoteSlot.fromStoredName(slotName)?.takeIf { !it.fixed } ?: return@mapNotNull null
                val action = RemoteAction.fromStoredName(actionName) ?: return@mapNotNull null
                slot to action
            }.toMap(),
        )

        /** Stored mappings win; without any, the legacy setting decides the defaults. */
        fun fromStored(stored: Set<String>?, legacyMode: RemoteChannelKeyMode): RemoteMappings =
            if (stored == null) migrated(legacyMode) else decode(stored)
    }
}

enum class RemoteKeyAction { DOWN, UP }

sealed interface RemoteKeyGesture {
    val button: RemoteButton

    data class Press(override val button: RemoteButton) : RemoteKeyGesture
    data class Hold(override val button: RemoteButton) : RemoteKeyGesture
}

/**
 * Turns raw key events into presses and holds.
 *
 * Android repeats a held key's down event; the first repeat is the hold,
 * about half a second in. A press therefore cannot fire on the first down,
 * because at that moment it is not yet known whether it is a hold. It fires
 * on the up, unless a hold already fired for that button.
 */
class RemoteKeyGestureResolver {
    private var pending: RemoteButton? = null
    private var held = false

    /** The gesture this event completes, or null if it completes nothing. */
    fun resolve(keyCode: Int, action: RemoteKeyAction, repeatCount: Int): RemoteKeyGesture? {
        val button = RemoteButton.fromKeyCode(keyCode) ?: return null
        return when (action) {
            RemoteKeyAction.DOWN -> when {
                repeatCount == 0 || button != pending -> {
                    pending = button
                    held = false
                    null
                }
                held -> null
                else -> {
                    held = true
                    RemoteKeyGesture.Hold(button)
                }
            }
            RemoteKeyAction.UP -> {
                if (button != pending) return null
                val gesture = if (held) null else RemoteKeyGesture.Press(button)
                pending = null
                held = false
                gesture
            }
        }
    }

    /** True while a hold has fired and the key is still down; its release must be swallowed. */
    fun isHolding(keyCode: Int): Boolean = held && RemoteButton.fromKeyCode(keyCode) == pending

    fun reset() {
        pending = null
        held = false
    }
}
