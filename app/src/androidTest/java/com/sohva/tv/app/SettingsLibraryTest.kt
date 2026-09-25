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
import com.sohva.tv.core.data.database.MetadataQueueEntity
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 41 §11 "Settings": Settings › Library opens on the TMDB switch, refuses to turn TMDB on
 * without a key, changes the metadata language with focus back on its row (and resets the queue,
 * META-FR-79), and clears the cache. Keys go through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class SettingsLibraryTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(5_000) { focused(tag) }
        } catch (e: Throwable) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun walkTo(tag: String, keyCode: Int = KeyEvent.KEYCODE_DPAD_DOWN, limit: Int = 30) {
        repeat(limit) {
            if (focused(tag)) return
            press(keyCode)
        }
        awaitFocus(tag)
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun queueSize(): Int = graph.data.database.metadata().queueSize()

    private fun openLibrary() {
        await(RailItem.SETTINGS.tag)
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("source-add-m3u")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        walkTo("settings-section-metadata")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-metadata-tmdb-enabled")
    }

    @Test
    fun tmdbNeedsAKeyAndTheLanguageChangeResetsTheLibrary() {
        runBlocking {
            graph.data.database.metadata().putQueue(listOf(MetadataQueueEntity("vod:movie:s:1", "movie", "Heat", 1995, 2, "complete", 0, 0, 1, 1)))
        }
        openLibrary()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { text("settings-metadata-status") == "Enter a TMDB key below first." }
        assertFalse(runBlocking { graph.metadata.settings.current() }.tmdbSwitch)
        // The language row opens its picker on the current language; Finnish is chosen.
        walkTo("settings-metadata-language")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-metadata-language-en-US")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("settings-metadata-language-fi-FI")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-metadata-language")
        compose.waitUntil(5_000) { runBlocking { graph.metadata.settings.current() }.language == "fi-FI" }
        compose.waitUntil(5_000) { queueSize() == 0 }
        // Clear metadata cache says so.
        walkTo("settings-metadata-clear-cache")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { text("settings-metadata-status") == "Metadata cache cleared" }
        assertEquals(0, queueSize())
    }
}
