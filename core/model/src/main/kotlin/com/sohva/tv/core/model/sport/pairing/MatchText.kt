package com.sohva.tv.core.model.sport.pairing

import java.text.Normalizer
import java.util.Locale

/**
 * Text as pairing compares it (spec 60 SPORT-FR-105): decomposed, marks removed, lower-case, every
 * run outside `[a-z0-9]` one space. "Bayern München" → "bayern munchen".
 */
object MatchText {
    fun normalise(text: String): String = words(text).joinToString(" ")

    /**
     * The words of [text], normalised, in one pass: ASCII letters and digits are kept (lower-cased),
     * anything else ASCII separates; only a non-ASCII character is decomposed (NFKD) and its marks
     * dropped. A guide description is 2,000 characters and pairing reads a hundred thousand of them,
     * so no regex and no whole-text copies (spec 60 §9 "Pairing").
     */
    fun words(text: String): List<String> {
        val out = ArrayList<String>()
        val word = StringBuilder()
        for (ch in text) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> word.append(ch)
                ch in 'A'..'Z' -> word.append(ch + CASE)
                ch.code < ASCII -> flush(word, out)
                else -> {
                    for (d in Normalizer.normalize(ch.toString(), Normalizer.Form.NFKD).lowercase(Locale.ROOT)) {
                        when {
                            d in 'a'..'z' || d in '0'..'9' -> word.append(d)
                            isMark(d) -> Unit
                            else -> flush(word, out)
                        }
                    }
                }
            }
        }
        flush(word, out)
        return out
    }

    private fun flush(word: StringBuilder, out: MutableList<String>) {
        if (word.isEmpty()) return
        out += word.toString()
        word.setLength(0)
    }

    private fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    private const val CASE = 32
    private const val ASCII = 128
}

/**
 * Team names and their aliases (SPORT-FR-106, -107). An alias applies only when the provider's name
 * normalises exactly to its canonical key; variants shorter than 3 characters are dropped.
 */
object TeamVariants {
    /** Beta 23's built-in football aliases, keyed by the normalised canonical name. */
    val BUILT_IN: Map<String, Set<String>> = mapOf(
        "manchester united" to setOf("man utd", "man united", "manchester utd"),
        "manchester city" to setOf("man city"),
        "tottenham hotspur" to setOf("tottenham", "spurs"),
        "paris saint germain" to setOf("psg", "paris sg"),
        "inter" to setOf("inter milan", "internazionale"),
        "bayern munchen" to setOf("bayern munich", "bayern"),
    )

    /** [extra] rows (canonical → aliases, any spelling) are merged with the built-in ones. */
    fun aliases(extra: Map<String, Set<String>>): Map<String, Set<String>> {
        val merged = HashMap<String, MutableSet<String>>()
        for ((canonical, names) in BUILT_IN.entries + extra.entries) {
            merged.getOrPut(MatchText.normalise(canonical)) { LinkedHashSet() } += names.map(MatchText::normalise)
        }
        return merged
    }

    fun of(team: String, aliases: Map<String, Set<String>>): List<String> {
        val key = MatchText.normalise(team)
        return (listOf(key) + aliases[key].orEmpty()).filter { it.length >= MIN_LENGTH }.distinct()
    }

    private const val MIN_LENGTH = 3
}
