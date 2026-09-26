package com.sohva.tv.core.model.player

import java.util.Locale
import java.util.MissingResourceException

/**
 * Language codes of addon subtitles and stream tracks (spec 50 ADDON-FR-96): lower-case, cut at
 * `-`/`_`; "no language" words become null; ISO 639-2 codes map to 639-1 through the spec's
 * table, then the platform's table, else stay as they are.
 */
object AddonLanguages {
    private val none = setOf("und", "unknown", "off", "none")
    private val table = mapOf(
        "fin" to "fi", "eng" to "en", "swe" to "sv", "dan" to "da", "nor" to "no", "nob" to "no", "nno" to "no", "nb" to "no", "nn" to "no",
        "est" to "et", "deu" to "de", "ger" to "de", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it", "nld" to "nl", "dut" to "nl",
        "por" to "pt", "pol" to "pl", "ces" to "cs", "cze" to "cs", "ell" to "el", "gre" to "el", "ron" to "ro", "rum" to "ro",
        "zho" to "zh", "chi" to "zh", "jpn" to "ja", "kor" to "ko", "ara" to "ar", "rus" to "ru", "ukr" to "uk", "tur" to "tr",
        "hun" to "hu", "hrv" to "hr", "srp" to "sr", "slk" to "sk", "slo" to "sk", "slv" to "sl", "bul" to "bg", "heb" to "he",
    )

    /** Three-letter codes the platform knows, built once on first use (a few hundred entries). */
    private val platform: Map<String, String> by lazy {
        buildMap {
            for (code in Locale.getISOLanguages()) {
                try {
                    put(Locale(code).isO3Language, code)
                } catch (e: MissingResourceException) {
                    // A code without a three-letter form is simply not in the table.
                }
            }
        }
    }

    fun normalise(code: String?): String? {
        val base = code?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotEmpty() } ?: return null
        if (base in none) return null
        return table[base] ?: (if (base.length == 3) platform[base] else null) ?: base
    }
}
