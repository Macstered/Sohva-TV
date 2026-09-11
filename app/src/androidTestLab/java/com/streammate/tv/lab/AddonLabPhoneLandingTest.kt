package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.sohva.tv.addons.AddonEndpoint
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AddonLabPhoneLandingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun app(): StreamMateApplication {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        return (compose.activity.application as StreamMateApplication).also { check(it.packageName == "com.streammate.tv.lab") }
    }
    @Test fun phoneTransferReturnsToPreviewWithoutInstalling(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val before = host.store.list(profile).map { it.installationId }
        compose.runOnUiThread { compose.activity.setContent {
            StreamMateTheme { StreamMateScreenBackground { modifier ->
                AddonImportScreen({ host.batchImport.preview(profile, it) }, { host.batchImport.commit(it) }, {}, modifier)
            } }
        } }
        compose.onNodeWithTag("addon-import-phone").performScrollTo().performClick()
        val urlMatcher = SemanticsMatcher("pairing address") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("http://") } == true
        }
        compose.waitUntil(10_000) { compose.onAllNodes(urlMatcher).fetchSemanticsNodes().isNotEmpty() }
        val url = compose.onNode(urlMatcher).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        val origin = url.substringBefore("/#")
        OkHttpClient().newCall(Request.Builder().url("$origin/submit").header("Origin", origin)
            .header("Authorization", "Bearer ${url.substringAfter('#')}")
            .post("https://example.invalid/phone-fixture/manifest.json".toRequestBody("text/plain".toMediaType())).build()).execute().use { assertEquals(200, it.code) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("1 addon ready for preview")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, host.store.list(profile).map { it.installationId })
        compose.onNodeWithTag("addon-import-preview").assertIsEnabled()
        compose.onNodeWithText("Clear list").assertIsDisplayed().performClick()
    }

    @Test fun phoneFileEntryReturnsToFilesAndKeepsPreviewExplicit(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val before = host.store.list(profile).map { it.installationId }
        val previews = AtomicInteger()
        compose.runOnUiThread { compose.activity.setContent {
            StreamMateTheme { StreamMateScreenBackground { modifier ->
                AddonImportScreen({ previews.incrementAndGet(); host.batchImport.preview(profile, it) },
                    { error("No installation in this test") }, {}, modifier)
            } }
        } }
        compose.onNodeWithTag("addon-import-section-files").performClick()
        compose.onNodeWithTag("addon-import-phone-file").performScrollTo().performClick()
        val urlMatcher = SemanticsMatcher("pairing address") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("http://") } == true
        }
        compose.waitUntil(10_000) { compose.onAllNodes(urlMatcher).fetchSemanticsNodes().isNotEmpty() }
        val url = compose.onNode(urlMatcher).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        val origin = url.substringBefore("/#")
        val client = OkHttpClient()
        client.newCall(Request.Builder().url(origin).build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(it.body.string().contains("Choose a text file on this phone"))
        }
        client.newCall(Request.Builder().url("$origin/submit").header("Origin", origin)
            .header("Authorization", "Bearer ${url.substringAfter('#')}")
            .post("\uFEFFhttps://example.invalid/phone-file-first/manifest.json\r\n\r\nhttps://example.invalid/phone-file-second/manifest.json\r\n"
                .toRequestBody("text/plain".toMediaType())).build()).execute().use { assertEquals(200, it.code) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 addons ready for preview")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addon-import-preview").assertIsEnabled()
        compose.onNodeWithTag("addon-import-file").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-back").assertIsFocused()
        assertEquals(0, previews.get())
        assertEquals(before, host.store.list(profile).map { it.installationId })
        compose.onAllNodes(hasText("phone-file-first", substring = true)).assertCountEquals(0)
        // Cancelling another QR session must return to Files without losing the pending list.
        compose.onNodeWithTag("addon-import-phone-file").performScrollTo().performClick()
        compose.onNodeWithTag("addon-phone-back").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("addon-import-file").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-count").assertTextEquals("2 addons ready for preview")
        compose.onNodeWithTag("addon-import-back").assertIsFocused()
        compose.onNodeWithTag("addon-import-clear").performClick()
    }
    @Test fun landingLoadsVisibleRowsAndRestoresHorizontalFocusWithSourcesBesideDetails(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        MockWebServer().use { server ->
            val catalogs = AtomicInteger(); val streams = AtomicInteger(); val subtitles = AtomicInteger(); val metadata = AtomicInteger()
            val requestedRows = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/catalog/") -> {
                        catalogs.incrementAndGet()
                        Regex("row(\\d+)").find(request.path!!)?.groupValues?.get(1)?.toInt()?.let { requestedRows.add(it) }
                        val titles = (1..20).joinToString(",") { index ->
                            val name = when (index) { 1 -> "First title"; 2 -> "Second title"; else -> "Title $index" }
                            """{"id":"tile$index","type":"lab.landing","name":"$name","poster":"${server.url("/poster.png")}","background":"${server.url("/backdrop.png")}","description":"A synthetic catalog preview. Settled focus can update its synopsis without requesting streams or subtitles.","releaseInfo":"2026","genres":["Drama"],"runtime":"95m","imdbRating":"7.4"}"""
                        }
                        MockResponse().setBody("""{"metas":[$titles]}""")
                    }
                    request.path!!.endsWith(".png") -> MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(artwork(request.path!!.contains("poster"))))
                    request.path!!.contains("/meta/") -> { metadata.incrementAndGet(); MockResponse().setBody("""{"meta":{"id":"two","type":"lab.landing","name":"Second title","description":"Synthetic detail overview","background":"${server.url("/backdrop.png")}","releaseInfo":"2026","runtime":"95m","imdbRating":"7.4","behaviorHints":{"defaultVideoId":"video"}}}""") }
                    request.path!!.contains("/stream/") -> { streams.incrementAndGet(); MockResponse().setBody("""{"streams":[{"name":"Synthetic 1080p stream","description":"Source card on the right","url":"https://example.invalid/video"}]}""") }
                    request.path!!.contains("/subtitles/") -> { subtitles.incrementAndGet(); MockResponse().setBody("""{"subtitles":[]}""") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val rows = (1..12).joinToString(",") { """{"id":"row$it","type":"lab.landing","name":"Catalog $it","extra":[{"name":"genre","options":["Drama","Comedy"]}]}""" } +
                """,{"id":"filtered","type":"lab.landing","name":"Filtered catalog","extra":[{"name":"genre","isRequired":true,"options":["Drama","Comedy"]},{"name":"year","isRequired":true,"options":["2025","2026"]}]}"""
            val manifest = """{"id":"test.lab.landing","version":"1","name":"Synthetic provider","types":["lab.landing"],"resources":["catalog","meta","stream","subtitles"],"catalogs":[$rows]}"""
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), manifest)
            val watch = com.sohva.tv.addons.AddonWatchIdentity(installed.installationId, com.sohva.tv.addons.AddonMediaKey("lab.landing", "tile2"), com.sohva.tv.addons.AddonMediaKey("lab.landing", "video"))
            host.progress.save(host.progress.begin(preferences.activeProfileId, watch, "Continue fixture",
                com.sohva.tv.addons.AddonWatchArtwork("Continue fixture", server.url("/poster.png").toString())), 1, 5_000, 45_000)
            try {
                compose.runOnUiThread { compose.activity.setContent {
                    StreamMateTheme { StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
                        AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) })
                    } }
                } }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-landing-card")).fetchSemanticsNodes().size >= 2 }
                assertTrue(catalogs.get() in 1..4)
                assertEquals(0, streams.get()); assertEquals(0, subtitles.get()); assertEquals(0, metadata.get())
                val firstRow = hasAnyAncestor(hasTestTag("addon-shelf-0"))
                val secondRow = hasAnyAncestor(hasTestTag("addon-shelf-1"))
                fun card(row: SemanticsMatcher, title: String) = compose.onNode(hasTestTag("addon-landing-card") and row and hasContentDescription(title))
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-continue-card") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-continue-card").assertIsFocused()
                compose.onNodeWithText("Continue watching").assertIsDisplayed()
                assertTrue(compose.onNodeWithTag("addon-continue-row").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithTag("addon-shelf-0").fetchSemanticsNode().boundsInRoot.top)
                compose.onNodeWithTag("addon-continue-card").performKeyInput { pressKey(Key.DirectionDown) }
                compose.waitUntil(10_000) { compose.onAllNodes(firstRow and hasTestTag("addon-landing-card") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                card(firstRow, "First title").assertIsFocused()
                compose.onNodeWithText("Synthetic provider").assertDoesNotExist()
                compose.onNodeWithText("Addons & setup").assertDoesNotExist()
                repeat(15) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
                assertTrue("Next catalog requested before navigation", 2 in requestedRows)
                card(firstRow, "Title 16").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                compose.waitUntil(10_000) { compose.onAllNodes(secondRow and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                card(secondRow, "First title").assertIsFocused()
                val cardBounds = card(secondRow, "First title").fetchSemanticsNode().boundsInRoot
                val shelfBounds = compose.onNodeWithTag("addon-shelves").fetchSemanticsNode().boundsInRoot
                assertTrue(cardBounds.bottom <= shelfBounds.bottom)
                val heading = compose.onNodeWithText("Catalog 2").fetchSemanticsNode().boundsInRoot
                assertTrue("Previous shelf tail must not remain above the active heading", heading.top - shelfBounds.top < 32)
                compose.waitUntil(10_000) { compose.onAllNodes(hasAnyAncestor(hasTestTag("addon-hero")) and hasText("First title")).fetchSemanticsNodes().isNotEmpty() }
                screenshot("addon-second-shelf-focus.png")
                // The final shelf must align too, without being clamped by the end of the list.
                repeat(10) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
                compose.waitUntil(10_000) { compose.onAllNodes(hasAnyAncestor(hasTestTag("addon-shelf-11")) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                assertTrue(compose.onNodeWithText("Catalog 12").fetchSemanticsNode().boundsInRoot.top - shelfBounds.top < 32)
                repeat(10) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) } }
                card(secondRow, "First title").performKeyInput { pressKey(Key.DirectionUp) }
                card(firstRow, "First title").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
                compose.onNodeWithTag("addon-rail-discover").assertIsFocused()
                compose.onNodeWithText("Addons & setup").assertIsDisplayed()
                compose.onNodeWithTag("addon-rail-discover").performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("addon-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("addon-search").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("addon-filter-discover").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("addon-manage").assertIsFocused()
                compose.onNodeWithTag("addon-manager-list").assertDoesNotExist()
                compose.onNodeWithTag("addon-manage").performKeyInput { pressKey(Key.DirectionRight) }
                card(firstRow, "First title").assertIsFocused()
                compose.onNodeWithText("Addons & setup").assertDoesNotExist()
                compose.waitUntil(10_000) { compose.onAllNodes(hasAnyAncestor(hasTestTag("addon-hero")) and hasText("First title")).fetchSemanticsNodes().isNotEmpty() }
                compose.mainClock.advanceTimeBy(250)
                screenshot("addon-landing-polish.png")
                // Settled focus may fetch the three titles actually paused on, not whole rows.
                assertTrue(metadata.get() <= 3); assertEquals(0, streams.get()); assertEquals(0, subtitles.get())
                card(firstRow, "First title").performKeyInput { pressKey(Key.DirectionRight) }
                card(firstRow, "Second title").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-play-source")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Synthetic detail overview").assertIsDisplayed()
                val detail = compose.onNodeWithTag("addon-details-loaded").fetchSemanticsNode().boundsInRoot
                val source = compose.onNodeWithTag("addon-play-source").fetchSemanticsNode().boundsInRoot
                assertTrue(source.left > detail.right)
                assertEquals(1, streams.get()); assertEquals(0, subtitles.get())
                compose.onNodeWithText("Continue watching").assertIsDisplayed()
                compose.onNodeWithText("Back to titles").assertDoesNotExist()
                compose.onNodeWithTag("addon-choice-Scraper").performClick()
                compose.onNode(hasText("Synthetic provider") and hasAnyAncestor(hasTestTag("addon-choice-options"))).performClick()
                compose.onNodeWithTag("addon-play-source").assertIsDisplayed()
                assertEquals(1, streams.get()) // Filtering reuses results; it does not scrape again.
                screenshot("addon-details-polish.png")
                val loaded = catalogs.get()
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                card(firstRow, "Second title").assertIsFocused()
                assertEquals(loaded, catalogs.get())
                compose.onNodeWithTag("addon-filter-discover").performClick()
                compose.onNodeWithTag("addon-choice-Catalog").performClick()
                compose.onNodeWithTag("addon-choice-options").performScrollToNode(hasText("Filtered catalog"))
                compose.onNodeWithText("Filtered catalog").performScrollTo().performClick()
                compose.onNodeWithTag("addon-text-filters").assertDoesNotExist()
                compose.onNodeWithText("Apply").assertDoesNotExist()
                val beforeFilters = catalogs.get()
                compose.onNodeWithTag("addon-choice-Genre").performClick()
                compose.onNodeWithText("Drama").performClick()
                assertEquals(beforeFilters, catalogs.get()) // Two required choices: the first must be retained without querying yet.
                compose.onNodeWithTag("addon-catalog-choices").performScrollToNode(hasTestTag("addon-choice-Year"))
                compose.onNodeWithTag("addon-choice-Year").performClick()
                compose.onNode(hasText("2026") and hasAnyAncestor(hasTestTag("addon-choice-options"))).performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().isNotEmpty() }
                screenshot("addon-filter-discover.png")
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithText("Filtered catalog").assertDoesNotExist()
                compose.onNodeWithTag("addon-filter-discover").assertIsFocused()
                compose.onNodeWithTag("addon-filter-discover").performKeyInput { pressKey(Key.DirectionRight) }
                compose.waitUntil(10_000) { compose.onAllNodes(firstRow and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                repeat(20) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
                compose.onNode(hasTestTag("addon-show-all") and firstRow).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-catalog-grid").assertIsDisplayed()
                compose.onNodeWithTag("addon-choice-Genre").assertDoesNotExist()
                screenshot("addon-show-all-grid.png")
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNode(hasTestTag("addon-show-all") and firstRow).assertIsFocused()
            } finally { host.progress.remove(preferences.activeProfileId, watch); host.store.remove(preferences.activeProfileId, installed.installationId) }
        }
    }
    @Test fun missingHistoryArtworkIsRepairedOnceAndSurvivesWithoutMetadataCache() = verifyHistoryArtworkRepair(0)

    @Test fun historyArtworkRepairWaitsForRealIoWithinItsDeadline() = verifyHistoryArtworkRepair(6)

    private fun verifyHistoryArtworkRepair(metadataDelaySeconds: Long): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        MockWebServer().use { server ->
            val metadata = AtomicInteger(); val posters = AtomicInteger(); val unexpected = AtomicInteger()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/meta/") -> {
                        metadata.incrementAndGet()
                        MockResponse().setHeadersDelay(metadataDelaySeconds, java.util.concurrent.TimeUnit.SECONDS).setHeader("Cache-Control", "no-store").setBody("""{"meta":{"id":"canonical","type":"lab.history","name":"Recovered title","poster":"${server.url("/poster.png")}"}}""")
                    }
                    request.path == "/poster.png" -> { posters.incrementAndGet(); MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(artwork(true))) }
                    else -> { unexpected.incrementAndGet(); MockResponse().setResponseCode(404) }
                }
            }
            val installed = host.store.install(preferences.activeProfileId, AddonEndpoint.parse(server.url("/manifest.json").toString(), true),
                """{"id":"test.lab.history","version":"1","name":"History fixture","types":["lab.history"],"resources":["meta"],"catalogs":[]}""")
            val watch = com.sohva.tv.addons.AddonWatchIdentity(installed.installationId, com.sohva.tv.addons.AddonMediaKey("lab.history", "original"), com.sohva.tv.addons.AddonMediaKey("lab.history", "video"))
            host.progress.save(host.progress.begin(preferences.activeProfileId, watch, "Old history title"), 1, 5_000, 45_000)
            val before = host.progress.get(preferences.activeProfileId, watch)!!
            val generation = androidx.compose.runtime.mutableIntStateOf(0)
            try {
                compose.runOnUiThread { compose.activity.setContent {
                    StreamMateTheme { StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
                        androidx.compose.runtime.key(generation.intValue) { AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) }) }
                    } }
                } }
                val clockStart = compose.mainClock.currentTime
                val wallStart = android.os.SystemClock.elapsedRealtime()
                val automaticClock = compose.mainClock.autoAdvance
                compose.mainClock.autoAdvance = false
                try {
                    compose.waitUntil(15_000) {
                        // HTTP/Room use real time, but LaunchedEffect's 8s timeout
                        // uses the Compose test clock. Don't fast-forward that
                        // deadline while waiting for real I/O on a slower runner.
                        val elapsed = android.os.SystemClock.elapsedRealtime() - wallStart
                        compose.mainClock.advanceTimeBy(
                            (elapsed - (compose.mainClock.currentTime - clockStart)).coerceAtLeast(0),
                            ignoreFrameDuration = true,
                        )
                        posters.get() > 0 && runBlocking { host.progress.get(preferences.activeProfileId, watch)?.artwork?.poster != null }
                    }
                } catch (timeout: ComposeTimeoutException) {
                    // Distinguish persistence/metadata failures from image loading.
                    // Counts and synthetic UI state only: never log addon URLs.
                    throw AssertionError("History artwork not ready: virtual=${compose.mainClock.currentTime - clockStart}, " +
                        "wall=${android.os.SystemClock.elapsedRealtime() - wallStart}, metadata=${metadata.get()}, " +
                        "posters=${posters.get()}, unexpected=${unexpected.get()}, " +
                        "savedPoster=${host.progress.get(preferences.activeProfileId, watch)?.artwork?.poster != null}, " +
                        "cards=${compose.onAllNodes(hasTestTag("addon-continue-card")).fetchSemanticsNodes().size}", timeout)
                } finally { compose.mainClock.autoAdvance = automaticClock }
                val repaired = host.progress.get(preferences.activeProfileId, watch)!!
                assertEquals(before.updatedAtMillis, repaired.updatedAtMillis)
                assertEquals(before.positionMillis, repaired.positionMillis)
                assertEquals(before.durationMillis, repaired.durationMillis)
                assertEquals("original", repaired.identity.media.id)
                assertEquals(1, metadata.get()); assertEquals(0, unexpected.get())
                assertNull(host.browser.cachedDetails(preferences.activeProfileId, installed.installationId, watch.media))
                compose.runOnIdle { generation.intValue++ }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-continue-card") and hasContentDescription("Recovered title")).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, metadata.get()) // Durable artwork, not the no-store response, supplies the second Home entry.
                screenshot("addon-history-artwork-repaired.png")
            } finally { host.progress.remove(preferences.activeProfileId, watch); host.store.remove(preferences.activeProfileId, installed.installationId) }
        }
    }
    private fun artwork(poster: Boolean): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(if (poster) 360 else 1280, if (poster) 540 else 720, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
            intArrayOf(0xff163e56.toInt(), 0xffc38253.toInt(), 0xff162438.toInt()), null, android.graphics.Shader.TileMode.CLAMP) }
        canvas.drawPaint(paint)
        paint.shader = null; paint.color = 0xffcdd9db.toInt(); paint.alpha = 75
        canvas.drawCircle(bitmap.width * .7f, bitmap.height * .32f, bitmap.width * .15f, paint)
        val bytes = java.io.ByteArrayOutputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle(); return bytes
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 3_000)
        AddonLabScreenshots.capture(compose.activity, name)
    }
}
