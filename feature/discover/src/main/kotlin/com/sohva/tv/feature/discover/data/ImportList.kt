package com.sohva.tv.feature.discover.data

import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonJson
import com.sohva.tv.feature.discover.protocol.forFields
import com.sohva.tv.feature.discover.protocol.forItems
import com.sohva.tv.feature.discover.protocol.textOrNull
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.squareup.moshi.JsonReader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import okio.Buffer

/**
 * The pending import list (spec 50 ADDON-FR-34): every method feeds one list of configured URLs.
 * UTF-8 only, the byte-order mark dropped, no NUL, at most 256 KiB and 1–32 non-blank lines. A
 * null entry is a Nuvio placeholder that fails in preview, so positions stay as they were.
 */
object ImportList {
    const val MAX_BYTES: Int = 262_144
    const val MAX_LINES: Int = 32
    private val BOM = Char(0xFEFF).toString()
    private val NUL = Char(0)

    /** Reads at most one byte past the limit, whatever the stream says its size is. */
    fun read(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        while (true) {
            val n = input.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - out.size()))
            if (n <= 0) break
            out.write(chunk, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toByteArray()
    }

    /** Strict UTF-8 text of a list, or null when the bytes break a rule. */
    fun text(bytes: ByteArray): String? {
        if (bytes.size > MAX_BYTES) return null
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        if (text.contains(NUL)) return null
        return text.removePrefix(BOM)
    }

    /** The non-blank lines, trimmed; null outside 1–32. */
    fun lines(text: String): List<String>? {
        val lines = text.split('\n').map { it.trim('\r', ' ', '\t') }.filter { it.isNotEmpty() }
        return lines.takeIf { it.size in 1..MAX_LINES }
    }

    fun parse(bytes: ByteArray): List<String>? = text(bytes)?.let(::lines)

    /** FR-35: [line] fits after [pending] (counting the joined text's UTF-8 size). */
    fun fits(pending: List<String?>, line: String): Boolean {
        val all = pending.map { it.orEmpty() } + line
        return all.size <= MAX_LINES && all.joinToString("\n").toByteArray(Charsets.UTF_8).size <= MAX_BYTES
    }
}

/**
 * A Nuvio addon export (FR-38): a JSON array (`/api/addons`) or an object with an `addons` array
 * (`/api/state`); only each entry's `url` string, in order, at most 32. An entry without one is kept
 * as a null placeholder. Everything else in the file is ignored.
 */
object NuvioExport {
    fun urls(bytes: ByteArray): List<String?>? {
        if (bytes.size > ImportList.MAX_BYTES) return null
        return try {
            AddonJson.parse(Buffer().write(bytes), AddonFailure.INVALID_RESPONSE) { r ->
                when (r.peek()) {
                    JsonReader.Token.BEGIN_ARRAY -> entries(r)
                    JsonReader.Token.BEGIN_OBJECT -> {
                        var found: List<String?>? = null
                        r.forFields { f -> if (f == "addons" && r.peek() == JsonReader.Token.BEGIN_ARRAY) found = entries(r) else r.skipValue() }
                        found
                    }
                    else -> null
                }
            }?.takeIf { it.size in 1..ImportList.MAX_LINES }
        } catch (e: AddonException) {
            null
        }
    }

    private fun entries(r: JsonReader): List<String?>? {
        val out = ArrayList<String?>()
        r.forItems {
            if (out.size >= ImportList.MAX_LINES) {
                r.skipValue()
                out.add(null)
                return@forItems
            }
            var url: String? = null
            if (r.peek() == JsonReader.Token.BEGIN_OBJECT) {
                r.forFields { f -> if (f == "url") url = r.textOrNull(16_384)?.trim()?.takeIf { it.isNotEmpty() } else r.skipValue() }
            } else {
                r.skipValue()
            }
            out.add(url)
        }
        return out.takeIf { it.size <= ImportList.MAX_LINES }
    }
}
