package com.streammate.tv.lab

import android.content.ComponentName
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.streammate.tv.app.*
import com.streammate.tv.core.database.*
import com.google.common.util.concurrent.ListenableFuture
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
import java.util.concurrent.TimeUnit

/** Exercises the real app back stack and MediaSession with a synthetic local VOD source. */
@OptIn(UnstableApi::class)
class VodLabPlaybackCompletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun movieReturnsToTitleDetails() = exercise(movie = true)
    @Test fun movieReturnsToDetailsEvenWithEpisodeAutoplayDisabled() = exercise(movie = true, autoplay = false)
    @Test fun seriesStillAdvancesAndFinalEpisodeReturnsToDetails() = exercise(movie = false)
    @Test fun disabledEpisodeAutoplayReturnsToSeriesDetails() = exercise(movie = false, autoplay = false)

    private fun exercise(movie: Boolean, autoplay: Boolean = true): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.contains("generic"))
        val container = app.container
        container.awaitReady()
        val before = container.preferencesRepository.preferences.first()
        check(container.trakt.currentAccount(before.activeProfileId) == null)
        val database = StreamMateDatabase.create(app)
        val sourceId = "synthetic-completion-vod"
        val key = "vod:${if (movie) "movie" else "episode"}:$sourceId:first"
        val nextKey = "vod:episode:$sourceId:next"
        val media = InstrumentationRegistry.getInstrumentation().context.assets.open("playback/fixture.mp4").use { it.readBytes() }
        var controller: MediaController? = null
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("0.0.0.0"), 0)
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && !it.isLoopbackAddress }.hostAddress!!
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val offset = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                    return MockResponse().setHeader("Content-Type", "video/mp4").setHeader("Accept-Ranges", "bytes").apply {
                        if (offset > 0) setResponseCode(206).setHeader("Content-Range", "bytes $offset-${media.size - 1}/${media.size}")
                        setBody(Buffer().write(media, offset, media.size - offset))
                    }
                }
            }
            try {
                val stream = container.secretCipher.encrypt("http://$address:${server.port}/fixture.mp4")
                database.guideDao().upsertSourceState(IptvSourceStateEntity(sourceId, "Completion fixture", "M3U", true, 1, 0, 1))
                database.catalogueDao().upsertMovies(listOf(VodMovieEntity(sourceId = sourceId, snapshotId = "snapshot",
                    movieId = "first", name = "Completion movie", normalizedName = "completion movie", categoryName = "Fixtures",
                    posterUrl = null, encryptedStreamUrl = stream, year = null, rating = null, plot = null)))
                database.catalogueDao().upsertSeries(listOf(VodSeriesEntity(sourceId = sourceId, snapshotId = "snapshot",
                    seriesId = "series", name = "Completion series", normalizedName = "completion series", categoryName = "Fixtures",
                    posterUrl = null, backdropUrl = null, year = null, rating = null, plot = null)))
                database.catalogueDao().activateCatalogueSnapshot(sourceId, "snapshot", 2, 1)
                database.catalogueDao().replaceSeriesEpisodes(sourceId, "series", listOf(
                    VodEpisodeEntity(sourceId, "series", "first", 1, 1, "First episode", stream, null, 45),
                    VodEpisodeEntity(sourceId, "series", "next", 2, 1, "Next season", stream, null, 45),
                ))
                container.preferencesRepository.setAutoPlayNextEpisodeEnabled(autoplay)
                container.catalogueRepository.updateProgress(key, 15_000, 45_000)
                container.homeResume.retry()
                compose.runOnUiThread { compose.activity.setContent { StreamMateTheme { StreamMateApp(container) } } }
                compose.waitUntil(20_000) { exists("home-resume-vod:$key") }
                compose.onNodeWithTag("home-resume-vod:$key").performClick()
                lateinit var future: ListenableFuture<MediaController>
                compose.runOnIdle {
                    future = MediaController.Builder(app, SessionToken(app, ComponentName(app, StreamMatePlaybackService::class.java))).buildAsync()
                }
                val player = future.get(10, TimeUnit.SECONDS).also { controller = it }
                compose.waitUntil(15_000) { compose.runOnIdle { player.isPlaying && player.duration > 0 && player.currentMediaItem?.mediaId == key } }
                compose.runOnIdle { player.seekTo(player.duration - 250); player.play() }
                if (!movie && autoplay) {
                    compose.waitUntil(15_000) { compose.runOnIdle { player.isPlaying && player.currentMediaItem?.mediaId == nextKey } }
                    assertTrue(compose.runOnIdle { player.currentPosition < 5_000 })
                    compose.runOnIdle { player.seekTo(player.duration - 250); player.play() }
                }
                val detailsTag = if (movie) "movie-details-title" else "series-details-title"
                compose.waitUntil(15_000) { exists(detailsTag) }
                compose.onNodeWithTag(detailsTag).assertTextEquals(if (movie) "Completion movie" else "Completion series")
                compose.waitUntil(5_000) { compose.runOnIdle { !player.isPlaying } }
                assertTrue(checkNotNull(container.catalogueRepository.progress(key)).completed)
            } finally {
                compose.runOnUiThread { compose.activity.setContent { Text("Fixture finished") }; controller?.release() }
                compose.waitForIdle()
                container.guideRepository.clearSource(sourceId)
                database.close()
                container.preferencesRepository.setAutoPlayNextEpisodeEnabled(before.autoPlayNextEpisodeEnabled)
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
