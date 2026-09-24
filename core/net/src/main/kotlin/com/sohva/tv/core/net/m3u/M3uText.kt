package com.sohva.tv.core.net.m3u

import com.sohva.tv.core.model.text.NameNormalizer
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale
import okio.Buffer

/** The text rules of spec 10 §4.10 that the reader applies to single lines. */
internal object M3uText {
    // SRC-FR-54, the grammar providers write; a later duplicate of a name wins.
    private val attribute = Regex("""([A-Za-z0-9_-]+)=(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    private val seriesHints = arrayOf("series", "show", "shows", "sarja", "sarjat")
    private val movieHints = arrayOf("vod", "movie", "movies", "film", "films", "elokuva", "elokuvat")
    private val movieExtensions = arrayOf(".avi", ".m4v", ".mkv", ".mov", ".mp4", ".webm")

    fun attributes(text: String): Map<String, String> {
        if (text.indexOf('=') < 0) return emptyMap()
        val out = HashMap<String, String>()
        for (match in attribute.findAll(text)) {
            val groups = match.groups
            val value = groups[2]?.value?.takeIf { it.isNotEmpty() }
                ?: groups[3]?.value?.takeIf { it.isNotEmpty() }
                ?: groups[4]?.value.orEmpty()
            out[match.groupValues[1].lowercase(Locale.ROOT)] = value
        }
        return out
    }

    /** Index of the first comma outside single or double quotes, or -1. */
    fun unquotedComma(text: String): Int {
        var quote = 0.toChar()
        for (i in text.indices) {
            val c = text[i]
            when {
                quote == 0.toChar() && (c == '"' || c == '\'') -> quote = c
                c == quote -> quote = 0.toChar()
                quote == 0.toChar() && c == ',' -> return i
            }
        }
        return -1
    }

    /** `Key=Value&Key=Value`, keys lower-cased, values URL-decoded (SRC-FR-53, SRC-FR-56). */
    fun headerPairs(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (pair in text.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val raw = pair.substring(eq + 1)
            val value = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: IllegalArgumentException) {
                raw
            }
            out[pair.substring(0, eq).trim().lowercase(Locale.ROOT)] = value
        }
        return out
    }

    /** The string members of an `#EXTHTTP` JSON object, names lower-cased; nothing when it is not JSON. */
    fun jsonHeaders(json: String): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName().lowercase(Locale.ROOT)
                if (reader.peek() == JsonReader.Token.STRING) out[name] = reader.nextString() else reader.skipValue()
            }
        } catch (_: IOException) {
            // Malformed JSON: a broken #EXTHTTP line adds no headers; the entry is still read.
        } catch (_: JsonDataException) {
            // Not an object, or a name where a value belongs: the same.
        }
        return out
    }

    /**
     * SRC-FR-57, kept for compatibility: hints are substrings of the type attributes, the
     * normalised group, or `/<hint>/` in the path.
     */
    fun kindOf(typeHints: String, group: String?, address: String, duration: Int?): M3uKind {
        val normalizedGroup = if (group == null) "" else NameNormalizer.normalize(group)
        val path = address.substringBefore('?').lowercase(Locale.ROOT)
        fun hinted(words: Array<String>): Boolean =
            words.any { it in typeHints || it in normalizedGroup || path.contains("/$it/") }
        return when {
            hinted(seriesHints) -> M3uKind.SERIES
            hinted(movieHints) || movieExtensions.any { path.endsWith(it) } || (duration ?: 0) > 0 -> M3uKind.MOVIE
            else -> M3uKind.LIVE
        }
    }
}
