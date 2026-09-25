package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 42 §11 and ORG-32…34, VOD-07: a group of your own is made in Settings › Library with the
 * remote, listed with its summary, and shown above the genres on the Movies wall with its titles.
 */
@RunWith(AndroidJUnit4::class)
class CustomGroupsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 30)
            val sql = graph.data.database.openHelper.writableDatabase
            // Three crime films (two of them from the nineties) and one drama.
            sql.execSQL("UPDATE movie SET genre = 'crime', year = 1995 WHERE key IN ('${LibraryFixture.key(0)}', '${LibraryFixture.key(1)}')")
            sql.execSQL("UPDATE movie SET genre = 'crime', year = 2005 WHERE key = '${LibraryFixture.key(2)}'")
            sql.execSQL("UPDATE movie SET genre = 'drama' WHERE key = '${LibraryFixture.key(3)}'")
            graph.metadata.passes.recountGenres()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { focused(tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("$tag not focused; focused: $now", e)
        }
    }

    private fun walkDownTo(tag: String) {
        repeat(40) { if (!focused(tag)) press(KeyEvent.KEYCODE_DPAD_DOWN) }
        awaitFocus(tag)
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    }.getOrDefault("")

    private fun type(value: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
    }

    private fun openLibrarySettings() {
        compose.waitUntil(15_000) { exists(RailItem.SETTINGS.tag) }
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("source-vod-0")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        repeat(30) { if (!focused("settings-section-metadata")) press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-metadata-tmdb-enabled")
    }

    @Test
    fun aGroupMadeInSettingsIsAWallAboveTheGenres() {
        openLibrarySettings()
        walkDownTo("custom-group-add")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("custom-group-name")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        type("Nineties crime")
        awaitFocus("custom-group-name")
        // The genres present in the library: Crime and Drama.
        compose.waitUntil(5_000) { exists("custom-group-genre-crime") && exists("custom-group-genre-drama") }
        assertTrue(!exists("custom-group-genre-action"))
        walkDownTo("custom-group-genre-crime")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Down from the switches (right edge) reaches the rightmost field; Left walks the row.
        walkDownTo("custom-group-min-rating")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("custom-group-from-year")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        type("1990")
        awaitFocus("custom-group-from-year")
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("custom-group-to-year")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        type("1999")
        walkDownTo("custom-group-save")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { !exists("custom-group-editor") }
        compose.waitUntil(5_000) { runBlocking { graph.data.preferences.customGroups.first() }.isNotEmpty() }
        val saved = runBlocking { graph.data.preferences.customGroups.first() }.single()
        assertEquals("Nineties crime", saved.name)
        // Focus is on the new row, which reads its summary.
        awaitFocus("custom-group-${saved.id}")
        assertTrue(text("custom-group-${saved.id}"), text("custom-group-${saved.id}").contains("Crime · 1990-1999"))

        // Movies › Genres: the group comes first and shows only the nineties crime films.
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists(RailItem.MOVIES.tag) }
        compose.onNodeWithTag(RailItem.MOVIES.tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(RailItem.MOVIES.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("library-view-genres") }
        compose.onNodeWithTag("library-view-genres").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-view-genres")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val row = "library-row-custom:${saved.id}"
        compose.waitUntil(10_000) { exists(row) && exists("library-row-genre:crime") }
        val top = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top(row) < top("library-row-genre:crime"))
        compose.onNodeWithTag(row).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(row)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val card = { id: Int -> "library-card-${LibraryFixture.key(id)}" }
        compose.waitUntil(10_000) { exists(card(0)) && exists(card(1)) }
        assertTrue(!exists(card(2)) && !exists(card(3)))
        assertTrue(text("library-label"), text("library-label").startsWith("Nineties crime"))
    }
}
