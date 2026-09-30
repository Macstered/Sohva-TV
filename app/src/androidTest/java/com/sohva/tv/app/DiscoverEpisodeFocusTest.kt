package com.sohva.tv.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.ui.design.theme.Palettes
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Visible episode focus with a real thumbnail, D-pad movement, and cards without artwork. */
@RunWith(AndroidJUnit4::class)
class DiscoverEpisodeFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val seed = object : ExternalResource() {
        override fun before() {
            val bitmap = Bitmap.createBitmap(460, 260, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(32, 64, 96))
            val artwork = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            bitmap.recycle()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when {
                        request.url.encodedPath.endsWith("/manifest.json") -> """{"id":"org.example.focus","version":"1.0.0","name":"Episode fixture","types":["series"],"resources":["catalog","meta"],"catalogs":[{"type":"series","id":"shows","name":"Shows"}]}"""
                        request.url.encodedPath.contains("/catalog/") -> """{"metas":[{"type":"series","id":"show1","name":"Fictional Show"}]}"""
                        request.url.encodedPath.contains("/meta/") -> """{"meta":{"type":"series","id":"show1","name":"Fictional Show","videos":[
                            {"id":"show1:1:1","title":"Pilot","season":1,"episode":1,"thumbnail":"${server.url("/art.png")}"},
                            {"id":"show1:1:2","title":"Second","season":1,"episode":2,"thumbnail":"${server.url("/art.png")}"},
                            {"id":"show1:2:1","title":"No artwork","season":2,"episode":1}]}}"""
                        request.url.encodedPath == "/art.png" -> return MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(artwork)).build()
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            server.start()
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, server.url("/cfg/manifest.json").toString()) }
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun ringPixel(tag: String): Int {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return image.getPixel(2, image.height / 2)
    }

    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun theVisibleOutlineFollowsTheFocusedEpisode() {
        compose.focusRail(RailItem.DISCOVER)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val show = "discover-card-1-show1"
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(show) }
        compose.onNodeWithTag(show).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("discover-season-1")
        val first = "discover-episode-show1:1:1"
        val second = "discover-episode-show1:1:2"
        compose.onNodeWithTag(first).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(first)
        // Wait for the actual image, not just the focus semantics: the image used to hide focus.
        compose.waitUntil(10_000) {
            val image = compose.onNodeWithTag(first).captureToImage().asAndroidBitmap()
            val pixel = image.getPixel(image.width / 2, image.height / 4)
            android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 30
        }
        val ring = Palettes.original.textPrimary.toArgb()
        assertEquals("First episode outline", ring, ringPixel(first))
        assertNotEquals("Unfocused episode has no outline", ring, ringPixel(second))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(second)
        assertEquals("Outline follows Right", ring, ringPixel(second))
        assertNotEquals("Previous episode outline clears", ring, ringPixel(first))
        screenshot("discover-episode-focus-art.png")
        compose.onNodeWithTag("discover-season-2").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("discover-season-2")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val noArt = "discover-episode-show1:2:1"
        awaitFocus(noArt)
        assertEquals("Episode without artwork also has an outline", ring, ringPixel(noArt))
        screenshot("discover-episode-focus-empty.png")
    }
}
