package com.sohva.tv.core.model.text

/**
 * Up to two letters for a tile whose artwork is missing (design/04 §3). Only words that start with
 * a letter count, so years, season markers and separators never become initials ("Ben 10" → "BE").
 * One word gives its first two characters; more give the first letter of each of the first two.
 * When no word starts with a letter, the first two non-blank characters are used (beta 23's
 * catalogue behaviour; its Home copy showed nothing).
 */
object Initials {
    private val separators = charArrayOf(' ', '.', '-', ':', '_', '·', '/')

    fun of(name: String): String {
        val words = name.split(*separators).filter { it.isNotEmpty() && it.first().isLetter() }
        val initials = when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}"
            words.size == 1 -> words[0].take(2)
            else -> name.filterNot { it.isWhitespace() }.take(2)
        }
        return initials.uppercase()
    }
}
