package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.feature.home.RailItem
import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Settings › Playlists › "Set up from a phone" on the emulator (spec 11 §11 "Device" and "UI"):
 * the dialog, a form posted to the real page from the device itself, and closing. Skipped when the
 * device has no home-network address.
 */
@RunWith(AndroidJUnit4::class)
class PhoneSetupTest {
    private val clearState = ClearStateRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(clearState).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    @Before
    fun needsANetwork() = assumeTrue("no site-local address", LocalAddress.current() != null)

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }

    private fun awaitFocus(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun openDialog(): String {
        await(RailItem.SETTINGS.tag)
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        await("source-add-phone")
        compose.onNodeWithTag("source-add-phone").performSemanticsAction(SemanticsActions.OnClick)
        await("phone-setup-url")
        awaitFocus("phone-setup-close")
        await("phone-setup-qr")
        return textOf("phone-setup-url")
    }

    /** Posts a form the way the page's script does, from the device to its own address. */
    private fun post(url: String, form: String): Int {
        val host = url.removePrefix("http://").substringBefore('/')
        val token = url.substringAfter('#')
        Socket(host.substringBefore(':'), host.substringAfter(':').toInt()).use { s ->
            s.soTimeout = 10_000
            val body = form.toByteArray(Charsets.UTF_8)
            s.getOutputStream().write(
                ("POST /submit HTTP/1.1\r\nHost: $host\r\nOrigin: http://$host\r\nAuthorization: Bearer $token\r\n" +
                    "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.size}\r\n\r\n").toByteArray(Charsets.UTF_8) + body,
            )
            return s.getInputStream().readBytes().toString(Charsets.UTF_8).substringAfter(' ').substringBefore(' ').toInt()
        }
    }

    @Test
    fun aSourceFromThePhoneIsSavedAndReported() {
        val url = openDialog()
        assertTrue(url, url.startsWith("http://") && url.contains("/#"))
        val status = post(url, "type=m3u&name=Living+room&m3u_url=http%3A%2F%2F192.0.2.10%2Flist.m3u&xmltv_url=http%3A%2F%2F192.0.2.10%2Fepg.xml")
        assertEquals(200, status)
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("phone-setup-received") }
        assertTrue(textOf("phone-setup-received").contains("Living room"))
        val saved = runBlocking { graph.data.sources.all() }.single()
        assertEquals("Living room", saved.name)
        val config = (runBlocking { graph.data.sources.load(saved.id) } as com.sohva.tv.core.model.error.Outcome.Ok).value!!
        assertEquals("http://192.0.2.10/epg.xml", config.secrets.xmlTvUrl)
        assertEquals("a wrong token is refused", 403, post(url.substringBefore('#') + "#" + "0".repeat(32), "type=keys&tmdb_token=t"))
    }

    @Test
    fun backClosesTheDialogStopsThePageAndReturnsFocus() {
        val url = openDialog()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("phone-setup-dialog") }
        awaitFocus("source-add-phone")
        compose.waitUntil(5_000) {
            try {
                post(url, "type=keys&tmdb_token=t")
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    @Test
    fun leavingSettingsStopsThePage() {
        val url = openDialog()
        compose.onNodeWithTag("phone-setup-close").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("phone-setup-dialog") }
        compose.onNodeWithTag("source-add-phone").performSemanticsAction(SemanticsActions.OnClick)
        await("phone-setup-url")
        val second = textOf("phone-setup-url")
        assertTrue("a new page has a new token", second != url)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("phone-setup-dialog") }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await(RailItem.SETTINGS.tag)
        compose.waitUntil(5_000) { graph.phone.server.state.value == com.sohva.tv.core.net.phone.PhoneState.Stopped }
    }
}
