package com.sohva.tv.core.model.text

import java.text.Normalizer
import java.util.Locale

/**
 * The `sort_name` column (plan/04 §15.1 principle 4): computed once at write so lists sort in an
 * index, because SQLite's `LOWER` and `NOCASE` fold ASCII only. NFKD without combining marks,
 * lower-cased in the root locale, whitespace collapsed; other scripts are kept. Provisional: the
 * walls milestone (M4) may switch to the interface language's collation, which rewrites the column
 * in a bulk pass, so every caller goes through this one function.
 */
object SortNames {
    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    fun of(name: String): String {
        val folded = marks.replace(Normalizer.normalize(name, Normalizer.Form.NFKD), "").lowercase(Locale.ROOT)
        return spaces.replace(folded.trim(), " ")
    }
}
