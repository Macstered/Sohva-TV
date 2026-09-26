package com.sohva.tv.core.model.text

import java.util.Locale

/**
 * Quality and language markers read off a channel name (spec 10 SRC-FR-102..103), computed once at
 * import and stored. The name is upper-cased and split on space and `| : - _ / ( ) [ ] , .`; per
 * kind the first matching token wins; languages are whole tokens only. Rebuild fix: a token the
 * resolution table claimed is never read as a frame rate ("Sport 720p" is "HD", not "HD · 720 FPS").
 */
object StreamTags {
    private val separators = Regex("[\\s|:\\-_/()\\[\\],.]+")
    private val frameRate = Regex("(\\d{2,3})(?:FPS|HZ|P)")

    private val resolution = mapOf(
        "4K" to "4K", "UHD" to "4K", "2160P" to "4K", "4KUHD" to "4K",
        "FHD" to "FHD", "FULLHD" to "FHD", "1080P" to "FHD", "1080" to "FHD",
        "HD" to "HD", "720P" to "HD", "720" to "HD",
        "SD" to "SD", "576P" to "SD", "480P" to "SD",
    )
    private val dynamicRange = mapOf(
        "HDR" to "HDR", "HDR10" to "HDR10", "HDR10+" to "HDR10+", "DV" to "DOLBY VISION",
        "DOLBYVISION" to "DOLBY VISION", "HLG" to "HLG",
    )
    private val languages: Map<String, String> = buildMap {
        fun code(label: String, vararg tokens: String) = tokens.forEach { put(it, label) }
        code("FI", "FI", "FIN", "SUOMI"); code("SE", "SE", "SV", "SWE"); code("NO", "NO", "NOR")
        code("DK", "DK", "DAN"); code("EN", "EN", "ENG"); code("UK", "UK", "GB", "GBR"); code("US", "US", "USA")
        code("DE", "DE", "GER", "DEU"); code("EE", "EE", "EST"); code("RU", "RU", "RUS"); code("FR", "FR", "FRA")
        code("ES", "ES", "ESP", "SPA"); code("IT", "IT", "ITA"); code("NL", "NL", "NLD"); code("PL", "PL", "POL")
        code("AR", "AR", "ARG"); code("AL", "AL", "ALB", "SQ"); code("PT", "PT", "POR"); code("BR", "BR", "BRA")
        code("TR", "TR", "TUR"); code("GR", "GR", "GRE"); code("RO", "RO", "RON"); code("CZ", "CZ", "CZE")
        code("HR", "HR", "HRV"); code("RS", "RS", "SRB"); code("CA", "CA", "CAN"); code("AU", "AU", "AUS")
    }

    /** What a tag says; a stream row tints each kind differently (spec 60 SPORT-FR-74). */
    enum class Kind { RESOLUTION, DYNAMIC_RANGE, FRAME_RATE, LANGUAGE }

    /** Tags in order resolution, dynamic range, frame rate, language. */
    fun of(name: String): List<String> = parts(name).map { it.second }

    /** [of] with each tag's kind. */
    fun parts(name: String): List<Pair<Kind, String>> {
        // "+" is not a separator, so "HDR10+" stays one token.
        val tokens = name.uppercase(Locale.ROOT).split(separators).filter { it.isNotEmpty() }
        val res = tokens.firstNotNullOfOrNull { resolution[it] }
        val range = tokens.firstNotNullOfOrNull { dynamicRange[it] }
        val fps = tokens.firstNotNullOfOrNull { token ->
            if (token in resolution) null else frameRate.matchEntire(token)?.let { "${it.groupValues[1]} FPS" }
        }
        val language = tokens.firstNotNullOfOrNull { languages[it] }
        return listOfNotNull(
            res?.let { Kind.RESOLUTION to it }, range?.let { Kind.DYNAMIC_RANGE to it },
            fps?.let { Kind.FRAME_RATE to it }, language?.let { Kind.LANGUAGE to it },
        )
    }

    /** The first language or country tag of [name], or null (spec 60 SPORT-FR-117 priority ordering). */
    fun language(name: String): String? =
        name.uppercase(Locale.ROOT).split(separators).firstNotNullOfOrNull { languages[it] }

    /** Normalises a priority list of language codes: known codes only, distinct, at most 8. */
    fun normalizeLanguages(codes: List<String>): List<String> =
        codes.mapNotNull { languages[it.trim().uppercase(Locale.ROOT)] }.distinct().take(8)
}
