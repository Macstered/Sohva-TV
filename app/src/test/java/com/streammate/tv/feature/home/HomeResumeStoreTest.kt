package com.streammate.tv.feature.home

import com.sohva.tv.addons.*
import com.streammate.tv.iptv.repository.ContinueWatchingItem
import com.streammate.tv.iptv.repository.WatchingProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeResumeStoreTest {
    @Test fun bothCachedSourcesStartTogetherAndProduceOneOrderedInitialRow() = runTest {
        val vod = MutableSharedFlow<List<ContinueWatchingItem>>()
        val discover = MutableSharedFlow<List<AddonWatchProgress>>()
        val store = HomeResumeStore(backgroundScope, flowOf(HomeResumeProfile("a", true)), { vod }, { discover })
        runCurrent()
        assertEquals(1, vod.subscriptionCount.value)
        assertEquals(1, discover.subscriptionCount.value)
        vod.emit(listOf(movie("old", 10)))
        runCurrent()
        assertEquals(HomeResumeStatus.LOADING, store.state.value.status)
        discover.emit(listOf(addon("new", 20), addon("done", 30, completed = true)))
        runCurrent()
        assertEquals(listOf("new", "old"), store.state.value.entries.map { it.title })
        assertEquals(HomeResumeStatus.READY, store.state.value.status)
        // A warm Home return reads this retained value without another subscription.
        assertEquals("new", store.state.value.entries.first().title)
        vod.emit(listOf(movie("old", 40)))
        runCurrent()
        assertEquals(listOf("old", "new"), store.state.value.entries.map { it.title })
        assertEquals(1, vod.subscriptionCount.value)
    }

    @Test fun discoverOnlyHistoryIsNotMistakenForEmpty() = runTest {
        val store = HomeResumeStore(backgroundScope, flowOf(HomeResumeProfile("a", true)), { flowOf(emptyList()) }, { flowOf(listOf(addon("series", 10))) })
        runCurrent()
        assertEquals(HomeResumeStatus.READY, store.state.value.status)
        assertEquals("series", store.state.value.entries.single().title)
    }

    @Test fun switchingProfileOrRestrictingDiscoverClearsThePreviousRow() = runTest {
        val profiles = MutableStateFlow(HomeResumeProfile("a", true))
        val newViewer = MutableSharedFlow<List<ContinueWatchingItem>>()
        val store = HomeResumeStore(backgroundScope, profiles,
            { if (it == "a") flowOf(emptyList()) else newViewer }, { flowOf(listOf(addon("private", 10))) })
        runCurrent()
        assertEquals(1, store.state.value.entries.size)
        profiles.value = HomeResumeProfile("a", false)
        runCurrent()
        assertEquals(HomeResumeStatus.EMPTY, store.state.value.status)
        profiles.value = HomeResumeProfile("b", false)
        runCurrent()
        assertEquals("b", store.state.value.profileId)
        assertTrue(store.state.value.entries.isEmpty())
        assertEquals(HomeResumeStatus.LOADING, store.state.value.status)
        newViewer.emit(emptyList())
        runCurrent()
        assertEquals(HomeResumeStatus.EMPTY, store.state.value.status)
    }

    @Test fun slowReadIsExplicitlyUnavailableThenRecoversWithoutPolling() = runTest {
        val slow = MutableSharedFlow<List<ContinueWatchingItem>>()
        val store = HomeResumeStore(backgroundScope, flowOf(HomeResumeProfile("a", false)), { slow }, { error("Discover is restricted") })
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(HomeResumeStatus.FAILED, store.state.value.status)
        slow.emit(listOf(movie("recovered", 10))); runCurrent()
        assertEquals(HomeResumeStatus.READY, store.state.value.status)
        assertEquals(1, slow.subscriptionCount.value)
    }

    @Test fun lowerRowBrowsingKeepsStructureWhileProgressChangesInPlace() {
        val original = listOf(movie("one", 10), movie("two", 20))
        val changed = listOf(movie("new", 40), movie("two", 30))
        val held = retainHomeOrder(original, changed, true) { it.contentKey }
        assertEquals(listOf("one", "two"), held.map { it.title })
        assertEquals(30, held[1].progress.lastWatchedEpochMillis)
        assertEquals(changed, retainHomeOrder(held, changed, false) { it.contentKey })
    }

    @Test fun overnightVodCopiesKeepOneCardUsingTheOriginalDiscoverSource() {
        val watched = discoverMovie("tt1234567", 10)
        val copies = listOf(vodMovie("provider-one", 101, "tt1234567", 20), vodMovie("provider-two", 101, "tt1234567", 20))
        val result = mergeHomeResume(copies, listOf(watched))
        assertEquals(1, result.size)
        assertSame(watched, (result.single() as HomeResumeEntry.Discover).progress)
        assertEquals(100L, watched.positionMillis)
    }

    @Test fun sharedIdsBridgeFormatsAndPreferTheMostRecentlyUsedLocalSource() {
        val usedVod = vodMovie("original", 101, null, 30, localTime = 30)
        val bridge = vodMovie("new-provider", 101, "tt1234567", 100)
        val olderDiscover = discoverMovie("tt1234567", 20)
        assertEquals(usedVod.contentKey, (mergeHomeResume(listOf(usedVod, bridge), listOf(olderDiscover)).single() as HomeResumeEntry.Vod).item.contentKey)
        val newerDiscover = discoverMovie("tmdb:101", 40)
        assertSame(newerDiscover, (mergeHomeResume(listOf(usedVod, bridge), listOf(olderDiscover, newerDiscover)).single() as HomeResumeEntry.Discover).progress)
    }

    @Test fun matchingTitlesAloneAndMovieSeriesIdCollisionsNeverMerge() {
        val unrelated = listOf(vodMovie("one", 101, null, 10), vodMovie("two", 202, null, 20), vodMovie("unknown", null, null, 30))
        val unknownDiscover = discoverMovie("addon-specific-id", 40)
        val series = AddonWatchProgress(AddonWatchIdentity("fixture", AddonMediaKey("series", "tmdb:101"), AddonMediaKey("series", "tmdb:101:1:1")), "Same title", 100, 1000, 50, false)
        assertEquals(5, mergeHomeResume(unrelated, listOf(unknownDiscover, series)).size)
    }

    @Test fun duplicateCopiesDoNotConsumeTheTwelveTitleLimit() {
        val copies = (1L..20L).flatMap { id -> listOf(vodMovie("a$id", id, null, id), vodMovie("b$id", id, null, id)) }
        val result = mergeHomeResume(copies, emptyList())
        assertEquals(12, result.size)
        assertEquals((20L downTo 9L).toList(), result.map { (it as HomeResumeEntry.Vod).item.tmdbId })
    }

    private fun vodMovie(source: String, tmdb: Long?, imdb: String?, time: Long, localTime: Long? = null): ContinueWatchingItem {
        val key = "vod:movie:$source:fixture"
        return ContinueWatchingItem(key, "Same title", null, null, WatchingProgress(key, 100, 1000, false, time),
            tmdbId = tmdb, imdbId = imdb, localWatchedAtMillis = localTime)
    }
    private fun discoverMovie(id: String, time: Long) = AddonWatchProgress(
        AddonWatchIdentity("fixture", AddonMediaKey("movie", id), AddonMediaKey("movie", id)),
        "Same title", 100, 1000, time, false,
    )

    private fun movie(name: String, time: Long) = ContinueWatchingItem(name, name, null, null, WatchingProgress(name, 100, 1000, false, time))
    private fun addon(name: String, time: Long, completed: Boolean = false) = AddonWatchProgress(
        AddonWatchIdentity("fixture", AddonMediaKey("series", name), AddonMediaKey("series", "$name:1:1")),
        name, 100, 1000, time, completed,
    )
}
