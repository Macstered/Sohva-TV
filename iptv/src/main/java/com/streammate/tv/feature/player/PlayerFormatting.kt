package com.streammate.tv.feature.player

import androidx.media3.common.Format
import java.util.Locale

/**
 * The strings the player's chrome puts on the screen, worked out away from it.
 *
 * Every one of these is a claim about the stream or the programme, so they are
 * decided in plain functions that can be checked: a placeholder zero printed
 * where a metric was never measured reads exactly like a measurement, and a
 * percentage that keeps counting past the end of a programme is worse than no
 * percentage at all.
 */

/** One reading in the status line. A [label] is only used where the number needs one. */
internal data class PlayerStatValue(val label: String?, val value: String)

/**
 * The status line, in order, with nothing in it that was not measured.
 *
 * media3 reports an unknown number as [Format.NO_VALUE]; every field here is
 * dropped rather than printed as a zero, so an absent bitrate reads as absent
 * instead of as a stream delivering nothing.
 */
internal fun playerStatsValues(
    stats: PlaybackStats,
    bufferLabel: String,
    droppedLabel: String,
): List<PlayerStatValue> = buildList {
    stats.resolutionValue()?.let { add(PlayerStatValue(null, it)) }
    stats.codecValue()?.let { add(PlayerStatValue(null, it)) }
    stats.bitrateValue()?.let { add(PlayerStatValue(null, it)) }
    add(PlayerStatValue(bufferLabel, stats.bufferValue()))
    stats.droppedFrames
        ?.takeIf { it >= 0 }
        ?.let { add(PlayerStatValue(droppedLabel, it.toString())) }
}

/**
 * `1920x1080 50p`, with the frame rate only where the stream declares one.
 *
 * The `p` is not a guess about scanning: media3 reports the decoded frame rate,
 * and everything this app plays is progressive by the time it reaches a
 * surface.
 */
private fun PlaybackStats.resolutionValue(): String? {
    if (width <= 0 || height <= 0) return null
    val rate = frameRate
        .takeIf { it > 0f }
        ?.let { String.format(Locale.ROOT, " %.0fp", it) }
        .orEmpty()
    return "$width×$height$rate"
}

/** `H264 - AAC 5.1`, dropping either half when the stream does not name it. */
private fun PlaybackStats.codecValue(): String? {
    val video = videoCodec?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)
    val audio = audioCodec?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.let { codec ->
        val layout = audioChannels
            .takeIf { it != Format.NO_VALUE && it > 0 }
            ?.let { channels -> CHANNEL_LAYOUTS[channels] ?: "$channels ch" }
        listOfNotNull(codec, layout).joinToString(" ")
    }
    return listOfNotNull(video, audio).takeIf { it.isNotEmpty() }?.joinToString(CODEC_SEPARATOR)
}

private fun PlaybackStats.bitrateValue(): String? = videoBitrate
    .takeIf { it != Format.NO_VALUE && it > 0 }
    ?.let { String.format(Locale.ROOT, "%.1f Mb/s", it / 1_000_000f) }

private fun PlaybackStats.bufferValue(): String =
    String.format(Locale.ROOT, "%.1f s", bufferedAheadMillis.coerceAtLeast(0L) / 1000f)

/**
 * How far through a programme is, as a fraction, or null when the question does
 * not apply.
 *
 * A programme with no length, one that has not started and one that has already
 * ended all return null rather than a number that would be drawn as a bar.
 */
internal fun playerProgrammeFraction(
    startEpochMillis: Long,
    stopEpochMillis: Long,
    nowEpochMillis: Long,
): Float? {
    if (stopEpochMillis <= startEpochMillis) return null
    if (nowEpochMillis < startEpochMillis || nowEpochMillis >= stopEpochMillis) return null
    return (nowEpochMillis - startEpochMillis).toFloat() / (stopEpochMillis - startEpochMillis)
}

/** A fraction as a whole percentage, for the "44 % watched" reading. */
internal fun playerPercentWatched(fraction: Float): Int =
    (fraction.coerceIn(0f, 1f) * 100).toInt()

/**
 * Minutes left, rounded up so a programme with seconds to run does not read as
 * finished. Null once it has actually ended.
 */
internal fun playerRemainingMinutes(stopEpochMillis: Long, nowEpochMillis: Long): Int? {
    val remaining = stopEpochMillis - nowEpochMillis
    if (remaining <= 0L) return null
    return ((remaining + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

/** Hours and minutes above the hour, plain minutes below it. */
internal fun playerDurationParts(minutes: Int): Pair<Int, Int> =
    minutes / MINUTES_PER_HOUR to minutes % MINUTES_PER_HOUR

private val CHANNEL_LAYOUTS = mapOf(
    1 to "1.0",
    2 to "2.0",
    6 to "5.1",
    8 to "7.1",
)

private const val CODEC_SEPARATOR = " · "
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
