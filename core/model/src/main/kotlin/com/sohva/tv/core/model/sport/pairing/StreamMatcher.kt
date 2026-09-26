package com.sohva.tv.core.model.sport.pairing

import com.sohva.tv.core.model.sport.SportEvent
import kotlin.math.abs

/** Where a stream match was found (SPORT-FR-74): a guide programme or the channel's own name. */
enum class MatchSource { GUIDE, NAME }

/** In presentation order (SPORT-FR-117). */
enum class Confidence { AVAILABLE, POSSIBLE, REJECTED }

/** The viewer's choice for one game and channel (SPORT-FR-76); none means the automatic confidence. */
enum class Decision { CONFIRMED, REJECTED }

/**
 * One text to pair (SPORT-FR-103, -104): a guide programme on a channel, or the channel's name
 * ([MatchSource.NAME], text = the name, times unused). Released once scored.
 */
data class Candidate(
    val channelKey: String,
    val channelName: String,
    val programmeId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val startMillis: Long,
    val source: MatchSource,
)

/** A stream for a game (SPORT-FR-74): small, kept per game and channel. */
data class StreamMatch(
    val eventId: String,
    val channelKey: String,
    val channelName: String,
    val programmeId: String,
    /** The guide programme's title, or the channel name for a name match. */
    val programmeTitle: String,
    val source: MatchSource,
    val programmeStartMillis: Long,
    /** Programme or stated start minus kick-off; 0 without an explicit start. */
    val offsetMinutes: Long,
    val explicitStart: Boolean,
    val score: Int,
    val automatic: Confidence,
    val decision: Decision? = null,
) {
    val confidence: Confidence
        get() = when (decision) {
            Decision.CONFIRMED -> Confidence.AVAILABLE
            Decision.REJECTED -> Confidence.REJECTED
            null -> automatic
        }

    /** Restore (null) returns to the automatic confidence (SPORT-FR-76). */
    fun withDecision(decision: Decision?): StreamMatch = copy(decision = decision)

    companion object {
        /** SPORT-FR-117 result order: confidence, score, closeness, name, key. */
        val ORDER: Comparator<StreamMatch> = compareBy<StreamMatch> { it.confidence.ordinal }
            .thenByDescending { it.score }.thenBy { abs(it.offsetMinutes) }.thenBy { it.channelName }.thenBy { it.channelKey }
    }
}

/**
 * Pairs one scan's candidates with today's games (spec 60 SPORT-FR-113…116, §9 "Pairing"). Built
 * once per scan with a token index (each variant's first word → its variants), so a candidate costs
 * one tokenisation and a few lookups however many games there are. [add] keeps only the best
 * candidate per game and channel; nothing else of a candidate outlives the call.
 */
class StreamMatcher(events: List<SportEvent>, aliases: Map<String, Set<String>>, private val decisions: Map<Pair<String, String>, Decision>) {
    private val events: List<SportEvent> = events.filter { it.startMillis > 0 }

    private class Variant(val words: List<String>, val event: Int, val side: Int)

    private val index = HashMap<String, MutableList<Variant>>()
    private val decidedByChannel = HashMap<String, MutableList<Int>>()

    /** The winner per (game index, channel key), with whether it came in as a fallback. */
    private class Kept(val match: StreamMatch, val fallback: Boolean)

    private val kept = HashMap<Pair<Int, String>, Kept>()

    init {
        this.events.forEachIndexed { i, e ->
            for ((side, team) in listOf(HOME to e.home.name, AWAY to e.away.name)) {
                for (v in TeamVariants.of(team, aliases)) {
                    val words = v.split(' ')
                    index.getOrPut(words.first()) { ArrayList() } += Variant(words, i, side)
                }
            }
        }
        val position = this.events.withIndex().associate { it.value.id to it.index }
        for ((key, _) in decisions) {
            val i = position[key.first] ?: continue
            decidedByChannel.getOrPut(key.second) { ArrayList() } += i
        }
    }

