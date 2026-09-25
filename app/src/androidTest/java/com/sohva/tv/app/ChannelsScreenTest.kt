package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 21 §11 "Instrumentation": channel management opened from the guide's options. */
@RunWith(AndroidJUnit4::class)
class ChannelsScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            GuideFixture.seed(graph, groups = 1, perGroup = 6)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    /** OK on a control through its click action, as the M1 settings tests do. */
    private fun click(tag: String) {
        compose.waitUntil(10_000) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, value: String) {
        click(tag)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
    }

    private fun row(i: Int) = "channels-row-fixture-0:c$i"

    private fun q(sql: String): List<String> = graph.data.database.openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    private fun openChannels() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_MENU)
        click("guide-options-channels")
        compose.waitUntil(10_000) { exists("screen-channels") }
        // Focus on entry: the first channel (spec 21 §3).
        awaitFocus(row(0))
    }

    @Test
    fun opensOnTheFirstChannelWithItsControlsAndLockNeedsAPin() {
        openChannels()
        assertEquals("6 channels", text("channels-count"))
        for (tag in listOf("channels-name", "channels-sort", "channels-change-epg", "channels-save", "channels-hide")) assertTrue(tag, exists(tag))
        compose.onNodeWithTag("channels-lock").assertIsNotEnabled()
    }

    @Test
    fun hidingPersistsAndShowHiddenOffTakesItOutOfTheList() {
        openChannels()
        click("channels-hide")
        compose.waitUntil(5_000) { q("SELECT visible FROM channel WHERE key = 'fixture-0:c0'") == listOf("0") }
        assertEquals(listOf("1"), q("SELECT hidden FROM channel_custom WHERE channel_key = 'fixture-0:c0'"))
        click("channels-show-hidden")
        compose.waitUntil(5_000) { !exists(row(0)) }
        assertEquals("5 channels", text("channels-count"))
        assertFalse(runBlocking { graph.data.preferences.editorsShowHidden.first() })
        click("channels-show-hidden")
        compose.waitUntil(5_000) { exists(row(0)) }
        assertTrue(runBlocking { graph.data.preferences.editorsShowHidden.first() })
    }

    @Test
    fun savingALogoAddressAndANumberPersistsBothAndTheGuideShowsTheName() {
        openChannels()
        type("channels-logo", "https://provider.example/mine.png")
        type("channels-number", "42")
        type("channels-name", "Mine")
        click("channels-save")
        compose.waitUntil(5_000) { q("SELECT custom_logo_url, custom_number, custom_name FROM channel_custom").isNotEmpty() }
        assertEquals(listOf("https://provider.example/mine.png|42|Mine"), q("SELECT custom_logo_url, custom_number, custom_name FROM channel_custom"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("42") && compose.onAllNodesWithTextExists("Changes saved") }
        // Back to the guide: the row reads the new name (GUIDE-FR-37).
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-guide") }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Mine", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun switchingChannelKeepsTheChosenList() {
        openChannels()
        type("channels-new-list", "Evening")
        click("channels-create-list")
        type("channels-new-list", "Weekend")
        click("channels-create-list")
        compose.waitUntil(5_000) { exists("channels-list-choice") && text("channels-list-choice") == "List: Evening" }
        click("channels-list-choice")
        compose.waitUntil(5_000) { text("channels-list-choice") == "List: Weekend" }
        click("channels-list-membership")
        compose.waitUntil(5_000) { q("SELECT COUNT(*) FROM channel_list_member") == listOf("1") }
        // Another channel starts on the list last shown (CHAN-FR-52 fix).
        compose.onNodeWithTag(row(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(row(1))
        compose.waitUntil(5_000) { text("channels-list-choice") == "List: Weekend" }
        assertEquals("Add to list", text("channels-list-membership"))
    }

    @Test
    fun movingUpPlacesTheChannelBeforeItsNeighbour() {
        openChannels()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        awaitFocus(row(2))
        click("channels-up")
        compose.waitUntil(5_000) {
            q("SELECT key FROM channel WHERE source_id = 'fixture-0' ORDER BY display_rank, id").take(3) ==
                listOf("fixture-0:c0", "fixture-0:c2", "fixture-0:c1")
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Channel order updated") }
    }

    /** Spec 21 §11: a phone logo post for another channel is refused; one for the open channel is stored and shown. */
    @Test
    fun aLogoFromThePhoneIsStoredForTheOpenChannelOnly() {
        openChannels()
        click("channels-logo-phone")
        compose.waitUntil(10_000) { exists("channels-logo-url") }
        val url = text("channels-logo-url")
        val bitmap = android.graphics.Bitmap.createBitmap(600, 300, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val png = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val image = "data:image/png;base64," + android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)
        assertEquals(403, postLogo(url, "fixture-0:c1", image))
        assertEquals(200, postLogo(url, "fixture-0:c0", image))
        compose.waitUntil(10_000) { !exists("channels-logo-dialog") && compose.onAllNodesWithTextExists("Logo received from the phone") }
        val stored = q("SELECT custom_logo_url FROM channel_custom WHERE channel_key = 'fixture-0:c0'").single()
        assertTrue(stored, stored.startsWith("file:") && stored.endsWith(".png"))
        val file = java.io.File(java.net.URI(stored))
        assertTrue(file.isFile)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        assertEquals("shrunk to 256 px on its longer side", 256, bounds.outWidth)
        // Reset deletes the file with the edit.
        click("channels-reset")
        compose.waitUntil(5_000) { !file.exists() }
    }

    /** Posts the logo form as the phone page's script does; returns the HTTP status. */
    private fun postLogo(pageUrl: String, channel: String, image: String): Int {
        val token = pageUrl.substringAfter('#')
        val connection = java.net.URL(pageUrl.substringBefore('#').trimEnd('/') + "/submit").openConnection() as java.net.HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "type=logo&channel=" + java.net.URLEncoder.encode(channel, "UTF-8") + "&image=" + java.net.URLEncoder.encode(image, "UTF-8")
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /** Beta 23's "Remove a source" leaves nothing behind (CHAN-30), checked from the store's side. */
    @Test
    fun anEditOfARemovedSourceIsGone() {
        openChannels()
        click("channels-hide")
        compose.waitUntil(5_000) { q("SELECT COUNT(*) FROM channel_custom") == listOf("1") }
        runBlocking { graph.sync.runner.remove("fixture-0") }
        assertEquals(listOf("0"), q("SELECT COUNT(*) FROM channel_custom"))
        assertTrue(compose.onAllNodes(hasTestTag("screen-channels")).fetchSemanticsNodes().isNotEmpty())
    }
}
