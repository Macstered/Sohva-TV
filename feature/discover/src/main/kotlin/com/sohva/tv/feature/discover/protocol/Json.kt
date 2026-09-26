package com.sohva.tv.feature.discover.protocol

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer
import okio.BufferedSource

/**
 * Reading addon JSON (spec 50 §9 "Parsing"): the body is at most 2 MiB (the client caps it while
 * reading), its nesting is checked once over the bytes before parsing, then a pull parser builds
 * only the fields the app uses. No tree, no whole-body String.
 */
internal object AddonJson {
    const val MAX_BODY_BYTES: Long = 2L * 1024 * 1024
    const val MAX_DEPTH: Int = 64

    /**
     * Parses [body] with [read]; the depth limit is checked first (FR-09); any malformed JSON
     * becomes [invalid].
     */
    fun <T> parse(body: Buffer, invalid: AddonFailure, read: (JsonReader) -> T): T {
        if (body.size > MAX_BODY_BYTES) fail(AddonFailure.RESPONSE_TOO_LARGE)
        if (depth(body) > MAX_DEPTH) fail(invalid)
        return try {
            JsonReader.of(body.peek()).use(read)
        } catch (e: AddonException) {
            throw e
        } catch (e: JsonDataException) {
            fail(invalid)
        } catch (e: JsonEncodingException) {
            fail(invalid)
        } catch (e: IOException) {
            fail(invalid)
        } catch (e: IllegalStateException) {
            fail(invalid)
        } catch (e: NumberFormatException) {
            fail(invalid)
        }
    }

    /** The deepest array/object nesting, strings skipped; one pass over bytes, no allocation. */
    fun depth(body: Buffer): Int {
        var depth = 0
        var max = 0
        var inString = false
        var escaped = false
        val peek: BufferedSource = body.peek()
        while (!peek.exhausted()) {
            val b = peek.readByte().toInt().toChar()
            if (inString) {
                when {
                    escaped -> escaped = false
                    b == '\\' -> escaped = true
                    b == '"' -> inString = false
                }
                continue
            }
            when (b) {
                '"' -> inString = true
                '[', '{' -> {
                    depth++
                    if (depth > max) max = depth
                }
                ']', '}' -> depth--
            }
        }
        return max
    }
}

/** A string within [max] characters and not blank, or null (anything else is skipped). */
internal fun JsonReader.textOrNull(max: Int): String? {
    if (peek() != JsonReader.Token.STRING) {
        skipValue()
        return null
    }
    val s = nextString()
    return s.takeIf { it.isNotBlank() && it.length <= max }
}

/** Like [textOrNull], also taking a number as its text (providers send `"releaseInfo": 2024`). */
internal fun JsonReader.textOrNumber(max: Int): String? =
    if (peek() == JsonReader.Token.NUMBER) nextString().takeIf { it.length <= max } else textOrNull(max)

/** A string that must be non-blank and within [max]; anything else fails with [invalid]. */
internal fun JsonReader.requiredText(max: Int, invalid: AddonFailure): String {
    if (peek() != JsonReader.Token.STRING) fail(invalid)
    val s = nextString()
    if (s.isBlank() || s.length > max) fail(invalid)
    return s
}

/** An int, or null when the value is not an integral number. */
internal fun JsonReader.intOrNull(): Int? = when (peek()) {
    JsonReader.Token.NUMBER -> nextString().toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
    else -> {
        skipValue()
        null
    }
}

internal fun JsonReader.longOrNull(): Long? = when (peek()) {
    JsonReader.Token.NUMBER -> nextString().toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong()
    JsonReader.Token.STRING -> nextString().toLongOrNull()
    else -> {
        skipValue()
        null
    }
}

internal fun JsonReader.boolOrNull(): Boolean? = if (peek() == JsonReader.Token.BOOLEAN) nextBoolean() else {
    skipValue()
    null
}

/** Walks an object's fields; [field] must consume its value. A non-object is skipped and reported false. */
internal inline fun JsonReader.forFields(field: (String) -> Unit): Boolean {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return false
    }
    beginObject()
    while (hasNext()) field(nextName())
    endObject()
    return true
}

/** Walks an array's items; [item] must consume each. A non-array is skipped and reported false. */
internal inline fun JsonReader.forItems(item: (Int) -> Unit): Boolean {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) {
        skipValue()
        return false
    }
    beginArray()
    var i = 0
    while (hasNext()) item(i++)
    endArray()
    return true
}

/**
 * An absolute HTTP(S) URL without user info or fragment (FR-17), else null: images and subtitle
 * files from providers are never loaded from anywhere else.
 */
internal fun safeUrl(value: String?): String? {
    if (value == null || value.length > MAX_URL || value.contains('#') || value.any { it.isISOControl() || it == '\\' }) return null
    val url = value.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    return value
}

private const val MAX_URL = 8_192
