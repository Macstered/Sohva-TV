package com.sohva.tv.feature.sport.pairing

import com.sohva.tv.core.data.database.EventChannelDecisionEntity
import com.sohva.tv.core.data.database.PairingDao
import com.sohva.tv.core.data.database.SportDao
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import com.sohva.tv.core.model.time.Clock
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stream pairing for today's games (spec 60 §4.10). It runs only while someone needs the results
 * — Today on screen ([setActive]) — and never during playback or start-up (§9 rule, new): the
 * cached results apply at once, then only games missing from the cache are scanned. A change of
 * the games or of the pairing inputs (the generation) cancels the running scan, whose results are
 * then thrown away, and starts again 250 ms after the inputs settle. Runs are serialised on
 * [work], one background-priority thread. Decisions never trigger a scan.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StreamPairing(
    private val dao: PairingDao,
    private val sport: SportDao,
    /** Emits when a table the generation reads changes (source, status, channel edits, live rules, aliases). */
    private val inputChanges: Flow<Unit>,
    private val games: Flow<List<SportEvent>>,
    private val cache: PairingCache,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    private val log: DiagnosticsLog,
) {
    private val scan = PairingScan(dao)
    private val automatic = MutableStateFlow<Map<String, List<StreamMatch>>>(emptyMap())
    private val decisions = MutableStateFlow<Map<Pair<String, String>, Decision>>(emptyMap())
    private val active = MutableStateFlow(false)
    private var started = false

    /** Every game's streams with the current decisions applied, in result order (SPORT-FR-117). */
    val streams: Flow<Map<String, List<StreamMatch>>> = combine(automatic, decisions) { results, chosen ->
        results.mapValues { (id, list) -> list.map { it.withDecision(chosen[id to it.channelKey]) }.sortedWith(StreamMatch.ORDER) }
    }

    /** Today (the only reader of streams) is on screen and the app in front. */
    fun setActive(on: Boolean) {
        active.value = on
        if (on && !started) {
            started = true
            scope.launch(work) { loop() }
        }
    }

    /**
     * Confirm, reject, or ([decision] null) restore (SPORT-FR-76): saved, then shown at once without
     * a scan. False when the save failed (MATCH_DECISION_SAVE).
     */
    suspend fun decide(eventId: String, channelKey: String, decision: Decision?): Boolean = try {
        withContext(work) {
            if (decision == null) {
                sport.deleteDecision(eventId, channelKey)
            } else {
                sport.putDecision(EventChannelDecisionEntity(eventId, channelKey, decision.name.lowercase(), clock.wallMillis()))
            }
        }
        decisions.update { if (decision == null) it - (eventId to channelKey) else it + ((eventId to channelKey) to decision) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("sport", "decision not saved", e)
        false
    }

    private suspend fun loop() {
        active.collectLatest { on ->
            if (!on) return@collectLatest
            val generations = inputChanges.onStart { emit(Unit) }.map { generation() }.distinctUntilChanged()
            // The same games (ids and kick-offs) need no new run; a score change is not a new input.
            val gameKeys = games.distinctUntilChanged { a, b -> a.map { it.id to it.startMillis } == b.map { it.id to it.startMillis } }
            combine(gameKeys, generations) { g, gen -> g to gen }.debounce(SETTLE_MS).collectLatest { (g, gen) -> pair(g, gen) }
        }
    }

    private suspend fun pair(events: List<SportEvent>, generation: String) {
        try {
            val ids = events.map { it.id }
            decisions.value = ids.chunked(CHUNK).flatMap { sport.decisions(it) }.mapNotNull { d ->
                val decision = Decision.entries.firstOrNull { it.name.equals(d.decision, ignoreCase = true) } ?: return@mapNotNull null
                (d.eventId to d.channelKey) to decision
            }.toMap()
            val now = clock.wallMillis()
            val cached = cache.read(generation, events, now)
            // Games still to scan keep what they showed until the scan publishes (no flash of "no broadcast").
            val shown = automatic.value
            automatic.value = events.associate { it.id to (cached[it.id] ?: shown[it.id].orEmpty()) }
            val missing = events.filter { it.id !in cached && it.startMillis > 0 }
            if (missing.isEmpty()) return
            val aliasRows = sport.aliases(missing.map { it.sport.name }.distinct())
            val aliases = TeamVariants.aliases(aliasRows.groupBy({ it.canonical }, { it.alias }).mapValues { it.value.toSet() })
            val started = clock.monotonicNanos()
            val scanned = scan.run(missing, aliases, decisions.value)
            val stats = scan.lastStats
            log.info(
                "sport",
                "pairing ${missing.size} games: ${stats.channelQueries} channel and ${stats.programmeQueries} programme pages, " +
                    "${(clock.monotonicNanos() - started) / 1_000_000} ms",
            )
            automatic.update { it + scanned.mapValues { (_, list) -> list.map { m -> m.withDecision(null) } } }
            cache.write(generation, missing, scanned, now)
        } catch (e: CancellationException) {
            // A newer generation or game list: this scan is discarded (SPORT-FR-120).
            throw e
        } catch (e: Exception) {
            // A failure keeps the last published results (SPORT-FR-100).
            log.error("sport", "pairing failed", e)
        }
    }

    /** SPORT-FR-101: a fingerprint of everything that can change a pairing result. */
    private suspend fun generation(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(lines: List<String>) = lines.forEach {
            digest.update(it.toByteArray())
            digest.update(10)
        }
        add(dao.sourceLines())
        add(dao.snapshotLines())
        var after = ""
        while (true) {
            val page = dao.customLines(after, CUSTOM_PAGE)
            add(page)
            if (page.size < CUSTOM_PAGE) break
            after = page.last().substringBefore('|')
        }
        add(dao.liveRuleLines())
        add(dao.aliasLines())
        val hex = "0123456789abcdef"
        return buildString { digest.digest().forEach { b -> append(hex[(b.toInt() shr 4) and 15]).append(hex[b.toInt() and 15]) } }
    }

    /** Tests start each case from nothing. */
    fun forgetForTests() {
        automatic.value = emptyMap()
        decisions.value = emptyMap()
        cache.clear()
    }

    private companion object {
        const val SETTLE_MS = 250L
        const val CHUNK = 500
        const val CUSTOM_PAGE = 2_000
    }
}
