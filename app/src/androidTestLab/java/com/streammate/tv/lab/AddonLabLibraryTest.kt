package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AddonLabLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun app(): StreamMateApplication {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        return (compose.activity.application as StreamMateApplication).also { check(it.packageName == "com.streammate.tv.lab") }
    }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun toggle(label: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-library-toggle") and hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused() and (hasTestTag("addon-find-sources") or hasText("Season 1") or hasTestTag("addon-library-toggle"))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addon-library-toggle").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("addon-library-toggle").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun movieAndWholeSeriesBookmarksSurviveDetailsAndDoNotChangeProgress(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first(); val profile = preferences.activeProfileId
        val movieKey = AddonMediaKey("movie", "library-fixture-original-movie")
        val seriesKey = AddonMediaKey("series", "library-fixture-series")
        // Never invoke an owner's broad stream provider while testing standard movie types.
        check(host.store.list(profile).none { it.enabled && listOf(movieKey.id, "library-fixture-canonical-movie").any { id -> it.manifest.supports("stream", "movie", id) } })
        check(host.library.list(profile).all { it.identity.installationId == "__lab_library_probe" })
        val progressBefore = host.progress.recent(profile).map { Triple(it.identity.metadataInstallationId, it.identity.media, it.identity.video) to it.positionMillis }
        val requests = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            val bitmap = android.graphics.Bitmap.createBitmap(80, 120, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(30, 130, 110))
            val poster = java.io.ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }; bitmap.recycle()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty(); requests += path
                    if (path == "/poster.png") return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(poster))
                    if (path.contains("/stream/")) return MockResponse().setBody("""{"streams":[]}""")
                    if (path.contains("/meta/")) {
                        val series = path.contains("/series/")
                        val videos = if (series) """, "videos":[{"id":"library-fixture-episode","title":"Pilot","season":1,"episode":1,"overview":"Episode description"}]""" else ""
                        return MockResponse().setHeader("Cache-Control", "max-age=600").setBody("""{"meta":{"id":"${if (series) seriesKey.id else "library-fixture-canonical-movie"}","type":"${if (series) "series" else "movie"}","name":"${if (series) "Saved series" else "Saved movie"}","description":"A synthetic story for Library verification.","poster":"${server.url("/poster.png")}","releaseInfo":"2026"$videos}}""")
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse(server.url("/manifest.json").toString(), true), MANIFEST)
            val movieId = AddonLibraryIdentity(installed.installationId, movieKey)
            val seriesId = AddonLibraryIdentity(installed.installationId, seriesKey)
            val scene = mutableIntStateOf(0)
            try {
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        key(scene.intValue) {
                            if (scene.intValue < 2) AddonDetailsScreen(host, profile, installed,
                                AddonMedia(if (scene.intValue == 0) movieKey else seriesKey, "Preview", null, "poster", null, null, null, emptyList()), {}, modifier)
                            else AddonDiscoverScreen(host, preferences, {}, modifier, loadInstallations = { listOf(installed) })
                        }
                    }
                } } }
                waitFor("addon-movie-details"); toggle("Add to library")
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("Remove from library")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-library-toggle").assertIsFocused()
                assertEquals(movieKey, host.library.get(profile, movieId)!!.identity.media)
                AddonLabScreenshots.capture(compose.activity, "addon-library-movie-action.png")
                compose.runOnIdle { scene.intValue = 1 }
                waitFor("addon-episode-card"); toggle("Add to library")
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("Remove from library")).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(seriesKey, host.library.get(profile, seriesId)!!.identity.media)
                compose.onNodeWithTag("addon-episode-card").assertIsDisplayed()
                AddonLabScreenshots.capture(compose.activity, "addon-library-series-action.png")
                compose.runOnIdle { scene.intValue = 2 }; waitFor("addon-library")
                compose.onNodeWithTag("addon-library").performClick(); waitFor("addon-library-page")
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-library-card") and hasContentDescription("Saved movie")).fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(10_000) {
                    val pixels = compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Saved movie")).captureToImage().toPixelMap()
                    val pixel = pixels[pixels.width / 2, pixels.height / 3]
                    pixel.green > .4f && pixel.red < .2f
                }
                AddonLabScreenshots.capture(compose.activity, "addon-library-grid.png")
                compose.onNodeWithTag("addon-library-movie").performClick()
                compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Saved series")).assertDoesNotExist()
                compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Saved movie")).performClick()
                waitFor("addon-movie-details"); toggle("Remove from library")
                compose.waitUntil(10_000) { compose.onAllNodes(hasText("Add to library") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                back(); waitFor("addon-library-empty")
                compose.onNodeWithTag("addon-library-back").assertIsFocused()
                compose.onNodeWithTag("addon-library-series").performClick()
                compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Saved series")).performClick()
                waitFor("addon-series-details"); back()
                compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Saved series")).assertIsFocused()
                compose.onNodeWithTag("addon-library-movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                compose.onNodeWithTag("addon-library-movie").performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithTag("addon-library-movie").assertIsFocused()
                back(); compose.onNodeWithTag("addon-library").assertIsFocused()
                assertNull(host.library.get(profile, movieId)); assertNotNull(host.library.get(profile, seriesId))
                assertEquals(progressBefore, host.progress.recent(profile).map { Triple(it.identity.metadataInstallationId, it.identity.media, it.identity.video) to it.positionMillis })
                assertFalse(requests.any { it.contains("/subtitles/") || it.contains("/catalog/") })
            } finally { host.library.remove(profile, movieId); host.library.remove(profile, seriesId); host.store.remove(profile, installed.installationId) }
        }
    }

    @Test fun missingAddonBookmarkCanBeRemovedWithoutRequests(): Unit = runBlocking {
        val app = app(); val host = AddonHost.get(app, app.container); val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val identity = AddonLibraryIdentity("__library_missing_fixture", AddonMediaKey("movie", "missing"))
        host.library.add(profile, identity, AddonWatchArtwork("Unavailable fixture", null))
        try {
            compose.runOnUiThread { compose.activity.setContent { StreamMateTheme { AddonLibraryScreen(host, profile, emptyList(), {}, Modifier) } } }
            waitFor("addon-library-card")
            compose.onNode(hasTestTag("addon-library-card") and hasContentDescription("Unavailable fixture")).performClick()
            waitFor("addon-library-remove-unavailable"); compose.onNodeWithTag("addon-library-remove-unavailable").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Unavailable fixture")).fetchSemanticsNodes().isEmpty() }
            assertNull(host.library.get(profile, identity))
        } finally { host.library.remove(profile, identity) }
    }

    @Test fun suppliedNavigationVectorsRenderWithLightAndDarkTints() {
        app()
        val main = listOf("Front page" to SohvaNavigationIcons.FrontPage, "Live TV" to SohvaNavigationIcons.LiveTv, "Sport" to SohvaNavigationIcons.Sport,
            "Movies" to SohvaNavigationIcons.Movies, "Series" to SohvaNavigationIcons.Series, "Search" to SohvaNavigationIcons.Search, "Discover" to SohvaNavigationIcons.Discover, "Settings" to SohvaNavigationIcons.Settings)
        val addons = listOf("Home" to SohvaNavigationIcons.AddonHome, "Library" to SohvaNavigationIcons.Library, "Search" to SohvaNavigationIcons.Search,
            "Discover" to SohvaNavigationIcons.Explore, "Addons" to SohvaNavigationIcons.Addons, "Back home" to SohvaNavigationIcons.BackToHome)
        compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF111820)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Color.White to Color(0xFF111820), Color(0xFF111820) to Color.White).forEach { (ink, ground) ->
                    listOf(main, addons).forEach { icons -> Row(Modifier.fillMaxWidth().background(ground).padding(12.dp)) {
                        icons.forEach { (label, icon) -> Column(Modifier.weight(1f)) {
                            Image(painterResource(icon), label, Modifier.size(36.dp), colorFilter = ColorFilter.tint(ink))
                            Text(label, color = ink)
                        } }
                    } }
                }
            }
        } } }
        compose.waitForIdle(); AddonLabScreenshots.capture(compose.activity, "sohva-navigation-icons.png")
        assertEquals(2, compose.onAllNodes(hasContentDescription("Library")).fetchSemanticsNodes().size)
    }
    private companion object { const val MANIFEST = """{"id":"test.library","name":"Library fixture","version":"1","types":["movie","series"],"idPrefixes":["library-fixture-"],"resources":["meta","stream"],"catalogs":[]}""" }
}
