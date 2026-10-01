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
