package com.sohva.tv.app.search

import com.sohva.tv.app.profile.ChannelStarter
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.feature.search.ResultKind
import com.sohva.tv.feature.search.SearchEnvironment
import com.sohva.tv.feature.search.SearchResult
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.navigation.BackStack
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Search's side of the graph (spec 03): the groups from [com.sohva.tv.core.data.search.SearchReads],
 * mapped to rows with their texts on the read threads, and the routes of §3.
 */
class AppSearchEnvironment(private val graph: AppGraph, private val stack: BackStack<AppRoute>, private val locale: Locale) : SearchEnvironment {
    private val reads get() = graph.data.search
    private val resources get() = com.sohva.tv.app.AppLocales.texts(graph.app).resources

    private fun joined(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    override suspend fun live(term: String): List<SearchResult> {
        val hits = reads.live(term)
        // Programme times in the chosen zone and the guide's style (spec 03 §8 quirk fixed).
        val labels = TimeLabels(TimeLabels.zoneOf(graph.data.preferences.timeZone.first()), com.sohva.tv.ui.design.text.TimeStyles.of(graph.app, locale))
        val channels = hits.channels.map { c ->
            SearchResult("channel:${c.id}", ResultKind.CHANNEL, c.name, c.groupName?.takeIf { it.isNotBlank() } ?: c.sourceName, c.logoUrl, c.key)
        }
        val programmes = hits.programmes.map { p ->
            val start = p.startAt + p.offsetMinutes * MINUTE_MS
            SearchResult(
                "programme:${p.id}:${p.channelId}", ResultKind.PROGRAMME, p.title,
                joined(p.channelName, labels.dayLabel(start) + " " + labels.guideTime(start)), p.logoUrl, p.channelKey,
            )
        }
        return channels + programmes
    }

    override suspend fun films(term: String): List<SearchResult> = reads.films(term).map { f ->
        SearchResult("movie:${f.key}", ResultKind.MOVIE, f.name, joined(f.groupName, f.year?.toString()), f.posterUrl, f.key)
    }

    override suspend fun series(term: String): List<SearchResult> = reads.series(term).map { s ->
        SearchResult("series:${s.key}", ResultKind.SERIES, s.name, joined(s.groupName, s.year?.toString()), s.posterUrl, s.key)
    }

    // "S1 E2" from the strings, not beta 23's hard-coded Finnish "K1 J2" (spec 03 §10).
    override suspend fun episodes(term: String): List<SearchResult> = reads.episodes(term).map { e ->
        val label = resources.getString(R.string.series_episode_label, e.season, e.number)
        SearchResult("episode:${e.key}", ResultKind.EPISODE, e.name?.takeIf { it.isNotBlank() } ?: label, joined(e.seriesName, label), e.posterUrl, e.key)
    }

    override fun open(result: SearchResult) {
        when (result.kind) {
            // Live, with Back to the guide on that channel (spec 01 SHELL-FR-20).
            ResultKind.CHANNEL, ResultKind.PROGRAMME -> ChannelStarter(graph, stack).play(result.target, forGuide = true)
            ResultKind.MOVIE -> stack.push(AppRoute.FilmDetails(result.target))
            ResultKind.SERIES -> stack.push(AppRoute.SeriesDetails(result.target))
            // From the saved position (a finished episode from the start); only while Search is still on top.
            ResultKind.EPISODE -> graph.appScope.launch(graph.dispatchers.main) {
                val progress = graph.data.progress.of(result.target)
                val start = progress?.takeIf { !it.completed }?.positionMs ?: 0L
                if (stack.top.route == AppRoute.Search) stack.push(AppRoute.VodPlayer(result.target, start))
            }
        }
    }

    override fun leave() {
        stack.pop()
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
