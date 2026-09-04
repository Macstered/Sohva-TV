package com.streammate.tv.core.model

/** What a [StreamTag] says about a stream, so a screen can order or style them. */
enum class StreamTagKind {
    RESOLUTION,
    DYNAMIC_RANGE,
    FRAME_RATE,
    LANGUAGE,
}

/** One readable marker taken off a provider's channel name, such as `FHD`. */
data class StreamTag(val kind: StreamTagKind, val label: String)

/**
 * Reads the quality and language markers a provider writes into a channel name.
 *
 * Playlists have no structured field for any of this. Providers put it in the
 * name - `TV5 FHD FI`, `FI| Sport 1 4K 50FPS` - and viewers rely on it to pick
 * between two channels carrying the same match, so it is worth surfacing as
 * something better than a wall of text.
 *
 * Only well-known markers are recognised. A token is never guessed at: a name
 * with nothing to say produces no tags rather than a misleading one.
 */
object ChannelStreamTags {

    fun read(channelName: String): List<StreamTag> {
        val tokens = channelName.uppercase().split(*SEPARATORS).filter(String::isNotBlank)
        val resolution = tokens.firstNotNullOfOrNull(RESOLUTIONS::get)
        val dynamicRange = tokens.firstNotNullOfOrNull(DYNAMIC_RANGES::get)
        val frameRate = tokens.firstNotNullOfOrNull { token ->
            FRAME_RATE.matchEntire(token)?.groupValues?.get(1)?.let { "$it FPS" }
        }
        // A language marker is only ever a whole token. "FI" in "SciFi" is not a
        // language, and a name that happens to contain "DE" is not German.
        val language = tokens.firstNotNullOfOrNull(LANGUAGES::get)

        return listOfNotNull(
            resolution?.let { StreamTag(StreamTagKind.RESOLUTION, it) },
            dynamicRange?.let { StreamTag(StreamTagKind.DYNAMIC_RANGE, it) },
            frameRate?.let { StreamTag(StreamTagKind.FRAME_RATE, it) },
            language?.let { StreamTag(StreamTagKind.LANGUAGE, it) },
        )
    }

    private val SEPARATORS = charArrayOf(' ', '|', ':', '-', '_', '/', '(', ')', '[', ']', ',', '.')

    private val RESOLUTIONS = mapOf(
        "4K" to "4K", "UHD" to "4K", "2160P" to "4K", "4KUHD" to "4K",
        "FHD" to "FHD", "FULLHD" to "FHD", "1080P" to "FHD", "1080" to "FHD",
        "HD" to "HD", "720P" to "HD", "720" to "HD",
        "SD" to "SD", "576P" to "SD", "480P" to "SD",
    )

    private val DYNAMIC_RANGES = mapOf(
        "HDR" to "HDR", "HDR10" to "HDR10", "HDR10+" to "HDR10+",
        "DV" to "DOLBY VISION", "DOLBYVISION" to "DOLBY VISION", "HLG" to "HLG",
    )

    private val FRAME_RATE = Regex("(\\d{2,3})(?:FPS|HZ|P)")

    private val LANGUAGES = mapOf(
        "FI" to "FI", "FIN" to "FI", "SUOMI" to "FI",
        "SE" to "SE", "SV" to "SE", "SWE" to "SE",
        "NO" to "NO", "NOR" to "NO",
        "DK" to "DK", "DAN" to "DK",
        "EN" to "EN", "ENG" to "EN", "UK" to "UK", "US" to "US",
        "DE" to "DE", "GER" to "DE",
        "EE" to "EE", "EST" to "EE",
        "RU" to "RU", "RUS" to "RU",
        "FR" to "FR", "ES" to "ES", "IT" to "IT", "NL" to "NL", "PL" to "PL",
    )
}
