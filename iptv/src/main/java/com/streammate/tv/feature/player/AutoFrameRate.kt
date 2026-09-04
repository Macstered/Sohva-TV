package com.streammate.tv.feature.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Chooses a display refresh rate that a stream's frame rate divides into evenly.
 *
 * A panel running at 60 Hz showing 50 fps football has to show some frames twice
 * and others once, which is the stutter people notice on a pan across a pitch.
 * Matching the display to the content removes it.
 *
 * Only whole multiples count. A rate the content does not divide into is worse
 * than leaving the display alone, because switching modes blanks the screen for
 * a second on most televisions - a cost only worth paying for a real fix.
 */
object AutoFrameRate {

    /**
     * The best of [availableRates] for content at [contentFrameRate], or null to
     * stay where we are.
     *
     * Ties are broken towards the smallest multiple: 24 fps prefers a 24 Hz mode
     * over a 48 Hz one, because the lower mode is the one a television is most
     * likely to drive without interpolation of its own.
     */
    fun pick(availableRates: List<Float>, contentFrameRate: Float): Float? {
        if (contentFrameRate <= 0f) return null

        return availableRates
            .asSequence()
            .filter { it > 0f }
            .mapNotNull { rate ->
                val multiple = max(1, (rate / contentFrameRate).roundToInt())
                val error = abs(rate - contentFrameRate * multiple) / contentFrameRate
                if (error > TOLERANCE) null else Candidate(rate, multiple, error)
            }
            .minWithOrNull(compareBy({ it.multiple }, { it.error }))
            ?.rate
    }

    private data class Candidate(val rate: Float, val multiple: Int, val error: Float)

    /**
     * Half a percent, which covers the NTSC rates - 23.976 against a 24 Hz mode
     * is a tenth of a percent out - without letting 50 pass for 60.
     */
    private const val TOLERANCE = 0.005f
}
