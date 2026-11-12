package com.sohva.tv.app

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.feature.home.RailItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 72 §11 "UI": About in a development build, the legal screen reachable card by card and
 * Back to the licences button, Save diagnostics with no secret in the file, and a link the TV
 * cannot open shown as an address and a code.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAboutTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val file = File(instrumentation.targetContext.cacheDir, "diagnostics-test.txt")

    @Before
    fun startIntents() {
        file.delete()
        Intents.init()
    }

    @After
    fun stopIntents() {
        Intents.release()
        file.delete()
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(8_000) { focused(tag) }
        } catch (e: Throwable) {
            val now = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map {
                it.config.getOrNull(SemanticsProperties.TestTag) ?: it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text }?.take(60)
            }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun walkTo(tag: String, limit: Int = 40) {
        repeat(limit) {
            if (focused(tag)) return
            press(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        awaitFocus(tag)
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun openAbout(first: String) {
        compose.waitUntil(10_000) { exists(RailItem.SETTINGS.tag) }
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("settings-section-sources") }
        // About is the last section, below the list's visible part: walk to it as a viewer does.
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        walkTo("settings-section-about")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus(first)
    }

    /** ABOUT-11, -13…24: a debug build explains itself; every legal card is reachable; Back returns to the button. */
    @Test
    fun theLegalScreenIsReachableCardByCardAndBackReturnsToTheButton() {
        openAbout("settings-about-licenses")
        assertTrue(text("settings-update-status").startsWith("Public updates are disabled in development builds"))
        assertFalse(exists("settings-update-check"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("legal-back")
        // Down goes through every card, text-only ones too, to the last button and the palettes card.
        walkTo("legal-open-apache-license")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Colour palette licences") }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("settings-about-licenses")
    }

    /** ABOUT-26…29 and the exit criterion: the file is written, and holds no address, user name or password. */
    @Test
    fun savedDiagnosticsHoldNoSecret() {
        runBlocking {
            graph.data.sources.save(
                SourceConfig(Source("diag", "Diagnostics playlist", SourceType.XTREAM), SourceSecrets(xtreamBaseUrl = "http://192.0.2.44:8080", xtreamUsername = "viewer-name", xtreamPassword = "hunter2-secret")),
            )
        }
        graph.diagnostics.info("http", "GET http://192.0.2.44:8080/live/viewer-name/hunter2-secret/7.ts: HTTP 403")
        graph.diagnostics.info("metadata", "request api_key=not-a-real-key")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
        openAbout("settings-about-licenses")
        compose.onNodeWithTag("settings-diagnostics-save").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(20_000) { text("settings-diagnostics-status") == "Diagnostics saved. Share the file with the developer if asked." }
        val saved = file.readText()
        assertTrue(saved.startsWith("Sohva TV diagnostics\n"))
        assertTrue(saved.contains("  Diagnostics playlist: xtream, in use, imports both, priority 0"))
        assertTrue(saved.contains("Display: "))
        for (secret in listOf("viewer-name", "hunter2-secret", "not-a-real-key", ":8080/live")) assertFalse(secret, saved.contains(secret))
    }

    private fun seedSecrets() {
        runBlocking {
            graph.data.sources.save(
                SourceConfig(Source("diag", "Diagnostics playlist", SourceType.XTREAM), SourceSecrets(xtreamBaseUrl = "http://192.0.2.44:8080", xtreamUsername = "viewer-name", xtreamPassword = "hunter2-secret")),
            )
        }
        graph.diagnostics.info("http", "GET http://192.0.2.44:8080/live/viewer-name/hunter2-secret/7.ts: HTTP 403")
    }

    private fun assertNoSecret(text: String) {
        assertTrue(text.startsWith("Sohva TV diagnostics\n"))
        for (secret in listOf("viewer-name", "hunter2-secret", ":8080/live")) assertFalse(secret, text.contains(secret))
    }

    /** Rebuild: a TV without a document picker (Google TV on the Chromecast) saves to the Download folder. */
    @Test
    fun withoutAFilePickerTheFileGoesToTheDownloadFolder() {
        seedSecrets()
        graph.filePickerOverride = false
        val resolver = instrumentation.targetContext.contentResolver
        var name = ""
        try {
            openAbout("settings-about-licenses")
            compose.onNodeWithTag("settings-diagnostics-save").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(20_000) { text("settings-diagnostics-status").startsWith("This TV has no file picker, so the file was saved in the Download folder as ") }
            name = text("settings-diagnostics-status").substringAfter(" as ").removeSuffix(".")
            assertTrue(name, Regex("sohva-tv-diagnostics-\\d{8}-\\d{4}\\.txt").matches(name))
            val uri = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null,
            )!!.use { c ->
                assertTrue("the file is in Download", c.moveToFirst())
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
            }
            assertNoSecret(resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            graph.filePickerOverride = null
            if (name.isNotEmpty()) resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name))
        }
    }

    /**
     * Rebuild: the phone page hands the redacted file over only with the page's token and Origin;
     * the dialog says a phone has it, and closing it returns focus to "Send to phone".
     */
    @Test
    fun sendToPhoneHandsTheFileToTheTokenHolder() {
        seedSecrets()
        openAbout("settings-about-licenses")
        compose.onNodeWithTag("settings-diagnostics-phone").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { text("settings-diagnostics-phone-url").startsWith("http://") }
        awaitFocus("settings-diagnostics-phone-close")
        val url = text("settings-diagnostics-phone-url")
        val origin = url.substringBefore("/#")
        fun fetch(token: String): Pair<Int, String> {
            val connection = URL("$origin/submit").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Origin", origin)
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.outputStream.use { }
                val code = connection.responseCode
                val body = (if (code == 200) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                return code to body
            } finally {
                connection.disconnect()
            }
        }
        assertTrue(fetch("0".repeat(32)).first == 403)
        assertFalse(exists("settings-diagnostics-phone-sent"))
        val (code, body) = fetch(url.substringAfter('#'))
        assertTrue(code == 200)
        assertNoSecret(body)
        compose.waitUntil(5_000) { exists("settings-diagnostics-phone-sent") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("settings-diagnostics-phone-dialog") }
        awaitFocus("settings-diagnostics-phone")
        compose.waitUntil(5_000) { graph.phone.server.state.value == com.sohva.tv.core.net.phone.PhoneState.Stopped }
    }

    /** ABOUT-FR-23 (rebuild): a TV without a browser shows the address and a code; Close returns focus. */
    @Test
    fun aLinkTheTvCannotOpenIsShownAsAnAddress() {
        openAbout("settings-about-licenses")
        compose.onNodeWithTag("settings-translate-help").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("settings-translate-help")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        // An emulator image with a browser opens the page instead; this check needs one without.
        assumeTrue(exists("link-dialog"))
        assertTrue(text("link-dialog-url") == "https://github.com/Macstered/Sohva-TV")
        awaitFocus("link-dialog-close")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-translate-help")
    }
}
