package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.sohva.tv.addons.AddonEndpoint
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Bounded synthetic stress, not a Shield/frame-time or real poster-decoding benchmark. */
class AddonLabCatalogStressTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun largeCatalogsStayViewportBoundedAndEvictedShelvesReturnFromDiskCache() = exercise(false)

    @Test fun pendingLookaheadDoesNotCountAsAReturnNavigationFetch() = exercise(true)

    private fun exercise(delayLastPrefetch: Boolean): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        MockWebServer().use { server ->
            val requested = ConcurrentHashMap<Int, AtomicInteger>()
            val unexpected = AtomicInteger()
            val finalPrefetchArrived = CountDownLatch(1)
            val releaseFinalPrefetch = CountDownLatch(1)
            val page = """{"metas":[${(1..1000).joinToString(",") {
                """{"id":"title$it","type":"lab.stress","name":"Synthetic title $it","description":"Synthetic catalog payload for bounded parsing and navigation","releaseInfo":"2026"}"""
            }}]}"""
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val row = Regex("/catalog/lab.stress/row(\\d+)\\.json").find(request.path.orEmpty())?.groupValues?.get(1)?.toInt()
                    if (row == null) { unexpected.incrementAndGet(); return MockResponse().setResponseCode(404) }
                    if (delayLastPrefetch && row == 34) {
                        finalPrefetchArrived.countDown()
                        if (!releaseFinalPrefetch.await(20, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                    }
                    requested.computeIfAbsent(row) { AtomicInteger() }.incrementAndGet()
                    return MockResponse().setHeader("Cache-Control", "max-age=600").setBody(page)
                }
            }
            val catalogs = (0..39).joinToString(",") {
                """{"id":"row$it","type":"lab.stress","name":"Stress catalog $it","extra":[{"name":"skip"}]}"""
            }
            val manifest = """{"id":"test.lab.stress","version":"1","name":"Synthetic stress","types":["lab.stress"],"resources":["catalog"],"catalogs":[$catalogs]}"""
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), manifest)
            val start = android.os.SystemClock.elapsedRealtime()
            val focusTimes = mutableListOf<Long>()
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) })
                    }
                } } }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card")).fetchSemanticsNodes().isNotEmpty() }
                val firstVisibleMillis = android.os.SystemClock.elapsedRealtime() - start
                assertTrue("Opening Home must not fetch all 40 catalogs", requested.size in 1..4)
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nothing to continue yet") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
                waitForRow(0)
                for (row in 1..32) {
                    val before = android.os.SystemClock.elapsedRealtime()
                    compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
                    waitForRow(row)
                    focusTimes += android.os.SystemClock.elapsedRealtime() - before
                }
                assertTrue("Only visited and nearby rows should load", requested.size in 33..36)
                if (delayLastPrefetch) compose.waitUntil(10_000) { finalPrefetchArrived.count == 0L }
                releaseFinalPrefetch.countDown()
                // Row 32 has two look-ahead requests. Focus readiness does not
                // prove those have reached the server or the encrypted cache.
                // Finish that known work before measuring return-navigation I/O.
                withTimeout(10_000) {
                    for (row in 0..34) {
                        while (host.browser.cachedCatalog(preferences.activeProfileId,
                                installed.installationId, "lab.stress", "row$row") == null) delay(10)
                    }
                }
                assertEquals("Only visited rows and the two look-ahead rows should load",
                    (0..34).toSet(), requested.keys.toSet())
                val requestsBeforeReturn = requested.values.sumOf { it.get() }
                // Cross the 24-shelf retention bound, then return: old rows should use encrypted cache.
                for (row in 31 downTo 0) {
                    compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
                    waitForRow(row)
                }
                assertEquals("Requests by row: ${requested.toSortedMap().mapValues { it.value.get() }}",
                    requestsBeforeReturn, requested.values.sumOf { it.get() })
                assertTrue("Coalesced shelf loads should request each page only once: ${requested.toSortedMap().mapValues { it.value.get() }}",
                    requested.values.all { it.get() == 1 })
                assertEquals("No metadata, streams or subtitle prefetch", 0, unexpected.get())
                val heapMiB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
                println("SOHVA_SYNTHETIC_STRESS catalogs=40 titlesPerResponse=1000 requests=$requestsBeforeReturn firstVisibleMs=$firstVisibleMillis navigationSamples=${focusTimes.size} navigationMaxMs=${focusTimes.maxOrNull()} managedHeapMiB=$heapMiB")
            } finally {
                releaseFinalPrefetch.countDown()
                host.store.remove(preferences.activeProfileId, installed.installationId)
            }
        }
    }

    private fun waitForRow(index: Int) {
        val row = hasAnyAncestor(hasTestTag("addon-shelf-$index"))
        compose.waitUntil(10_000) { compose.onAllNodes(row and hasTestTag("addon-landing-card") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(row and hasTestTag("addon-landing-card") and isFocused()).assertTextContains("Synthetic title 1")
    }
}
