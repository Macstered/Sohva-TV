package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.addons.AddonEndpoint
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AddonLabBrowsingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun catalogDetailsReturnCacheAndManagementWork(): Unit = runBlocking {
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val profileId = app.container.preferencesRepository.preferences.first().activeProfileId
        MockWebServer().use { server ->
            val catalogs = AtomicInteger()
            val streams = AtomicInteger()
            val subtitles = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/catalog/") -> {
                        catalogs.incrementAndGet()
                        MockResponse().setBody(PAGE).setHeader("Cache-Control", "max-age=300")
                    }
                    request.path!!.contains("/meta/") -> MockResponse().setBody(META)
                    request.path!!.contains("/stream/") -> {
                        streams.incrementAndGet()
                        MockResponse().setBody("""{"streams":[{"name":"Synthetic HTTP source","url":"https://example.invalid/video"}]}""")
                    }
                    request.path!!.contains("/subtitles/") -> {
                        subtitles.incrementAndGet()
                        MockResponse().setBody("""{"subtitles":[{"id":"sub1","lang":"eng","url":"https://example.invalid/sub.srt"}]}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val installed = host.store.install(profileId, AddonEndpoint.parse(server.url("/Synthetic/manifest.json").toString(), true), MANIFEST)
            try {
                compose.runOnUiThread { compose.activity.setContent {
                    com.streammate.tv.app.StreamMateTheme { DiscoverAddonFeature(startInManager = true).Screen(app.container, {}) }
                } }
                compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("addon-manager-list")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("addon-manager-list").performScrollToNode(androidx.compose.ui.test.hasText("Lab browsing fixture"))
                compose.onNodeWithTag("addon-browse-${installed.installationId}").performScrollTo().performClick()
                compose.onNodeWithText("Fixture catalog · lab.fixture").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Fixture series")).fetchSemanticsNodes().isNotEmpty() }
                screenshot("addon-lab-catalog.png")
                compose.onNodeWithText("Fixture series")
                    .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                    .performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Synthetic description")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Synthetic description").assertIsDisplayed()
                screenshot("addon-lab-details.png")
                assertEquals(0, streams.get())
                assertEquals(0, subtitles.get())
                compose.onNodeWithText("S1 · E1 · Pilot").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("addon-source-item")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Synthetic HTTP source").assertIsDisplayed()
                assertEquals(1, streams.get())
                assertEquals(0, subtitles.get())
                screenshot("addon-lab-sources.png")
                assertEquals(0, subtitles.get()) // Subtitle lookups belong to playback, not details.
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithText("Synthetic description").assertIsDisplayed()
                compose.onNodeWithText("S1 · E1 · Pilot").assertIsFocused()
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithText("Fixture series").assertIsDisplayed()
                compose.onNodeWithText("Fixture series").assertIsFocused()
                assertEquals(1, catalogs.get())
                compose.onNodeWithText("Back to addons").performClick()
                compose.onNodeWithText("Fixture catalog · lab.fixture").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Fixture series")).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, catalogs.get())
                compose.onNodeWithText("Back to addons").performClick()
                compose.onNodeWithText("Back to addons").performClick()
                compose.onNodeWithTag("addon-manager-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("addon-toggle-${installed.installationId}"))
                compose.onNodeWithTag("addon-toggle-${installed.installationId}").performScrollTo().performClick()
                compose.waitUntil(10_000) { runBlocking { host.store.list(profileId).first { it.installationId == installed.installationId }.enabled.not() } }
                assertFalse(host.store.list(profileId).first { it.installationId == installed.installationId }.enabled)
                compose.onNodeWithTag("addon-remove-${installed.installationId}").performScrollTo().performClick()
                compose.onNodeWithTag("addon-confirm-remove-${installed.installationId}").assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { runBlocking { host.store.list(profileId).none { it.installationId == installed.installationId } } }
            } finally {
                if (host.store.list(profileId).any { it.installationId == installed.installationId }) host.store.remove(profileId, installed.installationId)
            }
        }
    }
    private fun screenshot(name: String) {
        AddonLabScreenshots.capture(compose.activity, name)
    }
    private companion object {
        const val MANIFEST = """{"id":"test.lab.browse","version":"1","name":"Lab browsing fixture","types":["lab.fixture"],"resources":["catalog","meta","stream","subtitles"],"catalogs":[{"id":"fixture","type":"lab.fixture","name":"Fixture catalog","pageSize":100,"extra":[{"name":"skip"}]}]}"""
        const val PAGE = """{"metas":[{"id":"ttFixture","type":"lab.fixture","name":"Fixture series"}]}"""
        const val META = """{"meta":{"id":"ttFixture","type":"lab.fixture","name":"Fixture series","description":"Synthetic description","videos":[{"id":"opaque:episode:1","title":"Pilot","season":1,"episode":1}]}}"""
    }
}
