package com.sohva.tv.feature.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 03 §11 "Unit": term handling, one search per pause in typing, groups appended as they finish, partial failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun row(key: String, kind: ResultKind) = SearchResult(key, kind, key, "", null, key)

    private class Env : SearchEnvironment {
        val terms = mutableListOf<String>()
        var films = CompletableDeferred<List<SearchResult>>()
        var seriesFails = false

        override suspend fun live(term: String): List<SearchResult> {
            terms += term
            return listOf(SearchResult("channel:1", ResultKind.CHANNEL, "c", "", null, "c"))
        }

        override suspend fun films(term: String): List<SearchResult> = films.await()

        override suspend fun series(term: String): List<SearchResult> = if (seriesFails) error("broken") else emptyList()

        override suspend fun episodes(term: String): List<SearchResult> = emptyList()

        override fun open(result: SearchResult) = Unit

        override fun leave() = Unit
    }

    @Test
    fun shortTextShowsTheHintAndQuickTypingSearchesOnce() = runTest(main) {
        val env = Env()
        val model = SearchModel(env)
        model.setText(" a ")
        assertEquals(SearchStatus.HINT, model.state.value.status)
        for (text in listOf("ma", "mat", "matc", "match")) {
            model.setText(text)
            advanceTimeBy(100)
        }
        assertEquals(SearchStatus.SEARCHING, model.state.value.status)
        env.films.complete(emptyList())
        advanceUntilIdle()
        assertEquals(listOf("match"), env.terms)
        assertEquals(SearchStatus.COUNT, model.state.value.status)
    }

    @Test
    fun groupsAppendAsTheyFinishAndAFailureKeepsTheOthers() = runTest(main) {
        val env = Env()
        env.seriesFails = true
        val model = SearchModel(env)
        model.setText("match")
        advanceTimeBy(SearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        // Films are still reading: the channel is already listed, the line still says Searching.
        assertEquals(listOf("channel:1"), model.state.value.results.map { it.key })
        assertEquals(SearchStatus.SEARCHING, model.state.value.status)
        env.films.complete(listOf(row("movie:1", ResultKind.MOVIE)))
        advanceUntilIdle()
        assertEquals(listOf("channel:1", "movie:1"), model.state.value.results.map { it.key })
        assertEquals(SearchStatus.FAILED, model.state.value.status)
    }

    @Test
    fun aNewTextCancelsTheRunningSearchWithoutMarkingItFailed() = runTest(main) {
        val env = Env()
        val model = SearchModel(env)
        model.setText("match")
        advanceTimeBy(SearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        model.setText("mat")
        assertTrue(model.state.value.results.isEmpty())
        env.films = CompletableDeferred(emptyList())
        advanceUntilIdle()
        assertEquals(SearchStatus.COUNT, model.state.value.status)
        assertEquals(listOf("match", "mat"), env.terms)
    }
}
