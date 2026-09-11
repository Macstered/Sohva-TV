package com.sohva.tv.addons

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class AddonImportTextTest {
    @Test fun utf8BomBlankLinesAndCrLfAreAccepted() {
        val text = "https://example.invalid/a\r\n\r\nhttps://example.invalid/b\r\n"
        assertEquals(text, AddonImportText.read(ByteArrayInputStream(("\uFEFF" + text).toByteArray())))
    }

    @Test fun emptyTooManyBinaryMalformedAndOversizedInputsAreRejectedAndClosed() {
        val cases = listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(0xC3.toByte(), 0x28),
            "x".repeat(AddonImportText.MAX_BYTES + 1).toByteArray(),
            List(33) { "https://example.invalid/$it" }.joinToString("\n").toByteArray())
        cases.forEach { bytes ->
            var closed = false
            val input = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            try { AddonImportText.read(input); fail("Invalid document accepted") }
            catch (error: AddonException) { assertEquals(AddonFailure.INVALID_REQUEST, error.failure) }
            assertTrue(closed)
        }
    }

    @Test fun exactByteAndEntryLimitsAreAccepted() {
        assertEquals(AddonImportText.MAX_BYTES, AddonImportText.read(ByteArrayInputStream(
            "x".repeat(AddonImportText.MAX_BYTES).toByteArray())).length)
        val text = List(32) { "https://example.invalid/$it" }.joinToString("\n")
        assertEquals(text, AddonImportText.read(ByteArrayInputStream(text.toByteArray())))
    }

    @Test fun readFailureClosesStreamWithoutRewritingException() {
        var closed = false
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException()
            override fun close() { closed = true }
        }
        try { AddonImportText.read(input); fail("Expected failure") } catch (_: java.io.IOException) { }
        assertTrue(closed)
    }
}