    fun add(c: Candidate) {
        val text = if (c.source == MatchSource.GUIDE) listOfNotNull(c.title, c.subtitle, c.description).joinToString(" ") else c.title
        val words = MatchText.words(text)
        // Whole words only: a variant matches at a word position when all its words follow (SPORT-FR-106).
        val mentions = HashMap<Int, Int>()
        for (at in words.indices) {
            val variants = index[words[at]] ?: continue
            for (v in variants) {
                if (at + v.words.size <= words.size && (1 until v.words.size).all { words[at + it] == v.words[it] }) {
                    mentions[v.event] = (mentions[v.event] ?: 0) or v.side
                }
            }
        }
        val decided = decidedByChannel[c.channelKey].orEmpty()
        if (mentions.isEmpty() && decided.isEmpty()) return
        val schedule = if (c.source == MatchSource.NAME) NameSchedule.of(c.title) else null
        for (i in (mentions.keys + decided).toSet()) {
            val scored = score(i, mentions[i] ?: 0, c, schedule) ?: continue
            keep(i, c.channelKey, Kept(scored, fallback = mentions[i] == null))
        }
    }

    private fun score(i: Int, sides: Int, c: Candidate, schedule: NameSchedule?): StreamMatch? {
        val event = events[i]
        val kickOff = event.startMillis
        val both = sides == HOME or AWAY
        val fallback = sides == 0
        val explicit: Boolean
        val offset: Long
        val dateSupports: Boolean
        if (schedule == null) {
            explicit = true
            offset = (c.startMillis - kickOff) / MINUTE
            // The guide window applies to fallbacks too (SPORT-FR-116).
            if (abs(offset) > WINDOW) return null
            dateSupports = true
        } else {
            val stated = schedule.offsetMinutes(kickOff)
            explicit = stated != null
            offset = stated ?: 0
            dateSupports = schedule.dateSupports(kickOff, explicit)
            if (!fallback && !both && (!explicit || abs(offset) > WINDOW)) return null
        }
        val teamScore = when {
            both -> BOTH_TEAMS
            fallback -> 0
            else -> ONE_TEAM
        }
        val timeScore = when {
            !explicit -> 10
            abs(offset) <= 15 -> 30
            abs(offset) <= 30 -> 25
            abs(offset) <= 60 -> 15
            abs(offset) <= WINDOW -> 5
            else -> 0
        }
        val automatic = if (both && dateSupports && (!explicit || abs(offset) <= CLOSE)) Confidence.AVAILABLE else Confidence.POSSIBLE
        return StreamMatch(
            event.id, c.channelKey, c.channelName, c.programmeId, c.title, c.source,
            if (schedule == null) c.startMillis else kickOff + offset * MINUTE, offset, explicit, teamScore + timeScore, automatic,
        )
    }

    /** An automatic candidate replaces a fallback; otherwise the better one stays (SPORT-FR-116). */
    private fun keep(i: Int, channel: String, candidate: Kept) {
        val key = i to channel
        val old = kept[key]
        val wins = when {
            old == null -> true
            old.fallback != candidate.fallback -> old.fallback
            else -> BETTER.compare(candidate.match, old.match) < 0
        }
        if (wins) kept[key] = candidate
    }

    /** Every game's streams with the decisions applied, in result order (SPORT-FR-117). */
    fun finish(): Map<String, List<StreamMatch>> {
        val out = LinkedHashMap<String, MutableList<StreamMatch>>()
        events.forEach { out[it.id] = ArrayList() }
        for (k in kept.values) {
            val m = k.match
            out.getValue(m.eventId) += m.withDecision(decisions[m.eventId to m.channelKey])
        }
        out.values.forEach { it.sortWith(StreamMatch.ORDER) }
        return out
    }

    private companion object {
        const val HOME = 1
        const val AWAY = 2
        const val MINUTE = 60_000L
        const val WINDOW = 120
        const val CLOSE = 30
        const val BOTH_TEAMS = 70
        const val ONE_TEAM = 38

        /** Higher score, then closer, then the guide, then the earlier programme, then its id: page order never matters. */
        val BETTER: Comparator<StreamMatch> = compareByDescending<StreamMatch> { it.score }.thenBy { abs(it.offsetMinutes) }
            .thenBy { it.source.ordinal }.thenBy { it.programmeStartMillis }.thenBy { it.programmeId }
    }
}
