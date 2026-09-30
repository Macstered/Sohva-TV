package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Seen while preparing the Play review: on a fresh install, a first playlist added while the app
 * runs imported its channels, but Live TV kept saying "No channels have been imported yet" until
 * the app was restarted. A Play reviewer does exactly this first. The guide must show the new
 * channels as soon as they are imported.
 */
@RunWith(AndroidJUnit4::class)
class FirstSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/list.m3u" -> MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl").body(
                        "#EXTM3U\n" +
                            "#EXTINF:-1 tvg-id=\"a\" group-title=\"Open channels\",Harbor Lights\n${server.url("/live/1.ts")}\n" +
                            "#EXTINF:-1 tvg-id=\"b\" group-title=\"Open channels\",Northern Line\n${server.url("/live/2.ts")}\n",
                    ).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
            server.start()
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun channelRows(): Int =
        graph.data.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM channel").use { it.moveToFirst(); it.getInt(0) }

    private fun addAndSync() {
        val config = SourceConfig(Source("first", "First playlist", SourceType.M3U), SourceSecrets(m3uUrl = server.url("/list.m3u").toString()))
        assertTrue(runBlocking { graph.data.sources.save(config) } is Outcome.Ok)
        graph.sync.scheduler.syncNow("first")
        compose.waitUntil(20_000) { channelRows() == 2 }
    }

    private fun awaitFirstRow(what: String) {
        try {
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("guide-row-0").assertIsFocused() }.isSuccess }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what; empty page shown: ${exists("guide-empty")}", e)
        }
    }

    /** The guide seen empty before any playlist, then again after the first one was added. */
    @Test
    fun theGuideSeenEmptyShowsTheFirstPlaylistNextTime() {
        compose.waitUntil(15_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-guide") }
        compose.waitUntil(10_000) { exists("guide-empty") }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) && !exists("screen-guide") }
        addAndSync()
        compose.focusRail(RailItem.LIVE_TV)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-guide") }
        awaitFirstRow("the guide seen empty before does not show the new playlist")
    }

    /** The guide open and empty while the first playlist imports: the channels appear without leaving it. */
    @Test
    fun theGuideOpenWhileTheFirstPlaylistImportsShowsIt() {
        compose.waitUntil(15_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("guide-empty") }
        addAndSync()
        try {
            compose.waitUntil(15_000) { exists("guide-row-0") }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("the open guide stays empty after the import; empty page shown: ${exists("guide-empty")}", e)
        }
    }

    @Test
    fun aFirstPlaylistAddedWhileTheAppRunsShowsInTheGuide() {
        compose.waitUntil(15_000) { exists(RailItem.LIVE_TV.tag) }
        // As Settings › Playlists › Save securely does: store the source, then sync it now.
        val config = SourceConfig(Source("first", "First playlist", SourceType.M3U), SourceSecrets(m3uUrl = server.url("/list.m3u").toString()))
        assertTrue(runBlocking { graph.data.sources.save(config) } is Outcome.Ok)
        graph.sync.scheduler.syncNow("first")
        compose.waitUntil(20_000) { channelRows() == 2 }
        compose.focusRail(RailItem.LIVE_TV)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-guide") }
        try {
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("guide-row-0").assertIsFocused() }.isSuccess }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("the guide does not show the new playlist; empty page shown: ${exists("guide-empty")}", e)
        }
    }
}
