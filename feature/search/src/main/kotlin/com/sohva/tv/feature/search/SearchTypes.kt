package com.sohva.tv.feature.search

import androidx.compose.runtime.Immutable

/** A result's kind and its label (spec 03 SEARCH-FR-20). */
enum class ResultKind { SPORT, CHANNEL, PROGRAMME, MOVIE, SERIES, EPISODE }

/**
 * One result row, ready to draw: texts formatted off the main thread (spec 03 §9). [key] is
 * unique within a search (SEARCH-FR-22: a programme's key carries its id); [target] is what OK
 * opens (a channel, film, series or episode key).
 */
@Immutable
data class SearchResult(
    val key: String,
    val kind: ResultKind,
    val title: String,
    val subtitle: String,
    val image: String?,
    val target: String,
)

/** What Search needs from the app: the four groups, and where results lead (plan/03 §4.6). */
interface SearchEnvironment {
    /** Today's loaded games naming the term (SEARCH-08, spec 60 SPORT-FR-98); an in-memory filter. */
    suspend fun sport(term: String): List<SearchResult> = emptyList()

    /** Channels, then programmes (SEARCH-FR-11). */
    suspend fun live(term: String): List<SearchResult>

    suspend fun films(term: String): List<SearchResult>

    suspend fun series(term: String): List<SearchResult>

    suspend fun episodes(term: String): List<SearchResult>

    /** OK on a result (spec 03 §3). */
    fun open(result: SearchResult)

    fun leave()
}

/** The status line (SEARCH-FR-40), first matching rule wins. */
enum class SearchStatus { SEARCHING, HINT, FAILED, NONE, COUNT }

@Immutable
data class SearchState(
    val results: List<SearchResult> = emptyList(),
    /** Null while the text is shorter than two characters. */
    val term: String? = null,
    val running: Boolean = false,
    val failed: Boolean = false,
) {
    val status: SearchStatus
        get() = when {
            running -> SearchStatus.SEARCHING
            term == null -> SearchStatus.HINT
            failed -> SearchStatus.FAILED
            results.isEmpty() -> SearchStatus.NONE
            else -> SearchStatus.COUNT
        }
}
