package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit local file opt-in; reports never contain provider names, IDs, URLs or payloads. */
class AddonLiveBatchTest {
    @Test fun previewConfiguredBatch(): Unit = runBlocking {
        val path = System.getProperty("sohva.addon.liveInput").orEmpty()
        assumeTrue(path.isNotEmpty())
        val file = File(path)
        val output = mutableListOf<String>()
        try {
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { true })
            val preview = importer.preview("batch-probe", file.readText())
            preview.entries.forEach { entry ->
                output += "line.${entry.line}.status=${entry.status}"
                output += "line.${entry.line}.failure=${entry.failure}"
                entry.manifest?.let { manifest ->
                    output += "line.${entry.line}.catalogs=${manifest.catalogs.size}"
                    for (resource in listOf("meta", "stream", "subtitles")) output += "line.${entry.line}.$resource=${manifest.resources.any { it.name == resource }}"
                }
            }
            check(preview.entries.none { it.status == AddonImportStatus.FAILED })
            check(store.list("batch-probe").isEmpty())
            output += "outcome=PASS"
        } catch (error: Throwable) {
            output += "outcome=FAIL"
            throw AssertionError("Private batch preview failed (${(error as? AddonException)?.failure ?: "CHECK_FAILED"}); see redacted summary")
        } finally { File(file.parentFile, "live-batch-summary.txt").writeText(output.joinToString("\n")) }
    }
}
