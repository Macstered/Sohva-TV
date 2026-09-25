package com.sohva.tv.feature.library

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * What the film and series pages need from the app (plan/03 §4.6): one title's records, its
 * progress, the marks, the episode fetch and playback. Reads run on the database dispatcher behind
 * it; [format] is for the page's own text work (never the main thread, spec 40 §9.4).
 */
interface TitleEnvironment {
    val format: CoroutineDispatcher

    suspend fun film(key: String): FilmRecord?

    suspend fun series(key: String): SeriesRecord?

    fun episodes(seriesKey: String): Flow<List<EpisodeRecord>>

    suspend fun progress(key: String): Progress?

    suspend fun seriesProgress(seriesKey: String): Map<String, Progress>

    /** Any progress write (the player saving, a mark): the page re-reads its progress. */
    fun progressChanges(): Flow<Unit>

    suspend fun markWatched(key: String, knownDurationMs: Long)

    suspend fun forget(key: String)

    suspend fun markSeasonWatched(seriesKey: String, season: Int)

    suspend fun fetchEpisodes(seriesKey: String): Outcome<Unit>

    /** Opens the player on [key] from [startMs] (spec 30 `VodPlayer`). */
    fun play(key: String, startMs: Long)

    /** "Wrong details?" (the match picker arrives with metadata, M4b). */
    fun wrongDetails()
}

/** The film page's local facts, computed once when it opens (VOD-FR-59…62). */
@Immutable
data class FilmPageState(
    val record: FilmRecord,
    val breadcrumbGroup: String?,
    val quality: List<String>,
)

/** The series page's local facts (VOD-FR-73, -78). */
@Immutable
data class SeriesPageState(
    val record: SeriesRecord,
    val breadcrumbGroup: String?,
    val quality: List<String>,
)

/** One episode card, formatted off the main thread (VOD-FR-84, -85). */
@Immutable
data class EpisodeCard(
    val record: EpisodeRecord,
    /** Null reads as "Episode n". */
    val title: String?,
    val progress: Progress?,
)

/** Loading and failure of the episode list (VOD-FR-74, -75, -83). */
@Immutable
data class EpisodesState(
    val loading: Boolean = true,
    val error: AppError? = null,
)
