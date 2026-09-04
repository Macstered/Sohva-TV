package com.streammate.tv.matching

import com.streammate.tv.core.database.ChannelNameCandidateRow
import com.streammate.tv.core.database.EventChannelDecisionEntity
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.database.ProgrammeCandidateRow
import com.streammate.tv.core.database.TeamAliasEntity
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TeamAliasRepository(private val dao: GuideDao) {
    suspend fun aliasesFor(sport: SportType): Map<String, Set<String>> {
        val aliases = defaultAliases[sport].orEmpty()
            .mapKeys { (team, _) -> MatchTextNormalizer.normalize(team) }
            .mapValues { (_, values) -> values.mapTo(mutableSetOf(), MatchTextNormalizer::normalize) }
            .toMutableMap()
        dao.teamAliases(sport.name).forEach { alias ->
            aliases.getOrPut(alias.normalizedCanonicalName, ::mutableSetOf)
                .add(alias.normalizedAlias)
        }
        return aliases
    }

    suspend fun addAlias(sport: SportType, canonicalTeamName: String, alias: String) {
        val normalizedCanonical = MatchTextNormalizer.normalize(canonicalTeamName)
        val normalizedAlias = MatchTextNormalizer.normalize(alias)
        require(normalizedCanonical.isNotBlank() && normalizedAlias.isNotBlank())
        dao.upsertTeamAlias(TeamAliasEntity(sport.name, normalizedCanonical, normalizedAlias))
    }

    private companion object {
        val defaultAliases = mapOf(
            SportType.FOOTBALL to mapOf(
                "Manchester United" to setOf("Man Utd", "Man United", "Manchester Utd"),
                "Manchester City" to setOf("Man City"),
                "Tottenham Hotspur" to setOf("Tottenham", "Spurs"),
                "Paris Saint-Germain" to setOf("PSG", "Paris SG"),
                "Inter" to setOf("Inter Milan", "Internazionale"),
                "Bayern München" to setOf("Bayern Munich", "Bayern"),
            ),
        )
    }
}

class EventChannelDecisionRepository(
    private val dao: GuideDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun decisionsFor(eventIds: List<String>): Map<Pair<String, String>, ManualMatchDecision> {
        if (eventIds.isEmpty()) return emptyMap()
        return dao.eventChannelDecisions(eventIds).mapNotNull { entity ->
            ManualMatchDecision.fromStoredValue(entity.decision)?.let { decision ->
                (entity.eventId to entity.channelId) to decision
            }
        }.toMap()
    }

    suspend fun set(eventId: String, channelId: String, decision: ManualMatchDecision?) {
        if (decision == null) {
            dao.deleteEventChannelDecision(eventId, channelId)
        } else {
            dao.upsertEventChannelDecision(
                EventChannelDecisionEntity(
                    eventId = eventId,
                    channelId = channelId,
                    decision = decision.storedValue,
                    updatedAtEpochMillis = clock(),
                ),
            )
        }
    }
}

class EventChannelMatchingRepository(
    private val dao: GuideDao,
    private val aliasRepository: TeamAliasRepository = TeamAliasRepository(dao),
    private val decisionRepository: EventChannelDecisionRepository = EventChannelDecisionRepository(dao),
    private val matcher: EventChannelMatcher = EventChannelMatcher(),
) {
    suspend fun matchesFor(events: List<TodayEvent>): Map<String, List<EventChannelMatch>> {
        val matchableEvents = events.filter { it.startEpochMillis > 0 }
        if (matchableEvents.isEmpty()) return events.associate { it.id to emptyList() }
        val margin = EventChannelMatcher.MAX_START_DELTA_MINUTES * MILLIS_PER_MINUTE
        val programmeCandidates = dao.programmeCandidates(
            fromEpochMillis = matchableEvents.minOf { it.startEpochMillis } - margin,
            toEpochMillis = matchableEvents.maxOf { it.startEpochMillis } + margin,
        ).map { it.toDomain() }
        val channelNameCandidates = dao.channelNameCandidates().map { it.toDomain() }
        val aliases = mutableMapOf<String, MutableSet<String>>()
        for (sport in matchableEvents.map(TodayEvent::sport).distinct()) {
            aliasRepository.aliasesFor(sport).forEach { (team, variants) ->
                aliases.getOrPut(team, ::mutableSetOf).addAll(variants)
            }
        }
        val decisions = decisionRepository.decisionsFor(events.map(TodayEvent::id))
        return withContext(Dispatchers.Default) {
            matcher.match(
                events = events,
                candidates = programmeCandidates + channelNameCandidates,
                aliases = aliases,
                decisions = decisions,
            )
        }
    }

    suspend fun setDecision(eventId: String, channelId: String, decision: ManualMatchDecision?) {
        decisionRepository.set(eventId, channelId, decision)
    }

    private fun ProgrammeCandidateRow.toDomain() = ProgrammeCandidate(
        channelId = channelId,
        channelName = channelName,
        programmeId = programmeId,
        title = programmeTitle,
        subtitle = programmeSubtitle,
        description = programmeDescription,
        startEpochMillis = programmeStartEpochMillis,
        stopEpochMillis = programmeStopEpochMillis,
    )

    private fun ChannelNameCandidateRow.toDomain() = ProgrammeCandidate(
        channelId = channelId,
        channelName = channelName,
        programmeId = "m3u-name:$channelId",
        title = channelName,
        subtitle = null,
        description = null,
        startEpochMillis = 0,
        stopEpochMillis = 0,
        source = MatchCandidateSource.M3U_CHANNEL_NAME,
    )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
