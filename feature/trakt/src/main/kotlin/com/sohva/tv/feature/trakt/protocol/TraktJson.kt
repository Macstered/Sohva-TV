package com.sohva.tv.feature.trakt.protocol

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException
import java.time.Instant
import okio.Buffer

/**
 * Streaming reads of Trakt answers: one pass with Moshi's reader, fields picked as they come, no
 * tree. Malformed JSON is INVALID_RESPONSE. Callers run it on a background dispatcher (§4.12).
 */
internal object TraktJson {
    fun <T> parse(body: Buffer, read: (JsonReader) -> T): T = try {
        JsonReader.of(body).use(read)
    } catch (e: TraktException) {
        throw e
    } catch (e: JsonDataException) {
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    } catch (e: JsonEncodingException) {
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    } catch (e: IOException) {
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    } catch (e: IllegalStateException) {
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    } catch (e: NumberFormatException) {
        throw TraktException(TraktFailure.INVALID_RESPONSE)
    }

    fun write(block: JsonWriter.() -> Unit): String {
        val out = Buffer()
        JsonWriter.of(out).use { it.block() }
        return out.readUtf8()
    }

    /** ISO-8601 instant in milliseconds; unparsable or absent is 0 (§7). */
    fun time(value: String?): Long = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}

internal inline fun JsonReader.fields(field: (String) -> Unit) {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return
    }
    beginObject()
    while (hasNext()) field(nextName())
    endObject()
}

internal inline fun JsonReader.items(item: () -> Unit) {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) {
        skipValue()
        return
    }
    beginArray()
    while (hasNext()) item()
    endArray()
}

/** Non-blank text without control characters, at most [max]; anything else is null (FR-02). */
internal fun JsonReader.text(max: Int): String? = when (peek()) {
    JsonReader.Token.STRING -> nextString().takeIf { it.isNotBlank() && it.length <= max && it.none { c -> c < ' ' } }
    else -> {
        skipValue()
        null
    }
}

internal fun JsonReader.long(): Long? = when (peek()) {
    JsonReader.Token.NUMBER -> runCatching { nextLong() }.getOrElse { nextDouble().toLong() }
    else -> {
        skipValue()
        null
    }
}

internal fun JsonReader.double(): Double? = when (peek()) {
    JsonReader.Token.NUMBER -> nextDouble()
    else -> {
        skipValue()
        null
    }
}

internal fun JsonReader.ids(): TraktIds {
    var trakt: Long? = null
    var tmdb: Long? = null
    var imdb: String? = null
    var tvdb: Long? = null
    fields { f ->
        when (f) {
            "trakt" -> trakt = long()?.takeIf { it > 0 }
            "tmdb" -> tmdb = long()?.takeIf { it > 0 }
            "imdb" -> imdb = text(32)?.takeIf { IMDB.matches(it) }
            "tvdb" -> tvdb = long()?.takeIf { it > 0 }
            else -> skipValue()
        }
    }
    return TraktIds(trakt, tmdb, imdb, tvdb)
}

internal val IMDB = Regex("tt\\d{5,10}")
