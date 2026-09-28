package com.sohva.tv.app.home

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.model.metadata.MediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Continue watching's landscape pictures for titles matched before matches kept their backdrop
 * (decision "Library card art"): after Home's first read, each library card without a picture asks
 * TMDB's record by id (its 30-day cache, else one request) and the backdrop is stored with the
 * match, so later starts read it with the row. At most one lookup per card, once per title;
 * nothing when metadata or TMDB is off. [stored] tells Home to read the row again.
 */
class HomeCardArt(private val graph: AppGraph) {
    private val _stored = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stored: SharedFlow<Unit> = _stored.asSharedFlow()

    suspend fun fill() {
        if (!graph.metadata.settings.current().enabled) return
        val items = (graph.continueFeed.state.value as? ResumeState.Ready)?.items ?: return
        // A film's match is its copy's; an episode's is its series'.
        val wanted = items.filter { it.discover == null && it.backdrop == null }
            .associate { item -> if (item.season != null) item.groupKey to MediaType.SERIES else item.contentKey to MediaType.MOVIE }
        if (graph.metadata.service.fillBackdrops(wanted)) _stored.tryEmit(Unit)
    }
}
