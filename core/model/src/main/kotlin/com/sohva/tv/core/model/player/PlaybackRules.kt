package com.sohva.tv.core.model.player

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** "Playback recovery" (spec 30 PLAY-FR-91). */
enum class ReconnectPolicy(private val delaysMs: LongArray) {
    STANDARD(longArrayOf(2_000, 4_000, 6_000)),
    PERSISTENT(longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000, 30_000)),
    ;

    val attempts: Int get() = delaysMs.size

    /** The delay before [attempt] (1-based), or null when the attempt is outside the policy. */
    fun delayBefore(attempt: Int): Long? = delaysMs.getOrNull(attempt - 1)

    companion object {
        fun fromStored(value: String?): ReconnectPolicy = entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

/** "Playback buffer" profiles (PLAY-FR-86) with the rebuild's byte caps (§9). */
enum class BufferProfile(
    val minBufferMs: Int?,
    val maxBufferMs: Int?,
    val startMs: Int?,
    val rebufferMs: Int?,
    val targetBytes: Int,
) {
    DEFAULT(null, null, null, null, 32 * MIB),
    LOW_LATENCY(5_000, 15_000, 1_000, 2_000, 16 * MIB),
    STABILITY(60_000, 120_000, 5_000, 10_000, 64 * MIB),
    ;

    /** Stability is capped like the default on low-memory boxes (§9). */
    fun targetBytes(lowMemory: Boolean): Int = if (lowMemory) minOf(targetBytes, DEFAULT.targetBytes) else targetBytes

    companion object {
        fun fromStored(value: String?): BufferProfile = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

private const val MIB = 1024 * 1024

/** Picture shapes, cycled Fit → Fill → Zoom (PLAY-FR-81). */
enum class PictureShape {
    FIT, FILL, ZOOM;

    fun next(): PictureShape = entries[(ordinal + 1) % entries.size]
}

/** The skip step setting and the ladder (PLAY-FR-60..63). */
enum class SkipStep(val millis: Long) {
    TEN_SECONDS(10_000), THIRTY_SECONDS(30_000), ONE_MINUTE(60_000), TWO_MINUTES(120_000);

    companion object {
        fun fromStored(value: String?): SkipStep = entries.firstOrNull { it.name == value } ?: TEN_SECONDS
    }
}

class SkipLadder(private val step: SkipStep) {
    private var lastAt = Long.MIN_VALUE
    private var lastDirection = 0
    private var streak = 0

    /** The distance of a skip in [direction] (±1) at [nowMs], climbing with quick repeats. */
    fun skip(direction: Int, nowMs: Long): Long {
        streak = if (direction == lastDirection && nowMs - lastAt <= STREAK_MS) streak + 1 else 0
        lastDirection = direction
        lastAt = nowMs
        val start = LADDER.indexOfFirst { it >= step.millis }.let { if (it < 0) LADDER.lastIndex else it }
        val rung = minOf(start + streak / 3, LADDER.lastIndex)
        return maxOf(step.millis, LADDER[rung])
    }

    companion object {
        const val STREAK_MS: Long = 1_200
        private val LADDER = longArrayOf(10_000, 30_000, 60_000, 120_000)

        /** "10 s", "1 min"; signed with U+2212 when [signed] (PLAY-FR-63). */
        fun label(millis: Long, signed: Boolean = false, negative: Boolean = false): String {
            val seconds = millis / 1_000
            val body = if (seconds % 60 == 0L && seconds > 0) "${seconds / 60} min" else "$seconds s"
            return if (!signed) body else (if (negative) "−" else "+") + body
        }
    }
}

/** The container type from a stream address (PLAY-FR-15). */
enum class StreamContainer {
    HLS, DASH, SMOOTH, PROGRESSIVE;

    companion object {
        fun of(address: String): StreamContainer {
            val path = address.substringBefore('#').substringBefore('?').trimEnd('/').lowercase(Locale.ROOT)
            return when {
                path.endsWith(".m3u8") -> HLS
                path.endsWith(".mpd") -> DASH
                path.endsWith(".ism") || path.endsWith(".isml") || path.endsWith("/manifest") -> SMOOTH
                else -> PROGRESSIVE
            }
        }
    }
}

/**
 * The display refresh rate for a stream (PLAY-FR-103): at the current resolution, a rate `r`
 * whose multiple `m = max(1, round(r / fps))` is within 0.5 %; smallest `m`, then smallest error.
 */
object DisplayRate {
    fun pick(fps: Float, rates: List<Float>): Float? {
        if (fps <= 0f || fps.isNaN()) return null
        var best: Float? = null
        var bestMultiple = Int.MAX_VALUE
        var bestError = Float.MAX_VALUE
        for (r in rates) {
            val m = maxOf(1, (r / fps).roundToInt())
            val error = abs(r - fps * m) / fps
            if (error > 0.005f) continue
            if (m < bestMultiple || (m == bestMultiple && error < bestError)) {
                best = r
                bestMultiple = m
                bestError = error
            }
        }
        return best
    }
}

/** The live box's figures (PLAY-FR-42). */
object LiveFigures {
    fun fraction(start: Long, stop: Long, now: Long): Float? =
        if (stop > start && start <= now && now < stop) (now - start).toFloat() / (stop - start) else null

    fun percent(fraction: Float): Int = floor(fraction * 100.0).toInt()

    /** Minutes left, rounded up; null once ended. */
    fun minutesLeft(stop: Long, now: Long): Int? = if (now >= stop) null else ceil((stop - now) / 60_000.0).toInt()
}

/** Track languages (PLAY-FR-76). */
object TrackLanguage {
    private val three = mapOf(
        "fin" to "fi", "eng" to "en", "swe" to "sv", "dan" to "da", "nor" to "no", "nob" to "no", "nno" to "no",
        "est" to "et", "deu" to "de", "ger" to "de", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it",
        "nld" to "nl", "dut" to "nl",
    )

    fun normalize(code: String?): String? {
        val base = code?.trim()?.lowercase(Locale.ROOT)?.split('-', '_')?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return three[base] ?: base
    }
}

/** Sohva's own user agent for streams, playlists and guides (PLAY-FR-17). */
object UserAgents {
    fun sohva(versionName: String?, release: String?): String =
        "Sohva TV/${versionName?.takeIf { it.isNotBlank() } ?: "?"} (Android TV ${release?.takeIf { it.isNotBlank() } ?: "?"})"
}

/** Subtitle look (PLAY-FR-100), each following the TV by default. */
enum class SubtitleSize(val scale: Float?) {
    FOLLOW_TV(null), SMALL(0.8f), NORMAL(1.0f), LARGE(1.3f), VERY_LARGE(1.6f);

    companion object {
        fun fromStored(value: String?): SubtitleSize = entries.firstOrNull { it.name == value } ?: FOLLOW_TV
    }
}

enum class SubtitleColor(val argb: Long?) {
    FOLLOW_TV(null), WHITE(0xFFFFFFFF), YELLOW(0xFFFFE14D);

    companion object {
        fun fromStored(value: String?): SubtitleColor = entries.firstOrNull { it.name == value } ?: FOLLOW_TV
    }
}

enum class SubtitleBackground {
    FOLLOW_TV, NONE, SHADOW, BOX;

    companion object {
        fun fromStored(value: String?): SubtitleBackground = entries.firstOrNull { it.name == value } ?: FOLLOW_TV
    }
}

/**
 * The player's configuration, read once per playback from preferences and handed to every mode
 * (spec 30 L-08: one object, so no call site forgets a setting).
 */
data class PlaybackSettings(
    val buffer: BufferProfile = BufferProfile.DEFAULT,
    val reconnect: ReconnectPolicy = ReconnectPolicy.STANDARD,
    val skipStep: SkipStep = SkipStep.TEN_SECONDS,
    val matchFrameRate: Boolean = true,
    val pictureInPicture: Boolean = false,
    val subtitleSize: SubtitleSize = SubtitleSize.FOLLOW_TV,
    val subtitleColor: SubtitleColor = SubtitleColor.FOLLOW_TV,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.FOLLOW_TV,
    val showChannelNumbers: Boolean = true,
    val timeZone: String? = null,
    /** VOD audio and subtitle languages (PLAY-FR-75); all Automatic until Settings offers them (M7). */
    val vodLanguages: VodLanguages = VodLanguages(),
)
