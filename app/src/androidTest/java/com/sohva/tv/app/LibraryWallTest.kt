package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 40 §11 "Instrumentation": the wall's rail, paging and the left-to-rail rule, with real key events. */
@RunWith(AndroidJUnit4::class)
class LibraryWallTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 400)
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
        try {
            compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$tag not focused; focused: ${focusedTags()}", e)
        }
    }

    private fun focusedTags(): List<String?> = compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun card(id: Int) = "library-card-${LibraryFixture.key(id)}"

    private companion object {
        /** A remote's key repeat: about 20 presses a second. */
        const val REPEAT_MS = 50L
    }

    /** Right from the rail lands on the nearest card (VOD-FR-51); then the first card, as the viewer would walk to it. */
    private fun intoWall() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-card-") == true
            }
        }
        compose.onNodeWithTag(card(0)).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(card(0))
    }

    /** Home → Movies with OK, as the viewer does. */
    private fun openMovies() {
        compose.waitUntil(15_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        compose.onNodeWithTag(RailItem.MOVIES.tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(RailItem.MOVIES.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-movies") }
    }

    private fun openDrama() {
        compose.waitUntil(10_000) { exists("library-row-group:drama") }
        compose.onNodeWithTag("library-row-group:drama").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-row-group:drama")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists(card(0)) }
    }

    /** VOD-FR-04, -07: the Genres view lists only genres with titles, in the fixed order, then Unsorted. */
    @Test
    fun theGenresViewListsGenresWithTitlesAndOpensOne() {
        val sql = graph.data.database.openHelper.writableDatabase
        sql.execSQL("UPDATE movie SET genre = 'crime' WHERE key IN ('${LibraryFixture.key(0)}', '${LibraryFixture.key(1)}')")
        sql.execSQL("UPDATE movie SET genre = 'action' WHERE key = '${LibraryFixture.key(2)}'")
        graph.metadata.passes.recountGenres()
        openMovies()
        compose.onNodeWithTag("library-view-genres").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-view-genres")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("library-row-genre:crime") && exists("library-row-unsorted") }
        assertTrue(!exists("library-row-genre:drama"))
        // History stays selected: it is the first row of both views (VOD-FR-07). Action comes before Crime.
        assertTrue(text("library-label").startsWith("History"))
        val top = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top("library-row-genre:action") < top("library-row-genre:crime"))
        compose.onNodeWithTag("library-row-genre:crime").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-row-genre:crime")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { text("library-label") == "Crime  ·  2" && exists(card(0)) && exists(card(1)) }
        assertTrue(!exists(card(2)))
    }

    @Test
    fun firstEntryFocusesHistoryAndAnEmptyHistorySaysSo() {
        openMovies()
        awaitFocus("library-row-history")
        compose.waitUntil(10_000) { exists("library-message") }
        assertTrue(text("library-message"), text("library-message").contains("No titles"))
        assertTrue(text("library-label"), text("library-label").startsWith("History"))
    }

    @Test
    fun aGroupShowsItsFilmsAndLeftFromTheFirstColumnReturnsToIt() {
        openMovies()
        openDrama()
        assertTrue(text("library-label"), text("library-label") == "Drama  ·  400")
        intoWall()
        // Left from the second column moves one card; from the first it goes to the selected row.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(card(1))
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus(card(0))
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("library-row-group:drama")
    }

    /**
     * A held Down at key-repeat pace walks past the first page (120) to card 180: every press
     * moves exactly one row or, while the next page is on its way, none (spec 40 §9.3: never skip).
     */
    @Test
    fun heldDownWalksPastThePageWithoutSkipping() {
        openMovies()
        openDrama()
        intoWall()
        var at = 0
        var presses = 0
        while (at < 180 && presses < 120) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            presses++
            Thread.sleep(REPEAT_MS)
            compose.waitForIdle()
            val now = focusedCard() ?: error("no card focused after $presses presses")
            assertTrue("jumped from $at to $now", now == at || now == at + 6)
            at = now
        }
        assertTrue("stopped at $at after $presses presses", at == 180)
    }

    /** VOD-24 (VOD-FR-56): leaving Movies and coming back finds the same group and card, past the first page. */
    @Test
    fun movieBrowsingIsWhereItWasAfterLeaving() {
        openMovies()
        openDrama()
        intoWall()
        // Well past the first page of 120: row 25, second column.
        repeat(25) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            Thread.sleep(REPEAT_MS)
            compose.waitForIdle()
        }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(card(151))
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists(RailItem.MOVIES.tag) }
        awaitFocus(RailItem.MOVIES.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-movies") }
        awaitFocus(card(151))
        assertTrue(text("library-label"), text("library-label").startsWith("Drama"))
        // The pages on both sides were read: Up walks back without a gap.
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus(card(145))
    }

    private fun focusedCard(): Int? = focusedTags().firstNotNullOfOrNull { it?.removePrefix("library-card-")?.takeIf { key -> key != it }?.substringAfterLast(':')?.toIntOrNull() }

    @Test
    fun searchFiltersInsideTheSelectedGroup() {
        openMovies()
        openDrama()
        compose.onNodeWithTag("library-search").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("0012")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(10_000) { exists(card(12)) && !exists(card(0)) }
        compose.waitUntil(5_000) { text("library-label") == "Drama  ·  1" }
    }

    @Test
    fun backClosesTheOptionsSheetOntoOptions() {
        openMovies()
        openDrama()
        compose.onNodeWithTag("library-options").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("library-refresh")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.waitUntil(5_000) { !exists("library-sheet") }
        awaitFocus("library-options")
        assertTrue(exists("screen-movies"))
    }
}
