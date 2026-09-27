package com.sohva.tv.app

import androidx.compose.ui.test.hasAnyAncestor
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.ui.design.R
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 50 §11 "Instrumented" for addon subtitles with a synthetic subtitle addon: the automatic
 * choice downloads the preferred language before playing; with no preferences the picker says so,
 * Show all languages lists the rest, a choice downloads and applies it, and sync applies a draft
 * by re-preparing the stream. Subtitle files come only on a choice (FR-100, -101).
 */
@RunWith(AndroidJUnit4::class)
class DiscoverSubtitleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val requests = ConcurrentLinkedQueue<String>()
    private lateinit var base: String

    private val manifest = """{"id":"org.example.subs","version":"1.0.0","name":"Synthetic provider","types":["movie"],
        "resources":["catalog","meta","stream","subtitles"],"catalogs":[{"type":"movie","id":"films","name":"Films"}]}"""

    private fun srt(word: String) = "1\n00:00:00,500 --> 00:00:19,000\n$word\n"

    private val seed = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += path
                    if (path.startsWith("/media/")) {
                        return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
                    }
                    val body = when {
                        path.endsWith("/manifest.json") -> manifest
                        path.contains("/catalog/movie/films") -> """{"metas":[{"type":"movie","id":"film1","name":"Fictional Film"}]}"""
                        path.contains("/meta/movie/") -> """{"meta":{"type":"movie","id":"film1","name":"Fictional Film"}}"""
                        path.contains("/stream/") -> """{"streams":[{"name":"Synthetic 720p","url":"$base/media/film1.mp4"}]}"""
                        path.contains("/subtitles/") -> """{"subtitles":[{"id":"s-fi","lang":"fin","url":"$base/subs/fi.srt"},{"id":"s-en","lang":"eng","url":"$base/subs/en.srt"}]}"""
                        path == "/subs/fi.srt" -> srt("Hei")
                        path == "/subs/en.srt" -> srt("Hello")
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            base = "http://${deviceAddress()}:${server.port}"
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, "http://127.0.0.1:${server.port}/cfg/manifest.json") }
        }

        override fun after() {
            runBlocking {
                graph.data.preferences.setVodLanguage(VodLanguageSlot.SUBTITLES, null)
                host.settings.setAllLanguages(false)
            }
            server.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun deviceAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focusedTag(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("waiting for $tag, focused: ${focusedTag()}", e)
        }
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun click(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    /** The app's own text, in whatever language the device uses. */
    private fun string(id: Int): String = AppLocales.texts(graph.app).getString(id)

    private fun count(path: String) = requests.count { it == path }

    private fun play() {
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
        click("discover-card-1-film1")
        click("discover-source-Synthetic provider-0")
        compose.waitUntil(25_000) { exists("screen-player") && !exists("addon-loading") }
    }

    private fun openPicker() {
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        click("player-transport-subtitles")
        compose.waitUntil(5_000) { exists("addon-subtitle-picker") }
        awaitFocus("addon-subtitles-back")
    }

    @Test
    fun theAutomaticChoiceLoadsThePreferredLanguageBeforePlaying() {
        runBlocking { graph.data.preferences.setVodLanguage(VodLanguageSlot.SUBTITLES, "fi") }
        play()
        // The Finnish file came during start-up; the English one was never asked for.
        assertEquals(1, count("/subs/fi.srt"))
        assertEquals(0, count("/subs/en.srt"))
        openPicker()
        // Only the preferred language is listed, and its option is the one selected.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(string(R.string.addon_ui_selected))).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(exists("addon-subtitles-language-fi"))
        assertTrue(!exists("addon-subtitles-language-en"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("addon-subtitle-picker") }
    }

    @Test
    fun withoutPreferencesThePickerListsAllLanguagesOnRequestAndSyncApplies() {
        play()
        assertEquals(0, requests.count { it.startsWith("/subs/") })
        openPicker()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(string(R.string.addon_ui_choose_subtitle_languages_in_sohva_settings_or_show_all_languages))).fetchSemanticsNodes().isNotEmpty() }
        click("addon-subtitles-show-all")
        click("addon-subtitles-language-en")
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Synthetic provider")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { count("/subs/en.srt") == 1 && !exists("addon-subtitle-picker") }
        val prepared = count("/media/film1.mp4")
        openPicker()
        click("addon-subtitles-sync")
        awaitFocus("addon-sync-control")
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("+0.300 s", text("addon-sync-draft"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Applied by preparing the stream again at the same place; the file is not downloaded again.
        compose.waitUntil(20_000) { count("/media/film1.mp4") > prepared && !text("addon-sync-status").contains("…") }
        assertTrue(text("addon-sync-status"), text("addon-sync-status") == string(R.string.addon_ui_left_right_0_1_s_hold_1_s_ok_or_apply_to_preview_playback_may_brie))
        assertEquals(1, count("/subs/en.srt"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { exists("addon-subtitle-picker") }
    }

    /** Owner report 27 Sept: focus left the subtitle picker for the transport controls behind it. */
    @Test
    fun focusNeverLeavesThePickerForTheControlsBehindIt() {
        play()
        openPicker()
        assertTrue("the controls stay drawn behind the picker", exists("player-transport-subtitles"))
        val inside = { compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("addon-subtitle-picker"))).fetchSemanticsNodes().isNotEmpty() }
        val moves = List(8) { KeyEvent.KEYCODE_DPAD_DOWN } + List(3) { KeyEvent.KEYCODE_DPAD_RIGHT } + List(3) { KeyEvent.KEYCODE_DPAD_LEFT } +
            List(12) { KeyEvent.KEYCODE_DPAD_UP }
        for (key in moves) {
            press(key)
            assertTrue("focus left the picker for ${focusedTag()}", inside())
        }
        // The list grows under focus: still nowhere else.
        click("addon-subtitles-show-all")
        for (key in List(10) { KeyEvent.KEYCODE_DPAD_DOWN } + List(3) { KeyEvent.KEYCODE_DPAD_LEFT } + List(12) { KeyEvent.KEYCODE_DPAD_UP }) {
            press(key)
            assertTrue("focus left the picker for ${focusedTag()}", inside())
        }
    }
}
