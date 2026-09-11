package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.Lifecycle
import androidx.tv.material3.Text
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Real embedded/sidecar cues using synthetic media and a private resource type. */
@OptIn(UnstableApi::class)
class AddonLabAutomaticSubtitleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun primaryEmbeddedSubtitlesRenderAndHaveAnAccurateControlLabel() = exercise("fixture-embedded.mkv", "fi", "en", "fi", "Fixture Finnish subtitle", "fi")
    @Test fun secondaryEmbeddedSubtitlesRenderWhenPrimaryIsUnavailable() = exercise("fixture-secondary.mkv", "fi", "en", "fi", "Fixture English subtitle", "en")
    @Test fun primaryAddonSubtitlesWinOverSecondaryEmbedded() = exercise("fixture-secondary.mkv", "fi", "en", "fi", "Fixture addon subtitle", "fin", addon = true)
    @Test fun preferredPrimaryAudioStillSuppressesAutomaticSubtitles() = exercise("fixture-embedded.mkv", "fi", "en", "en", null, null)
    @Test fun slowReadinessStillSelectsSubtitles() = exercise("fixture-embedded.mkv", "fi", "en", "fi", "Fixture Finnish subtitle", "fi", delayed = true)
    @Test fun openingPickerWithoutChoosingDoesNotCancelAutomaticSelection() = exercise("fixture-embedded.mkv", "fi", "en", "fi", "Fixture Finnish subtitle", "fi", delayed = true, inspectOnly = true)
    @Test fun startupWaitsPausedForAddonSubtitles() = exercise("fixture-secondary.mkv", "fi", "en", "fi", "Fixture addon subtitle", "fin", addon = true, delayedSubtitle = true, showArtwork = true)
    @Test fun subtitleTimeoutStartsWithPreferredEmbeddedFallback() = exercise("fixture-secondary.mkv", "fi", "en", "fi", "Fixture English subtitle", "en", addon = true, delayedSubtitle = true, subtitleTimeout = 250)
    @Test fun cancelDuringSubtitleLoadingNeverStartsPlayback() = exercise("fixture-secondary.mkv", "fi", "en", "fi", null, null, addon = true, delayedSubtitle = true, leaveDuringStartup = "cancel")
    @Test fun backDuringSubtitleLoadingNeverStartsPlayback() = exercise("fixture-secondary.mkv", "fi", "en", "fi", null, null, addon = true, delayedSubtitle = true, leaveDuringStartup = "back")
    @Test fun backgroundDuringSubtitleLoadingRequiresExplicitRetry() = exercise("fixture-secondary.mkv", "fi", "en", "fi", null, null, addon = true, delayedSubtitle = true, leaveDuringStartup = "background")

    private fun exercise(format: String, primary: String, secondary: String, audio: String, expectedCue: String?, expectedLabel: String?, addon: Boolean = false, delayed: Boolean = false, inspectOnly: Boolean = false,
        delayedSubtitle: Boolean = false, subtitleTimeout: Long = 20_000, leaveDuringStartup: String? = null, showArtwork: Boolean = false): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository
        val before = preferences.preferences.first()
        val profile = before.activeProfileId
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val requests = AtomicInteger(); val subtitleRequests = AtomicInteger(); val downloads = AtomicInteger()
        val allowMedia = CountDownLatch(if (delayed) 1 else 0)
        val allowSubtitle = CountDownLatch(if (delayedSubtitle) 1 else 0)
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            val base = "http://$address:${server.port}"
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.startsWith("/media") -> {
                        requests.incrementAndGet()
                        check(allowMedia.await(40, TimeUnit.SECONDS))
                        val bytes = assets.open("playback/$format").use { it.readBytes() }
                        val range = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        MockResponse().setHeader("Content-Type", "video/x-matroska").setHeader("Accept-Ranges", "bytes").apply {
                            if (range > 0) setResponseCode(206).setHeader("Content-Range", "bytes $range-${bytes.size - 1}/${bytes.size}")
                            setBody(Buffer().write(bytes, range, bytes.size - range))
                        }
                    }
                    request.path!!.contains("/subtitles/") -> {
                        subtitleRequests.incrementAndGet()
                        check(allowSubtitle.await(30, TimeUnit.SECONDS))
                        MockResponse().setBody(if (addon) """{"subtitles":[{"id":"fixture","lang":"fin","url":"$base/subtitle.srt"}]}""" else """{"subtitles":[]}""")
                    }
                    request.path == "/subtitle.srt" -> { downloads.incrementAndGet(); MockResponse().setBody("1\n00:00:00,000 --> 00:00:44,000\nFixture addon subtitle\n") }
                    request.path == "/backdrop.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(artwork(false)))
                    request.path == "/logo.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(artwork(true)))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse("$base/manifest.json", true),
                """{"id":"test.auto-subtitle","version":"1","name":"Synthetic subtitles","types":["lab.auto-subtitle"],"resources":["stream","subtitles"],"catalogs":[]}""")
            val video = AddonMediaKey("lab.auto-subtitle", "fixture")
            val identity = AddonWatchIdentity(installed.installationId, video, video)
            val stream = AddonSourceParser.streams("""{"streams":[{"name":"Synthetic video","url":"$base/media.mkv"}]}""").single()
            val selection = AddonPlaybackSelection(installed.installationId, installed.revision, video, stream)
            var showingPlayer by mutableStateOf(true)
            try {
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, primary)
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, secondary)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, audio)
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_AUDIO, "en")
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                        if (showingPlayer) AddonPlayerScreen(host, profile, identity, "Synthetic subtitle test", selection, false, { showingPlayer = false }, modifier,
                            artwork = if (showArtwork) AddonWatchArtwork("Synthetic subtitle test", null, "$base/backdrop.png") else null,
                            startupLogo = if (showArtwork) "$base/logo.png" else null,
                            subtitleStartupTimeoutMillis = subtitleTimeout)
                        else Text("Playback closed")
                    }
                } } }
                if (delayed) {
                    compose.waitUntil(10_000) { requests.get() > 0 }
                    compose.onNodeWithTag("addon-playback-loading").assertIsDisplayed()
                    assertFalse(compose.runOnIdle { host.activePlayback!!.player.playWhenReady })
                    if (inspectOnly) {
                        compose.onNodeWithTag("player-subtitles").performClick()
                        compose.onNodeWithText("Back to player").performClick()
                    }
                    compose.mainClock.advanceTimeBy(26_000)
                    allowMedia.countDown()
                }
                compose.waitUntil(15_000) { host.activePlayback?.ready == true }
                if (delayedSubtitle) {
                    compose.waitUntil(10_000) { subtitleRequests.get() > 0 }
                    if (subtitleTimeout > 250) {
                        compose.onNodeWithTag("addon-loading-stage").assertTextContains("Fetching subtitles…")
                        assertFalse(compose.runOnIdle { host.activePlayback!!.player.playWhenReady })
                        if (showArtwork) {
                            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-loading-cancel") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
                            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
                            compose.onNodeWithTag("player-subtitles").assertIsFocused()
                            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
                            compose.onNodeWithTag("addon-loading-cancel").assertIsFocused()
                            // Verify decoded provider imagery, not merely non-null request URLs.
                            compose.waitUntil(10_000) {
                                val pixels = compose.onNodeWithTag("addon-loading-backdrop").captureToImage().toPixelMap()
                                pixels[pixels.width * 3 / 4, pixels.height / 2].green > .2f
                            }
                            compose.waitUntil(10_000) {
                                compose.onAllNodes(hasText("Synthetic subtitle test")).fetchSemanticsNodes().isEmpty()
                            }
                            compose.onNodeWithTag("addon-loading-logo").assertIsDisplayed()
                        }
                        AddonLabScreenshots.capture(compose.activity, "addon-playback-loading.png")
                        if (leaveDuringStartup == null) allowSubtitle.countDown()
                    }
                }
                if (leaveDuringStartup != null) {
                    when (leaveDuringStartup) {
                        "cancel" -> compose.onNodeWithTag("addon-loading-cancel").performClick()
                        "back" -> InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        "background" -> compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                    }
                    if (leaveDuringStartup == "background") {
                        assertFalse(compose.runOnUiThread { host.activePlayback!!.player.playWhenReady })
                        allowSubtitle.countDown()
                        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                        compose.onNodeWithText("Playback stopped while the app was in the background.").assertIsDisplayed()
                        compose.onNodeWithTag("addon-player-retry").assertIsDisplayed()
                    } else {
                        compose.waitUntil(10_000) { host.activePlayback == null }
                        compose.onNodeWithText("Playback closed").assertIsDisplayed()
                        allowSubtitle.countDown()
                    }
                    compose.mainClock.advanceTimeBy(1_000)
                    assertEquals(0, downloads.get())
                    assertFalse(compose.runOnIdle { host.activePlayback?.player?.isPlaying == true })
                } else if (expectedCue != null) {
                    compose.waitUntil(12_000) { compose.runOnIdle { host.activePlayback!!.player.currentCues.cues.any { it.text.toString().contains(expectedCue) } } }
                    assertEquals(expectedLabel, host.activePlayback!!.selectedSubtitle)
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-playback-loading")).fetchSemanticsNodes().isEmpty() }
                    assertTrue(compose.runOnIdle { host.activePlayback!!.player.playWhenReady })
                    if (addon && subtitleTimeout > 250) assertEquals(1, downloads.get())
                    else assertEquals(0, downloads.get())
                    allowSubtitle.countDown()
                    // A later tracks/readiness event must not overwrite an explicit Off choice.
                    compose.onNodeWithTag("player-subtitles").performClick()
                    compose.onNodeWithText("Subtitles off").performClick()
                    // The old media may still be ready while the choice validates
                    // asynchronously. Wait for the applied Off state and re-prepare.
                    compose.waitUntil(10_000) {
                        compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() &&
                            compose.runOnIdle {
                                val playback = host.activePlayback!!
                                playback.ready && playback.selectedSubtitle == null &&
                                    C.TRACK_TYPE_TEXT in playback.player.trackSelectionParameters.disabledTrackTypes &&
                                    !playback.player.currentTracks.isTypeSelected(C.TRACK_TYPE_TEXT) &&
                                    playback.player.currentCues.cues.isEmpty()
                            }
                    }
                    compose.runOnIdle { host.activePlayback!!.player.seekTo(10_000) }
                    compose.mainClock.advanceTimeBy(1_000)
                    assertNull(host.activePlayback!!.selectedSubtitle)
                    assertTrue(compose.runOnIdle { C.TRACK_TYPE_TEXT in host.activePlayback!!.player.trackSelectionParameters.disabledTrackTypes })
                } else {
                    compose.mainClock.advanceTimeBy(1_000)
                    assertNull(host.activePlayback!!.selectedSubtitle)
                    assertTrue(compose.runOnIdle { host.activePlayback!!.player.currentCues.cues.isEmpty() })
                    assertEquals(0, downloads.get()); assertEquals(0, subtitleRequests.get())
                }
            } finally {
                allowMedia.countDown()
                allowSubtitle.countDown()
                compose.runOnIdle { host.activePlayback?.release() }
                host.pendingProgressWrite?.join()
                host.progress.remove(profile, identity); host.store.remove(profile, installed.installationId)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, before.preferredSubtitleLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, before.secondarySubtitleLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, before.preferredAudioLanguage)
                preferences.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_AUDIO, before.secondaryAudioLanguage)
            }
        }
    }

    private fun artwork(logo: Boolean): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(640, if (logo) 160 else 360, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        if (logo) {
            paint.color = android.graphics.Color.WHITE; paint.textSize = 72f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("SOHVA LAB", 100f, 105f, paint)
        } else {
            paint.shader = android.graphics.LinearGradient(0f, 0f, 640f, 360f,
                android.graphics.Color.rgb(10, 35, 85), android.graphics.Color.rgb(12, 150, 145), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 640f, 360f, paint)
            paint.shader = null; paint.color = android.graphics.Color.rgb(245, 170, 55)
            canvas.drawCircle(540f, 100f, 55f, paint)
        }
        return java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream); bitmap.recycle(); stream.toByteArray()
        }
    }
}
