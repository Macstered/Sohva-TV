package com.sohva.tv.addons

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AddonCopyExportTest {
    @Test fun nuvioArrayAndStateKeepUrlOrderAndIgnoreAccountsAndLayout() {
        val array = """[{"url":"https://example.invalid/Secret/manifest.json","name":"Ignored"},{"url":"stremio://example.invalid/Other/manifest.json"}]"""
        for (json in listOf(array, """{"addons":$array,"authKey":"NeverCopy","collections":[{"url":"NeverCopy"}]}""")) {
            val result = AddonCopyExport.nuvio(json)
            assertEquals(2, result.count)
            assertEquals("https://example.invalid/Secret/manifest.json\nstremio://example.invalid/Other/manifest.json", result.asUrlList())
            assertFalse(result.toString().contains("Secret"))
            assertFalse(result.asUrlList().contains("NeverCopy"))
        }
    }
    @Test fun malformedEntriesRetainPositionalFailureInsteadOfInjectingLines() {
        val result = AddonCopyExport.nuvio("""[{"url":"https://example.invalid/\nhttps://bad.invalid/"},null,{}, {"url":"https://good.invalid/"}]""")
        assertEquals(4, result.count)
        assertEquals(4, result.asUrlList().lines().size)
        assertEquals("https://good.invalid/", result.asUrlList().lines().last())
    }
    @Test fun prettyPrintedNuvioDocumentDoesNotUseUrlLineCountAndClosesStream() {
        var closed = false
        val text = "\uFEFF{\n" + "\n".repeat(40) + "\"addons\": []}"
        val stream = object : java.io.ByteArrayInputStream(text.toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        assertEquals(0, AddonCopyExport.readNuvio(stream).count)
        assertTrue(closed)
    }
    @Test fun excessiveListsBytesAndNestingAreRejectedWithoutSecrets(): Unit = runBlocking {
        expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { AddonCopyExport.nuvio("[" + List(33) { "{}" }.joinToString(",") + "]") }
        expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { AddonCopyExport.nuvio("x".repeat(262145)) }
        expectFailure(AddonFailure.INVALID_RESPONSE) { AddonCopyExport.nuvio("[".repeat(1000) + "]".repeat(1000)) }
        for (text in listOf("{\"authKey\":\"Secret\"}", "<html>Secret</html>", "null", "{\"addons\":\"Secret\"}")) {
            val failure = expectFailure(AddonFailure.INVALID_RESPONSE) { AddonCopyExport.nuvio(text) }
            assertFalse(failure.toString().contains("Secret"))
            assertNull(failure.cause)
        }
    }
}
