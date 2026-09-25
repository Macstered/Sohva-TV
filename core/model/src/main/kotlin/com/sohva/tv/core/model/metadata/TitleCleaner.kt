package com.sohva.tv.core.model.metadata

import java.text.Normalizer
import java.util.Locale

/**
 * The title cleaning of spec 41 §4.4, written by hand (§9.1): no regular expression runs here.
 * Beta 23 defined these steps as regexes and ran about twenty of them per title through ICU,
 * which cost seconds per catalogue on slow cores. Each step below is a forward scan (or a
 * backwards check for the end-anchored steps) that reproduces its pattern exactly; the patterns
 * themselves live in the tests as the reference, and a property test compares both on 100,000
 * generated titles (TitleCleanerEquivalenceTest).
 *
 * Character classes follow the JVM's defaults, as the patterns were written: `\s` is the six ASCII
 * space characters, `\d` is ASCII digits, `(?i)` folds ASCII only, and a word character (for `\b`)
 * is a letter, a digit or `_`.
 */
object TitleCleaner {
    /**
     * Stored keys that depend on this code (lookup keys, film work keys) carry this version; a
     * change of behaviour bumps it (spec 41 META-FR-18).
     */
    const val NORMALISER_VERSION: Int = 4

    /** What is sent to providers and shown before metadata arrives (META-FR-20). */
    fun searchTitle(value: String): String {
        var s = decorationGroups(value)
        s = languagePrefix(s)
        s = decorationPrefix(s)
        s = seasonEpisodeMarkers(s)
        s = bracketedTags(s)
        s = trailingYear(s)
        s = trailingTechnical(s)
        return collapseSpaces(trimSearch(s))
    }

    /** The comparison form of a title (META-FR-21): lower case, letters and digits only. */
    fun normalizeTitle(value: String): String {
        var s = nfkd(decorationGroups(value)).lowercase(Locale.ROOT)
        s = languagePrefix(s)
        s = decorationPrefix(s)
        s = seasonEpisodeMarkers(s)
        s = bracketedTags(s)
        s = trailingYear(s)
        s = trailingTechnical(s)
        return collapseSpaces(lettersAndDigits(s).trim())
    }

    /** A `(19|20)dd` year at the very end, bracketed or after a space (META-FR-22). */
    fun yearFromTitle(value: String): Int? {
        val at = yearSuffixStart(value)
        if (at < 0) return null
        var year = 0
        var digits = 0
        for (i in at until value.length) {
            val c = value[i]
            if (c in '0'..'9') {
                year = year * 10 + (c - '0')
                digits++
            }
        }
        return if (digits > 0) year else null
    }

    // ---- Step A: decoration groups --------------------------------------------------------------

