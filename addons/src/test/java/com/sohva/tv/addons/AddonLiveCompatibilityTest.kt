package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Never runs in normal tests/CI. Neither input nor provider payloads enter reports. */
class AddonLiveCompatibilityTest {
    @Test fun configuredAddonBrowsingCompatibility(): Unit = runBlocking {
        val inputPath = System.getProperty("sohva.addon.liveInput").orEmpty()
        assumeTrue("Live input is explicitly opt-in", inputPath.isNotBlank())
        val input = File(inputPath)
        val report = File(input.parentFile, "live-compatibility-summary.txt")
        val results = mutableListOf<String>()
        var stage = "input"
        try {
            val lines = input.readLines().map(String::trim).filter(String::isNotEmpty)
            check(lines.size == 1) { "Expected one configured URL in the private input file" }
            val client = AddonClient()
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val access = AddonManagementAccess { it == "live-probe" }
            val manager = AddonManager(client, store, access)
            stage = "manifest"
            var started = System.nanoTime()
            val installed = manager.install("live-probe", lines.single())
            results += "manifest.millis=${(System.nanoTime() - started) / 1_000_000}"
            results += "manifest.catalogs=${installed.manifest.catalogs.size}"
            results += "manifest.meta=${installed.manifest.resources.any { it.name == "meta" }}"
            results += "manifest.stream=${installed.manifest.resources.any { it.name == "stream" }}"
            results += "manifest.subtitles=${installed.manifest.resources.any { it.name == "subtitles" }}"
            val browser = AddonBrowseRepository(client, store, MemoryResponseCache(), access)
            // Small bounded sample; no searches, tracking endpoints, streams or prefetch.
            val catalogs = installed.manifest.catalogs.filter { catalog ->
                catalog.extras.none { it.required && it.name != "skip" }
            }.groupBy { it.type }.values.flatMap { it.take(1) }.take(4)
            assertTrue("No catalog can be browsed without additional input", catalogs.isNotEmpty())
            catalogs.forEachIndexed { index, catalog ->
                stage = "catalog.$index"
                val extras = if (catalog.extras.any { it.name == "skip" }) mapOf("skip" to "0") else emptyMap()
                started = System.nanoTime()
                val page = browser.catalog("live-probe", installed.installationId, catalog.type, catalog.id, extras).value
                results += "catalog.$index.millis=${(System.nanoTime() - started) / 1_000_000}"
                results += "catalog.$index.raw=${page.receivedCount}"
                results += "catalog.$index.visible=${page.items.size}"
                results += "catalog.$index.posters=${page.items.count { it.poster != null }}"
                started = System.nanoTime()
                val warm = browser.catalog("live-probe", installed.installationId, catalog.type, catalog.id, extras)
                results += "catalog.$index.warmMillis=${(System.nanoTime() - started) / 1_000_000}"
                results += "catalog.$index.warmFromCache=${warm.fromCache}"
                if (!warm.fromCache) {
                    val policy = client.resource(installed.endpoint, installed.manifest, "catalog", catalog.type, catalog.id, extras)
                    results += "catalog.$index.noStore=${policy.noStore}"
                    results += "catalog.$index.maxAge=${policy.maxAgeSeconds}"
                    results += "catalog.$index.staleAllowed=${policy.staleAllowed}"
                }
                assertTrue("Warm catalog item count differs", warm.value.items.size == page.items.size)
                val first = page.items.firstOrNull { installed.manifest.supports("meta", it.key.type, it.key.id) }
                if (first != null) {
                    stage = "metadata.$index"
                    started = System.nanoTime()
                    val details = browser.metadata("live-probe", installed.installationId, first.key).value
                    results += "metadata.$index.millis=${(System.nanoTime() - started) / 1_000_000}"
                    results += "metadata.$index.canonicalChanged=${details.key != first.key}"
                    results += "metadata.$index.episodes=${details.videos.size}"
                    results += "metadata.$index.poster=${details.poster != null}"
                }
                val next = catalog.nextSkip(0, page.receivedCount)
                if (next != null && index < 2) {
                    stage = "pagination.$index"
                    val second = browser.catalog("live-probe", installed.installationId, catalog.type, catalog.id, mapOf("skip" to next.toString())).value
                    results += "pagination.$index.raw=${second.receivedCount}"
                    results += "pagination.$index.new=${second.items.count { candidate -> page.items.none { it.key == candidate.key } }}"
                }
            }
            results += "outcome=PASS"
        } catch (error: Exception) {
            val category = (error as? AddonException)?.failure?.name ?: "LOCAL_OR_UNEXPECTED"
            results += "outcome=FAIL stage=$stage category=$category"
            throw AssertionError("Live compatibility failed at $stage ($category); private summary contains no URLs or payloads")
        } finally {
            report.writeText(results.joinToString("\n", postfix = "\n"))
        }
    }
}
