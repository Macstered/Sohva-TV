package com.streammate.tv.addons

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.util.Locale

internal object AddonSubtitleTiming {
    const val LIMIT_MILLIS = 60_000L
    fun bound(value: Long) = value.coerceIn(-LIMIT_MILLIS, LIMIT_MILLIS)
    fun label(value: Long) = String.format(Locale.ROOT, "%+.3f s", bound(value) / 1000.0)
    fun step(value: Long, direction: Int, coarse: Boolean) = bound(value + direction.coerceIn(-1, 1) * if (coarse) 1000L else 100L)
}

/** A fixed offset for one media preparation. Applied before cues enter Media3's
 * sample queues, so seek filtering uses shifted timestamps too. No video/audio
 * timestamps or subtitle bytes are rewritten. A new setting is committed once
 * with Apply; it never restarts the media for each D-pad repeat. */
@OptIn(UnstableApi::class)
internal class AddonTimingParserFactory(
    delayMillis: Long,
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
) : SubtitleParser.Factory {
    private val delayUs = AddonSubtitleTiming.bound(delayMillis) * 1000
    override fun supportsFormat(format: Format) = delegate.supportsFormat(format)
    override fun getCueReplacementBehavior(format: Format) = delegate.getCueReplacementBehavior(format)
    override fun create(format: Format): SubtitleParser {
        val parser = delegate.create(format)
        if (delayUs == 0L) return parser
        return object : SubtitleParser by parser {
            override fun parse(data: ByteArray, outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) =
                parse(data, 0, data.size, outputOptions, output)
            override fun parse(data: ByteArray, offset: Int, length: Int, outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
                val options = when {
                    outputOptions.startTimeUs == C.TIME_UNSET -> outputOptions
                    outputOptions.outputAllCues -> SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(outputOptions.startTimeUs - delayUs)
                    else -> SubtitleParser.OutputOptions.onlyCuesAfter(outputOptions.startTimeUs - delayUs)
                }
                parser.parse(data, offset, length, options) { cues ->
                    // Untimed cues are relative to their container sample timestamp.
                    val start = if (cues.startTimeUs == C.TIME_UNSET) delayUs else Math.addExact(cues.startTimeUs, delayUs)
                    output.accept(CuesWithTiming(cues.cues, start, cues.durationUs))
                }
            }
        }
    }
}
