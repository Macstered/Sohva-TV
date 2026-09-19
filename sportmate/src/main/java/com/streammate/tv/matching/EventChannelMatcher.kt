package com.streammate.tv.matching

import com.streammate.tv.core.model.TodayEvent
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class ChannelMatchConfidence {
    AVAILABLE,
    POSSIBLE,
    REJECTED,
}

enum class ManualMatchDecision(val storedValue: String) {
    CONFIRMED("confirmed"),
    REJECTED("rejected");

    companion object {
        fun fromStoredValue(value: String): ManualMatchDecision? = entries
            .firstOrNull { it.storedValue == value }
    }
}

enum class MatchCandidateSource {
    XMLTV_PROGRAMME,
    M3U_CHANNEL_NAME,
}

data class ProgrammeCandidate(
    val channelId: String,
    val channelName: String,
    val programmeId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val startEpochMillis: Long,
    val stopEpochMillis: Long,
    val source: MatchCandidateSource = MatchCandidateSource.XMLTV_PROGRAMME,
)

data class EventChannelMatch(
    val eventId: String,
    val channelId: String,
    val channelName: String,
    val programmeId: String,
    val programmeTitle: String,
    val programmeStartEpochMillis: Long,
    val startOffsetMinutes: Long,
    val confidence: ChannelMatchConfidence,
    val score: Int,
    val manualDecision: ManualMatchDecision?,
    val source: MatchCandidateSource,
    /** A time usable for kickoff comparison; an unzoned provider clock cannot supply an offset. */
    val hasExplicitStartTime: Boolean,
    val automaticConfidence: ChannelMatchConfidence = confidence,
) {
    fun withDecision(decision: ManualMatchDecision?): EventChannelMatch = copy(
        confidence = when (decision) {
            ManualMatchDecision.CONFIRMED -> ChannelMatchConfidence.AVAILABLE
            ManualMatchDecision.REJECTED -> ChannelMatchConfidence.REJECTED
            null -> automaticConfidence
        },
        manualDecision = decision,
    )
}

class EventChannelMatcher {
    fun match(
        events: List<TodayEvent>,
        candidates: List<ProgrammeCandidate>,
        aliases: Map<String, Set<String>>,
        decisions: Map<Pair<String, String>, ManualMatchDecision>,
    ): Map<String, List<EventChannelMatch>> {
        val accumulator = accumulator(events, aliases, decisions)
        candidates.forEach(accumulator::add)
        return accumulator.finish()
    }

    fun accumulator(
        events: List<TodayEvent>,
        aliases: Map<String, Set<String>>,
        decisions: Map<Pair<String, String>, ManualMatchDecision>,
    ) = Accumulator(events, aliases, decisions)

    /** Retains winners only, never programme descriptions or normalized candidate corpora. */
    inner class Accumulator internal constructor(
        events: List<TodayEvent>,
        aliases: Map<String, Set<String>>,
        private val decisions: Map<Pair<String, String>, ManualMatchDecision>,
    ) {
        private val states = events.map { event ->
            EventState(event, teamVariants(event.home, aliases), teamVariants(event.away, aliases))
        }

        fun add(candidate: ProgrammeCandidate) {
            val prepared = prepareCandidate(candidate)
            for (state in states) {
                val automatic = scoreCandidate(state.event, prepared, state.home, state.away, false)
                if (automatic != null) {
                    val previous = state.automatic[candidate.channelId]
                    if (previous == null || candidateOrder.compare(automatic, previous) < 0) {
                        state.automatic[candidate.channelId] = automatic
                    }
                    state.fallback.remove(candidate.channelId)
                } else if (candidate.channelId !in state.automatic &&
                    decisions[state.event.id to candidate.channelId] != null
                ) {
                    val fallback = scoreCandidate(state.event, prepared, state.home, state.away, true) ?: continue
                    val previous = state.fallback[candidate.channelId]
                    if (previous == null || candidateOrder.compare(fallback, previous) < 0) {
                        state.fallback[candidate.channelId] = fallback
                    }
                }
            }
        }

        fun finish(): Map<String, List<EventChannelMatch>> = states.associate { state ->
            state.event.id to (state.automatic.values.asSequence() + state.fallback.values.asSequence())
                .map { it.withDecision(decisions[it.eventId to it.channelId]) }
                .sortedWith(
                    compareBy<EventChannelMatch> { confidenceRank(it.confidence) }
                        .thenByDescending { it.score }
                        .thenBy { abs(it.startOffsetMinutes) }
                        .thenBy { it.channelName }
                        .thenBy { it.channelId },
                ).toList()
        }
    }

    private class EventState(val event: TodayEvent, val home: Set<String>, val away: Set<String>) {
        val automatic = mutableMapOf<String, EventChannelMatch>()
        val fallback = mutableMapOf<String, EventChannelMatch>()
    }

