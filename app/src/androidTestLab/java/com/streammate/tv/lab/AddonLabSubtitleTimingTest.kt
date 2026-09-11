package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
class AddonLabSubtitleTimingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun downloadedTimingAndRemotePicker() = exercise(false)
    @Test fun embeddedTimingAndSameLanguageTrackSelection() = exercise(true)
    @Test fun externalAddonSrtTimingWithEmbeddedTracks() = exercise(true, EXTERNAL_SRT)
    @Test fun externalAddonVttTimingWithEmbeddedTracks() = exercise(true, EXTERNAL_VTT)
    @Test fun externalAddonAssTimingWithEmbeddedTracks() = exercise(true, EXTERNAL_ASS)
    @Test fun externalAddonTimingDuringPlaybackAndPickerReopen() = exercise(true, EXTERNAL_SRT, checkWhilePlaying = true)
    @Test fun parsersShiftAbsoluteAndSampleRelativeTimesAndSeekFilters() {
        val srt = "1\n00:00:02,000 --> 00:00:04,000\nTiming\n".toByteArray()
        val vtt = "WEBVTT\n\n00:02.000 --> 00:04.000\nTiming\n".toByteArray()
        for ((mime, data) in listOf(MimeTypes.APPLICATION_SUBRIP to srt, MimeTypes.TEXT_VTT to vtt)) {
            val parser = AddonTimingParserFactory(2000).create(Format.Builder().setSampleMimeType(mime).build())
            val cues = mutableListOf<CuesWithTiming>()
            parser.parse(data, SubtitleParser.OutputOptions.onlyCuesAfter(3_000_000), cues::add)
            assertTrue(cues.any { it.cues.isNotEmpty() && it.startTimeUs == 4_000_000L })
            parser.reset(); cues.clear()
            parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.allCues(), cues::add)
            assertTrue(cues.any { it.cues.isNotEmpty() && it.startTimeUs == 4_000_000L })
        }
        var captured: SubtitleParser.OutputOptions? = null
        val fake = object : SubtitleParser {
            override fun getCueReplacementBehavior() = Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
            override fun parse(data: ByteArray, offset: Int, length: Int, options: SubtitleParser.OutputOptions, output: androidx.media3.common.util.Consumer<CuesWithTiming>) {
                captured = options; output.accept(CuesWithTiming(emptyList(), C.TIME_UNSET, 2_000_000))
            }
        }
        val factory = object : SubtitleParser.Factory {
            override fun supportsFormat(format: Format) = true
            override fun getCueReplacementBehavior(format: Format) = Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
            override fun create(format: Format) = fake
        }
        val cues = mutableListOf<CuesWithTiming>()
        AddonTimingParserFactory(-1000, factory).create(Format.Builder().build()).parse(byteArrayOf(),
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(5_000_000), cues::add)
        assertEquals(-1_000_000L, cues.single().startTimeUs)
        assertEquals(6_000_000L, captured!!.startTimeUs)
        assertTrue(captured.outputAllCues)
        val late = AddonTimingParserFactory(-2000).create(Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build())
        cues.clear()
        late.parse(SRT.toByteArray(), SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(15_000_000), cues::add)
        assertTrue("A backward seek must retain cues before a late subtitle preparation", cues.any { it.cues.isNotEmpty() && it.startTimeUs == 0L })
    }

    private fun exercise(embedded: Boolean, externalPayload: String? = null, checkWhilePlaying: Boolean = false): Unit = runBlocking {
        val expectedCue = if (externalPayload == null) "Timing dialogue" else "External dialogue"
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository
        val before = preferences.preferences.first(); val profile = before.activeProfileId
        val showAllBefore = host.showAllSubtitleLanguages.first()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val mediaRequests = AtomicInteger(); val subtitleRequests = AtomicInteger()
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            val base = "http://$address:${server.port}"
            val file = if (embedded) "fixture-timing.mkv" else "fixture.mp4"
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.startsWith("/media") -> {
                        mediaRequests.incrementAndGet()
                        val bytes = assets.open("playback/$file").use { it.readBytes() }
                        val range = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        MockResponse().setHeader("Accept-Ranges", "bytes").apply {
                            if (range > 0) setResponseCode(206).setHeader("Content-Range", "bytes $range-${bytes.size - 1}/${bytes.size}")
                            setBody(Buffer().write(bytes, range, bytes.size - range))
                        }
                    }
                    request.path!!.contains("/subtitles/") -> MockResponse().setBody(if (externalPayload == null) """{"subtitles":[]}"""
                        else """{"subtitles":[{"id":"external-fi","lang":"fin","url":"$base/download/subtitle"}]}""")
                    request.path == "/download/subtitle" -> MockResponse().setResponseCode(302).setHeader("Location", "$base/fi.srt")
                    request.path == "/fi.srt" -> { subtitleRequests.incrementAndGet(); MockResponse().setBody(externalPayload ?: SRT) }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse("$base/manifest.json", true),
                """{"id":"test.timing","name":"Synthetic timing","version":"1","types":["lab.timing"],"resources":["stream","subtitles"],"catalogs":[]}""")
            val key = AddonMediaKey("lab.timing", "fixture")
            val identity = AddonWatchIdentity(installed.installationId, key, key)
            val inline = if (externalPayload != null) "" else """, "subtitles":[{"id":"fi","lang":"fin","url":"$base/fi.srt"},{"id":"es","lang":"spa","url":"$base/fi.srt"}]"""
            val stream = AddonSourceParser.streams("""{"streams":[{"name":"Timing source","url":"$base/media/$file"$inline}]}""").single()
            try {
                host.setShowAllSubtitleLanguages(false)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, "fi")
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, "en")
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, "fi")
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        AddonPlayerScreen(host, profile, identity, "Subtitle timing fixture", AddonPlaybackSelection(installed.installationId, installed.revision, key, stream), false, {}, modifier)
                    }
                } } }
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-player-ready")).fetchSemanticsNodes().isNotEmpty() }
                val playback = checkNotNull(host.activePlayback)
                if (externalPayload != null) {
                    // Match the reported path: automatic embedded subtitles work,
                    // then the viewer manually chooses an addon-provided track.
                    compose.runOnIdle { playback.player.pause(); playback.player.seekTo(3000) }
                    waitForCue("Timing dialogue")
                    compose.onNodeWithTag("player-subtitles").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-select-subtitle")).fetchSemanticsNodes().size == 1 }
                    compose.onNodeWithTag("addon-select-subtitle").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    waitForCue(expectedCue)
                    assertTrue(playback.selectedSubtitleChoice!!.startsWith("external-"))
                }
                if (checkWhilePlaying) {
                    val externalChoice = playback.selectedSubtitleChoice
                    compose.runOnIdle { playback.player.seekTo(0); playback.player.play() }
                    compose.onNodeWithTag("player-subtitles").performClick()
                    compose.onNodeWithTag("addon-subtitle-sync").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-subtitle-adjust") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                    repeat(20) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
                    compose.onNodeWithTag("addon-subtitle-apply").performClick()
                    compose.waitUntil(10_000) { playback.ready && playback.subtitleDelayMillis == 2000L }
                    assertEquals(externalChoice, playback.selectedSubtitleChoice)
                    // Observe progressing presentation, not only a paused seek:
                    // the original 2–4 s cue must now appear at 4–6 s.
                    compose.runOnIdle { playback.player.seekTo(2500) }
                    compose.waitUntil(10_000) { playback.ready && compose.runOnIdle { playback.player.currentPosition in 2700..3500 } }
                    assertTrue(compose.runOnIdle { playback.player.currentCues.cues.isEmpty() })
                    waitForCue(expectedCue)
                    assertTrue(compose.runOnIdle { playback.player.currentPosition in 3900..5900 })
                    remoteKey(android.view.KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithTag("addon-subtitle-sync").assertTextContains("Subtitle sync · +2.000 s")
                    assertEquals(externalChoice, playback.selectedSubtitleChoice)
                    compose.onNodeWithTag("addon-subtitle-sync").performClick()
                    compose.onNodeWithTag("addon-subtitle-offset").assertTextContains("+2.000 s")
                    assertEquals(externalChoice, playback.selectedSubtitleChoice)
                    compose.onNodeWithTag("addon-subtitle-reset").performClick()
                    compose.onNodeWithTag("addon-subtitle-apply").performClick()
                    compose.waitUntil(10_000) { playback.ready && playback.subtitleDelayMillis == 0L }
                    compose.runOnIdle { playback.player.seekTo(2500) }
                    waitForCue(expectedCue)
                    remoteKey(android.view.KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithText("Back to player").performClick()
                }
                compose.runOnIdle { playback.player.pause(); playback.player.seekTo(5000) }
                compose.waitUntil(10_000) { playback.ready && compose.runOnIdle { playback.player.currentCues.cues.isEmpty() } }
                val position = compose.runOnIdle { playback.player.currentPosition }
                withContext(Dispatchers.Main) { playback.applySubtitleDelay(2000) }
                waitForCue(expectedCue)
                assertFalse(compose.runOnIdle { playback.player.playWhenReady })
                assertTrue(kotlin.math.abs(compose.runOnIdle { playback.player.currentPosition } - position) < 150)
                compose.runOnIdle { playback.player.seekTo(7000) }
                compose.waitUntil(10_000) { playback.ready && compose.runOnIdle { playback.player.currentCues.cues.isEmpty() } }
                compose.runOnIdle { playback.player.seekTo(5000) }; waitForCue(expectedCue)
                withContext(Dispatchers.Main) { playback.applySubtitleDelay(12_000) }
                compose.waitUntil(10_000) { playback.ready }
                compose.runOnIdle { playback.player.seekTo(15_000) }; waitForCue(expectedCue)
                withContext(Dispatchers.Main) { playback.applySubtitleDelay(-2000) }
                compose.waitUntil(10_000) { playback.ready }
                compose.runOnIdle { playback.player.seekTo(1000) }; waitForCue(expectedCue)
                compose.onNodeWithTag("player-subtitles").performClick()
                compose.onNodeWithTag("addon-subtitle-language-fi").assertIsDisplayed()
                compose.onNodeWithTag("addon-subtitle-language-es").assertDoesNotExist()
                if (embedded && externalPayload == null) {
                    compose.onAllNodes(hasTestTag("addon-select-embedded-subtitle")).assertCountEquals(2)
                    compose.onAllNodes(hasTestTag("addon-select-embedded-subtitle"))[1].performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    assertEquals(0L, playback.subtitleDelayMillis)
                    compose.runOnIdle { playback.player.seekTo(3000) }; waitForCue("Timing commentary")
                    compose.onNodeWithTag("player-subtitles").performClick()
                    // Removing a downloaded sidecar changes Media3's group IDs.
                    // The second same-language embedded track must still win.
                    compose.onNodeWithTag("addon-select-subtitle").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    waitForCue("Timing dialogue")
                    compose.onNodeWithTag("player-subtitles").performClick()
                    compose.onAllNodes(hasTestTag("addon-select-embedded-subtitle"))[1].performClick()
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    waitForCue("Timing commentary")
                    compose.onNodeWithTag("player-subtitles").performClick()
                }
                withContext(Dispatchers.Main) { playback.applySubtitleDelay(0) }
                compose.runOnIdle { playback.player.seekTo(5000) }
                compose.waitUntil(10_000) { playback.ready && compose.runOnIdle { playback.player.currentCues.cues.isEmpty() } }
                compose.onNodeWithText("Back to player").assertIsFocused()
                compose.waitForIdle()
                InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(200, 2_000)
                AddonLabScreenshots.capture(compose.activity, if (embedded) "addon-subtitle-picker-embedded.png" else "addon-subtitle-picker.png")
                compose.onNodeWithTag("addon-subtitle-sync").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-subtitle-adjust") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                val oldDelay = playback.subtitleDelayMillis
                val requestsBefore = mediaRequests.get()
                repeat(20) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
                compose.onNodeWithTag("addon-subtitle-offset").assertTextContains(AddonSubtitleTiming.label(oldDelay + 2000))
                assertEquals(oldDelay, playback.subtitleDelayMillis)
                assertEquals(requestsBefore, mediaRequests.get())
                AddonLabScreenshots.capture(compose.activity, "addon-subtitle-sync.png")
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                // The retained underlying window can remember its own focus;
                // assert this dialog's Apply target, not one global focused node.
                compose.onNodeWithTag("addon-subtitle-apply").assertIsFocused()
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(10_000) { playback.ready && playback.subtitleDelayMillis == oldDelay + 2000 }
                waitForCue(if (embedded && externalPayload == null) "Timing commentary" else expectedCue)
                assertTrue(kotlin.math.abs(compose.runOnIdle { playback.player.currentPosition } - 5000) < 150)
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("addon-subtitle-reset").assertIsFocused()
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                compose.onNodeWithTag("addon-subtitle-apply").assertIsFocused()
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(10_000) { playback.ready && playback.subtitleDelayMillis == 0L }
                compose.waitUntil(10_000) { compose.runOnIdle { playback.player.currentCues.cues.isEmpty() } }
                // OK on the adjustment must apply as well, without moving to a
                // separate action. Assert real cues, not just the stored offset.
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("addon-subtitle-adjust").assertIsFocused()
                repeat(20) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(10_000) { playback.ready && playback.subtitleDelayMillis == 2000L }
                waitForCue(if (embedded && externalPayload == null) "Timing commentary" else expectedCue)
                val appliedRequests = mediaRequests.get()
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                assertEquals("An unchanged OK must not restart media", appliedRequests, mediaRequests.get())
                if (!embedded) {
                    compose.onNodeWithTag("addon-sync-play-pause").performClick()
                    compose.waitUntil(10_000) { compose.runOnIdle { playback.player.isPlaying } }
                }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.onNodeWithText("Back to player").assertIsFocused()
                assertFalse(compose.runOnIdle { playback.player.playWhenReady })
                compose.onNodeWithText("Back to player").performClick()
                compose.onNodeWithTag("player-subtitles").assertIsFocused()
                assertEquals(!embedded, compose.runOnIdle { playback.player.playWhenReady })
                if (!embedded || externalPayload != null) assertEquals("Timing reuses downloaded subtitle bytes", 1, subtitleRequests.get())
            } finally {
                compose.runOnIdle { host.activePlayback?.release() }; host.pendingProgressWrite?.join()
                host.progress.remove(profile, identity); host.store.remove(profile, installed.installationId)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, before.preferredSubtitleLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, before.secondarySubtitleLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, before.preferredAudioLanguage)
                host.setShowAllSubtitleLanguages(showAllBefore)
            }
        }
    }
    private fun remoteKey(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun waitForCue(text: String) {
        try { compose.waitUntil(15_000) {
            hostPlaybackReady() && compose.runOnIdle { AddonHost.get(compose.activity.application as StreamMateApplication,
                (compose.activity.application as StreamMateApplication).container).activePlayback!!.player.currentCues.cues.any { it.text.toString().contains(text) } }
        } } catch (error: ComposeTimeoutException) {
            val playback = AddonHost.get(compose.activity.application as StreamMateApplication,
                (compose.activity.application as StreamMateApplication).container).activePlayback!!
            val diagnostic = compose.runOnIdle { "position=${playback.player.currentPosition}, ready=${playback.ready}, failed=${playback.failed}, offset=${playback.subtitleDelayMillis}, selected=${playback.selectedSubtitle}, cues=${playback.player.currentCues.cues.map { it.text }}" }
            throw AssertionError("Synthetic cue '$text' missing: $diagnostic", error)
        }
    }
    private fun hostPlaybackReady() = AddonHost.get(compose.activity.application as StreamMateApplication,
        (compose.activity.application as StreamMateApplication).container).activePlayback?.ready == true
    private companion object {
        const val SRT = "1\n00:00:02,000 --> 00:00:04,000\nTiming dialogue\n\n2\n00:00:08,000 --> 00:00:10,000\nSecond timing line\n"
        const val EXTERNAL_SRT = "1\n00:00:02,000 --> 00:00:04,000\nExternal dialogue\n\n2\n00:00:08,000 --> 00:00:10,000\nExternal second line\n"
        const val EXTERNAL_VTT = "WEBVTT\n\n00:02.000 --> 00:04.000\nExternal dialogue\n\n00:08.000 --> 00:10.000\nExternal second line\n"
        const val EXTERNAL_ASS = "[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Start, End, Text\nDialogue: 0:00:02.00,0:00:04.00,External dialogue\nDialogue: 0:00:08.00,0:00:10.00,External second line\n"
    }
}
