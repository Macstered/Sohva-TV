package com.sohva.tv.core.model.player

import java.util.Locale

/**
 * One sample of the playback info line (spec 30 PLAY-FR-83). Unmeasured values are null and are
 * left out, never printed as zero.
 */
data class StreamStats(
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val audioChannels: Int? = null,
    val videoBitrate: Int? = null,
    val subtitleMime: String? = null,
    val bufferedSeconds: Float = 0f,
    val droppedFrames: Int? = null,
)

/** Values of the info line in its order; labels ("Subtitles", "Buffer", "Dropped") come from resources. */
object InfoLine {
    /** "1920×1080 50p", or null without a size. */
    fun resolution(s: StreamStats): String? {
        val w = s.width ?: return null
        val h = s.height ?: return null
        if (w <= 0 || h <= 0) return null
        val rate = s.frameRate?.takeIf { it > 0f }?.let { " " + String.format(Locale.ROOT, "%.0f", it) + "p" }.orEmpty()
        return "$w×$h$rate"
    }

    /** "AVC · MP4A-LATM 2.0"; either half alone; null when both are unknown. */
    fun codecs(s: StreamStats): String? {
        val video = subtype(s.videoMime)
        val audio = subtype(s.audioMime)?.let { a -> s.audioChannels?.let { "$a ${layout(it)}" } ?: a }
        return listOfNotNull(video, audio).joinToString(" · ").ifEmpty { null }
    }

    fun bitrate(s: StreamStats): String? =
        s.videoBitrate?.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f Mb/s", it / 1_000_000.0) }

    fun buffer(s: StreamStats): String = String.format(Locale.ROOT, "%.1f s", maxOf(0f, s.bufferedSeconds))

    fun layout(channels: Int): String = when (channels) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$channels ch"
    }

    private fun subtype(mime: String?): String? =
        mime?.substringAfter('/', "")?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
}

/** What went wrong in words (PLAY-FR-93); the player maps Media3's codes to these. */
enum class PlaybackCause {
    NETWORK, REFUSED, GONE, SERVER, BROKE_OFF, NOT_A_STREAM, DECODER, CONNECTION_LIMIT, NO_LONGER_AVAILABLE, TOO_HEAVY, OTHER;

    companion object {
        /** HTTP status of a failed stream request to its cause. */
        fun ofHttpStatus(status: Int): PlaybackCause = when (status) {
            401, 403 -> REFUSED
            404, 410 -> GONE
            in 500..599 -> SERVER
            else -> OTHER
        }
    }
}
