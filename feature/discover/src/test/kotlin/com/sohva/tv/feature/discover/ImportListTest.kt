package com.sohva.tv.feature.discover

import com.sohva.tv.feature.discover.data.ImportList
import com.sohva.tv.feature.discover.data.NuvioExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 50 §11 "Unit": URL-list and Nuvio rules (FR-34, -35, -38). */
class ImportListTest {
    private val a = "https://provider.example/a/manifest.json"
    private val b = "stremio://provider.example/b/manifest.json"

    @Test
    fun listsAreUtf8LinesWithoutTheMarkAndBlankLines() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "$a\r\n\r\n  $b  \n".toByteArray()
        assertEquals(listOf(a, b), ImportList.parse(bytes))
        assertNull(ImportList.parse(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(ImportList.parse("$a\u0000".toByteArray()))
        assertNull(ImportList.parse("\n \n".toByteArray()))
        assertNull(ImportList.parse((1..33).joinToString("\n") { "https://provider.example/$it" }.toByteArray()))
        assertEquals(32, ImportList.parse((1..32).joinToString("\n") { "https://provider.example/$it" }.toByteArray())?.size)
    }

    @Test
    fun readingStopsOneBytePastTheLimit() {
        val big = ByteArray(ImportList.MAX_BYTES + 10) { 'a'.code.toByte() }
        assertNull(ImportList.read(big.inputStream()))
        val ok = ByteArray(ImportList.MAX_BYTES) { 'a'.code.toByte() }
        assertEquals(ImportList.MAX_BYTES, ImportList.read(ok.inputStream())?.size)
    }

    @Test
    fun manualUrlsFitOnlyWithinTheLimits() {
        assertTrue(ImportList.fits(listOf(a), b))
        assertFalse(ImportList.fits(List(32) { a }, b))
        assertFalse(ImportList.fits(emptyList(), "x".repeat(ImportList.MAX_BYTES + 1)))
    }

    @Test
    fun nuvioExportsGiveUrlsInOrderWithPlaceholders() {
        assertEquals(listOf(a, null, b), NuvioExport.urls("""[{"url":"$a","name":"x"},{"name":"no url"},{"url":"$b"}]""".toByteArray()))
        assertEquals(listOf(a), NuvioExport.urls("""{"layout":{},"addons":[{"url":"$a","manifest":{"id":"x"}}]}""".toByteArray()))
        assertNull(NuvioExport.urls("""{"addons":[]}""".toByteArray()))
        assertNull(NuvioExport.urls("not json".toByteArray()))
        assertNull(NuvioExport.urls(("[" + "[".repeat(70) + "]".repeat(70) + "]").toByteArray()))
        assertNull(NuvioExport.urls(("[" + (1..33).joinToString(",") { """{"url":"$a$it"}""" } + "]").toByteArray()))
    }
}
