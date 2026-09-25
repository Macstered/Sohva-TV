package com.sohva.tv.app.library

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.feature.library.TitleEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** The details pages' side of the graph (plan/03 §4.6): title reads, progress, marks, episodes and playback. */
class AppTitleEnvironment(private val graph: AppGraph, private val navigation: TitleNavigation) : TitleEnvironment {
    private val titles get() = graph.data.titles
    private val progress get() = graph.data.progress

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
}

/** Where a details page goes: the player. */
fun interface TitleNavigation {
    fun play(key: String, startMs: Long)
}
