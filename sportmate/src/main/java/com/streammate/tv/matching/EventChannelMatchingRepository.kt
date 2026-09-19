package com.streammate.tv.matching

import com.streammate.tv.core.database.ChannelNameCandidateRow
import com.streammate.tv.core.database.EventChannelDecisionEntity
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.database.ProgrammeCandidateRow
import com.streammate.tv.core.database.TeamAliasEntity
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val cache: EventChannelMatchCache = EventChannelMatchCache(),
) {
    private val matchingMutex = Mutex()
    fun observeInputChanges() = dao.observeSportsMatchGeneration()

    suspend fun cachedMatchesFor(events: List<TodayEvent>): Map<String, List<EventChannelMatch>> =
        applyDecisions(cache.read(events, inputGeneration()), events)

    private suspend fun inputGeneration() = EventChannelMatchCache.fingerprint(dao.sportsMatchGeneration())

    private suspend fun applyDecisions(matches: Map<String, List<EventChannelMatch>>, events: List<TodayEvent>): Map<String, List<EventChannelMatch>> {
        val decisions = decisionRepository.decisionsFor(events.map { it.id })
        return matches.mapValues { (_, values) -> values.map { it.withDecision(decisions[it.eventId to it.channelId]) } }
    }

    suspend fun matchesFor(events: List<TodayEvent>): Map<String, List<EventChannelMatch>> = withContext(Dispatchers.Default) {
        matchingMutex.withLock {
            val generation = inputGeneration()
            val cached = cache.read(events, generation)
            if (events.all { it.id in cached }) return@withLock applyDecisions(cached, events)
            val missing = events.filter { it.id !in cached }
            val matchableEvents = missing.filter { it.startEpochMillis > 0 }
            if (matchableEvents.isEmpty()) return@withLock applyDecisions(cached + missing.associate { it.id to emptyList() }, events)
            val margin = EventChannelMatcher.MAX_START_DELTA_MINUTES * MILLIS_PER_MINUTE
            val from = matchableEvents.minOf { it.startEpochMillis } - margin
            val to = matchableEvents.maxOf { it.startEpochMillis } + margin
            val aliases = mutableMapOf<String, MutableSet<String>>()
            for (sport in matchableEvents.map(TodayEvent::sport).distinct()) {
                aliasRepository.aliasesFor(sport).forEach { (team, variants) ->
                    aliases.getOrPut(team, ::mutableSetOf).addAll(variants)
                }
            }
            val decisions = decisionRepository.decisionsFor(events.map(TodayEvent::id))
            val accumulator = matcher.accumulator(missing, aliases, decisions)
            var afterChannel = Long.MIN_VALUE
            while (true) {
                yield()
                val channels = dao.channelNameCandidatesPage(afterChannel, CHANNEL_PAGE_SIZE)
                if (channels.isEmpty()) break
                for (channel in channels) {
                    currentCoroutineContext().ensureActive()
                    accumulator.add(channel.toDomain())
                }
                addProgrammePages(accumulator, channels.map { it.channelRowId }, from, to)
                afterChannel = channels.last().channelRowId
                if (channels.size < CHANNEL_PAGE_SIZE) break
            }
            currentCoroutineContext().ensureActive()
            // Do not publish/cache a mixture of snapshots if an import or visibility
            // edit raced the scan. The input observer schedules the current generation.
            if (generation != inputGeneration()) return@withLock cachedMatchesFor(events)
            val matches = accumulator.finish()
            cache.write(missing, generation, matches)
            applyDecisions(cached + matches, events)
        }
    }

    private suspend fun addProgrammePages(
        accumulator: EventChannelMatcher.Accumulator,
        channelRowIds: List<Long>,
        from: Long,
        to: Long,
    ) {
        var afterProgramme = Long.MIN_VALUE
        var afterChannel = Long.MIN_VALUE
        while (true) {
            yield()
            val programmes = dao.programmeCandidatesPage(
                channelRowIds, from, to, afterProgramme, afterChannel, PROGRAMME_PAGE_SIZE,
            )
            for (programme in programmes) {
                currentCoroutineContext().ensureActive()
                accumulator.add(programme.toDomain())
            }
            if (programmes.size < PROGRAMME_PAGE_SIZE) break
            afterProgramme = programmes.last().programmeRowId
            afterChannel = programmes.last().channelRowId
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
        const val CHANNEL_PAGE_SIZE = 256
        const val PROGRAMME_PAGE_SIZE = 64
    }
}
