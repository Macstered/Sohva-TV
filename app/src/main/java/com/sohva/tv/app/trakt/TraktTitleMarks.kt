package com.sohva.tv.app.trakt

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.vod.TitleMark
import com.sohva.tv.core.model.vod.TitleMarks
import com.sohva.tv.feature.trakt.marks.TraktMarks
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Trakt's marks for Discover (spec 51 FR-31, -36): the rows of the keys on screen only, for an
 * unrestricted active profile with an account. Building it costs nothing; Trakt and the cache
 * are touched on the first lookup.
 */
class TraktTitleMarks(private val graph: AppGraph) : TitleMarks {
    override val revision: StateFlow<Long> by lazy { graph.data.traktState.revision }

    override suspend fun marks(keys: Collection<String>): Map<String, TitleMark> {
        if (keys.isEmpty()) return emptyMap()
        val trakt = graph.trakt ?: return emptyMap()
        val profile = graph.data.profiles.activeId
        if (!trakt.access.allowed(profile) || trakt.account(profile) == null) return emptyMap()
        return withContext(graph.dispatchers.io) {
            val (imdbs, tmdbs) = TraktMarks.ids(keys)
            TraktMarks.marks(keys, graph.data.traktState.rows(profile, imdbs, tmdbs))
        }
    }
}
