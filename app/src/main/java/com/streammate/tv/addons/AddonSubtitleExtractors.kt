package com.streammate.tv.addons

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.*
import androidx.media3.extractor.text.SubtitleParser

/** Read enough container history after an unbuffered seek to recover delayed
 * embedded cues. Decode still starts at the requested video position. Sidecars
 * do not need this: their complete cue timeline is independently available. */
@OptIn(UnstableApi::class)
internal class AddonSubtitleExtractors(delayMillis: Long, private val delegate: DefaultExtractorsFactory = DefaultExtractorsFactory()) : ExtractorsFactory {
    private val prerollUs = AddonSubtitleTiming.bound(delayMillis).coerceAtLeast(0) * 1000
    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory {
        delegate.setSubtitleParserFactory(factory)
        return this
    }
    override fun createExtractors(): Array<Extractor> = delegate.createExtractors().map(::wrap).toTypedArray()
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        delegate.createExtractors(uri, responseHeaders).map(::wrap).toTypedArray()

    private fun wrap(extractor: Extractor): Extractor = if (prerollUs == 0L) extractor else object : Extractor by extractor {
        override fun init(output: ExtractorOutput) = extractor.init(object : ExtractorOutput by output {
            override fun seekMap(seekMap: SeekMap) {
                output.seekMap(object : SeekMap by seekMap {
                    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
                        val points = seekMap.getSeekPoints((timeUs - prerollUs).coerceAtLeast(0))
                        // Only the byte position changes; do not shift the player's
                        // requested time or advertise a different video timeline.
                        return SeekMap.SeekPoints(SeekPoint(timeUs, points.first.position))
                    }
                })
            }
        })
    }
}
