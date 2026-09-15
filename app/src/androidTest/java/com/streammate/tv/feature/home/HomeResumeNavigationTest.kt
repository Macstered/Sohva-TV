package com.streammate.tv.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.trakt.TraktIds
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.iptv.repository.*
import com.streammate.tv.trakt.TraktHomeTitle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Synthetic UI state and an in-memory catalogue. No account or playback calls. */
class HomeResumeNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: StreamMateDatabase
    private val snapshot = mutableStateOf(HomeResumeSnapshot("fixture"))
    private val next = mutableStateOf<List<TraktHomeTitle>>(emptyList())
    private val visible = mutableStateOf(true)
    private val recommendation = TraktHomeTitle("movie", TraktIds(tmdb = 200), "Synthetic recommendation", null, null, null, null)
    private val nextTitle = TraktHomeTitle("episode", TraktIds(tmdb = 300), "Synthetic next episode", null, null, null, null, 1, 2)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.streammate.tv.debug")
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
    }
    @After fun close() = database.close()

    private fun show(withRecommendation: Boolean = true) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogue = CatalogueRepository(database.catalogueDao())
        val guide = GuideRepository(database.guideDao())
        val preferences = AppPreferencesRepository(context)
        compose.setContent {
            StreamMateTheme {
                if (visible.value) HomeScreen(
                    guideRepository = guide, catalogueRepository = catalogue, preferencesRepository = preferences,
                    sportsEvents = emptyList(), onLiveTv = {}, onSportMate = {}, onMovies = {}, onSeries = {},
                    onSearch = {}, onSettings = {}, onPlayChannel = {}, onPlayVod = { _, _ -> },
                    resumeSnapshot = snapshot.value, nextUp = next.value,
                    recommendations = if (withRecommendation) listOf(recommendation) else emptyList(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun immediateDownWaitsForCachedRowThenFocusesItsFirstCard() {
        show()
        compose.onNodeWithTag("home-resume-status").assertIsDisplayed().assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("home-resume-status").assertIsFocused()
        compose.runOnIdle { snapshot.value = ready("one") }
        compose.waitForIdle()
        compose.onNodeWithTag(tag("one")).assertIsDisplayed().assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("home-trakt-${recommendation.key}").assertIsFocused()
    }

    @Test fun laterRowsAndResumeReorderingWaitUntilTheViewerReturnsToTop() {
        snapshot.value = ready("one")
        show()
        press(Key.DirectionDown)
        compose.onNodeWithTag("home-trakt-${recommendation.key}").assertIsFocused()
        compose.runOnIdle { next.value = listOf(nextTitle); snapshot.value = ready("new", "one") }
        compose.waitForIdle()
        compose.onNodeWithTag("home-trakt-${recommendation.key}").assertIsFocused()
        compose.onNodeWithTag("home-trakt-${nextTitle.key}").assertDoesNotExist()
        press(Key.DirectionUp)
        compose.onNodeWithTag(tag("one")).assertIsFocused()
        compose.onNodeWithTag(tag("new")).assertIsDisplayed()
    }

    @Test fun warmHomeReturnUsesUpdatedSnapshotImmediatelyAndProfileChangeClearsIt() {
        snapshot.value = ready("one")
        show()
        compose.onNodeWithTag(tag("one")).assertIsFocused()
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { snapshot.value = ready("updated"); visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag(tag("updated")).assertIsDisplayed().assertIsFocused()
        compose.runOnIdle { snapshot.value = HomeResumeSnapshot("other-viewer") }
        compose.waitForIdle()
        compose.onNodeWithTag(tag("updated")).assertDoesNotExist()
        compose.onNodeWithTag("home-resume-status").assertIsFocused()
    }

    @Test fun confirmedEmptyCachedHistoryFocusesTheWelcomeAction() {
        show(withRecommendation = false)
        compose.runOnIdle { snapshot.value = HomeResumeSnapshot("fixture", status = HomeResumeStatus.EMPTY) }
        compose.waitForIdle()
        compose.onNodeWithTag("home-hero-primary").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("home-resume-status").assertDoesNotExist()
    }

    private fun press(key: Key) {
        compose.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
    private fun tag(name: String) = "home-resume-vod:vod:movie:fixture:$name"
    private fun ready(vararg names: String) = HomeResumeSnapshot("fixture", names.mapIndexed { index, name ->
        val key = "vod:movie:fixture:$name"
        HomeResumeEntry.Vod(ContinueWatchingItem(key, name, null, null, WatchingProgress(key, 1000, 5000, false, 100L - index)))
    }, HomeResumeStatus.READY)
}
