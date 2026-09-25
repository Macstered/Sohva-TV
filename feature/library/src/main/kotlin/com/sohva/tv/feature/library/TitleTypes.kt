package com.sohva.tv.feature.library

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.vod.CopyLanguage
import com.sohva.tv.core.model.vod.SimilarReference
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

    /** What the metadata memory cache holds for the film, for the first frame (VOD-FR-60); never blocks. */
    fun cachedFilmMetadata(film: FilmRecord): TitleMetadata?

    /**
     * The film page's details lookup (VOD-FR-60, spec 41 META-FR-37): null when metadata is off or
     * nothing was found; failures are silent. A missing library poster is repaired from it.
     */
    suspend fun filmMetadata(film: FilmRecord): TitleMetadata?

    /** The film's copies for the Versions row (VOD-FR-68). */
    suspend fun versions(film: FilmRecord): List<VersionCard>

    /** The library films that stand for TMDB's similar titles (VOD-FR-70, -71). */
    suspend fun similar(film: FilmRecord, metadata: TitleMetadata): List<SimilarCard>

    /** Pushes another film page (a Similar card, spec 40 §3). */
    fun openFilm(key: String)

    /** "Source: …" asks the platform to open the record's page; failure is ignored (VOD-FR-66). */
    fun openUrl(url: String)
}

/**
 * What metadata adds to a details page (spec 41 §4.9), formatted for drawing: artwork addresses
 * already sized for where they are drawn (§9.5).
 */
@Immutable
data class TitleMetadata(
    val title: String?,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val rating: String?,
    val cast: List<CastCard>,
    /** "TMDB" or "TVmaze" for "Source: %1$s". */
    val sourceName: String,
    val sourceUrl: String,
    /** True once the film details call answered: only then does Similar appear (VOD-FR-70). */
    val detailsLoaded: Boolean,
    val similar: List<SimilarReference>,
)

/** A cast member (VOD-FR-69): not focusable; initials under the photo. */
@Immutable
data class CastCard(val name: String, val character: String?, val photoUrl: String?, val initials: String)

/** A copy on the Versions row (VOD-FR-68); the claims line is put together where it is drawn. */
@Immutable
data class VersionCard(
    val key: String,
    val sourceName: String,
    val languages: List<CopyLanguage>,
    val picture: List<String>,
    /** The provider's full name, shown when the copy claims nothing. */
    val name: String,
    val current: Boolean,
)

/** A Similar card (VOD-FR-70): TMDB's title and year, TMDB's poster else the library's. */
@Immutable
data class SimilarCard(val key: String, val title: String, val year: Int?, val posterUrl: String?)

/** The Similar row (VOD-FR-70): absent until the details lookup completes, then checking, then the cards. */
sealed interface SimilarState {
    data object Hidden : SimilarState

    data object Checking : SimilarState

    @Immutable
    data class Ready(val cards: List<SimilarCard>) : SimilarState
}

/** The film page's local facts, computed once when it opens (VOD-FR-59…62). */
@Immutable
data class FilmPageState(
    val record: FilmRecord,
    val breadcrumbGroup: String?,
    val quality: List<String>,
    /** The provider name cleaned by the matcher's search-title rule, the title before metadata (VOD-FR-61). */
    val cleanTitle: String,
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
