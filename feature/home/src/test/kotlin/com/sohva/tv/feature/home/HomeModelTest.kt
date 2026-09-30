package com.sohva.tv.feature.home

import com.sohva.tv.core.data.home.NowProgramme
import com.sohva.tv.core.data.home.RecentChannel
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.data.vod.ContinueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Spec 02 §11 "Unit": rows, the structure lock, and the hero's idle rules and 180 ms rest (with a test clock). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(key: String, position: Long = 10, updated: Long = 1) =
        ContinueItem(key, key, key, 2001, null, null, false, null, null, null, null, position * 60_000, 100 * 60_000, updated)

    private class Env(val resumeState: MutableStateFlow<ResumeState>, var recent: List<RecentChannel>) : HomeEnvironment {
        val looked = mutableListOf<HeroSubject>()
        val layoutState = MutableStateFlow(com.sohva.tv.core.model.home.HomeLayout.DEFAULT)
        var recentReads = 0
        var traktReads = 0
        var traktLists = TraktLists.EMPTY
        override val resume: StateFlow<ResumeState> get() = resumeState
        override val layout: Flow<com.sohva.tv.core.model.home.HomeLayout> get() = layoutState
        override val trakt: Flow<TraktLists> get() = kotlinx.coroutines.flow.flow {
            traktReads++
            emit(traktLists)
        }
        override fun retryResume() = Unit
        override suspend fun recentChannels(now: Long): List<RecentChannel> = recent.also { recentReads++ }
        override val timeZone: Flow<String?> = flowOf(null)
        override fun now(): Long = NOW
        override suspend fun heroDetails(subject: HeroSubject): HeroDetails? {
            looked += subject
            return HeroDetails("synopsis", null)
        }
        override fun resume(card: ResumeCard, fromStart: Boolean) = Unit
        override suspend fun markWatched(card: ResumeCard) = Unit
        override suspend fun remove(card: ResumeCard) = Unit
        override fun playChannel(card: ChannelCard) = Unit
        override fun openGuide() = Unit
        override val lowMemory: Boolean = false
    }

    private val live = RecentChannel(7, "s:c7", "Pulse", null, 7, NowProgramme(1, "News", null, NOW - 60_000, NOW + 60_000))

    private fun TestScope.model(env: Env): HomeModel = HomeModel(env).also { runCurrent() }

    /** A retained model reads once per visible Home entry, including a return with the row locked. */
    @Test
    fun returningHomeReplacesTheRecentSnapshotEvenWhileTheRowIsLocked() = runTest(main) {
        val env = Env(MutableStateFlow(ResumeState.Empty), listOf(live))
        val model = model(env)
        assertEquals(1, env.recentReads)
        model.enter()
        runCurrent()
        assertEquals(1, env.recentReads)
        model.setLocked(true)
        val newer = live.copy(id = 8, key = "s:c8", name = "Next")
        env.recent = listOf(newer, live)
        model.enter()
        runCurrent()
        assertEquals(2, env.recentReads)
        assertEquals(listOf("channel:8", "channel:7"),
            (model.rows.value.single() as HomeRow.Channels).cards.map { it.key })
    }

    @Test
    fun whileLockedCardsKeepTheirPlacesAndTakeNewValues() = runTest(main) {
        val state = MutableStateFlow<ResumeState>(ResumeState.Ready(listOf(item("a", updated = 2), item("b", updated = 1))))
        val model = model(Env(state, listOf(live)))
        assertEquals(listOf("vod:a", "vod:b"), (model.rows.value[0] as HomeRow.Resume).cards.map { it.key })
        model.setLocked(true)
        // b played again: newest first in the data, but the locked row keeps its order; a new title does not appear.
        state.value = ResumeState.Ready(listOf(item("b", position = 50, updated = 3), item("a", updated = 2), item("c", updated = 1)))
        runCurrent()
        val locked = (model.rows.value[0] as HomeRow.Resume).cards
        assertEquals(listOf("vod:a", "vod:b"), locked.map { it.key })
        assertEquals(0.5f, locked[1].fraction, 0.001f)
        model.setLocked(false)
        assertEquals(listOf("vod:b", "vod:a", "vod:c"), (model.rows.value[0] as HomeRow.Resume).cards.map { it.key })
    }

    @Test
    fun theHeroWaits180MsForFocusToRestAndTheRailBringsBackTheIdleSubject() = runTest(main) {
        val env = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        val model = model(env)
        val cards = (model.rows.value[0] as HomeRow.Resume).cards
        assertEquals(HeroSubject.Resume(cards[0]), model.hero.value)
        val channel = (model.rows.value[1] as HomeRow.Channels).cards[0]
        model.focus(HeroSubject.Channel(channel))
        advanceTimeBy(HomeModel.HERO_REST_MS - 10)
        assertEquals(HeroSubject.Resume(cards[0]), model.hero.value)
        advanceTimeBy(20)
        assertEquals(HeroSubject.Channel(channel), model.hero.value)
        model.focus(null)
        assertEquals(HeroSubject.Resume(cards[0]), model.hero.value)
    }

    @Test
    fun theIdleHeroIsAChannelWithoutPausedTitlesAndWelcomeWithNothing() = runTest(main) {
        val model = model(Env(MutableStateFlow(ResumeState.Empty), listOf(live)))
        assertEquals(HeroSubject.Channel(ChannelCard("channel:7", live)), model.hero.value)
        val empty = model(Env(MutableStateFlow(ResumeState.Empty), emptyList()))
        assertEquals(HeroSubject.Welcome, empty.hero.value)
        assertEquals(true, empty.empty.value)
    }

    @Test
    fun aLoadingRowIsTheStatusCardAndLookupsWaitForItToSettle() = runTest(main) {
        val state = MutableStateFlow<ResumeState>(ResumeState.Loading)
        val env = Env(state, listOf(live))
        val model = model(env)
        assertEquals(HomeRow.Status(failed = false), model.rows.value[0])
        // The channel hero is up, but its lookup waits for Continue watching (HOME-FR-65).
        assertEquals(emptyList<HeroSubject>(), env.looked)
        state.value = ResumeState.Empty
        runCurrent()
        assertEquals(1, env.looked.size)
    }

    /** A Discover card is landscape: the addon's background, else its poster (decision "Discover card art"). */
    @Test
    fun aDiscoverCardShowsTheAddonsBackground() {
        fun paused(background: String?) = item("d").copy(
            posterUrl = "https://provider.example/poster.jpg",
            discover = com.sohva.tv.core.data.vod.DiscoverResume("i", "movie", "tt0000001", "tt0000001", null, background),
        )
        assertEquals("https://provider.example/wide.jpg", HomeModel.card(paused("https://provider.example/wide.jpg")).image)
        assertEquals("https://provider.example/poster.jpg", HomeModel.card(paused(null)).image)
    }

    /** A library card is landscape too: the title's backdrop when one is known, else its poster (decision "Library card art"). */
    @Test
    fun aLibraryCardShowsTheTitlesBackdrop() {
        val film = item("f").copy(posterUrl = "https://provider.example/poster.jpg")
        assertEquals("https://provider.example/poster.jpg", HomeModel.card(film).image)
        assertEquals("https://provider.example/wide.jpg", HomeModel.card(film.copy(backdrop = "https://provider.example/wide.jpg")).image)
    }

    /** Spec 02 HOME-FR-87: the rows follow the profile's order; hidden ones are left out and never read. */
    @Test
    fun rowsFollowTheLayoutAndHiddenRowsAreNeverRead() = runTest(main) {
        val env = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        env.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.decode("recent-channels,continue-watching")
        val model = model(env)
        assertEquals(listOf(HomeRow.RECENT, HomeRow.CONTINUE), model.rows.value.map { it.key })

        val hidden = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        hidden.traktLists = TraktLists.EMPTY
        hidden.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.decode("continue-watching,-recent-channels,-watch-next,-recommended")
        val quiet = model(hidden)
        runCurrent()
        assertEquals(listOf(HomeRow.CONTINUE), quiet.rows.value.map { it.key })
        assertEquals("hidden recent channels are not read", 0, hidden.recentReads)
        assertEquals("hidden Trakt rows are not read", 0, hidden.traktReads)
        // Shown again (back from Settings): the Trakt lists are read.
        hidden.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.DEFAULT
        runCurrent()
        assertEquals(1, hidden.traktReads)
    }

    /** HOME-FR-94: an added Trakt row takes its layout place once its list has titles; only a shown one is read. */
    @Test
    fun addedTraktRowsFollowTheLayout() = runTest(main) {
        fun card(key: String, source: String) = TraktCard("$source/$key", false, false, key, 2026, null, null, null, null, null, null, null, null, source)
        val trending = com.sohva.tv.core.model.home.HomeLayout.TRAKT_TRENDING_MOVIES
        val watchlist = com.sohva.tv.core.model.home.HomeLayout.TRAKT_WATCHLIST_SHOWS
        val env = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        env.traktLists = TraktLists(emptyList(), emptyList(), false, mapOf(trending to listOf(card("x", trending), card("y", trending)), watchlist to emptyList()))
        env.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.decode("$trending,continue-watching,-watch-next,-recommended,$watchlist,recent-channels")
        val model = model(env)
        runCurrent()
        // The empty watchlist is left out, as any row without cards (HOME-FR-01).
        assertEquals(listOf(trending, HomeRow.CONTINUE, HomeRow.RECENT), model.rows.value.map { it.key })
        assertEquals(listOf("$trending/x", "$trending/y"), (model.rows.value.first() as HomeRow.Trakt).cards.map { it.key })
        // Nothing Trakt shown: nothing Trakt read.
        val quiet = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        quiet.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.decode("continue-watching,-watch-next,-recommended,-$trending")
        model(quiet)
        runCurrent()
        assertEquals(0, quiet.traktReads)
    }

    /** HOME-FR-98: a library-only row keeps the owned titles; one with none owned is left out. */
    @Test
    fun aLibraryOnlyRowKeepsTheOwnedTitles() = runTest(main) {
        val trending = com.sohva.tv.core.model.home.HomeLayout.TRAKT_TRENDING_MOVIES
        val popular = com.sohva.tv.core.model.home.HomeLayout.TRAKT_POPULAR_MOVIES
        fun card(key: String, source: String, owned: Boolean) = TraktCard("$source/$key", false, false, key, 2026, null, null, null, null, null, null, null, null, source, owned)
        val env = Env(MutableStateFlow(ResumeState.Ready(listOf(item("a")))), listOf(live))
        env.traktLists = TraktLists(
            emptyList(), emptyList(), false,
            mapOf(trending to listOf(card("x", trending, false), card("y", trending, true)), popular to listOf(card("z", popular, false))),
        )
        env.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.DEFAULT.withAdded(trending).withAdded(popular)
            .withLibraryOnly(trending, true).withLibraryOnly(popular, true)
        val model = model(env)
        runCurrent()
        val row = model.rows.value.filterIsInstance<HomeRow.Trakt>().single()
        assertEquals(trending, row.key)
        assertEquals(listOf("$trending/y"), row.cards.map { it.key })
    }

    /** HOME-FR-89: with Continue watching hidden there is no row and no status card, and the idle hero is a recent channel. */
    @Test
    fun aHiddenContinueWatchingShowsNoStatusCard() = runTest(main) {
        val env = Env(MutableStateFlow(ResumeState.Loading), listOf(live))
        env.layoutState.value = com.sohva.tv.core.model.home.HomeLayout.DEFAULT.withShown(HomeRow.CONTINUE, false)
        val model = model(env)
        assertEquals(listOf(HomeRow.RECENT), model.rows.value.map { it.key })
        env.resumeState.value = ResumeState.Ready(listOf(item("a")))
        runCurrent()
        assertEquals(HeroSubject.Channel(ChannelCard("channel:7", live)), model.hero.value)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
