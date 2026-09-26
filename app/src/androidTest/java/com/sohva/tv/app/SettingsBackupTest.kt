package com.sohva.tv.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.feature.home.RailItem
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 71 §11 "UI" and "Integration": the password rule, a save and restore through the (answered)
 * document pickers with the confirmation that names a removed source, a file beta 23 wrote, and a
 * wrong password that changes nothing. The status line is in the Backup section itself.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBackupTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SohvaApplication
    private val graph get() = app.graph
    private val file = File(instrumentation.targetContext.cacheDir, "backup-test.smbak")

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
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun status(): String = runCatching {
        compose.onNodeWithTag("settings-backup-status").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun awaitStatus(text: String, timeout: Long = 60_000) {
        try {
            compose.waitUntil(timeout) { status() == text }
        } catch (e: Throwable) {
            throw AssertionError("status '${status()}', expected '$text'", e)
        }
    }

    /** Settings opens on Playlists; Left to the rail, down to Backup, OK: focus on the password (SET-FR-14). */
    private fun openBackup() {
        compose.waitUntil(10_000) { exists(RailItem.SETTINGS.tag) }
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("settings-section-sources") }
        compose.onNodeWithTag("settings-section-backup").performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("settings-backup-passphrase")
    }

    private fun typePassword(value: String) {
        compose.onNodeWithTag("settings-backup-passphrase").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
    }

    /** The pickers answer with [file] (spec 71 §5: the system UI; nothing of the app over it). */
    private fun answerPickers() {
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)
    }

    private fun config(id: String, name: String) = SourceConfig(
        Source(id, name, SourceType.M3U),
        SourceSecrets(m3uUrl = "https://provider.example/$id.m3u"),
    )

    private fun sourceNames(): List<String> = runBlocking { graph.data.sources.all().map { it.name }.sorted() }

    /** Beta 23's fixture payload sealed as beta 23 seals it, with a non-ASCII password (spec 71 §10 Q4). */
    private fun writeBeta23File() {
        val payload = instrumentation.context.assets.open("backup/beta23-format2.json").use { it.readBytes() }
        file.writeBytes(Beta23Envelope.seal(payload, FIXTURE_PHRASE.toCharArray()))
    }

    /** BACKUP-FR-02: seven characters leave both buttons off, eight turn them on. */
    @Test
    fun theButtonsNeedEightCharacters() {
        openBackup()
        compose.onNodeWithTag("settings-backup-export").assertIsNotEnabled()
        typePassword("1234567")
        compose.onNodeWithTag("settings-backup-export").assertIsNotEnabled()
        compose.onNodeWithTag("settings-backup-restore").assertIsNotEnabled()
        typePassword("12345678")
        compose.onNodeWithTag("settings-backup-export").assertIsEnabled()
        compose.onNodeWithTag("settings-backup-restore").assertIsEnabled()
    }

    /** BACKUP-FR-07, -18, -21 and §8: what was saved comes back; a source added since is named, then removed. */
    @Test
    fun aSavedBackupRestoresAfterAskingAboutTheSourceItRemoves() {
        runBlocking {
            graph.data.sources.save(config("keep", "Kept playlist"))
            graph.data.preferences.setTheme(ColorThemeId.entries.last())
        }
        answerPickers()
        openBackup()
        typePassword("fictional-pass-1")
        compose.onNodeWithTag("settings-backup-export").performSemanticsAction(SemanticsActions.OnClick)
        awaitStatus("Encrypted backup saved")
        assertTrue("file written", file.length() > 52)
        // Changed since the save: another source and another theme.
        runBlocking {
            graph.data.sources.save(config("extra", "Extra playlist"))
            graph.data.preferences.setTheme(ColorThemeId.entries.first())
        }
        compose.waitUntil(5_000) { sourceNames().size == 2 }
        typePassword("fictional-pass-1")
        compose.onNodeWithTag("settings-backup-restore").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(30_000) { exists("settings-backup-confirm") }
        assertTrue(compose.onAllNodesWithTextExists("Extra playlist", substring = true))
        // Focus opens on Cancel; Confirm goes on with the restore.
        awaitFocus("settings-backup-confirm-cancel")
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("settings-backup-confirm-yes")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitStatus("Backup restored. Refresh channels and the programme guide.")
        assertEquals(listOf("Kept playlist"), sourceNames())
        assertEquals(ColorThemeId.entries.last(), runBlocking { graph.data.preferences.theme.first() })
        // Focus went back to Restore before the question closed (AGENTS.md §5 rule 2).
        awaitFocus("settings-backup-restore")
        assertTrue(!runBlocking { graph.data.backup.unfinished() })
    }

    /** Spec 71 §11: a file beta 23 wrote (non-ASCII password) restores its sources, profiles, PIN and edits. */
    @Test
    fun aBeta23FileRestores() {
        writeBeta23File()
        answerPickers()
        openBackup()
        typePassword(FIXTURE_PHRASE)
        compose.onNodeWithTag("settings-backup-restore").performSemanticsAction(SemanticsActions.OnClick)
        awaitStatus("Backup restored. Refresh channels and the programme guide.")
        runBlocking {
            assertEquals(listOf("src-1", "src-2"), graph.data.sources.all().map { it.id }.sorted())
            assertEquals("kanagawa", graph.data.preferences.theme.first().id)
            val household = graph.data.preferences.household.first()
            assertTrue(household.pinConfigured)
            assertTrue(household.stored.any { it.id == "p1790000000000" })
        }
        val logo = graph.data.database.openHelper.readableDatabase.query("SELECT custom_logo_url FROM channel_custom WHERE channel_key = 'src-1:c42'").use { c ->
            c.moveToFirst()
            c.getString(0)
        }
        // The phone logo was made again from its bytes on this TV (BACKUP-FR-18 step 6).
        assertTrue(logo, logo.startsWith("file:") && File(Uri.parse(logo).path!!).isFile)
    }

    private fun count(table: String): Int = graph.data.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    /** SET-FR-96 (decision "Clear all guide data"): every programme goes, the channels stay. */
    @Test
    fun clearAllGuideDataRemovesTheProgrammesOnly() {
        GuideFixture.seed(graph, groups = 1, perGroup = 6)
        val channels = count("channel")
        assertTrue(count("programme") > 0)
        openBackup()
        compose.onNodeWithTag("settings-clear-guide").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(20_000) { exists("settings-maintenance-status") }
        assertEquals(0, count("programme"))
        assertEquals(channels, count("channel"))
    }

    /** §4.6: a wrong password is refused in its own words and nothing changes. */
    @Test
    fun aWrongPasswordChangesNothing() {
        writeBeta23File()
        runBlocking { graph.data.sources.save(config("keep", "Kept playlist")) }
        answerPickers()
        openBackup()
        typePassword("not-the-password")
        compose.onNodeWithTag("settings-backup-restore").performSemanticsAction(SemanticsActions.OnClick)
        awaitStatus("Wrong password, or the backup is damaged")
        assertEquals(listOf("Kept playlist"), sourceNames())
    }

    private companion object {
        /** Fictional; ä and ö take the UTF-8 path of the key derivation. */
        const val FIXTURE_PHRASE = "Sohva-äö-1"
    }
}
