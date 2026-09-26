package com.sohva.tv.feature.sport.provider

import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.squareup.moshi.JsonReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Reads an API-Sports answer item by item (spec 60 §9 rule: never the whole document as a tree).
 * Each `response` item is read into a small map, a game being a couple of kilobytes, and handed to
 * [onItem]; a non-empty `errors` value fails the answer (SPORT-FR-37).
 */
internal object ResponseReader {
    fun read(reader: JsonReader, onItem: (Map<String, Any?>) -> Unit) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) throw SportsException(SportsProblem.INVALID_DATA)
        var failed: SportsProblem? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "errors" -> failed = failed ?: problem(reader.readJsonValue())
                "response" -> {
                    if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
                        reader.skipValue()
                        continue
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        @Suppress("UNCHECKED_CAST")
                        (reader.readJsonValue() as? Map<String, Any?>)?.let(onItem)
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        failed?.let { throw SportsException(it) }
    }

    /**
     * Null when `errors` is empty. API-Sports reports a spent daily allowance under `requests`
     * ("You have reached the request limit for the day"); that is the quota, anything else a
     * service error (spec 60 §7).
     */
    private fun problem(value: Any?): SportsProblem? {
        val present = when (value) {
            null -> false
            is List<*> -> value.isNotEmpty()
            is Map<*, *> -> value.isNotEmpty()
            is String -> value.isNotBlank()
            is Boolean -> value
            else -> true
        }
        if (!present) return null
        val quota = (value as? Map<*, *>)?.containsKey("requests") == true || value.toString().contains("request limit", ignoreCase = true)
        return if (quota) SportsProblem.QUOTA_EXHAUSTED else SportsProblem.SERVICE_ERROR
    }
}

/** Field reading on one item (SPORT-FR-37): blank is absent, texts capped, an object where a primitive was expected is absent. */
internal object Fields {
    private const val TEXT_MAX = 4_000
    private const val URL_MAX = 2_048

    @Suppress("UNCHECKED_CAST")
    fun obj(from: Any?, vararg path: String): Map<String, Any?>? {
        var at: Any? = from
        for (key in path) at = (at as? Map<String, Any?>)?.get(key)
        return at as? Map<String, Any?>
    }

    fun raw(from: Any?, vararg path: String): Any? {
        if (path.isEmpty()) return from
        return obj(from, *path.dropLast(1).toTypedArray())?.get(path.last())
    }

    fun text(from: Any?, vararg path: String): String? = when (val v = raw(from, *path)) {
        is String -> v.trim().takeIf { it.isNotEmpty() }?.take(TEXT_MAX)
        is Number -> numberText(v)
        is Boolean -> v.toString()
        else -> null
    }

    fun long(from: Any?, vararg path: String): Long? = when (val v = raw(from, *path)) {
        is Number -> v.toDouble().takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    fun int(from: Any?, vararg path: String): Int? = long(from, *path)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** Kept only as an `https://` address of at most 2,048 characters. */
    fun image(from: Any?, vararg path: String): String? =
        (raw(from, *path) as? String)?.trim()?.takeIf { it.startsWith("https://") && it.length <= URL_MAX }

    /** An ISO offset date-time, else an ISO instant (SPORT-FR-38). */
    fun instant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(text.trim()).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(text.trim())
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /** `date` or else `timestamp` (epoch seconds as a number or a string, > 0). */
    fun start(from: Any?): Instant? {
        instant(raw(from, "date") as? String)?.let { return it }
        return long(from, "timestamp")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
    }

    private fun numberText(n: Number): String {
        val d = n.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }
}