    /** `\[[^\[\]\r\n]*\]|\([^()\r\n]*\)|\{[^{}\r\n]*\}` → space; all-decoration names keep their words. */
    internal fun decorationGroups(value: String): String {
        // Most titles have no brackets: nothing to remove, and the fallback would change nothing either.
        if (value.none { it == '[' || it == '(' || it == '{' || it == ']' || it == ')' || it == '}' }) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val close = when (c) {
                '[' -> ']'
                '(' -> ')'
                '{' -> '}'
                else -> NO_CHAR
            }
            if (close != NO_CHAR) {
                var j = i + 1
                while (j < value.length) {
                    val d = value[j]
                    if (d == c || d == close || d == '\r' || d == '\n') break
                    j++
                }
                if (j < value.length && value[j] == close) {
                    out.append(' ')
                    i = j + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        if (out.any { it.isLetterOrDigit() }) return out.toString()
        val plain = StringBuilder(value.length)
        for (c in value) plain.append(if (c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}') ' ' else c)
        return plain.toString()
    }

    // ---- Step B: language prefix ----------------------------------------------------------------

    /** A bracketed 2/3-letter code, or a 3-letter code before `| • · -`, at the start (anchored, once). */
    internal fun languagePrefix(s: String): String {
        var i = skipSpaces(s, 0)
        if (i >= s.length) return s
        val c = s[i]
        if (c == '[' || c == '(') {
            var matched = -1
            for (k in 2..3) {
                val close = i + 1 + k
                if (close < s.length && (s[close] == ']' || s[close] == ')') && lowerAscii(s, i + 1, close) in LANGUAGE_CODES) {
                    matched = close + 1
                    break
                }
            }
            if (matched < 0) return s
            i = matched
        } else {
            if (i + 3 > s.length || lowerAscii(s, i, i + 3) !in LANGUAGE_CODES_3) return s
            var j = skipSpaces(s, i + 3)
            if (j >= s.length || s[j] !in LANGUAGE_DELIMITERS) return s
            i = j + 1
        }
        return " " + s.substring(skipSpaces(s, i))
    }

    // ---- Step C: decoration prefix --------------------------------------------------------------

    /**
     * One or more whole decoration tokens at the start, each with its trailing spaces and
     * delimiters, removed when something non-space follows (anchored, once). The search follows
     * the pattern's priorities: as many tokens as possible, each alternative in pattern order,
     * trailing runs as long as possible, then fewer, so the result equals the regex's.
     */
    internal fun decorationPrefix(s: String): String {
        val start = skipSpaces(s, 0)
        val end = prefixRepetitions(s, start, 0)
        return if (end < 0) s else " " + s.substring(end)
    }

    private fun prefixRepetitions(s: String, pos: Int, count: Int): Int {
        val tokenEnds = IntArray(MAX_ALTERNATIVES)
        val tokens = prefixTokenEnds(s, pos, tokenEnds)
        for (t in 0 until tokens) {
            val e = tokenEnds[t]
            // \s* greedy, then (?:[delims]+\s*)? greedy: longest first.
            val spaces = countSpaces(s, e)
            for (a in spaces downTo 0) {
                val p1 = e + a
                if (a == spaces) {
                    val delims = countWhile(s, p1) { it in PREFIX_DELIMITERS }
                    for (d in delims downTo 1) {
                        val p2 = p1 + d
                        val after = countSpaces(s, p2)
                        for (b in after downTo 0) {
                            val found = prefixRepetitions(s, p2 + b, count + 1)
                            if (found >= 0) return found
                        }
                    }
                }
                val found = prefixRepetitions(s, p1, count + 1)
                if (found >= 0) return found
            }
        }
        return if (count >= 1 && pos < s.length && !isSpace(s[pos])) pos else -1
    }

    /** Ends of the decoration tokens at [pos], in the pattern's priority order, each followed by `\s`, a delimiter or the end. */
    private fun prefixTokenEnds(s: String, pos: Int, out: IntArray): Int {
        var n = 0
        fun offer(end: Int) {
            if (end <= 0 || n == out.size) return
            if (end == s.length || isSpace(s[end]) || s[end] in PREFIX_DELIMITERS) out[n++] = end
        }
        for (token in PREFIX_TOKENS) {
            when (token) {
                "hdr" -> hdrEnds(s, pos) { offer(it) }
                "dolby" -> offer(dolbyVisionEnd(s, pos))
                "x26" -> if (matchesAscii(s, pos, "x26") && pos + 3 < s.length && (s[pos + 3] == '4' || s[pos + 3] == '5')) offer(pos + 4)
                "multi" -> multiEnds(s, pos, withAnd = false) { offer(it) }
                else -> if (matchesAscii(s, pos, token)) offer(pos + token.length)
            }
        }
        return n
    }

    // ---- Step D: season and episode markers -----------------------------------------------------

    /** `\b(?:s\d{1,2}e\d{1,3}|k\d{1,2}\s*j\d{1,3})\b` → space, everywhere. */
    internal fun seasonEpisodeMarkers(s: String): String {
        var out: StringBuilder? = null
        var i = 0
        var copied = 0
        while (i < s.length) {
            val c = s[i]
            if ((c == 's' || c == 'S' || c == 'k' || c == 'K') && (i == 0 || !isWord(s[i - 1]))) {
                val end = markerEnd(s, i)
                if (end > 0) {
                    val b = out ?: StringBuilder(s.length).also { out = it }
                    b.append(s, copied, i).append(' ')
                    i = end
                    copied = end
                    continue
                }
            }
            i++
        }
        val b = out ?: return s
        return b.append(s, copied, s.length).toString()
    }

    private fun markerEnd(s: String, i: Int): Int {
        val first = countDigits(s, i + 1)
        if (first !in 1..2) return -1
        var p = i + 1 + first
        val season = s[i] == 's' || s[i] == 'S'
        if (!season) p = skipSpaces(s, p)
        if (p >= s.length) return -1
        val sep = s[p]
        if (season && sep != 'e' && sep != 'E') return -1
        if (!season && sep != 'j' && sep != 'J') return -1
        val second = countDigits(s, p + 1)
        if (second !in 1..3) return -1
        val end = p + 1 + second
        return if (end == s.length || !isWord(s[end])) end else -1
    }

    // ---- Step E: bracketed technical tags -------------------------------------------------------

    /**
     * `\[(?:multi…|imdb…|4k|uhd|fhd|hd|hdr…|dolby vision|only on … devices|x26[45]|hevc)\]` →
     * space. Step A has removed every complete `[…]` group, so this fires only on brackets that
     * NFKD made from compatibility characters; it is kept for equality.
     */
    internal fun bracketedTags(s: String): String {
        if (s.indexOf('[') < 0) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '[') {
                val close = s.indexOf(']', i + 1)
                if (close > 0 && isBracketTag(s, i + 1, close)) {
                    out.append(' ')
                    i = close + 1
                    continue
                }
            }
            out.append(s[i])
            i++
        }
        return out.toString()
    }

    private fun isBracketTag(s: String, from: Int, to: Int): Boolean {
        for (tag in SIMPLE_TAGS) if (to - from == tag.length && matchesAscii(s, from, tag)) return true
        var found = false
        hdrEnds(s, from) { if (it == to) found = true }
        if (found || dolbyVisionEnd(s, from) == to) return true
        if (to - from == 4 && matchesAscii(s, from, "x26") && (s[from + 3] == '4' || s[from + 3] == '5')) return true
        multiEnds(s, from, withAnd = true, requireGroup = true) { if (it == to) found = true }
        return found || isImdb(s, from, to) || isOnlyOnDevices(s, from, to)
    }

    /** `imdb(?:\s*(?:top\s*\d+|\d+(?:\.\d+)?))?` filling exactly [from, to). */
    private fun isImdb(s: String, from: Int, to: Int): Boolean {
        if (!matchesAscii(s, from, "imdb") || from + 4 > to) return false
        var p = from + 4
        if (p == to) return true
        p = skipSpaces(s, p, to)
        if (p >= to) return false
        if (matchesAscii(s, p, "top") && p + 3 <= to) {
            val q = skipSpaces(s, p + 3, to)
            val d = countDigits(s, q, to)
            if (d > 0 && q + d == to) return true
        }
        val d = countDigits(s, p, to)
        if (d == 0) return false
        p += d
        if (p == to) return true
        if (s[p] != '.') return false
        val f = countDigits(s, p + 1, to)
        return f > 0 && p + 1 + f == to
    }

    /** `only\s+on[^]]+devices?` filling exactly [from, to). */
    private fun isOnlyOnDevices(s: String, from: Int, to: Int): Boolean {
        if (!matchesAscii(s, from, "only")) return false
        val spaces = countSpaces(s, from + 4, to)
        if (spaces == 0) return false
        val on = from + 4 + spaces
        if (!matchesAscii(s, on, "on")) return false
        val rest = on + 2
        for (word in DEVICE_WORDS) {
            val start = to - word.length
            if (start > rest && matchesAscii(s, start, word)) return true
        }
        return false
    }

    // ---- Step F: trailing year ------------------------------------------------------------------

    /** `(?:[\[(](?:19|20)\d{2}[\])]|\s+(?:19|20)\d{2})\s*$` → space (the leftmost match reaches the end). */
    internal fun trailingYear(s: String): String {
        val at = yearSuffixStart(s)
        return if (at < 0) s else s.substring(0, at) + " "
    }

    private fun yearSuffixStart(s: String): Int {
        // Only spaces may follow the year, so the year sits just before the trailing spaces.
        var end = s.length
        while (end > 0 && isSpace(s[end - 1])) end--
        // Bracketed: [(]YYYY[)] ending at end.
        if (end >= 6) {
            val o = end - 6
            // No match of the other alternative can start earlier: it needs digits right after spaces.
            if ((s[o] == '[' || s[o] == '(') && (s[end - 1] == ']' || s[end - 1] == ')') && isCenturyYear(s, o + 1)) return o
        }
        if (end >= 5 && isCenturyYear(s, end - 4) && isSpace(s[end - 5])) {
            var i = end - 5
            while (i > 0 && isSpace(s[i - 1])) i--
            return i
        }
        return -1
    }

    private fun isCenturyYear(s: String, at: Int): Boolean =
        at + 4 <= s.length && ((s[at] == '1' && s[at + 1] == '9') || (s[at] == '2' && s[at + 1] == '0')) && s[at + 2] in '0'..'9' && s[at + 3] in '0'..'9'

    // ---- Step G: trailing technical suffix ------------------------------------------------------

    /** The longest suffix of an optional delimiter, one quality or multi tag, more quality tags → space. */
    internal fun trailingTechnical(s: String): String {
        // Every tag ends in one of these letters (then only spaces follow): most titles stop here.
        var last = s.length - 1
        while (last >= 0 && isSpace(s[last])) last--
        if (last < 0 || s[last].lowercaseChar() !in TAG_ENDINGS) return s
        for (i in 0 until s.length) {
            if (technicalSuffixFrom(s, i)) return s.substring(0, i) + " "
        }
        return s
    }

    private fun technicalSuffixFrom(s: String, i: Int): Boolean {
        // (?:\s*(?:[|•·]|[-–—])\s*)? then the tag.
        val lead = countSpaces(s, i)
        val d = i + lead
        if (d < s.length && s[d] in SUFFIX_DELIMITERS && suffixTagFrom(s, skipSpaces(s, d + 1))) return true
        return suffixTagFrom(s, i)
    }

    private fun suffixTagFrom(s: String, at: Int): Boolean {
        var ok = false
        firstSuffixTagEnds(s, at) { if (!ok && moreQualityTags(s, it)) ok = true }
        return ok
    }

    private fun firstSuffixTagEnds(s: String, at: Int, each: (Int) -> Unit) {
        for (tag in SIMPLE_TAGS) if (matchesAscii(s, at, tag)) each(at + tag.length)
        hdrEnds(s, at, each)
        dolbyVisionEnd(s, at).takeIf { it > 0 }?.let(each)
        if (matchesAscii(s, at, "x26") && at + 3 < s.length && (s[at + 3] == '4' || s[at + 3] == '5')) each(at + 4)
        multiEnds(s, at, withAnd = true, each = each)
    }

    /** `(?:\s+(?:4k|uhd|fhd|hd|hdr(?:10\+?)?|x26[45]|hevc))*\s*$` from [at]. */
    private fun moreQualityTags(s: String, at: Int): Boolean {
        val spaces = countSpaces(s, at)
        if (at + spaces == s.length) return true
        if (spaces == 0) return false
        val q = at + spaces
        var ok = false
        for (tag in SIMPLE_TAGS) if (matchesAscii(s, q, tag) && moreQualityTags(s, q + tag.length)) return true
        hdrEnds(s, q) { if (!ok && moreQualityTags(s, it)) ok = true }
        if (ok) return true
        return matchesAscii(s, q, "x26") && q + 3 < s.length && (s[q + 3] == '4' || s[q + 3] == '5') && moreQualityTags(s, q + 4)
    }

    // ---- Shared token pieces --------------------------------------------------------------------

    /** `hdr(?:10\+?)?`, longest first. */
    private inline fun hdrEnds(s: String, at: Int, each: (Int) -> Unit) {
        if (!matchesAscii(s, at, "hdr")) return
        if (at + 5 <= s.length && s[at + 3] == '1' && s[at + 4] == '0') {
            if (at + 5 < s.length && s[at + 5] == '+') each(at + 6)
            each(at + 5)
        }
        each(at + 3)
    }

    /** `dolby\s*vision`: its end, or -1. */
    private fun dolbyVisionEnd(s: String, at: Int): Int {
        if (!matchesAscii(s, at, "dolby")) return -1
        val v = skipSpaces(s, at + 5)
        return if (matchesAscii(s, v, "vision")) v + 6 else -1
    }

    /**
     * `multi(?:[- ]?(?:subs?|subtitles?|audio))?`, and with [withAnd] the suffix step's
     * `(?:\s*&\s*(?:subs?|subtitles?|audio))?`, ends in the pattern's priority order. With
     * [requireGroup] (Step E) the first group is required.
     */
    private inline fun multiEnds(s: String, at: Int, withAnd: Boolean, requireGroup: Boolean = false, each: (Int) -> Unit) {
        if (!matchesAscii(s, at, "multi")) return
        val base = at + 5
        val starts = if (base < s.length && (s[base] == '-' || s[base] == ' ')) intArrayOf(base + 1, base) else intArrayOf(base)
        for (start in starts) {
            for (word in MULTI_WORDS) {
                if (!matchesAscii(s, start, word)) continue
                val e = start + word.length
                if (withAnd) andWordEnds(s, e, each)
                each(e)
            }
        }
        if (!requireGroup) {
            if (withAnd) andWordEnds(s, base, each)
            each(base)
        }
    }

    private inline fun andWordEnds(s: String, at: Int, each: (Int) -> Unit) {
        val amp = skipSpaces(s, at)
        if (amp >= s.length || s[amp] != '&') return
        val w = skipSpaces(s, amp + 1)
        for (word in MULTI_WORDS) if (matchesAscii(s, w, word)) each(w + word.length)
    }

    // ---- Character helpers ----------------------------------------------------------------------

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    /** A word character for ``: the JVM's rule (letter, digit, `_`), with combining marks counting as part of their word. */
    private fun isWord(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    private fun skipSpaces(s: String, from: Int, to: Int = s.length): Int {
        var i = from
        while (i < to && isSpace(s[i])) i++
        return i
    }

    private fun countSpaces(s: String, from: Int, to: Int = s.length): Int = skipSpaces(s, from, to) - from

    private fun countDigits(s: String, from: Int, to: Int = s.length): Int {
        var i = from
        while (i < to && s[i] in '0'..'9') i++
        return i - from
    }

    private inline fun countWhile(s: String, from: Int, test: (Char) -> Boolean): Int {
        var i = from
        while (i < s.length && test(s[i])) i++
        return i - from
    }

    /** ASCII case-insensitive match of the lower-case [word] at [at]. */
    private fun matchesAscii(s: String, at: Int, word: String): Boolean {
        if (at < 0 || at + word.length > s.length) return false
        for (k in word.indices) {
            var c = s[at + k]
            if (c in 'A'..'Z') c += 32
            if (c != word[k]) return false
        }
        return true
    }

    private fun lowerAscii(s: String, from: Int, to: Int): String {
        val b = CharArray(to - from)
        for (k in b.indices) {
            val c = s[from + k]
            b[k] = if (c in 'A'..'Z') c + 32 else c
        }
        return String(b)
    }

    /** Leading and trailing space, `-`, `–`, `—` and `:` go (searchTitle's trim). */
    private fun trimSearch(s: String): String {
        var a = 0
        var b = s.length
        while (a < b && s[a] in SEARCH_TRIM) a++
        while (b > a && s[b - 1] in SEARCH_TRIM) b--
        return s.substring(a, b)
    }

    /** Runs of `\s` become one space. */
    private fun collapseSpaces(s: String): String {
        var i = 0
        while (i < s.length && !isSpace(s[i])) i++
        if (i == s.length) return s
        val out = StringBuilder(s.length)
        var space = false
        for (c in s) {
            if (isSpace(c)) {
                if (!space) out.append(' ')
                space = true
            } else {
                out.append(c)
                space = false
            }
        }
        return out.toString()
    }

    /** Runs of anything but a Unicode letter or number (`[^\p{L}\p{N}]+`) become one space. */
    private fun lettersAndDigits(s: String): String {
        val out = StringBuilder(s.length)
        var gap = false
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Character.isLetter(cp) || isNumber(cp)) {
                out.appendCodePoint(cp)
                gap = false
            } else if (!gap) {
                out.append(' ')
                gap = true
            }
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private fun isNumber(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    /** NFKD only when there is something outside ASCII: most provider titles skip it. */
    internal fun nfkd(s: String): String {
        for (c in s) if (c.code > 0x7F) return Normalizer.normalize(s, Normalizer.Form.NFKD)
        return s
    }

    private const val NO_CHAR = '\u0000'
    private const val MAX_ALTERNATIVES = 16

    private val LANGUAGE_CODES = setOf("fi", "fin", "en", "eng", "sv", "swe", "da", "dan", "no", "nor", "de", "ger", "fr", "fre", "es", "spa")
    private val LANGUAGE_CODES_3 = setOf("fin", "eng", "swe", "dan", "nor", "ger", "fre", "spa")
    private const val LANGUAGE_DELIMITERS = "|•·-"
    private const val PREFIX_DELIMITERS = "|•·:–—-"
    private const val SUFFIX_DELIMITERS = "|•·-–—"
    private const val SEARCH_TRIM = " -–—:"

    /** Last characters of 4k, uhd, fhd, hd, hdr, hdr10, hdr10+, vision, x264, x265, hevc, multi, sub(s), subtitle(s), audio. */
    private const val TAG_ENDINGS = "kdr0+n45cisebo"

    /** Step C's alternatives in pattern order; "hdr", "dolby", "x26" and "multi" stand for their sub-patterns. */
    private val PREFIX_TOKENS = listOf(
        "4k", "uhd", "fhd", "hd", "hdr", "dolby", "dv", "x26", "hevc", "nc", "nordic", "multi", "vip", "vod",
        "fi", "fin", "en", "eng", "sv", "swe", "da", "dan", "no", "nor", "de", "ger", "fr", "fre", "es", "spa",
    )
    private val SIMPLE_TAGS = listOf("4k", "uhd", "fhd", "hd", "hevc")
    private val MULTI_WORDS = listOf("subs", "sub", "subtitles", "subtitle", "audio")
    private val DEVICE_WORDS = listOf("devices", "device")
}