    private fun scoreCandidate(
        event: TodayEvent,
        prepared: PreparedCandidate,
        homeVariants: Set<String>,
        awayVariants: Set<String>,
        allowWithoutTeam: Boolean,
    ): EventChannelMatch? {
        val candidate = prepared.candidate
        if (event.startEpochMillis <= 0) return null
        val channelStartOffset = prepared.schedule?.startOffsetMinutes(event)
        val hasExplicitStartTime = candidate.source == MatchCandidateSource.XMLTV_PROGRAMME ||
            channelStartOffset != null
        val offsetMinutes = when (candidate.source) {
            MatchCandidateSource.XMLTV_PROGRAMME ->
                (candidate.startEpochMillis - event.startEpochMillis) / MILLIS_PER_MINUTE
            MatchCandidateSource.M3U_CHANNEL_NAME -> channelStartOffset ?: 0L
        }
        val absoluteOffset = abs(offsetMinutes)
        if (
            candidate.source == MatchCandidateSource.XMLTV_PROGRAMME &&
            absoluteOffset > MAX_START_DELTA_MINUTES
        ) return null

        val homeMatched = homeVariants.any(prepared.corpus::contains)
        val awayMatched = awayVariants.any(prepared.corpus::contains)
        if (!homeMatched && !awayMatched && !allowWithoutTeam) return null

        val bothTeams = homeMatched && awayMatched
        if (
            candidate.source == MatchCandidateSource.M3U_CHANNEL_NAME &&
            hasExplicitStartTime &&
            absoluteOffset > MAX_START_DELTA_MINUTES &&
            !bothTeams &&
            !allowWithoutTeam
        ) return null
        if (
            candidate.source == MatchCandidateSource.M3U_CHANNEL_NAME &&
            !hasExplicitStartTime &&
            !bothTeams &&
            !allowWithoutTeam
        ) return null
        val teamScore = when {
            bothTeams -> 70
            homeMatched || awayMatched -> 38
            else -> 0
        }
        val timeScore = when {
            !hasExplicitStartTime -> 10
            absoluteOffset <= 15 -> 30
            absoluteOffset <= 30 -> 25
            absoluteOffset <= 60 -> 15
            absoluteOffset <= MAX_START_DELTA_MINUTES -> 5
            else -> 0
        }
        val automaticConfidence = if (
            bothTeams && prepared.schedule?.dateSupportsMatch(event, channelStartOffset) != false &&
            (!hasExplicitStartTime || absoluteOffset <= AUTO_MATCH_DELTA_MINUTES)
        ) {
            ChannelMatchConfidence.AVAILABLE
        } else {
            ChannelMatchConfidence.POSSIBLE
        }
        return EventChannelMatch(
            eventId = event.id,
            channelId = candidate.channelId,
            channelName = candidate.channelName,
            programmeId = candidate.programmeId,
            programmeTitle = candidate.title,
            programmeStartEpochMillis = if (candidate.source == MatchCandidateSource.XMLTV_PROGRAMME) {
                candidate.startEpochMillis
            } else {
                event.startEpochMillis + offsetMinutes * MILLIS_PER_MINUTE
            },
            startOffsetMinutes = offsetMinutes,
            confidence = automaticConfidence,
            score = teamScore + timeScore,
            manualDecision = null,
            source = candidate.source,
            hasExplicitStartTime = hasExplicitStartTime,
        )
    }

    private fun prepareCandidate(candidate: ProgrammeCandidate): PreparedCandidate = PreparedCandidate(
        candidate = candidate,
        corpus = " " + MatchTextNormalizer.normalize(
            listOfNotNull(candidate.title, candidate.subtitle, candidate.description).joinToString(" "),
        ) + " ",
        schedule = if (candidate.source == MatchCandidateSource.M3U_CHANNEL_NAME) {
            ChannelNameSchedule.parse(candidate.channelName)
        } else null,
    )

    // The old read visited programmes chronologically. Keep earlier starts on
    // otherwise equal scores; a final id tie-break makes page order irrelevant.
    private val candidateOrder =
        compareByDescending<EventChannelMatch> { it.score }
            .thenBy { abs(it.startOffsetMinutes) }
            .thenBy { if (it.source == MatchCandidateSource.XMLTV_PROGRAMME) 0 else 1 }
            .thenBy { it.programmeStartEpochMillis }
            .thenBy { it.programmeId }

    private fun teamVariants(teamName: String, aliases: Map<String, Set<String>>): Set<String> {
        val canonical = MatchTextNormalizer.normalize(teamName)
        return buildSet {
            add(canonical)
            addAll(aliases[canonical].orEmpty())
        }.filter { it.length >= MIN_TERM_LENGTH }.mapTo(mutableSetOf()) { " $it " }
    }

    private fun confidenceRank(confidence: ChannelMatchConfidence): Int = when (confidence) {
        ChannelMatchConfidence.AVAILABLE -> 0
        ChannelMatchConfidence.POSSIBLE -> 1
        ChannelMatchConfidence.REJECTED -> 2
    }

    companion object {
        const val MAX_START_DELTA_MINUTES = 120L
        private const val AUTO_MATCH_DELTA_MINUTES = 30L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MIN_TERM_LENGTH = 3
    }

    private data class PreparedCandidate(
        val candidate: ProgrammeCandidate,
        val corpus: String,
        val schedule: ChannelNameSchedule?,
    )
}

internal object MatchTextNormalizer {
    private val markPattern = Regex("\\p{M}+")
    private val punctuationPattern = Regex("[^a-z0-9]+")
    private val whitespacePattern = Regex("\\s+")

    fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .replace(markPattern, "")
        .lowercase(Locale.ROOT)
        .replace(punctuationPattern, " ")
        .trim()
        .replace(whitespacePattern, " ")
}
