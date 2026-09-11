package com.sohva.tv.addons

import java.util.Locale

/** Language codes from providers may be ISO-639-1, -2 or region-qualified. */
object AddonSubtitlePolicy {
    private val iso3Aliases = Locale.getISOLanguages().mapNotNull { code ->
        runCatching { Locale.forLanguageTag(code).isO3Language to code }.getOrNull()
    }.toMap()
    fun language(value: String?): String? {
        val base = value?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
            ?.takeIf { it.isNotEmpty() && it !in setOf("und", "unknown", "off", "none") } ?: return null
        return when (base) {
            "fin" -> "fi"; "eng" -> "en"; "swe" -> "sv"; "dan" -> "da"
            "nor", "nob", "nno", "nb", "nn" -> "no"; "est" -> "et"
            "deu", "ger" -> "de"; "fra", "fre" -> "fr"; "spa" -> "es"
            "ita" -> "it"; "nld", "dut" -> "nl"; "por" -> "pt"
            "pol" -> "pl"; "ces", "cze" -> "cs"; "ell", "gre" -> "el"
            "ron", "rum" -> "ro"; "zho", "chi" -> "zh"; "jpn" -> "ja"
            "kor" -> "ko"; "ara" -> "ar"; "rus" -> "ru"; "ukr" -> "uk"
            "tur" -> "tr"; "hun" -> "hu"; "hrv" -> "hr"; "srp" -> "sr"
            "slk", "slo" -> "sk"; "slv" -> "sl"; "bul" -> "bg"; "heb" -> "he"
            else -> iso3Aliases[base] ?: base
        }
    }
    fun preferred(primary: String?, secondary: String?): List<String> = listOfNotNull(language(primary), language(secondary)).distinct()
    fun matches(value: String?, preferred: List<String>): Boolean = language(value) in preferred
    fun visible(items: List<AddonSubtitle>, preferred: List<String>, showAll: Boolean): List<AddonSubtitle> =
        items.filter { showAll || matches(it.language, preferred) }
            .sortedBy { preferred.indexOf(language(it.language)).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    fun suppressForAudio(primaryAudio: String?, availableAudio: List<String?>): Boolean =
        language(primaryAudio)?.let { primary -> availableAudio.any { language(it) == primary } } == true
}
