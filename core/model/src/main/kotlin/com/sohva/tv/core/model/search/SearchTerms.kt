package com.sohva.tv.core.model.search

/**
 * The search text (spec 03 SEARCH-FR-01, -02): cut to 80 characters, trimmed, and at least two
 * characters before anything runs. [match] turns it into an FTS4 query: every word must start a
 * word of the title (word-prefix matching, decision "Search matching").
 */
object SearchTerms {
    const val MAX: Int = 80
    const val MIN: Int = 2

    fun cut(text: String): String = text.take(MAX)

    /** The term to search for, or null when it is too short. */
    fun term(text: String): String? = cut(text).trim().takeIf { it.length >= MIN }

    /**
     * `"word*"` (FTS4's prefix form) for each run of letters and digits, joined with spaces (all must match). Quotes keep
     * FTS operators (AND, OR, NEAR, `-`) literal; null when the term has no letters or digits.
     */
    fun match(term: String): String? {
        val words = term.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "\"$it*\"" }
    }
}
