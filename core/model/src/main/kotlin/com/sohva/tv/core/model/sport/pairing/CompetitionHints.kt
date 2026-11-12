package com.sohva.tv.core.model.sport.pairing

/** Index only the current scan's reviewed competitions. Country/language tags are not evidence. */
internal class CompetitionHints(names: Map<String, Set<String>>) {
    private data class Phrase(val words: List<String>, val identity: String)
    private val index = HashMap<String, MutableList<Phrase>>()

    init {
        names.forEach { (identity, aliases) ->
            aliases.forEach { alias ->
                val words = alias.split(' ')
                if (words.isNotEmpty() && words.first().isNotEmpty()) index.getOrPut(words.first()) { ArrayList() } += Phrase(words, identity)
            }
        }
    }

    fun find(words: List<String>): Set<String> {
        if (index.isEmpty()) return emptySet()
        val identities = HashSet<String>()
        for (at in words.indices) {
            val candidates = index[words[at]] ?: continue
            var width = 0
            val found = HashSet<String>()
            for (phrase in candidates) {
                if (at + phrase.words.size > words.size || !(1 until phrase.words.size).all { words[at + it] == phrase.words[it] }) continue
                if (phrase.words.size > width) {
                    width = phrase.words.size
                    found.clear()
                }
                if (phrase.words.size == width) found += phrase.identity
            }
            identities += found
        }
        return identities
    }

    fun evidence(expected: String?, found: Set<String>, supporting: Set<String> = emptySet(), day: Long? = null): Int {
        if (found.isEmpty() || expected == null && supporting.isEmpty()) return 0
        // Alias groups from other fixture dates cannot lend historical competition names.
        val eligible = if (day == null) found else found.filter { it.endsWith("@$day") }.map { it.substringBeforeLast('@') }.toSet()
        return when {
            eligible.size != 1 -> 0
            expected in eligible || expected == null && eligible.any { it in supporting } -> 1
            else -> -1
        }
    }
}
