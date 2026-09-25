package com.sohva.tv.core.model.guide

/**
 * Number dialling shared by the guide and the player (spec 20 GUIDE-FR-80..84, spec 30
 * PLAY-FR-59): up to four digits, committed [COMMIT_MS] after the last one; "No channel N" shows
 * for [NOT_FOUND_MS].
 */
class DialBuffer {
    var digits: String = ""
        private set

    /** Appends a digit; a fifth is dropped. Returns whether the buffer changed. */
    fun append(digit: Int): Boolean {
        require(digit in 0..9)
        if (digits.length >= MAX_DIGITS) return false
        digits += digit
        return true
    }

    fun clear() {
        digits = ""
    }

    /** The dialled number with leading zeros ignored ("007" = 7), or null when empty or zero. */
    fun number(): Int? = digits.toIntOrNull()?.takeIf { it > 0 }

    companion object {
        const val MAX_DIGITS: Int = 4
        const val COMMIT_MS: Long = 2_000
        const val NOT_FOUND_MS: Long = 1_500
    }
}

object ChannelDial {
    /**
     * The shared rule (GUIDE-FR-81, PLAY-FR-59): the first channel whose own number equals
     * [number]; otherwise the channel at position `number − 1` when that channel has no own
     * number. Both look-ups are indexed queries in the caller (GUIDE-FR-84), never a list scan.
     */
    suspend fun <C> resolve(
        number: Int,
        byOwnNumber: suspend (Int) -> C?,
        atPosition: suspend (Int) -> C?,
        ownNumber: suspend (C) -> Int?,
    ): C? {
        if (number <= 0) return null
        byOwnNumber(number)?.let { return it }
        val positioned = atPosition(number - 1) ?: return null
        return positioned.takeIf { ownNumber(it) == null }
    }
}
