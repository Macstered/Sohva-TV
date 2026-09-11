package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.player.BottomTransportControls
import com.streammate.tv.feature.player.PLAYER_CONTROLS_TIMEOUT_MILLIS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Synthetic-only player chrome navigation. Never installs on a physical device. */
@OptIn(UnstableApi::class)
class AddonLabPlayerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun subtitleRemoteBackDismissesControlsBeforeLeavingPlayback() = exercise(ReturnPath.REMOTE_BACK)
    @Test fun subtitleBackButtonThenOnScreenBackDismissesControls() = exercise(ReturnPath.BUTTON, screenBack = true)
    @Test fun selectedAddonSubtitleReturnsToDismissibleControls() = exercise(ReturnPath.SELECT)
    @Test fun syncRoundTripAfterEarlierDismissalKeepsBackOnPlayer() = exercise(ReturnPath.SYNC)
    @Test fun subtitleReturnAutoHidesIdleFocusedControls() = exercise(ReturnPath.BUTTON, idle = true)
    @Test fun syncReturnAutoHidesIdleFocusedControls() = exercise(ReturnPath.SYNC, idle = true)
    @Test fun pausedSubtitleReturnStaysPausedAndBackHidesControls() = exercise(ReturnPath.REMOTE_BACK, paused = true)
    @Test fun audioReturnUsesSameBackAndIdlePolicy() = exercise(ReturnPath.AUDIO, idle = true)
    @Test fun remoteInteractionRestartsReturnedControlsIdleTimer() = exercise(ReturnPath.BUTTON, idle = true, interact = true)
    @Test fun sharedTransportDefaultStillKeepsFocusedControls(): Unit = runBlocking {
        check(compose.activity.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
            BottomTransportControls("Shared default", true, 0, 45_000, 0, 1,
                aspectModeLabel = "Fit", audioTrackLabel = "Auto", subtitleTrackLabel = "Off",
                onBack = {}, onCycleAspectMode = {}, onCycleAudioTrack = {}, onCycleSubtitleTrack = {},
                onRewind = {}, onPlayPause = {}, onForward = {}, onControlsFocusChanged = {}, onDismissed = {})
        } } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("player-play-pause") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(PLAYER_CONTROLS_TIMEOUT_MILLIS + 500L)
        compose.waitForIdle()
        compose.onNodeWithTag("player-play-pause").assertIsDisplayed().assertIsFocused()
    }

    private enum class ReturnPath { REMOTE_BACK, BUTTON, SELECT, SYNC, AUDIO }

    private fun exercise(path: ReturnPath, screenBack: Boolean = false, idle: Boolean = false, paused: Boolean = false, interact: Boolean = false): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository
        val before = preferences.preferences.first(); val profile = before.activeProfileId
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            val base = "http://$address:${server.port}"
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/fixture.mkv" -> {
                        val bytes = assets.open("playback/fixture-timing.mkv").use { it.readBytes() }
                        val range = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        MockResponse().setHeader("Accept-Ranges", "bytes").apply {
                            if (range > 0) setResponseCode(206).setHeader("Content-Range", "bytes $range-${bytes.size - 1}/${bytes.size}")
                            setBody(Buffer().write(bytes, range, bytes.size - range))
                        }
                    }
                    request.path!!.contains("/subtitles/") -> MockResponse().setBody(
                        """{"subtitles":[{"id":"navigation-fi","lang":"fin","url":"$base/subtitle.srt"}]}""")
                    request.path == "/subtitle.srt" -> MockResponse().setBody("1\n00:00:00,000 --> 00:00:40,000\nNavigation fixture subtitle\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse("$base/manifest.json", true),
                """{"id":"test.player.navigation","name":"Synthetic navigation","version":"1","types":["lab.player.navigation"],"resources":["stream","subtitles"],"catalogs":[]}""")
            val key = AddonMediaKey("lab.player.navigation", "fixture")
            val identity = AddonWatchIdentity(installed.installationId, key, key)
            val stream = AddonSourceParser.streams("""{"streams":[{"name":"Navigation fixture","url":"$base/fixture.mkv"}]}""").single()
            try {
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, "fi")
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, "fi")
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    var exited by remember { mutableStateOf(false) }
                    if (exited) Text("Synthetic details", Modifier.testTag("navigation-exited"))
                    else AddonPlayerScreen(host, profile, identity, "Navigation fixture",
                        AddonPlaybackSelection(installed.installationId, installed.revision, key, stream), false,
                        { exited = true }, Modifier.fillMaxSize())
                } } }
                compose.waitUntil(15_000) { exists("addon-player-ready") }
                val playback = checkNotNull(host.activePlayback)
                compose.waitUntil(10_000) { compose.runOnIdle { playback.player.isPlaying } }

                // Real users have already hidden and reopened transport before
                // visiting subtitles. This catches replay of old dismiss/focus keys.
                remote(android.view.KeyEvent.KEYCODE_BACK)
                waitHidden()
                remote(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("player-play-pause") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("player-play-pause").assertIsFocused()
                if (paused) compose.onNodeWithTag("player-play-pause").performClick()
                compose.onNodeWithTag(if (path == ReturnPath.AUDIO) "player-audio" else "player-subtitles").performClick()
                when (path) {
                    ReturnPath.REMOTE_BACK, ReturnPath.AUDIO -> remote(android.view.KeyEvent.KEYCODE_BACK)
                    ReturnPath.BUTTON -> compose.onNodeWithText("Back to player").performClick()
                    ReturnPath.SELECT -> {
                        compose.waitUntil(10_000) { exists("addon-select-subtitle") }
                        compose.onNodeWithTag("addon-select-subtitle").performClick()
                    }
                    ReturnPath.SYNC -> {
                        compose.onNodeWithTag("addon-subtitle-sync").performClick()
                        compose.waitUntil(5_000) { exists("addon-subtitle-sync-panel") }
                        remote(android.view.KeyEvent.KEYCODE_BACK)
                        compose.onNodeWithText("Back to player").performClick()
                    }
                }
                compose.waitUntil(10_000) { !exists("addon-player-subtitle-list") && playback.ready }
                if (path == ReturnPath.SELECT) {
                    // Verify the chosen track survived re-prepare, not just that
                    // the dialog closed and control focus returned.
                    compose.waitUntil(10_000) { compose.runOnIdle {
                        playback.ready && playback.selectedSubtitle == "fin" &&
                            playback.player.currentCues.cues.any { it.text.toString().contains("Navigation fixture subtitle") }
                    } }
                }
                compose.onNodeWithTag(if (path == ReturnPath.AUDIO) "player-audio" else "player-subtitles").assertIsFocused()
                assertEquals(!paused, compose.runOnIdle { playback.player.playWhenReady })
                if (idle) {
                    compose.waitUntil(10_000) { compose.runOnIdle { playback.player.isPlaying } }
                    val automaticClock = compose.mainClock.autoAdvance
                    compose.mainClock.autoAdvance = false
                    try {
                        // The idle deadline uses Compose time, not Media3/wall
                        // time. A slow renderer may advance fewer than five
                        // virtual seconds during an eight-second wall wait.
                        compose.mainClock.advanceTimeByFrame()
                        compose.waitForIdle()
                        var remaining = PLAYER_CONTROLS_TIMEOUT_MILLIS + 500L
                        if (interact) {
                            compose.mainClock.advanceTimeBy(3000)
                            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                            compose.mainClock.advanceTimeByFrame()
                            compose.waitForIdle()
                            compose.mainClock.advanceTimeBy(2500)
                            compose.waitForIdle()
                            compose.onNodeWithTag("player-bottom-controls").assertIsDisplayed()
                            remaining -= 2500
                        }
                        compose.mainClock.advanceTimeBy(remaining)
                        compose.waitForIdle()
                        compose.onNodeWithTag("player-bottom-controls").assertDoesNotExist()
                    } finally {
                        compose.mainClock.autoAdvance = automaticClock
                    }
                } else {
                    if (screenBack) compose.onNodeWithTag("player-back").performClick()
                    else remote(android.view.KeyEvent.KEYCODE_BACK)
                    waitHidden()
                }
                compose.onNodeWithTag("navigation-exited").assertDoesNotExist()
                compose.onNodeWithTag("addon-player").assertIsDisplayed()
                assertEquals(!paused, compose.runOnIdle { playback.player.playWhenReady })
                // Controls can still be summoned after either kind of dismissal.
                remote(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(5_000) { exists("player-bottom-controls") }
                remote(android.view.KeyEvent.KEYCODE_BACK)
                waitHidden()
                compose.onNodeWithTag("navigation-exited").assertDoesNotExist()
                remote(android.view.KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("navigation-exited").assertIsDisplayed()
            } finally {
                compose.runOnIdle { host.activePlayback?.release() }; host.pendingProgressWrite?.join()
                host.progress.remove(profile, identity); host.store.remove(profile, installed.installationId)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, before.preferredSubtitleLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, before.preferredAudioLanguage)
            }
        }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitHidden() = compose.waitUntil(5_000) { !exists("player-bottom-controls") }
    private fun remote(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
}
