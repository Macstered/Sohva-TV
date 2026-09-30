package com.sohva.tv.app

import android.content.ComponentName
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.core.player.PlaybackService
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith

/** Real library playback: English is the default; the preferred second audio track must win. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryAudioLanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val name = TestName()
    private val server = MockWebServer()
    private var finnishName = "Finnish"
    private val seed = object : ExternalResource() {
        override fun before() {
            val language = when (name.methodName) {
                "aFinnishLanguageCodeIsSelected" -> "fin"
                "aSuomiLanguageNameIsSelected" -> "Suomi"
                "aFinnishLabelWithoutALanguageCodeIsSelected" -> null
                else -> "Finnish"
            }
            if (language == "Suomi") finnishName = "Suomi"
            val segments = TestMedia.aacSegments(count = 12)
            val languageAttribute = language?.let { ",LANGUAGE=\"$it\"" }.orEmpty()
            val master = """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="english.m3u8"
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="$finnishName"$languageAttribute,DEFAULT=NO,AUTOSELECT=YES,URI="finnish.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2",AUDIO="audio"
                english.m3u8
            """.trimIndent()
            val playlist = buildString {
                append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n")
                for (i in segments.indices) append("#EXTINF:4.0,\nseg$i.aac\n")
                append("#EXT-X-ENDLIST\n")
            }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    return if (path.endsWith(".m3u8")) {
                        MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl")
                            .body(if (path.endsWith("master.m3u8")) master else playlist).build()
                    } else {
                        val index = path.substringAfterLast("seg").substringBefore(".aac").toInt()
                        MockResponse.Builder().addHeader("Content-Type", "audio/aac").body(Buffer().write(segments[index])).build()
                    }
                }
            }
            server.start()
            LibraryFixture.seed(graph, perGroup = 2, stream = { server.url("/vod/master.m3u8").toString() })
            runBlocking { graph.data.preferences.setVodLanguage(VodLanguageSlot.AUDIO, "fi") }
        }

        override fun after() {
            runBlocking { graph.data.preferences.setVodLanguage(VodLanguageSlot.AUDIO, null) }
            server.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(name).around(seed).around(compose)

    private fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        compose.waitForIdle()
    }

    private fun open(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun assertPreferredAudio() {
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        open("library-row-group:drama")
        open("library-card-${LibraryFixture.key(0)}")
        open("details-watch")
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("screen-player") }
        val context = instrumentation.targetContext
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            fun tracks(): List<Triple<String?, String?, Boolean>> {
                var snapshot = emptyList<Triple<String?, String?, Boolean>>()
                instrumentation.runOnMainSync {
                    snapshot = controller.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
                        (0 until group.length).map { i ->
                            val format = group.getTrackFormat(i)
                            Triple(format.label, format.language, group.isTrackSelected(i))
                        }
                    }
                }
                return snapshot
            }
            compose.waitUntil(15_000) { tracks().any { it.first == finnishName } }
            try {
                compose.waitUntil(5_000) { tracks().any { it.first == finnishName && it.third } }
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Finnish was available but not selected: ${tracks()}", e)
            }
            assertEquals("fi", runBlocking { graph.data.preferences.playback().vodLanguages.audio })
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    @Test fun aFinnishLanguageCodeIsSelected() = assertPreferredAudio()
    @Test fun aFinnishLanguageNameIsSelected() = assertPreferredAudio()
    @Test fun aSuomiLanguageNameIsSelected() = assertPreferredAudio()
    @Test fun aFinnishLabelWithoutALanguageCodeIsSelected() = assertPreferredAudio()
}
