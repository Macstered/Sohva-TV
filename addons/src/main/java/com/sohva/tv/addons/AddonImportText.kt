package com.sohva.tv.addons

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Plain UTF-8 URL lists only. Never interpret exports as scripts or log their contents. */
object AddonImportText {
    const val MAX_BYTES = 256 * 1024
    const val MAX_ENTRIES = 32

    /** Takes ownership of the stream, including on invalid input or an I/O failure. */
    fun read(input: InputStream): String = readUtf8(input).also(::validate)

    internal fun readUtf8(input: InputStream): String = input.use { stream ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            // Read at most one byte beyond the limit, including unknown-size documents.
            val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - bytes.size()))
            if (count < 0) break
            if (count == 0) fail(AddonFailure.INVALID_REQUEST)
            bytes.write(buffer, 0, count)
            if (bytes.size() > MAX_BYTES) fail(AddonFailure.INVALID_REQUEST)
        }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) { fail(AddonFailure.INVALID_REQUEST) }
        text
    }

    fun validate(text: String) {
        if (text.length > MAX_BYTES || text.any { it == '\u0000' } ||
            text.lineSequence().count { it.isNotBlank() } !in 1..MAX_ENTRIES) {
            fail(AddonFailure.INVALID_REQUEST)
        }
    }
}
