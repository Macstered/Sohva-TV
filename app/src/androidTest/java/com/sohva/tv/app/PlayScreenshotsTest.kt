package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Google Play listing's TV screenshots (docs/play/README.md §4, §9): Home, the guide, the movie
 * wall, a film page and Settings › Home, at the emulator's 1920×1080, from fictional data only (no
 * real channel logos, posters or crests). Each is written to /data/local/tmp/play-<n>.png for
 * `adb pull`; the checks only make sure each screen is the one meant.
 */
@RunWith(AndroidJUnit4::class)
class PlayScreenshotsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    private val films = listOf(
        "Harbour Lights", "Quiet Orchard", "The Long Winter", "Paper Moons", "Cold Current", "Silver Ridge",
        "Night Ferry", "Glass Garden", "Last Signal", "Blue Hour", "Salt Road", "The Lantern Keeper",
    )
    private val comedies = listOf(
        "Kitchen Chaos", "Second Chances", "Lucky Street", "The Neighbours", "Holiday Mix-up", "Uncle Otto",
        "Wrong Number", "Best Man Down", "Summer Rules", "Grand Opening", "Plan B", "Two Left Feet",
    )

    /** Fictional plots, one per film, in the order of [films] then [comedies]. */
    private val plots = listOf(
        "A lighthouse keeper waits for a ship that never comes.", "Three sisters return to the orchard their father left them.",
        "A village snowed in for a season learns to live together.", "A young illustrator draws the moon every night for a year.",
        "Two divers search the northern sea for a lost bell.", "A mountain guide takes one last group across the ridge.",
        "Strangers share a night crossing and a secret.", "A glasshouse gardener grows something no one has seen.",
        "A radio operator hears a message from a closed station.", "A photographer chases the light between day and night.",
        "Salt merchants cross the desert with an unexpected passenger.", "An old keeper teaches a boy to tend the harbour lamps.",
        "A small restaurant has one evening to save its name.", "A retired dancer gets a second chance on a small stage.",
        "A street of neighbours wins the lottery together.", "The new family next door is not what anyone expected.",
        "Two families book the same holiday cottage.", "An uncle arrives for a weekend and stays for a summer.",
        "A wrong number turns into a friendship.", "A best man loses the rings and his way.",
        "Cousins make their own rules for one long summer.", "A tiny bakery prepares for its grand opening.",
        "When plan A fails, plan B is even stranger.", "A clumsy teacher coaches the school dance team.",
    )
    private val art = MockWebServer()

    private val seed = object : ExternalResource() {
        override fun before() {
            val assets = instrumentation.context.assets
            art.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val name = request.url.encodedPath.removePrefix("/")
                    val bytes = runCatching { assets.open("play-art/$name").use { it.readBytes() } }.getOrNull()
                        ?: return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(bytes)).build()
                }
            }
            art.start()
            GuideFixture.seed(graph, groups = 4, perGroup = 8)
            LibraryFixture.seed(graph, perGroup = 12, series = 2, episodes = 4)
            val db = graph.data.database.openHelper.writableDatabase
            for ((i, title) in (films + comedies).withIndex()) {
                db.execSQL(
                    "UPDATE movie SET name = ?, sort_name = ?, plot = ?, poster_url = ? WHERE key = ?",
                    arrayOf(title, title.lowercase(), plots[i], art.url("/poster-$i.jpg").toString(), LibraryFixture.key(i)),
                )
            }
            runBlocking {
                for (id in listOf(3, 0, 14)) {
                    graph.data.progress.save(LibraryFixture.key(id), (35 + id) * MINUTE, 110 * MINUTE)
                    Thread.sleep(5)
                }
                for (c in listOf("fixture-0:c2", "fixture-0:c9", "fixture-0:c0")) graph.data.live.recordWatched(c)
            }
            graph.continueFeed.retry()
        }

        override fun after() = art.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun focusAndPress(tag: String) {
        compose.waitUntil(10_000) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    /** Lets pictures and the hero settle, then writes the screen. */
    private fun shot(n: Int) {
        android.os.SystemClock.sleep(2_500)
        compose.waitForIdle()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/play-$n.png").use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }

    @Test
    fun takeTheListingScreenshots() {
        // 1. Home: Continue watching and recently watched channels.
        compose.waitUntil(15_000) { exists("home-resume-vod:${LibraryFixture.key(14)}") }
        shot(1)
        // 2. The guide.
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("guide-row-0") }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        shot(2)
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("home-rows") }
        // 3. The movie wall.
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        focusAndPress("library-row-group:drama")
        compose.waitUntil(10_000) { exists("library-card-${LibraryFixture.key(0)}") }
        compose.onNodeWithTag("library-card-${LibraryFixture.key(1)}").performSemanticsAction(SemanticsActions.RequestFocus)
        shot(3)
        // 4. A film page.
        focusAndPress("library-card-${LibraryFixture.key(3)}")
        compose.waitUntil(10_000) { exists("screen-film") }
        shot(4)
        repeat(4) { if (!exists("home-rows")) press(KeyEvent.KEYCODE_BACK) }
        // 5. Settings › Home, the row editor.
        compose.waitUntil(10_000) { exists("home-rows") }
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("settings-section-home") }
        compose.onNodeWithTag("settings-section-home").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("settings-home-order") }
        shot(5)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
