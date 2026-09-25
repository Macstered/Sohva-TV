package com.sohva.tv.app.library

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.core.model.vod.CopyClaimReader
import com.sohva.tv.core.model.vod.SimilarReference
import com.sohva.tv.core.net.metadata.Artwork
import com.sohva.tv.core.net.metadata.MetadataRecord
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.feature.library.CastCard
import com.sohva.tv.feature.library.SimilarCard
import com.sohva.tv.feature.library.TitleEnvironment
import com.sohva.tv.feature.library.TitleMetadata
import com.sohva.tv.feature.library.VersionCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The details pages' side of the graph (plan/03 §4.6): title reads, progress, marks, episodes, metadata and playback. */
class AppTitleEnvironment(private val graph: AppGraph, private val navigation: TitleNavigation) : TitleEnvironment {
    private val titles get() = graph.data.titles
    private val progress get() = graph.data.progress
    private val metadata get() = graph.metadata.service

    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override suspend fun film(key: String): FilmRecord? = titles.film(key)

    override suspend fun series(key: String): SeriesRecord? = titles.series(key)

    override fun episodes(seriesKey: String): Flow<List<EpisodeRecord>> = titles.episodes(seriesKey)

    override suspend fun progress(key: String): Progress? = progress.of(key)

    override suspend fun seriesProgress(seriesKey: String): Map<String, Progress> = progress.ofSeries(seriesKey)

    override fun progressChanges(): Flow<Unit> = progress.changes()

    override suspend fun markWatched(key: String, knownDurationMs: Long) = progress.markWatched(key, knownDurationMs)

    override suspend fun forget(key: String) = progress.forget(key)

    override suspend fun markSeasonWatched(seriesKey: String, season: Int) = progress.markSeasonWatched(seriesKey, season)

    override suspend fun fetchEpisodes(seriesKey: String): Outcome<Unit> = graph.sync.episodes.fetch(seriesKey)

    override fun play(key: String, startMs: Long) = navigation.play(key, startMs)

    override fun wrongDetails() = graph.notYetAvailable()

    override fun cachedFilmMetadata(film: FilmRecord): TitleMetadata? = metadata.cached(request(film))?.let(::toMetadata)

    override suspend fun filmMetadata(film: FilmRecord): TitleMetadata? {
        val record = metadata.filmDetails(request(film)) ?: return null
        // A missing library poster is repaired from the details record (VOD-FR-60, META-FR-71).
        val poster = record.poster
        if (film.posterUrl.isNullOrBlank() && poster != null) titles.repairPoster(film.key, poster)
        return withContext(format) { toMetadata(record) }
    }

    override suspend fun versions(film: FilmRecord): List<VersionCard> {
        val rows = titles.versions(film.workKey)
        return withContext(format) {
            rows.map { row ->
                val claims = CopyClaimReader.read(row.name)
                VersionCard(row.key, row.sourceName, claims.languages, claims.picture, row.name, row.key == film.key)
            }
        }
    }

    override suspend fun similar(film: FilmRecord, metadata: TitleMetadata): List<SimilarCard> =
        titles.similar(film, metadata.similar).map { found ->
            val reference = found.reference
            SimilarCard(found.key, reference.title, reference.year, Artwork.url(reference.poster, Artwork.POSTER_WALL) ?: found.libraryPoster)
        }

    override fun cachedSeriesMetadata(series: SeriesRecord): TitleMetadata? = metadata.cached(request(series))?.let(::toMetadata)

    override suspend fun seriesMetadata(series: SeriesRecord): TitleMetadata? {
        val record = metadata.seriesDetails(request(series)) ?: return null
        val poster = record.poster
        if (series.posterUrl.isNullOrBlank() && poster != null) titles.repairPoster(series.key, poster)
        return withContext(format) { toMetadata(record) }
    }

    override fun cachedEpisodeMetadata(series: SeriesRecord, season: Int, episode: Int): TitleMetadata? =
        metadata.cached(episodeRequest(series, season, episode))?.let { toMetadata(it, still = true) }

    override suspend fun episodeMetadata(series: SeriesRecord, season: Int, episode: Int): TitleMetadata? {
        val record = metadata.episode(request(series), season, episode) ?: return null
        return withContext(format) { toMetadata(record, still = true) }
    }

    override fun openFilm(key: String) = navigation.openFilm(key)

    override fun openUrl(url: String) = navigation.openUrl(url)

    private fun request(film: FilmRecord) = MetadataRequest(MediaType.MOVIE, film.name, film.year, contentKey = film.key)

    private fun request(series: SeriesRecord) = MetadataRequest(MediaType.SERIES, series.name, series.year, contentKey = series.key)

    /** As [MetadataService.episode] builds it, so the memory cache is asked with the same key. */
    private fun episodeRequest(series: SeriesRecord, season: Int, episode: Int) =
        request(series).copy(type = MediaType.EPISODE, season = season, episode = episode)

    /**
     * A record as the page draws it: artwork sized for where it is drawn (spec 41 §9.5); an
     * episode's backdrop is its still on the episode card.
     */
    private fun toMetadata(record: MetadataRecord, still: Boolean = false) = TitleMetadata(
        title = record.title.ifBlank { null },
        overview = record.overview,
        backdropUrl = Artwork.url(record.backdrop, if (still) Artwork.STILL else Artwork.BACKDROP),
        posterUrl = Artwork.url(record.poster, Artwork.POSTER_WALL),
        year = record.year,
        runtimeMinutes = record.runtimeMinutes,
        rating = record.rating,
        cast = record.cast.map { CastCard(it.name, it.character?.ifBlank { null }, Artwork.url(it.profile, Artwork.PROFILE), Initials.of(it.name)) },
        sourceName = record.provider.displayName,
        sourceUrl = record.attributionUrl ?: record.provider.home,
        detailsLoaded = record.detailsLoaded,
        similar = record.similar.map { SimilarReference(it.externalId, it.title, it.alternativeTitles, it.year, it.poster) },
    )
}

/** Where a details page goes: the player, another film page, a web page. */
interface TitleNavigation {
    fun play(key: String, startMs: Long)

    fun openFilm(key: String)

    /** Opens [url] in whatever the TV has; nothing happens when it has nothing (VOD-FR-66). */
    fun openUrl(url: String)
}
