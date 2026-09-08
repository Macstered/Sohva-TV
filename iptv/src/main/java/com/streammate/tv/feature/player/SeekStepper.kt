package com.streammate.tv.feature.player

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far a skip goes: the chosen step, and further the longer the button is
 * held or pressed in quick succession. Every [pressesPerRung] presses in the
 * same direction within [chainWindowMillis] of each other climb one rung of
 * the ladder, up to two minutes; a pause, or the other direction, starts over.
 */
class SeekStepper(
    private val ladderMillis: List<Long> = SEEK_LADDER_MILLIS,
    private val pressesPerRung: Int = SEEK_PRESSES_PER_RUNG,
    private val chainWindowMillis: Long = SEEK_CHAIN_WINDOW_MILLIS,
) {
    private var lastDirection = 0
    private var lastAtMillis = Long.MIN_VALUE
    private var streak = 0

    /** The distance for one press, in [direction] (-1 back, +1 forward), never below [baseMillis]. */
    fun step(baseMillis: Long, direction: Int, nowMillis: Long): Long {
        streak = if (direction == lastDirection && nowMillis - lastAtMillis <= chainWindowMillis) streak + 1 else 0
        lastDirection = direction
        lastAtMillis = nowMillis
        val start = ladderMillis.indexOfFirst { it >= baseMillis }.let { if (it < 0) ladderMillis.lastIndex else it }
        val rung = (start + streak / pressesPerRung).coerceAtMost(ladderMillis.lastIndex)
        return maxOf(baseMillis, ladderMillis[rung])
    }
}

val SEEK_LADDER_MILLIS: List<Long> = listOf(10_000L, 30_000L, 60_000L, 120_000L)
const val SEEK_PRESSES_PER_RUNG = 3
const val SEEK_CHAIN_WINDOW_MILLIS = 1_200L

/**
 * The chosen skip step, for the transport controls to name on their buttons.
 * It travels as a composition local rather than a parameter: the player is
 * one enormous composable already, and every argument added inside it costs
 * registers the dex verifier has to account for.
 */
val LocalPlayerSeekStep = staticCompositionLocalOf { SEEK_LADDER_MILLIS.first() }

/** "30 s" or "2 min": how far one skip goes, for a button that names it. */
fun seekAmountLabel(millis: Long): String {
    val seconds = kotlin.math.abs(millis) / 1000
    return if (seconds >= 60 && seconds % 60 == 0L) "${seconds / 60} min" else "$seconds s"
}

/** "+30 s" or "−2 min": what one skip did, for the overlay. */
fun seekStepLabel(signedMillis: Long): String {
    val sign = if (signedMillis < 0) "\u2212" else "+"
    return sign + seekAmountLabel(signedMillis)
}
