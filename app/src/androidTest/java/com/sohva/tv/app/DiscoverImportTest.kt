package com.sohva.tv.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.feature.home.RailItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 50 §11 "Instrumented" for import: a list sent from the phone page replaces the pending list
 * and returns to Import; Preview checks each line in order (ready, duplicate, bad, already
 * installed, needs configuration) and Install adds only the selected new one.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()

    private fun manifest(id: String, configure: Boolean = false) = """{"id":"org.example.$id","version":"1.0.0","name":"Provider $id","types":["movie"],
        "resources":["catalog"],"catalogs":[{"type":"movie","id":"c","name":"Titles"}]${if (configure) ""","behaviorHints":{"configurationRequired":true}""" else ""}}"""

    private val seed = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val body = when (path) {
                        "/a/manifest.json" -> manifest("a")
                        "/b/manifest.json" -> manifest("b")
                        "/c/manifest.json" -> manifest("c", configure = true)
                        else -> """{"metas":[]}"""
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            server.start()
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, url("b")) }
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    @Before
    fun needsANetwork() = assumeTrue("no site-local address", LocalAddress.current() != null)

    private fun url(name: String) = "http://127.0.0.1:${server.port}/$name/manifest.json"

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun click(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    /** What the phone page's script does: a plain-text POST with the token and the page's origin. */
    private fun sendFromPhone(pageUrl: String, list: String): Int {
        val origin = pageUrl.substringBefore("/#")
        val token = pageUrl.substringAfter('#')
        val connection = URL("$origin/submit").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Origin", origin)
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.outputStream.use { it.write(list.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun aListFromThePhoneIsPreviewedAndOnlyTheNewAddonIsInstalled() {
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
        click("discover-rail-setup")
        click("discover-setup-add")
        click("discover-setup-import")
        click("discover-import-method-phone")
        click("discover-import-phone")
        compose.waitUntil(10_000) { exists("discover-import-phone-url") }
        val page = text("discover-import-phone-url")
        val list = listOf(url("a"), url("a"), "not a url", url("b"), url("c")).joinToString("\n")
        val code = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Int> { sendFromPhone(page, list) }.get()
        assertEquals(200, code)
        // Back on Import with the list queued; the phone session is over.
        compose.waitUntil(10_000) { exists("discover-import-preview") && text("discover-import-count").startsWith("5 ") }
        click("discover-import-preview")
        compose.waitUntil(15_000) { exists("discover-import-install") && text("discover-import-count").startsWith("5 ") }
        // FR-40: each line in order; only the first is new and ready.
        compose.waitUntil(15_000) { text("discover-import-row-5").contains("Configure on the provider") }
        assertTrue(text("discover-import-row-1"), text("discover-import-row-1").contains("Ready to install"))
        assertTrue(text("discover-import-row-2"), text("discover-import-row-2").contains("Duplicate in this list"))
        assertTrue(text("discover-import-row-3"), text("discover-import-row-3").contains("Use a configured HTTPS or stremio URL"))
        assertTrue(text("discover-import-row-4"), text("discover-import-row-4").contains("Already installed"))
        click("discover-import-install")
        compose.waitUntil(10_000) { exists("discover-import-another") && !exists("discover-import-install") }
        val names = runBlocking { host.manager.list(graph.data.profiles.activeId).map { it.manifest.name } }
        assertEquals(listOf("Provider b", "Provider a"), names)
    }
}
