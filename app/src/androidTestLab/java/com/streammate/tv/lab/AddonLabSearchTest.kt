package com.streammate.tv.lab

import com.streammate.tv.addons.*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Only explicitly installed synthetic catalogs are passed to Search. No real
 * addon configuration changes or stream/subtitle lookups. */
class AddonLabSearchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun query(value: String) {
        compose.onNodeWithTag("addon-search-query").performClick()
        compose.onNodeWithTag("addon-search-query").performTextReplacement(value)
        compose.onNodeWithTag("addon-search-query").performImeAction()
        compose.onNodeWithTag("addon-search-submit").performClick()
    }

    @Test fun separateDestinationsRowsAndDetailsRestoreRemoteFocus() = fixture { host, installed, requests, _, _, escaped ->
        waitFor("addon-discover")
        compose.onNodeWithTag("addon-search").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("addon-search").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        waitFor("addon-search-page")
        compose.onNodeWithTag("addon-search-query").assertIsFocused()
        assertTrue(requests.isEmpty())
        query("Orbit & Ä")
        waitFor("addon-search-result-movie"); waitFor("addon-search-result-series")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Results for “Orbit & Ä”")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(setOf("movies", "series", "custom"), requests.map { it.first }.toSet())
        assertTrue(requests.all { it.second == "Orbit & Ä" })
        compose.onNodeWithTag("addon-choice-Type").assertDoesNotExist()
        compose.onNodeWithTag("addon-search-results").performScrollToIndex(0)
        compose.onAllNodes(hasTestTag("addon-search-result-movie"))[0].performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(isFocused()).performKeyInput { repeat(7) { pressKey(Key.DirectionRight) } }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasTestTag("addon-search-result-series") and hasContentDescription("Orbit series 0")).assertIsFocused()
        val before = requests.size
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        waitFor("addon-series-details")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Synthetic search synopsis")).fetchSemanticsNodes().isNotEmpty() }
        back()
        compose.onNode(hasTestTag("addon-search-result-series") and hasContentDescription("Orbit series 0")).assertIsFocused()
        assertEquals(before, requests.size)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(hasTestTag("addon-search-result-movie") and hasContentDescription("Orbit movie 0")).assertIsFocused()
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(200, 2_000)
        AddonLabScreenshots.capture(compose.activity, "addon-search-results.png")
        query("Orbit & Ä")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Results for “Orbit & Ä”")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Repeat query uses the existing guarded cache", before, requests.size)
        compose.onNodeWithTag("addon-search-clear").performClick()
        compose.onNodeWithTag("addon-search-result-movie").assertDoesNotExist()
        compose.onNodeWithTag("addon-search-result-series").assertDoesNotExist()
        back()
        compose.onNodeWithTag("addon-search").assertIsFocused()
        compose.onNodeWithTag("addon-filter-discover").performClick()
        compose.onNodeWithTag("addon-choice-Type").assertIsDisplayed()
        compose.onNodeWithTag("addon-search-page").assertDoesNotExist()
        back(); compose.onNodeWithTag("addon-filter-discover").assertIsFocused()
        assertEquals(0, escaped.get())
        assertTrue(host.manager.list(installed.profileId).any { it.installationId == installed.installationId && it.enabled })
    }

    @Test fun newQueryCancelsOldResultsAndPartialFailureKeepsTheOtherRow() = fixture { _, _, requests, failSeries, _, _ ->
        waitFor("addon-discover"); compose.onNodeWithTag("addon-search").performClick(); waitFor("addon-search-page")
        query("slow")
        compose.waitUntil(10_000) { requests.any { it.second == "slow" } }
        failSeries.set(true)
        query("fresh")
        waitFor("addon-search-result-movie")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Some catalogs could not be searched. Choose Search to retry.")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasTestTag("addon-search-result-series")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Fresh movie 0").assertExists()
        compose.onNodeWithContentDescription("Slow movie 0").assertDoesNotExist()
        failSeries.set(false)
        query("empty")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("No matching movies.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No matching series.").assertExists()
        compose.onNodeWithContentDescription("Fresh movie 0").assertDoesNotExist()
    }

    private fun fixture(block: suspend (AddonHost, InstalledAddon, CopyOnWriteArrayList<Pair<String, String>>, AtomicBoolean, AtomicInteger, AtomicInteger) -> Unit): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        val failSeries = AtomicBoolean(); val forbidden = AtomicInteger(); val escaped = AtomicInteger()
        MockWebServer().use { server ->
            server.start()
            val base = server.url("/").toString().removeSuffix("/")
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(24, 75, 92))
            Canvas(bitmap).drawText("ORBIT", 8f, 64f, Paint().apply { color = Color.WHITE; textSize = 18f })
            val poster = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            bitmap.recycle()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path == "/poster.png") return MockResponse().setBody(Buffer().write(poster)).setHeader("Content-Type", "image/png")
                    if (path.contains("/meta/series/")) return MockResponse().setBody("""{"meta":{"id":"search-fixture-series-0","type":"series","name":"Orbit series 0","description":"Synthetic search synopsis","videos":[]}}""")
                    if (!path.contains("/catalog/")) { forbidden.incrementAndGet(); return MockResponse().setResponseCode(404) }
                    val parts = request.requestUrl!!.encodedPathSegments
                    val catalog = parts[parts.indexOf("catalog") + 2]
                    val extra = parts.last().removeSuffix(".json").split('&').firstOrNull { it.startsWith("search=") }?.substringAfter('=')
                    val text = extra?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
                    requests += catalog to text
                    if (catalog !in setOf("movies", "series", "custom") || text.isEmpty()) { forbidden.incrementAndGet(); return MockResponse().setResponseCode(400) }
                    if (catalog == "series" && failSeries.get()) return MockResponse().setResponseCode(503)
                    val type = if (catalog == "series") "series" else "movie"
                    val label = when (text) { "slow" -> "Slow"; "fresh" -> "Fresh"; else -> "Orbit" }
                    val count = if (text == "empty") 0 else if (type == "movie") 10 else 6
                    return MockResponse().setBody("""{"metas":[${(0 until count).joinToString(",") { """{"id":"search-fixture-$type-$it","type":"$type","name":"$label $type $it","poster":"$base/poster.png","releaseInfo":"2026"}""" }}]}""")
                        .setHeader("Cache-Control", "max-age=300").apply { if (text == "slow") setBodyDelay(1, TimeUnit.SECONDS) }
                }
            }
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse("$base/manifest.json", true), MANIFEST)
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, { escaped.incrementAndGet() }, modifier, loadInstallations = { listOf(installed) })
                    }
                } } }
                block(host, installed, requests, failSeries, forbidden, escaped)
                assertEquals("No unrelated catalog, stream or subtitle requests", 0, forbidden.get())
            } finally { host.store.remove(preferences.activeProfileId, installed.installationId) }
        }
    }
    private companion object {
        const val MANIFEST = """{"id":"test.search","name":"Synthetic search","version":"1","types":["movie","series","lab.search"],"idPrefixes":["search-fixture-"],"resources":["catalog","meta"],"catalogs":[{"id":"movies","type":"movie","extra":[{"name":"search","isRequired":true},{"name":"skip"}]},{"id":"series","type":"series","extra":[{"name":"search","isRequired":true}]},{"id":"custom","type":"lab.search","extra":[{"name":"search","isRequired":true}]},{"id":"filter","type":"movie","extra":[{"name":"search","isRequired":true},{"name":"year","isRequired":true}]}]}"""
    }
}
