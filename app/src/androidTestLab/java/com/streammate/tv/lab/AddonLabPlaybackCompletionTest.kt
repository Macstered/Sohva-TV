package com.streammate.tv.lab

import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.addons.*
import com.streammate.tv.app.*
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
import java.util.concurrent.CopyOnWriteArrayList

/** Real end-of-stream transitions using synthetic media on the disposable Lab emulator. */
@OptIn(UnstableApi::class)
class AddonLabPlaybackCompletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun movieReturnsToDetailsAndKeepsCompletedProgress() = exercise(movie = true)
    @Test fun episodeResolvesNextStreamAndStartsFromBeginning() = exercise()
    @Test fun nextEpisodeCanCrossSeasonBoundary() = exercise(crossSeason = true)
    @Test fun disabledAutoplayReturnsToEpisodeDetails() = exercise(autoplay = false)
    @Test fun lastEpisodeReturnsToItsDetails() = exercise(last = true)
    @Test fun missingNextStreamLeavesUsableNextEpisodeDetails() = exercise(missingStream = true)
    @Test fun discoverHistoryUsesTheSameEpisodeCompletionFlow() = exercise(history = true)
    @Test fun pausingDoesNotAdvanceOrLeavePlayback() = exercise(pauseOnly = true)

    private fun exercise(movie: Boolean = false, autoplay: Boolean = true, crossSeason: Boolean = false,
        last: Boolean = false, missingStream: Boolean = false, history: Boolean = false, pauseOnly: Boolean = false): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.contains("generic"))
        app.container.awaitReady()
        val preferences = app.container.preferencesRepository
        val before = preferences.preferences.first()
        val profile = before.activeProfileId
        check(app.container.trakt.currentAccount(profile) == null)
        val host = AddonHost.get(app, app.container)
        val media = InstrumentationRegistry.getInstrumentation().context.assets.open("playback/fixture-timing.mkv").use { it.readBytes() }
        val type = if (movie) "completion.movie" else "completion.series"
        val titleKey = AddonMediaKey(type, "title")
        val firstKey = AddonMediaKey(type, if (movie) "title" else "opaque-first")
        val nextKey = AddonMediaKey(type, "opaque-next")
        val sourceRequests = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            val base = "http://$address:${server.port}"
            val nextSeason = if (crossSeason) 2 else 1
            val nextNumber = if (crossSeason) 1 else 2
            val videos = if (movie) "" else """, "videos":[
                ${if (last) "" else """{"id":"opaque-next","title":"Next episode","season":$nextSeason,"episode":$nextNumber},"""}
                {"id":"opaque-first","title":"First episode","season":1,"episode":1}]"""
            val metadata = """{"meta":{"id":"title","type":"$type","name":"Completion fixture"
                ${if (movie) """, "behaviorHints":{"defaultVideoId":"title"}""" else videos}}}"""
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/meta/") -> MockResponse().setBody(metadata)
                        path.startsWith("/stream/") -> {
                            sourceRequests += path
                            MockResponse().setBody(if (missingStream && path.contains("opaque-next")) """{"streams":[]}"""
                                else """{"streams":[{"name":"Completion fixture","url":"$base/fixture.mkv"}]}""")
                        }
                        path == "/fixture.mkv" -> {
                            val offset = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                            MockResponse().setHeader("Content-Type", "video/x-matroska").setHeader("Accept-Ranges", "bytes").apply {
                                if (offset > 0) setResponseCode(206).setHeader("Content-Range", "bytes $offset-${media.size - 1}/${media.size}")
                                setBody(Buffer().write(media, offset, media.size - offset))
                            }
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val installed = host.store.install(profile, AddonEndpoint.parse("$base/manifest.json", true),
                """{"id":"synthetic.completion","name":"Completion fixture","version":"1","types":["$type"],"resources":["meta","stream"],"catalogs":[]}""")
            val identity = AddonWatchIdentity(installed.installationId, titleKey, firstKey)
            val nextIdentity = AddonWatchIdentity(installed.installationId, titleKey, nextKey)
            try {
                preferences.setAutoPlayNextEpisodeEnabled(autoplay)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, "fi")
                // Automatic advancement must not resume an older viewing of the next episode.
                host.progress.save(host.progress.begin(profile, nextIdentity, "Next episode"), 1, 15_000, 45_000)
                val preview = AddonMedia(titleKey, "Completion fixture", null, "poster", null, null, null, emptyList(),
                    defaultVideoId = if (movie) firstKey.id else null)
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                    if (history) AddonHistoryDetailsScreen(host, profile,
                        AddonWatchProgress(identity, "First episode", 0, 45_000, 1, false), {}, Modifier.fillMaxSize())
                    else AddonDetailsScreen(host, profile, installed, preview, {}, Modifier.fillMaxSize(), initialVideo = if (movie) null else firstKey)
                } } }
                compose.waitUntil(15_000) { exists("addon-play-source") }
                compose.onAllNodesWithTag("addon-play-source").onFirst().performClick()
                compose.waitUntil(15_000) { exists("addon-player-ready") }
                val first = checkNotNull(host.activePlayback)
                compose.waitUntil(10_000) { compose.runOnIdle { first.player.isPlaying && first.player.duration > 0 } }
                if (pauseOnly) {
                    compose.runOnIdle { first.player.pause() }
                    compose.waitForIdle()
                    compose.onNodeWithTag("addon-player").assertIsDisplayed()
                    assertSame(first, host.activePlayback)
                    assertFalse(sourceRequests.any { it.contains("opaque-next") })
                    return@use
                }
                compose.runOnIdle { first.player.seekTo(first.player.duration - 250); first.player.play() }
                if (!movie && autoplay && !last && !missingStream) {
                    compose.waitUntil(20_000) { compose.runOnIdle {
                        host.activePlayback?.let { it !== first && it.selection.video == nextKey && it.player.isPlaying } == true
                    } }
                    assertTrue(compose.runOnIdle { checkNotNull(host.activePlayback).player.currentPosition < 5_000 })
                    assertTrue(sourceRequests.any { it.contains("opaque-next") })
                } else {
                    compose.waitUntil(15_000) { exists(if (movie) "addon-movie-details" else "addon-episode-details") && !exists("addon-player") }
                    assertNull(host.activePlayback)
                    if (missingStream) {
                        compose.waitUntil(10_000) { exists("addon-autoplay-unavailable") }
                        compose.onNodeWithTag("addon-details-loaded").assertTextEquals("Next episode")
                        compose.onNodeWithTag("addon-find-sources").assertIsEnabled()
                    } else assertFalse(sourceRequests.any { it.contains("opaque-next") })
                }
                host.pendingProgressWrite?.join()
                assertTrue(checkNotNull(host.progress.get(profile, identity)).completed)
            } finally {
                compose.runOnUiThread { compose.activity.setContent { Text("Fixture finished") } }
                compose.waitForIdle()
                compose.runOnIdle { host.activePlayback?.release() }
                host.pendingProgressWrite?.join()
                host.progress.remove(profile, identity)
                host.progress.remove(profile, nextIdentity)
                host.store.remove(profile, installed.installationId)
                preferences.setAutoPlayNextEpisodeEnabled(before.autoPlayNextEpisodeEnabled)
                preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, before.preferredSubtitleLanguage)
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
