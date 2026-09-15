package com.streammate.tv.addons

import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in synthetic media only: no Lab state, real Trakt account or physical device. */
@OptIn(UnstableApi::class)
class AddonStartupPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun primaryEmbeddedStartsWithoutSubtitleRequests() = exercise("embedded")
    @Test fun fastPrimaryDoesNotWaitForASlowProvider() = exercise("fast")
    @Test fun slowProvidersFallBackAndLateResultsDoNotRestartPlayback() = exercise("timeout")
    @Test fun cancelDuringLookupNeverStartsPlayback() = exercise("cancel")
    @Test fun backgroundDuringLookupNeverStartsPlayback() = exercise("background")

    private fun exercise(mode: String): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("syntheticPlayback") == "true")
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.debug")
        check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.contains("generic"))
        app.container.awaitReady()
        val prefs = app.container.preferencesRepository
        val before = prefs.preferences.first()
        val profile = before.activeProfileId
        check(app.container.trakt.currentAccount(profile) == null)
        val host = AddonHost.get(app, app.container)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val media = assets.open("playback/fixture-${if (mode == "embedded") "embedded" else "secondary"}.mkv").use { it.readBytes() }
        val subtitleRequests = AtomicInteger()
        val downloads = AtomicInteger()
        val slowStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val installed = mutableListOf<String>()
        var identity: AddonWatchIdentity? = null
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            val base = "http://$address:${server.port}"
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.startsWith("/media") -> {
                        val offset = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        MockResponse().setHeader("Content-Type", "video/x-matroska").setHeader("Accept-Ranges", "bytes").apply {
                            if (offset > 0) setResponseCode(206).setHeader("Content-Range", "bytes $offset-${media.size - 1}/${media.size}")
                            setBody(Buffer().write(media, offset, media.size - offset))
                        }
                    }
                    request.path!!.contains("/subtitles/") -> {
                        subtitleRequests.incrementAndGet()
                        if (request.path!!.startsWith("/slow/")) { slowStarted.countDown(); releaseSlow.await(15, TimeUnit.SECONDS) }
                        else check(slowStarted.await(5, TimeUnit.SECONDS))
                        MockResponse().setBody("""{"subtitles":[{"id":"synthetic","lang":"fin","url":"$base/subtitle.srt"}]}""")
                    }
                    request.path == "/subtitle.srt" -> {
                        downloads.incrementAndGet()
                        MockResponse().setBody("1\n00:00:00,000 --> 00:00:44,000\nFixture remote primary\n")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            try {
                val origins = if (mode == "fast") listOf("slow", "fast") else listOf("slow")
                val originsInstalled = origins.map { name ->
                    host.store.install(profile, AddonEndpoint.parse("$base/$name/manifest.json", true),
                        """{"id":"synthetic.$name","version":"1","name":"Synthetic $name","types":["perf.subtitle"],"resources":["stream","subtitles"],"catalogs":[]}""")
                        .also { installed += it.installationId }
                }
                val origin = originsInstalled.last()
                val video = AddonMediaKey("perf.subtitle", "fixture")
                val watch = AddonWatchIdentity(origin.installationId, video, video).also { identity = it }
                val stream = AddonSourceParser.streams("""{"streams":[{"url":"$base/media.mkv"}]}""").single()
                val selection = AddonPlaybackSelection(origin.installationId, origin.revision, video, stream)
                prefs.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, "fi")
                prefs.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, "en")
                prefs.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, "fi")
                var visible by mutableStateOf(true)
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    if (visible) AddonPlayerScreen(host, profile, watch, "Synthetic startup", selection, false, { visible = false }, androidx.compose.ui.Modifier)
                    else Text("Closed")
                } } }
                if (mode in setOf("cancel", "background")) {
                    compose.waitUntil(10_000) { subtitleRequests.get() > 0 }
                    val playback = host.activePlayback!!
                    if (mode == "cancel") compose.onNodeWithTag("addon-loading-cancel").performClick()
                    else compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                    releaseSlow.countDown()
                    if (mode == "background") compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.waitForIdle()
                    assertFalse(compose.runOnIdle { "playing" in playback.startupMilestones })
                    assertEquals(0, downloads.get())
                } else {
                    compose.waitUntil(12_000) { compose.runOnIdle { host.activePlayback?.player?.isPlaying == true } }
                    val playback = host.activePlayback!!
                    val expected = when (mode) { "embedded" -> "fi"; "fast" -> "fin"; else -> "en" }
                    compose.waitUntil(5000) { compose.runOnIdle { playback.selectedSubtitle == expected } }
                    val times = compose.runOnIdle { playback.startupMilestones.toMap() }
                    assertTrue(times.keys.containsAll(listOf("stream-ready", "subtitles-ready", "first-frame", "playing")))
                    val subtitleWait = times.getValue("subtitles-ready") - times.getValue("stream-ready")
                    assertTrue("$mode startup timings: $times", subtitleWait < AUTOMATIC_SUBTITLE_BUDGET_MILLIS + 2000)
                    if (mode == "fast") assertTrue("Ready provider waited for slow one: $times", subtitleWait < 4000)
                    if (mode == "embedded") assertEquals(0, subtitleRequests.get())
                    releaseSlow.countDown()
                    Thread.sleep(400)
                    assertEquals(expected, compose.runOnIdle { playback.selectedSubtitle })
                    assertEquals(if (mode == "fast") 1 else 0, downloads.get())
                    println("Synthetic subtitle $mode: $times")
                }
            } finally {
                releaseSlow.countDown()
                compose.runOnIdle { host.activePlayback?.release() }
                host.pendingProgressWrite?.join()
                identity?.let { host.progress.remove(profile, it) }
                installed.forEach { host.store.remove(profile, it) }
                prefs.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, before.preferredSubtitleLanguage)
                prefs.setPreferredLanguage(PreferredLanguageSlot.SECONDARY_SUBTITLE, before.secondarySubtitleLanguage)
                prefs.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_AUDIO, before.preferredAudioLanguage)
            }
        }
    }
}
