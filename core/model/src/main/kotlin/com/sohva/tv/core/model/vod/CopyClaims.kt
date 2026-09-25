package com.sohva.tv.core.model.vod

import java.util.Locale

/** A language claim a copy's name makes (spec 40 VOD-FR-28), in the Versions row's order. */
enum class CopyLanguage {
    FINNISH, SWEDISH, ENGLISH, DANISH, NORWEGIAN, GERMAN, FRENCH, SPANISH, NORDIC, SUBTITLED, MULTIPLE_AUDIO;

    val bit: Int get() = 1 shl ordinal
}

/** A picture-quality chip (spec 40 VOD-FR-30), in display order. */
enum class QualityChip(val label: String) {
    UHD("4K UHD"), DOLBY_VISION("Dolby Vision"), HDR10_PLUS("HDR10+"), HDR10("HDR10");

    val bit: Int get() = 1 shl ordinal
}

/**
 * What a copy's name claims about itself (display and ranking only, never trusted as fact).
 * [languageMask] holds [CopyLanguage] bits, [qualityMask] [QualityChip] bits; [resolution] is a
 * lower-cased "1080p"-style token or null.
 */
data class CopyClaims(val languageMask: Int, val qualityMask: Int, val resolution: String?) {
    val languages: List<CopyLanguage> get() = CopyLanguage.entries.filter { languageMask and it.bit != 0 }

    /** Chips, then the resolution: the Versions row's picture part. */
    val picture: List<String> get() = QualityChips.labels(qualityMask) + listOfNotNull(resolution)

    /**
     * The "largest picture" rank of VOD-FR-29: size × 2, plus 1 for any high dynamic range. Size is
     * 4 for 4K UHD or 2160p, 3 for 1080p, 2 for 720p, 1 for 480p, else 0.
     */
    val pictureRank: Int
        get() {
            val size = when {
                qualityMask and QualityChip.UHD.bit != 0 || resolution == "2160p" -> 4
                resolution == "1080p" -> 3
                resolution == "720p" -> 2
                resolution == "480p" -> 1
                else -> 0
            }
            val hdr = qualityMask and (QualityChip.DOLBY_VISION.bit or QualityChip.HDR10_PLUS.bit or QualityChip.HDR10.bit) != 0
            return size * 2 + if (hdr) 1 else 0
        }
}

/**
 * Reads [CopyClaims] off a provider title. Languages are read only inside the claim zones — a
 * prefix closed by a delimiter, anything bracketed, or a technical tail — so "Fin del mundo" is
 * not a Finnish copy. Runs at import, never in composition (spec 40 §9.4).
 */
object CopyClaimReader {
    private val zones = Regex(
        """(?i)^(?:\s*\p{L}{2,7}\s*[|•·:–—-]+)+|[\[({][^\])}]*[\])}]|(?:[|•·]|\s[-–—])\s*(?:multi[- ]?\w+\s*)+$""",
    )
    private val markers: List<Pair<CopyLanguage, Regex>> = listOf(
        CopyLanguage.FINNISH to "fi|fin|suomi",
        CopyLanguage.SWEDISH to "sv|swe",
        CopyLanguage.ENGLISH to "en|eng",
        CopyLanguage.DANISH to "da|dan",
        CopyLanguage.NORWEGIAN to "no|nor",
        CopyLanguage.GERMAN to "de|ger",
        CopyLanguage.FRENCH to "fr|fre",
        CopyLanguage.SPANISH to "es|spa",
        CopyLanguage.NORDIC to "nordic|nc",
        CopyLanguage.SUBTITLED to "multi[- ]?(?:subs?|subtitles?)",
        CopyLanguage.MULTIPLE_AUDIO to "multi[- ]?audio",
    ).map { (language, words) -> language to Regex("(?<![a-z0-9])(?:$words)(?![a-z0-9])") }
    private val resolution = Regex("""(?<![A-Za-z0-9])\d{3,4}[pP](?![A-Za-z0-9])""")

    fun read(name: String): CopyClaims {
        val claimed = zones.findAll(name).joinToString(" ") { it.value }.lowercase(Locale.ROOT)
        var mask = 0
        if (claimed.isNotEmpty()) {
            for ((language, marker) in markers) if (marker.containsMatchIn(claimed)) mask = mask or language.bit
        }
        return CopyClaims(mask, QualityChips.mask(name), resolution.find(name)?.value?.lowercase(Locale.ROOT))
    }
}

/** The quality chips of VOD-FR-30: "4K UHD", then at most one of Dolby Vision, HDR10+, HDR10. */
object QualityChips {
    private val uhd = Regex("""(?:^|[^A-Z0-9])(?:4K|UHD)(?:[^A-Z0-9]|$)""")
    private val hdrAlone = Regex("""(?:^|[^A-Z0-9])HDR(?:[^A-Z0-9+]|$)""")

    fun mask(title: String): Int {
        val upper = title.uppercase(Locale.ROOT)
        var mask = if (uhd.containsMatchIn(upper)) QualityChip.UHD.bit else 0
        mask = mask or when {
            "DOLBY VISION" in upper || "DOVI" in upper -> QualityChip.DOLBY_VISION.bit
            "HDR10+" in upper -> QualityChip.HDR10_PLUS.bit
            "HDR10" in upper || hdrAlone.containsMatchIn(upper) -> QualityChip.HDR10.bit
            else -> 0
        }
        return mask
    }

    fun labels(mask: Int): List<String> = QualityChip.entries.filter { mask and it.bit != 0 }.map { it.label }
}
