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
    val hasExplicitStartTime: Boolean,
)

class EventChannelMatcher {
    fun match(
        events: List<TodayEvent>,
        candidates: List<ProgrammeCandidate>,
        aliases: Map<String, Set<String>>,
        decisions: Map<Pair<String, String>, ManualMatchDecision>,
    ): Map<String, List<EventChannelMatch>> {
        val preparedByChannel = candidates
            .map(::prepareCandidate)
            .groupBy { it.candidate.channelId }
        return events.associate { event ->
            val homeVariants = teamVariants(event.home, aliases)
            val awayVariants = teamVariants(event.away, aliases)
            val channelMatches = preparedByChannel.mapNotNull { (channelId, options) ->
                val decision = decisions[event.id to channelId]
                val automaticallyScored = options.mapNotNull { candidate ->
                    scoreCandidate(event, candidate, homeVariants, awayVariants, allowWithoutTeam = false)
                }
                val bestAutomatic = automaticallyScored.bestCandidate()
                val selected = bestAutomatic ?: if (decision != null) {
                    options.mapNotNull { candidate ->
                        scoreCandidate(event, candidate, homeVariants, awayVariants, allowWithoutTeam = true)
                    }.bestCandidate()
                } else {
                    null
                }
                selected?.applyDecision(decision)
            }.sortedWith(
                compareBy<EventChannelMatch> { confidenceRank(it.confidence) }
                    .thenByDescending { it.score }
                    .thenBy { abs(it.startOffsetMinutes) }
                    .thenBy { it.channelName },
            )
            event.id to channelMatches
        }
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
        val hasExplicitStartTime = candidate.source == MatchCandidateSource.XMLTV_PROGRAMME ||
            prepared.channelStartMinute != null
        val offsetMinutes = when (candidate.source) {
            MatchCandidateSource.XMLTV_PROGRAMME ->
                (candidate.startEpochMillis - event.startEpochMillis) / MILLIS_PER_MINUTE
            MatchCandidateSource.M3U_CHANNEL_NAME -> prepared.channelStartMinute
                ?.let { closestMinuteOffset(event.startMinuteOfDay, it) }
                ?: 0L
        }
        val absoluteOffset = abs(offsetMinutes)
        if (
            candidate.source == MatchCandidateSource.XMLTV_PROGRAMME &&
            absoluteOffset > MAX_START_DELTA_MINUTES
        ) return null

        val homeMatched = homeVariants.any { prepared.corpus.containsTerm(it) }
        val awayMatched = awayVariants.any { prepared.corpus.containsTerm(it) }
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
            bothTeams && (!hasExplicitStartTime || absoluteOffset <= AUTO_MATCH_DELTA_MINUTES)
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
        corpus = MatchTextNormalizer.normalize(
            listOfNotNull(candidate.title, candidate.subtitle, candidate.description).joinToString(" "),
        ),
        channelStartMinute = if (candidate.source == MatchCandidateSource.M3U_CHANNEL_NAME) {
            channelTimePattern.find(candidate.channelName)?.let { match ->
                match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
            }
        } else {
            null
        },
    )

    private fun closestMinuteOffset(eventMinute: Int, candidateMinute: Int): Long {
        val raw = candidateMinute - eventMinute
        return when {
            raw > MINUTES_PER_HALF_DAY -> (raw - MINUTES_PER_DAY).toLong()
            raw < -MINUTES_PER_HALF_DAY -> (raw + MINUTES_PER_DAY).toLong()
            else -> raw.toLong()
        }
    }

    private fun List<EventChannelMatch>.bestCandidate(): EventChannelMatch? = sortedWith(
        compareByDescending<EventChannelMatch> { it.score }
            .thenBy { abs(it.startOffsetMinutes) }
            .thenBy { if (it.source == MatchCandidateSource.XMLTV_PROGRAMME) 0 else 1 },
    ).firstOrNull()

    private fun EventChannelMatch.applyDecision(decision: ManualMatchDecision?): EventChannelMatch = copy(
        confidence = when (decision) {
            ManualMatchDecision.CONFIRMED -> ChannelMatchConfidence.AVAILABLE
            ManualMatchDecision.REJECTED -> ChannelMatchConfidence.REJECTED
            null -> confidence
        },
        manualDecision = decision,
    )

    private fun teamVariants(teamName: String, aliases: Map<String, Set<String>>): Set<String> {
        val canonical = MatchTextNormalizer.normalize(teamName)
        return buildSet {
            add(canonical)
            addAll(aliases[canonical].orEmpty())
        }.filterTo(mutableSetOf()) { it.length >= MIN_TERM_LENGTH }
    }

    private fun String.containsTerm(term: String): Boolean = " $this ".contains(" $term ")

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
        private const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTES_PER_HALF_DAY = MINUTES_PER_DAY / 2
        private val channelTimePattern = Regex("""(?<!\d)([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)""")
    }

    private data class PreparedCandidate(
        val candidate: ProgrammeCandidate,
        val corpus: String,
        val channelStartMinute: Int?,
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
