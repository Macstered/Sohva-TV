package com.sohva.tv.app.home

import com.sohva.tv.app.profile.ChannelStarter
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.library.AppTitleEnvironment
import com.sohva.tv.app.library.TitleNavigation
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.app.navigation.CatalogueMode
import com.sohva.tv.core.data.home.RecentChannel
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.net.metadata.Artwork
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.feature.home.ChannelCard
import com.sohva.tv.feature.home.HeroDetails
import com.sohva.tv.feature.home.HeroSubject
import com.sohva.tv.feature.home.HomeEnvironment
import com.sohva.tv.feature.home.ResumeCard
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

/**
 * Home's side of the graph (plan/03 §4.6): the process's Continue watching projection, the recent
 * channels, the hero's lookups (the same the details pages and the guide hero make, spec 02
 * HOME-FR-66…71) and the routes of spec 02 §3.2. Every asynchronous route checks that Home is
 * still on top (SHELL-FR-17).
 */
class AppHomeEnvironment(private val graph: AppGraph, private val stack: BackStack<AppRoute>) : HomeEnvironment {
    private val io get() = graph.dispatchers.io

    // The details pages' lookups, without their navigation.
    private val titles = AppTitleEnvironment(
        graph,
        object : TitleNavigation {
            override fun play(key: String, startMs: Long) = Unit
            override fun openFilm(key: String) = Unit
            override fun openUrl(url: String) = Unit
        },
    )

    override val resume: StateFlow<ResumeState> get() = graph.continueFeed.state

    override fun retryResume() = graph.continueFeed.retry()

    override suspend fun recentChannels(now: Long): List<RecentChannel> = graph.data.home.recentChannels(now)

    override val timeZone: Flow<String?> get() = graph.appZone.flowOn(io)

    override fun now(): Long = System.currentTimeMillis()

    override val lowMemory: Boolean get() = graph.player.lowMemory

    override suspend fun heroDetails(subject: HeroSubject): HeroDetails? = when (subject) {
        is HeroSubject.Resume -> if (subject.card.isEpisode) episode(subject.card) else film(subject.card)
        is HeroSubject.Channel -> channel(subject.card)
        HeroSubject.Welcome -> null
    }

    /** HOME-FR-66: the match's overview, else the provider's plot; the match's backdrop only. */
    private suspend fun film(card: ResumeCard): HeroDetails? {
        val film = graph.data.titles.film(card.item.contentKey) ?: return null
        val match = titles.cachedFilmMetadata(film) ?: titles.filmMetadata(film)
        return HeroDetails(match?.overview?.takeIf { it.isNotBlank() } ?: film.plot, match?.backdropUrl)
    }

    /**
     * HOME-FR-67: the provider's episode plot, else the episode match's overview, else the series
     * match's, else the provider's series plot; the series match's backdrop, else the provider's.
     */
    private suspend fun episode(card: ResumeCard): HeroDetails? {
        val series = graph.data.titles.series(card.item.groupKey) ?: return null
        val season = card.item.season ?: return null
        val number = card.item.episode ?: return null
        val plot = graph.data.titles.episodes(series.key).first().firstOrNull { it.key == card.item.contentKey }?.plot
        val seriesMatch = titles.cachedSeriesMetadata(series) ?: titles.seriesMetadata(series)
        val synopsis = plot?.takeIf { it.isNotBlank() }
            ?: titles.episodeMetadata(series, season, number)?.overview?.takeIf { it.isNotBlank() }
            ?: seriesMatch?.overview?.takeIf { it.isNotBlank() }
            ?: series.plot
        return HeroDetails(synopsis, seriesMatch?.backdropUrl ?: series.backdropUrl)
    }

    /** HOME-FR-70: the programme's match gives the picture; a channel's hero has no synopsis line. */
    private suspend fun channel(card: ChannelCard): HeroDetails? {
        val title = card.channel.programme?.title ?: return null
        val record = graph.metadata.cached(MetadataRequest(MediaType.PROGRAMME, title)) ?: graph.metadata.enrich(MetadataRequest(MediaType.PROGRAMME, title))
        val picture = record?.let { Artwork.url(it.backdrop, Artwork.BACKDROP) ?: Artwork.url(it.poster, Artwork.BACKDROP) }
        return HeroDetails(null, picture)
    }

    /**
     * The resume route (spec 01 SHELL-FR-28): `[Home, Catalogue, details, player]`, so Back walks
     * the title's pages; from the saved position, or the start.
     */
    override fun resume(card: ResumeCard, fromStart: Boolean) {
        val start = if (fromStart) 0L else card.item.positionMs
        val player = AppRoute.VodPlayer(card.item.contentKey, start)
        stack.resetTo(
            if (card.isEpisode) {
                listOf(AppRoute.Home, AppRoute.Catalogue(CatalogueMode.SERIES), AppRoute.SeriesDetails(card.item.groupKey), player)
            } else {
                listOf(AppRoute.Home, AppRoute.Catalogue(CatalogueMode.MOVIES), AppRoute.FilmDetails(card.item.contentKey), player)
            },
        )
    }

    /** HOME-FR-40: completed at the known duration (else 0); the card leaves the row. */
    override suspend fun markWatched(card: ResumeCard) = graph.data.progress.markWatched(card.item.contentKey, card.item.durationMs)

    /** Deletes this copy's progress for the profile, not marked watched. */
    override suspend fun remove(card: ResumeCard) = graph.data.progress.forget(card.item.contentKey)

    override fun playChannel(card: ChannelCard) = ChannelStarter(graph, stack).play(card.channel.key, forGuide = true)

    override fun openGuide() {
        stack.push(AppRoute.Guide)
    }
}
