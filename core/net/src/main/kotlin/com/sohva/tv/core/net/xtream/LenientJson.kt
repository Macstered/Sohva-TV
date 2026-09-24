package com.sohva.tv.core.net.xtream

import com.squareup.moshi.JsonReader
import java.util.Locale

/**
 * Value readers that accept what panels send (spec 10 SRC-FR-69): numbers as JSON numbers or
 * numeric strings, booleans as `1`/`true` in any case, and anything unexpected as absent. Each one
 * consumes exactly one value, so a surprising type never derails the rest of the array.
 */
internal object LenientJson {
    fun string(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
        JsonReader.Token.NULL -> reader.nextNull<String>()
        else -> {
            reader.skipValue()
            null
        }
    }

    /** A trimmed, non-blank string capped at [max] characters, or null. */
    fun text(reader: JsonReader, max: Int): String? = string(reader)?.trim()?.take(max)?.ifEmpty { null }

    fun int(reader: JsonReader): Int? = string(reader)?.trim()?.let { raw ->
        raw.toIntOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
    }

    fun bool(reader: JsonReader): Boolean = when (string(reader)?.trim()?.lowercase(Locale.ROOT)) {
        "1", "true" -> true
        else -> false
    }

    /** An id: a JSON number or string written without a fractional `.0`. */
    fun id(reader: JsonReader): String? = string(reader)?.trim()?.removeSuffix(".0")?.ifEmpty { null }

    /** The first string of an array, or the string itself (`backdrop_path`). */
    fun firstString(reader: JsonReader, max: Int): String? {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) return text(reader, max)
        reader.beginArray()
        var first: String? = null
        while (reader.hasNext()) {
            val value = text(reader, max)
            if (first == null) first = value
        }
        reader.endArray()
        return first
    }

    /** `year`, else the first four characters of a release date (SRC-FR-68). */
    fun yearOf(year: String?, releaseDate: String?): Int? =
        year?.trim()?.take(4)?.toIntOrNull() ?: releaseDate?.trim()?.take(4)?.toIntOrNull()

    /** `container_extension` lower-cased when it is `[a-z0-9]{1,8}`, else [default] (SRC-FR-70). */
    fun extension(raw: String?, default: String): String {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return default
        return if (value.length in 1..8 && value.all { it in 'a'..'z' || it in '0'..'9' }) value else default
    }
}
