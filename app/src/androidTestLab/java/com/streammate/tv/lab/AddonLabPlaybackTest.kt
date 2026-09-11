package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent

import androidx.annotation.OptIn
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonMediaKey
import com.sohva.tv.addons.AddonWatchIdentity
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.app.activeRestriction
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

/** Entirely synthetic network/media; custom type prevents requests to real installed addons. */
@OptIn(UnstableApi::class)
class AddonLabPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mp4PlaybackSubtitlesResumeAndNetworkRetry() = exercise("fixture.mp4", full = true)
    @Test fun hlsPlaybackAndSeek() = exercise("fixture.m3u8", full = false)
    @Test fun dashPlaybackAndSeek() = exercise("fixture.mpd", full = false)
    @Test fun episodePlaybackContinueAndStartOver() = exercise("fixture.mp4", full = true, series = true)
    @Test fun delayedEpisodeSubtitleReprepareRetainsSelection() = exercise("fixture.mp4", full = true, series = true, delayedSubtitleReload = true)

    private fun exercise(format: String, full: Boolean, series: Boolean = false, delayedSubtitleReload: Boolean = false): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        check(assets.list("playback")?.contains(format) == true) { "Generate synthetic playback fixtures before building the Lab test APK." }
        val host = AddonHost.get(app, app.container)
        val profileId = app.container.preferencesRepository.preferences.first().activeProfileId
        val originalPreferences = app.container.preferencesRepository.preferences.first()
        val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val base = "http://$address:${server.port}"
            val resolutions = AtomicInteger()
            val mediaRequests = AtomicInteger()
            val subtitleRequests = AtomicInteger()
            val offline = AtomicBoolean(false)
            val mediaAuth = AtomicBoolean(false)
            val delayNextMedia = AtomicBoolean(false)
            val delayedMediaRequests = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    return when {
                        path.contains("/catalog/") -> MockResponse().setBody("""{"metas":[{"id":"fixture-title","type":"lab.playback","name":"Playback fixture"}]}""")
                        path.contains("/meta/") -> MockResponse().setBody(if (series)
                            """{"meta":{"id":"fixture-title","type":"lab.playback","name":"Playback fixture","videos":[{"id":"fixture-video","title":"Fixture episode","season":1,"episode":1,"overview":"Synthetic episode"}]}}"""
                            else """{"meta":{"id":"fixture-title","type":"lab.playback","name":"Playback fixture","behaviorHints":{"defaultVideoId":"fixture-video"}}}""")
                        path.contains("/stream/") -> {
                            val generation = resolutions.incrementAndGet()
                            MockResponse().setBody("""{"streams":[{"name":"Fixture source","url":"$base/media/$format?generation=$generation","behaviorHints":{"filename":"$format","proxyHeaders":{"request":{"X-Fixture-Key":"fixture-only"}}}}]}""")
                        }
                        path.contains("/subtitles/") -> MockResponse().setBody("""{"subtitles":[{"id":"fixture-sub","lang":"eng","url":"$base/subtitle"}]}""")
                        path == "/subtitle" -> {
                            subtitleRequests.incrementAndGet()
                            check(request.getHeader("X-Fixture-Key") == null)
                            MockResponse().setBody("1\n00:00:00,000 --> 00:00:40,000\nFixture subtitle\n")
                        }
                        path.startsWith("/media/") -> {
                            mediaRequests.incrementAndGet()
                            mediaAuth.set(request.getHeader("X-Fixture-Key") == "fixture-only")
                            if (offline.get()) MockResponse().setResponseCode(503)
                            else {
                                val name = path.substringAfterLast('/')
                                if (name == format && request.requestUrl!!.queryParameter("generation") != resolutions.get().toString()) MockResponse().setResponseCode(403)
                                else try {
                                    val bytes = assets.open("playback/$name").use { it.readBytes() }
                                    val range = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                                    val response = MockResponse().setHeader("Accept-Ranges", "bytes").setHeader("Content-Type", when {
                                        name.endsWith("m3u8") -> "application/vnd.apple.mpegurl"
                                        name.endsWith("mpd") -> "application/dash+xml"
                                        name.endsWith("ts") -> "video/mp2t"
                                        else -> "video/mp4"
                                    })
                                    if (delayNextMedia.compareAndSet(true, false)) {
                                        delayedMediaRequests.incrementAndGet()
                                        response.setHeadersDelay(2, TimeUnit.SECONDS)
                                    }
                                    if (range >= bytes.size) response.setResponseCode(416)
                                    else {
                                        if (range > 0) response.setResponseCode(206).setHeader("Content-Range", "bytes $range-${bytes.size - 1}/${bytes.size}")
                                        response.setBody(Buffer().write(bytes, range, bytes.size - range))
                                    }
                                } catch (_: Exception) { MockResponse().setResponseCode(404) }
                            }
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val installed = host.store.install(profileId, AddonEndpoint.parse("$base/manifest.json", true), MANIFEST)
            val identity = AddonWatchIdentity(installed.installationId, AddonMediaKey("lab.playback", "fixture-title"), AddonMediaKey("lab.playback", "fixture-video"))
            try {
                app.container.preferencesRepository.setPreferredLanguage(com.streammate.tv.app.PreferredLanguageSlot.PRIMARY_SUBTITLE, if (full) "en" else null)
                app.container.preferencesRepository.setPreferredLanguage(com.streammate.tv.app.PreferredLanguageSlot.PRIMARY_AUDIO, null)
                compose.runOnUiThread { compose.activity.setContent {
                    com.streammate.tv.app.StreamMateTheme { DiscoverAddonFeature(startInManager = true).Screen(app.container, {}) }
                } }
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-manager-list")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-manager-list").performScrollToNode(hasTestTag("addon-browse-${installed.installationId}"))
                compose.onNodeWithTag("addon-browse-${installed.installationId}").performScrollTo().performClick()
                compose.onNodeWithText("Playback catalog · lab.playback").performClick()
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodes(hasTestTag("addon-media-card"))[0].performClick()
                if (series) {
                    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-episode-card")).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("addon-episode-card").performClick()
                }
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-find-sources")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-find-sources").performScrollTo().performClick()
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-play-source")).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(0, mediaRequests.get())
                compose.onAllNodes(hasTestTag("addon-play-source"))[0].performScrollTo().performClick()
                waitReady()
                if (full) {
                    // Automatic sidecar attachment prepares the media item again.
                    // Exercise transport only after that asynchronous preparation.
                    waitForSubtitle(host, "eng")
                }
                assertTrue(mediaAuth.get())
                compose.runOnIdle { host.activePlayback!!.player.seekTo(12_000); host.activePlayback!!.player.pause() }
                compose.waitUntil(15_000) { runBlocking { host.progress.get(profileId, identity)?.positionMillis?.let { it >= 11_000 } == true } }
                assertEquals(45_000.0, compose.runOnIdle { host.activePlayback!!.player.duration }.toDouble(), 1500.0)
                val playerBounds = compose.onNodeWithTag("addon-player").fetchSemanticsNode().boundsInRoot
                val controlsBounds = compose.onNodeWithTag("player-bottom-controls").fetchSemanticsNode().boundsInRoot
                assertTrue("VOD controls stay in the lower half", controlsBounds.top > playerBounds.height / 2)
                if (!full) screenshot("addon-lab-playback-${format.substringAfterLast('.')}.png")
                if (full) {
                    waitForSubtitle(host, "eng")
                    assertEquals(1, subtitleRequests.get()) // Automatically applied the primary language.
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    compose.waitUntil(10_000) { compose.runOnIdle { host.activePlayback!!.player.isPlaying } }
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    compose.waitUntil(10_000) { compose.runOnIdle { !host.activePlayback!!.player.playWhenReady } }
                    compose.onNodeWithTag("player-aspect").performClick().assertTextContains("Picture: Zoom")
                    compose.onNodeWithTag("player-aspect").performClick().performClick().assertTextContains("Picture: Fit")
                    compose.onNodeWithTag("player-audio").performClick()
                    compose.onNodeWithTag("player-track-picker").assertIsDisplayed()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithTag("player-audio").assertIsFocused()
                    compose.onNodeWithTag("player-subtitles").performClick()
                    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-select-subtitle")).fetchSemanticsNodes().isNotEmpty() }
                    // Real remote events: performClick bypasses focus and hid the Shield bug.
                    compose.onNodeWithText("Back to player").assertIsFocused()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                    compose.onAllNodes(hasTestTag("addon-select-subtitle"))[0].assertIsFocused()
                    delayNextMedia.set(delayedSubtitleReload)
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    // Closing the picker means the choice was applied, not that
                    // Media3 has finished rebuilding the selected text track.
                    waitForSubtitle(host, "eng")
                    assertEquals("eng", host.activePlayback?.selectedSubtitle)
                    if (delayedSubtitleReload) assertEquals(1, delayedMediaRequests.get())
                    assertEquals(2, subtitleRequests.get())
                    compose.waitUntil(10_000) { compose.runOnIdle { host.activePlayback!!.player.currentCues.cues.any { it.text.toString().contains("Fixture subtitle") } } }
                    screenshot("addon-lab-playback-subtitles.png")
                    compose.onNodeWithTag("player-subtitles").assertIsFocused()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.onNodeWithText("Back to player").assertIsFocused()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithTag("player-subtitles").assertIsFocused()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.onNodeWithText("Back to player").assertIsFocused()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                    compose.onNodeWithText("Subtitles off").assertIsFocused()
                    delayNextMedia.set(delayedSubtitleReload)
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-player-subtitle-list")).fetchSemanticsNodes().isEmpty() }
                    waitForSubtitle(host, null)
                    if (delayedSubtitleReload) assertEquals(2, delayedMediaRequests.get())
                    compose.onNodeWithTag("player-subtitles").assertIsFocused()
                    offline.set(true)
                    compose.runOnIdle { host.activePlayback!!.player.stop(); host.activePlayback!!.player.prepare(); host.activePlayback!!.player.play() }
                    compose.waitUntil(25_000) { host.activePlayback?.failed == true }
                    offline.set(false)
                    val before = resolutions.get()
                    compose.onNodeWithTag("addon-player-retry").performClick()
                    waitReady()
                    assertTrue(resolutions.get() > before)
                    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("addon-player-retry")).fetchSemanticsNodes().isNotEmpty() }
                    assertFalse(compose.runOnIdle { host.activePlayback!!.player.isPlaying })
                    val beforeForegroundRetry = resolutions.get()
                    compose.onNodeWithTag("addon-player-retry").performClick()
                    waitReady()
                    assertTrue(resolutions.get() > beforeForegroundRetry)
                }
                leavePlayer()
                compose.waitUntil(15_000) { host.activePlayback == null && compose.onAllNodes(hasTestTag("addon-play-source")).fetchSemanticsNodes().isNotEmpty() }
                if (full) {
                    val before = resolutions.get()
                    assertTrue(before >= 2)
                    compose.waitUntil(10_000) { compose.onAllNodes(hasText("Continue watching") and hasTestTag("addon-find-sources")).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("addon-find-sources").performScrollTo().performClick()
                    waitReady()
                    assertTrue(compose.runOnIdle { host.activePlayback!!.player.currentPosition } >= 11_000)
                    // Return does not auto-start a second time. Start over actually plays at zero.
                    leavePlayer()
                    compose.waitUntil(15_000) { host.activePlayback == null && compose.onAllNodes(hasTestTag("addon-start-over")).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("addon-start-over").performScrollTo().performClick()
                    waitReady()
                    assertTrue(compose.runOnIdle { host.activePlayback!!.player.currentPosition } < 3_000)
                    val preferences = app.container.preferencesRepository
                    val previous = preferences.preferences.first()
                    try {
                        preferences.setAllowedGroups(profileId, com.streammate.tv.core.model.LibraryRoom.MOVIES, setOf("fixture-only"))
                        compose.waitUntil(10_000) { host.activePlayback == null }
                    } finally {
                        preferences.setAllowedGroups(profileId, com.streammate.tv.core.model.LibraryRoom.MOVIES,
                            previous.activeRestriction.movies)
                    }
                }
            } finally {
                compose.runOnIdle { host.activePlayback?.release() }
                host.progress.remove(profileId, identity)
                host.store.remove(profileId, installed.installationId)
                app.container.preferencesRepository.setPreferredLanguage(com.streammate.tv.app.PreferredLanguageSlot.PRIMARY_SUBTITLE, originalPreferences.preferredSubtitleLanguage)
                app.container.preferencesRepository.setPreferredLanguage(com.streammate.tv.app.PreferredLanguageSlot.PRIMARY_AUDIO, originalPreferences.preferredAudioLanguage)
            }
        }
    }
    private fun leavePlayer() {
        // Match VOD's two-stage Back: dismiss visible controls, then leave video.
        compose.onNodeWithTag("player-back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("player-bottom-controls")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("addon-player").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }
    private fun waitReady() { compose.waitUntil(25_000) { compose.onAllNodes(hasTestTag("addon-player-ready")).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitForSubtitle(host: AddonHost, language: String?) {
        compose.waitUntil(20_000) { compose.runOnIdle {
            host.activePlayback?.let { playback ->
                playback.ready && playback.selectedSubtitle == language &&
                    playback.player.currentTracks.isTypeSelected(C.TRACK_TYPE_TEXT) == (language != null) &&
                    (language != null || (C.TRACK_TYPE_TEXT in playback.player.trackSelectionParameters.disabledTrackTypes &&
                        playback.player.currentCues.cues.isEmpty()))
            } == true
        } }
        waitReady()
    }
    private fun screenshot(name: String) {
        AddonLabScreenshots.capture(compose.activity, name)
    }
    companion object {
        const val MANIFEST = """{"id":"test.lab.playback","version":"1","name":"Lab playback fixture","types":["lab.playback"],"resources":["catalog","meta","stream","subtitles"],"catalogs":[{"id":"fixture","type":"lab.playback","name":"Playback catalog"}]}"""
    }
}
