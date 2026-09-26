package com.sohva.tv.feature.sport.pairing

import com.sohva.tv.core.data.database.PairingDao
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** What one scan cost (spec 60 §11 "Performance"): pages read and candidates scored. */
data class ScanStats(val channelQueries: Int, val programmeQueries: Int, val candidates: Long)

/**
 * One pairing scan (spec 60 §9 "Pairing"): channels in pages of [CHANNEL_PAGE] in row-id order;
 * for each page its channel names, then that page's programmes in the games' window in pages of
 * [PROGRAMME_PAGE]; every candidate goes straight into the matcher's accumulator and is dropped.
 * The heap holds one page and the per-game winners, never the guide. Cancellation is checked
 * between candidates and the scan yields between pages.
 */
class PairingScan(private val dao: PairingDao) {
    var lastStats: ScanStats = ScanStats(0, 0, 0)
        private set

    suspend fun run(events: List<SportEvent>, aliases: Map<String, Set<String>>, decisions: Map<Pair<String, String>, Decision>): Map<String, List<StreamMatch>> {
        val out = LinkedHashMap<String, List<StreamMatch>>()
        events.forEach { out[it.id] = emptyList() }
        val matchable = events.filter { it.startMillis > 0 }
        if (matchable.isEmpty()) return out
        val matcher = StreamMatcher(matchable, aliases, decisions)
        val from = matchable.minOf { it.startMillis } - WINDOW_MS
        val to = matchable.maxOf { it.startMillis } + WINDOW_MS
        val guides = dao.guideSources().associateBy { it.id }
        var channelQueries = 0
        var programmeQueries = 0
        var candidates = 0L
        var after = 0L
        while (true) {
            val page = dao.channels(after, CHANNEL_PAGE)
            channelQueries++
            if (page.isEmpty()) break
            after = page.last().id
            for (c in page) {
                currentCoroutineContext().ensureActive()
                matcher.add(Candidate(c.key, c.name, "m3u-name:${c.key}", c.name, null, null, 0, MatchSource.NAME))
                candidates++
            }
            for ((sourceId, channels) in page.filter { !it.epgId.isNullOrEmpty() }.groupBy { it.sourceId }) {
                val guide = guides[sourceId] ?: continue
                val snapshot = guide.epgSnapshot ?: continue
                // The offset moves the bound, never the indexed column (§9 rule).
                val shift = guide.epgOffsetMinutes * MINUTE
                // Several channels can share one guide id: each gets the programme.
                val byGuideId = channels.groupBy { it.epgId!! }
                var programmeAfter = 0L
                while (true) {
                    val programmes = dao.programmes(sourceId, snapshot, byGuideId.keys.toList(), from - shift, to - shift, programmeAfter, PROGRAMME_PAGE)
                    programmeQueries++
                    if (programmes.isEmpty()) break
                    programmeAfter = programmes.last().id
                    for (p in programmes) {
                        for (c in byGuideId.getValue(p.epgId)) {
                            currentCoroutineContext().ensureActive()
                            matcher.add(Candidate(c.key, c.name, p.programmeKey, p.title, p.subtitle, p.description, p.startAt + shift, MatchSource.GUIDE))
                            candidates++
                        }
                    }
                    if (programmes.size < PROGRAMME_PAGE) break
                    yield()
                }
            }
            if (page.size < CHANNEL_PAGE) break
            yield()
        }
        lastStats = ScanStats(channelQueries, programmeQueries, candidates)
        out.putAll(matcher.finish())
        return out
    }

    companion object {
        const val CHANNEL_PAGE: Int = 256
        const val PROGRAMME_PAGE: Int = 64
        private const val MINUTE = 60_000L

        /** SPORT-FR-104: programmes starting up to two hours either side of the games. */
        private const val WINDOW_MS = 120 * MINUTE
    }
}
