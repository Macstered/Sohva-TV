package com.sohva.tv.feature.sport.provider

import com.sohva.tv.core.data.database.SportCompetitionEntity
import com.sohva.tv.core.data.database.SportDao
import com.sohva.tv.core.data.database.SportEventEntity
import com.sohva.tv.core.data.database.SportFeedEntity
import com.sohva.tv.core.data.database.SportQuotaEntity
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.sport.Competition
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer

/** Where an answer came from (SPORT-FR-12): the saved copy, the network, or a saved copy past its freshness. */
enum class CacheState { HIT, MISS, STALE }

/** One sport's day: its followed games, where they came from, and the quota the provider last reported. */
data class SportDay(val sport: SportType, val events: List<SportEvent>, val state: CacheState, val quotaRemaining: Int?)

/** What the feed reads: the saved day, the day through the cache, and the quotas. */
interface SportDays {
    suspend fun savedDay(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay?

    suspend fun day(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay

    suspend fun quotas(): Map<SportType, Int>
}

/**
 * API-Sports through the database (spec 60 §4.3). Cache-first: a fresh entry answers without a
 * request; the network answers otherwise; a failure falls back to a saved entry inside its 24-hour
 * stale window. A day is stored as normalised games of every competition (§9), so following
 * another competition costs no request. Requests are counted per sport per UTC day; once the
 * provider says the day's quota is spent, that sport asks nothing more until the next UTC day
 * (§9 rule, decision "Spec 60 open questions"). Everything runs on [io].
 */
class SportsRepository(
    private val dao: SportDao,
    private val http: SportsHttp,
    private val key: suspend () -> String?,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: DiagnosticsLog,
) : SportDays {
    /** The saved day only, still inside its stale window; no request (SPORT-FR-22). */
    override suspend fun savedDay(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay? = withContext(io) {
        val entry = dao.feed(dayKey(sport, date, zone))?.takeIf { clock.wallMillis() < it.staleUntil } ?: return@withContext null
        SportDay(sport, readEvents(entry.key, competitions), if (fresh(entry)) CacheState.HIT else CacheState.STALE, entry.quotaRemaining)
    }

    /** The day through the cache (SPORT-FR-30…34); the followed [competitions] only, or every game when null. */
    override suspend fun day(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay = withContext(io) {
        val feedKey = dayKey(sport, date, zone)
        val state = cached(sport, feedKey, "events", freshness(date, zone)) { answer ->
            val events = SportsParsers.events(sport, answer.body, zone)
            dao.putDay(entry(sport, feedKey, "events", freshness(date, zone), answer.quotaRemaining, null), events.map { row(feedKey, it) })
        }
        val entry = dao.feed(feedKey)
        SportDay(sport, readEvents(feedKey, competitions), state, entry?.quotaRemaining)
    }

    /** A sport's competitions, a request at most weekly (SPORT-FR-06, -32). */
    suspend fun competitions(sport: SportType): List<Competition> = withContext(io) {
        val feedKey = "${sport.provider}|competitions|${if (sport == SportType.FOOTBALL) "current" else "all"}"
        cached(sport, feedKey, "competitions", COMPETITIONS_FRESH) { answer ->
            val rows = SportsParsers.competitions(sport, answer.body).map { SportCompetitionEntity(sport.name, it.id, it.name, it.country, it.logo) }
            dao.putCompetitions(entry(sport, feedKey, "competitions", COMPETITIONS_FRESH, answer.quotaRemaining, null), sport.name, rows)
        }
        dao.competitions(sport.name).map { Competition(sport, it.competitionId, it.name, it.country, it.logo) }
            .sortedWith(compareBy({ it.country?.lowercase() ?: "" }, { it.name.lowercase() }))
    }

    /** A football game's events, fresh for 2 minutes (SPORT-FR-91). */
    suspend fun incidents(eventId: String): Pair<List<Incident>, CacheState> = withContext(io) {
        val fixture = eventId.removePrefix(FOOTBALL_PREFIX).takeIf { eventId.startsWith(FOOTBALL_PREFIX) && it.all(Char::isDigit) }
            ?: throw SportsException(SportsProblem.INVALID_DATA)
        val feedKey = "football|incidents|$fixture"
        val state = cached(SportType.FOOTBALL, feedKey, "incidents", INCIDENTS_FRESH) { answer ->
            val text = answer.body.readUtf8()
            SportsParsers.incidents(fixture, Buffer().writeUtf8(text))
            dao.putFeed(entry(SportType.FOOTBALL, feedKey, "incidents", INCIDENTS_FRESH, answer.quotaRemaining, text))
        }
        val payload = dao.feed(feedKey)?.payload ?: return@withContext emptyList<Incident>() to state
        SportsParsers.incidents(fixture, Buffer().writeUtf8(payload)) to state
    }

    /** Each sport's remaining quota as last reported today (SPORT-FR-12); a spent one reads 0. */
    override suspend fun quotas(): Map<SportType, Int> = withContext(io) {
        val today = utcDay()
        dao.quotas().filter { it.utcDay == today }.mapNotNull { q ->
            val sport = SportType.entries.firstOrNull { it.provider == q.provider } ?: return@mapNotNull null
            val remaining = if (q.exhausted) 0 else q.remaining ?: return@mapNotNull null
            sport to remaining
        }.toMap()
    }

    /**
     * The cache rule for one entry: a fresh entry answers; else the network, stored by [store];
     * on failure the saved entry inside its stale window, else the failure. Cancellation is never
     * turned into a fallback (SPORT-FR-34).
     */
    private suspend fun cached(sport: SportType, feedKey: String, kind: String, fresh: Long, store: suspend (SportsAnswer) -> Unit): CacheState {
        val now = clock.wallMillis()
        dao.dropStale(now)
        val saved = dao.feed(feedKey)
        if (saved != null && fresh(saved)) return CacheState.HIT
        return try {
            val apiKey = key()?.takeIf { it.isNotBlank() } ?: throw SportsException(SportsProblem.KEY_MISSING)
            if (spent(sport)) throw SportsException(SportsProblem.QUOTA_EXHAUSTED)
            val answer = try {
                http.get(url(sport, feedKey, kind), apiKey)
            } catch (e: SportsException) {
                count(sport, null, exhausted = false)
                throw e
            }
            try {
                store(answer)
                count(sport, answer.quotaRemaining, exhausted = answer.quotaRemaining == 0)
            } catch (e: SportsException) {
                count(sport, answer.quotaRemaining, exhausted = e.problem == SportsProblem.QUOTA_EXHAUSTED || answer.quotaRemaining == 0)
                throw e
            }
            CacheState.MISS
        } catch (e: CancellationException) {
            throw e
        } catch (e: SportsException) {
            log.info("sport", "${sport.provider} $kind: ${e.problem.name.lowercase()}${if (e.status > 0) " ${e.status}" else ""}")
            if (saved != null && now < saved.staleUntil) CacheState.STALE else throw e
        }
    }

    private fun url(sport: SportType, feedKey: String, kind: String) = when (kind) {
        "events" -> feedKey.split('|').let { http.dayUrl(sport, it[2], it[3]) }
        "competitions" -> http.competitionsUrl(sport)
        else -> http.incidentsUrl(feedKey.substringAfterLast('|'))
    }

    private suspend fun spent(sport: SportType): Boolean = dao.quota(sport.provider)?.let { it.utcDay == utcDay() && it.exhausted } == true

    /** One more request today, with what the provider said about the rest. */
    private suspend fun count(sport: SportType, remaining: Int?, exhausted: Boolean) {
        val today = utcDay()
        val q = dao.quota(sport.provider)?.takeIf { it.utcDay == today }
        dao.putQuota(
            SportQuotaEntity(
                sport.provider, today, (q?.requests ?: 0) + 1, remaining ?: q?.remaining,
                exhausted || (q?.exhausted == true),
            ),
        )
        if (exhausted && q?.exhausted != true) log.info("sport", "${sport.provider}: daily quota spent, automatic requests stop until tomorrow (UTC)")
    }

    private fun utcDay(): Long = Instant.ofEpochMilli(clock.wallMillis()).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    /** Fresh: inside its window, and not fetched "in the future" by a clock set back (§8). */
    private fun fresh(entry: SportFeedEntity): Boolean {
        val now = clock.wallMillis()
        return entry.fetchedAt <= now && now < entry.expiresAt
    }

    /** Today 20 minutes, a past day 24 hours, a future day 2 hours (SPORT-FR-32). */
    private fun freshness(date: LocalDate, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(clock.wallMillis()).atZone(zone).toLocalDate()
        return when {
            date == today -> 20 * MINUTE
            date < today -> 24 * HOUR
            else -> 2 * HOUR
        }
    }

    private fun entry(sport: SportType, feedKey: String, kind: String, fresh: Long, quota: Int?, payload: String?): SportFeedEntity {
        val now = clock.wallMillis()
        return SportFeedEntity(feedKey, sport.name, kind, now, now + fresh, now + fresh + STALE_WINDOW, quota, "api-sports-${sport.provider}", payload)
    }

    private suspend fun readEvents(feedKey: String, competitions: Set<String>?): List<SportEvent> {
        val rows = if (competitions == null) dao.allEvents(feedKey) else if (competitions.isEmpty()) emptyList() else dao.events(feedKey, competitions.toList())
        return rows.mapNotNull(::event)
    }

    private fun row(feedKey: String, e: SportEvent) = SportEventEntity(
        feedKey = feedKey, eventId = e.id, sport = e.sport.name, competitionId = e.competitionId, competition = e.competition,
        competitionLogo = e.competitionLogo, home = e.home.name, homeLogo = e.home.logo, away = e.away.name, awayLogo = e.away.logo,
        startMs = e.startMillis, startMinute = e.startMinuteOfDay, status = e.status.name, score = e.score, scoreDetail = e.scoreDetail, minute = e.minute,
    )

    private fun event(r: SportEventEntity): SportEvent? = SportEvent(
        r.eventId, SportType.fromStored(r.sport) ?: return null, r.competitionId, r.competition, r.competitionLogo,
        Side(r.home, r.homeLogo), Side(r.away, r.awayLogo), r.startMs, r.startMinute,
        EventStatus.entries.firstOrNull { it.name == r.status } ?: EventStatus.UNKNOWN, r.score, r.scoreDetail, r.minute,
    )

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val STALE_WINDOW = 24 * HOUR
        private const val COMPETITIONS_FRESH = 7 * 24 * HOUR
        private const val INCIDENTS_FRESH = 2 * MINUTE
        private const val FOOTBALL_PREFIX = "api-sports:football:"

        /** `"<provider>|events|<yyyy-MM-dd>|<zoneId>"` (SPORT-FR-30). */
        fun dayKey(sport: SportType, date: LocalDate, zone: ZoneId): String = "${sport.provider}|events|$date|${zone.id}"
    }
}
