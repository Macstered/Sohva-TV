package com.streammate.tv.sports.repository

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.database.SportsApiCacheEntity
import com.streammate.tv.core.database.SportsCacheDao
import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.FootballIncidentKind
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.hasCompetitions
import com.streammate.tv.core.model.SportsCompetition
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.core.security.SecretSettingsStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class DirectSportsRepository(
    httpClient: OkHttpClient,
    private val cacheDao: SportsCacheDao,
    private val settingsStore: SecretSettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    private val endpoints: ApiSportsEndpoints = ApiSportsEndpoints(),
) : SportsRepository {
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val parser = ApiSportsParser()

    override suspend fun footballEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = events(SportType.FOOTBALL, date, zoneId, selectedCompetitionIds)

    override suspend fun hockeyEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = events(SportType.ICE_HOCKEY, date, zoneId, selectedCompetitionIds)

    override suspend fun aflEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = events(SportType.AUSTRALIAN_FOOTBALL, date, zoneId, selectedCompetitionIds)

    override suspend fun competitions(sport: SportType): List<SportsCompetition> {
        // No leagues endpoint to ask; the day listing is shown unfiltered.
        if (!sport.hasCompetitions) return emptyList()
        val catalogueScope = if (sport.competitionQuery.isEmpty()) "all" else "current"
        val key = "${sport.providerName}|competitions|$catalogueScope"
        val now = clock.millis()
        cacheDao.deleteExpired(now)
        val cached = cacheDao.cached(key)
        if (cached != null && now < cached.expiresAtEpochMillis) {
            return parser.competitions(sport, cached.payload)
        }
        return try {
            val response = fetch(
                baseUrl = endpointFor(sport),
                path = "leagues",
                query = sport.competitionQuery,
            )
            val competitions = parser.competitions(sport, response.payload)
            cacheDao.upsert(
                cacheEntry(
                    key = key,
                    sport = sport.providerName,
                    kind = "competitions",
                    payload = response.payload,
                    source = sport.sourceName,
                    quotaRemaining = response.quotaRemaining,
                    now = now,
                    ttlMillis = COMPETITION_TTL_MILLIS,
                ),
            )
            competitions
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (cached != null && now < cached.staleUntilEpochMillis) {
                parser.competitions(sport, cached.payload)
            } else {
                throw safeException(error)
            }
        }
    }

    override suspend fun footballIncidents(eventId: String): FootballIncidentsSnapshot {
        val fixtureId = FOOTBALL_EVENT_ID.matchEntire(eventId)?.groupValues?.get(1)
            ?: throw SportsBackendException(CoreR.string.error_football_event_id_invalid)
        val key = "football|incidents|$fixtureId"
        val now = clock.millis()
        cacheDao.deleteExpired(now)
        val cached = cacheDao.cached(key)
        if (cached != null && now < cached.expiresAtEpochMillis) {
            return parser.footballIncidents(cached.payload, eventId, "hit", cached.source, cached.quotaRemaining)
        }
        return try {
            val response = fetch(
                baseUrl = endpoints.football,
                path = "fixtures/events",
                query = mapOf("fixture" to fixtureId),
            )
            val parsed = parser.footballIncidents(
                response.payload,
                eventId,
                "miss",
                FOOTBALL_SOURCE,
                response.quotaRemaining,
            )
            cacheDao.upsert(
                cacheEntry(
                    key = key,
                    sport = "football",
                    kind = "incidents",
                    payload = response.payload,
                    source = FOOTBALL_SOURCE,
                    quotaRemaining = response.quotaRemaining,
                    now = now,
                    ttlMillis = INCIDENT_TTL_MILLIS,
                ),
            )
            parsed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (cached != null && now < cached.staleUntilEpochMillis) {
                parser.footballIncidents(cached.payload, eventId, "stale", cached.source, cached.quotaRemaining)
            } else {
                throw safeException(error)
            }
        }
    }

    suspend fun clearCache() = cacheDao.clear()

    override suspend fun events(
        sport: SportType,
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot {
        val sportName = sport.providerName
        val key = "$sportName|events|$date|${zoneId.id}"
        val now = clock.millis()
        cacheDao.deleteExpired(now)
        val cached = cacheDao.cached(key)
        if (cached != null && now < cached.expiresAtEpochMillis) {
            return parser.events(
                sport,
                cached.payload,
                zoneId,
                "hit",
                cached.source,
                cached.quotaRemaining,
                selectedCompetitionIds,
            )
        }
        return try {
            val response = fetch(
                baseUrl = endpointFor(sport),
                path = eventsPath(sport),
                query = mapOf("date" to date.toString(), "timezone" to zoneId.id),
            )
            val source = sport.sourceName
            val parsed = parser.events(
                sport,
                response.payload,
                zoneId,
                "miss",
                source,
                response.quotaRemaining,
                selectedCompetitionIds,
            )
            cacheDao.upsert(
                cacheEntry(
                    key = key,
                    sport = sportName,
                    kind = "events",
                    payload = response.payload,
                    source = source,
                    quotaRemaining = response.quotaRemaining,
                    now = now,
                    ttlMillis = eventTtl(date, zoneId, now),
                ),
            )
            parsed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (cached != null && now < cached.staleUntilEpochMillis) {
                parser.events(
                    sport,
                    cached.payload,
                    zoneId,
                    "stale",
                    cached.source,
                    cached.quotaRemaining,
                    selectedCompetitionIds,
                )
            } else {
                throw safeException(error)
            }
        }
    }

    private fun endpointFor(sport: SportType): String = when (sport) {
        SportType.FOOTBALL -> endpoints.football
        SportType.ICE_HOCKEY -> endpoints.hockey
        SportType.AUSTRALIAN_FOOTBALL -> endpoints.afl
        SportType.BASKETBALL -> endpoints.basketball
        SportType.BASEBALL -> endpoints.baseball
        SportType.HANDBALL -> endpoints.handball
        SportType.RUGBY -> endpoints.rugby
        SportType.VOLLEYBALL -> endpoints.volleyball
        SportType.AMERICAN_FOOTBALL -> endpoints.americanFootball
        SportType.MMA -> endpoints.mma
        SportType.FORMULA_1 -> endpoints.formula1
        SportType.NBA -> endpoints.nba
    }

    /**
     * Each API-Sports product names its day listing differently. Football
     * has fixtures, MMA fight cards, Formula 1 race weekends (every session
     * of the day, practice through race); the rest call them games.
     */
    private fun eventsPath(sport: SportType): String = when (sport) {
        SportType.FOOTBALL -> "fixtures"
        SportType.MMA -> "fights"
        SportType.FORMULA_1 -> "races"
        else -> "games"
    }

    private suspend fun fetch(
        baseUrl: String,
        path: String,
        query: Map<String, String>,
    ): ApiSportsHttpResponse {
        val key = settingsStore.loadSportsApiSettings().apiKey
        if (key.isBlank()) throw SportsBackendException(CoreR.string.error_api_sports_key_missing)
        val builder = baseUrl.toHttpUrl().newBuilder().addPathSegments(path)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("x-apisports-key", key)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SportsBackendException(CoreR.string.error_api_sports_http, listOf(response.code))
                }
                val body = response.body
                if (body.contentLength() > MAX_RESPONSE_BYTES) {
                    throw SportsBackendException(CoreR.string.error_api_sports_response_too_large)
                }
                val payload = body.string()
                if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                    throw SportsBackendException(CoreR.string.error_api_sports_response_too_large)
                }
                ApiSportsHttpResponse(
                    payload = payload,
                    quotaRemaining = response.header("x-ratelimit-requests-remaining")?.toIntOrNull(),
                )
            }
        }
    }

    private fun cacheEntry(
        key: String,
        sport: String,
        kind: String,
        payload: String,
        source: String,
        quotaRemaining: Int?,
        now: Long,
        ttlMillis: Long,
    ) = SportsApiCacheEntity(
        cacheKey = key,
        sport = sport,
        kind = kind,
        payload = payload,
        source = source,
        quotaRemaining = quotaRemaining,
        fetchedAtEpochMillis = now,
        expiresAtEpochMillis = now + ttlMillis,
        staleUntilEpochMillis = now + ttlMillis + STALE_TTL_MILLIS,
    )

    private fun eventTtl(date: LocalDate, zoneId: ZoneId, now: Long): Long {
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return when {
            date == today -> TODAY_TTL_MILLIS
            date < today -> PAST_TTL_MILLIS
            else -> FUTURE_TTL_MILLIS
        }
    }

    private fun safeException(error: Exception): SportsBackendException = when (error) {
        is SportsBackendException -> error
        is IOException -> SportsBackendException(
            CoreR.string.error_api_sports_unavailable,
            cause = error,
        )
        else -> SportsBackendException(
            CoreR.string.error_api_sports_invalid_data,
            cause = error,
        )
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val TODAY_TTL_MILLIS = 20 * 60_000L
        const val PAST_TTL_MILLIS = 24 * 60 * 60_000L
        const val FUTURE_TTL_MILLIS = 2 * 60 * 60_000L
        const val INCIDENT_TTL_MILLIS = 2 * 60_000L
        const val COMPETITION_TTL_MILLIS = 7 * 24 * 60 * 60_000L
        const val STALE_TTL_MILLIS = 24 * 60 * 60_000L
        const val USER_AGENT = "SohvaTV/0.1 (Android TV; personal use)"
        const val FOOTBALL_SOURCE = "api-sports-football"
        val FOOTBALL_EVENT_ID = Regex("api-sports:football:([1-9][0-9]*)")
    }
}

data class ApiSportsEndpoints(
    val football: String = "https://v3.football.api-sports.io",
    val hockey: String = "https://v1.hockey.api-sports.io",
    val afl: String = "https://v1.afl.api-sports.io",
    val basketball: String = "https://v1.basketball.api-sports.io",
    val baseball: String = "https://v1.baseball.api-sports.io",
    val handball: String = "https://v1.handball.api-sports.io",
    val rugby: String = "https://v1.rugby.api-sports.io",
    val volleyball: String = "https://v1.volleyball.api-sports.io",
    val americanFootball: String = "https://v1.american-football.api-sports.io",
    val mma: String = "https://v1.mma.api-sports.io",
    val formula1: String = "https://v1.formula-1.api-sports.io",
    val nba: String = "https://v2.nba.api-sports.io",
) {
    init {
        listOf(
            football, hockey, afl, basketball, baseball, handball, rugby, volleyball,
            americanFootball, mma, formula1, nba,
        ).forEach { endpoint ->
            val parsed = endpoint.toHttpUrl()
            if (!parsed.isHttps || parsed.encodedPath != "/") {
                throw LocalizedException(CoreR.string.error_endpoint_must_be_https)
            }
        }
    }
}

private data class ApiSportsHttpResponse(val payload: String, val quotaRemaining: Int?)

internal class ApiSportsParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun events(
        sport: SportType,
        payload: String,
        zoneId: ZoneId,
        cacheState: String,
        source: String,
        quotaRemaining: Int?,
        selectedCompetitionIds: Set<String> = sport.selectedCompetitions,
    ): SportsEventsSnapshot {
        val root = parseRoot(payload)
        ensureNoProviderErrors(root)
        val parsed = root.array("response").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            when (sport) {
                SportType.FOOTBALL -> footballEvent(item, zoneId)
                SportType.ICE_HOCKEY -> hockeyEvent(item, zoneId)
                SportType.AUSTRALIAN_FOOTBALL -> aflEvent(item, zoneId)
                SportType.BASKETBALL,
                SportType.BASEBALL,
                SportType.HANDBALL,
                SportType.RUGBY,
                SportType.VOLLEYBALL,
                SportType.AMERICAN_FOOTBALL,
                -> teamSportEvent(sport, item, zoneId)
                SportType.MMA -> mmaEvent(item, zoneId)
                SportType.FORMULA_1 -> formulaOneEvent(item, zoneId)
                SportType.NBA -> nbaEvent(item, zoneId)
            }
        }.filter { event -> !sport.hasCompetitions || event.competitionId in selectedCompetitionIds }
        val events = if (sport == SportType.AUSTRALIAN_FOOTBALL) deduplicateAfl(parsed) else parsed
        return SportsEventsSnapshot(events, cacheState, source, quotaRemaining)
    }

    fun competitions(sport: SportType, payload: String): List<SportsCompetition> {
        val root = parseRoot(payload)
        ensureNoProviderErrors(root)
        return root.array("response")
            .mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val league = item.objOrNull("league") ?: item
                val id = league.int("id")?.takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val name = league.string("name") ?: return@mapNotNull null
                val countryObject = item.objOrNull("country") ?: league.objOrNull("country")
                val country = countryObject?.string("name")
                    ?: league.string("country")
                    ?: item.string("country")
                SportsCompetition(
                    sport = sport,
                    id = id,
                    name = name,
                    country = country,
                    type = league.string("type") ?: item.string("type"),
                    logoUrl = league.https("logo") ?: item.https("logo"),
                )
            }
            .distinctBy(SportsCompetition::preferenceKey)
            .sortedWith(compareBy<SportsCompetition>({ it.country.orEmpty() }, { it.name }))
    }

    fun footballIncidents(
        payload: String,
        eventId: String,
        cacheState: String,
        source: String,
        quotaRemaining: Int?,
    ): FootballIncidentsSnapshot {
        val root = parseRoot(payload)
        ensureNoProviderErrors(root)
        val providerId = eventId.substringAfterLast(':')
        val incidents = root.array("response").mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val time = item.obj("time")
            FootballIncident(
                id = "api-sports:football:$providerId:incident:$index",
                eventId = eventId,
                elapsedMinutes = time.int("elapsed") ?: 0,
                extraMinutes = time.int("extra"),
                kind = when (item.string("type")?.lowercase()) {
                    "goal" -> FootballIncidentKind.GOAL
                    "card" -> FootballIncidentKind.CARD
                    "subst" -> FootballIncidentKind.SUBSTITUTION
                    "var" -> FootballIncidentKind.VAR
                    else -> FootballIncidentKind.OTHER
                },
                detail = item.string("detail") ?: "Tapahtuma",
                comments = item.string("comments"),
                teamName = item.objOrNull("team")?.string("name"),
                actorName = item.objOrNull("player")?.string("name"),
                relatedName = item.objOrNull("assist")?.string("name"),
            )
        }
        return FootballIncidentsSnapshot(incidents, cacheState, source, quotaRemaining)
    }

    private fun footballEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val fixture = item.obj("fixture")
        val league = item.obj("league")
        val teams = item.obj("teams")
        val home = teams.obj("home")
        val away = teams.obj("away")
        val goals = item.obj("goals")
        val status = fixture.obj("status")
        val id = fixture.int("id")?.takeIf { it > 0 } ?: return null
        val start = startInstant(fixture) ?: return null
        return event(
            id = "api-sports:football:$id",
            sport = SportType.FOOTBALL,
            competitionId = league.int("id")?.toString() ?: return null,
            competition = league.string("name") ?: return null,
            competitionLogo = league.https("logo"),
            homeName = home.string("name") ?: return null,
            homeLogo = home.https("logo"),
            awayName = away.string("name") ?: return null,
            awayLogo = away.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = status.string("short").orEmpty(),
            elapsed = status.int("elapsed"),
            homeScore = goals.int("home"),
            awayScore = goals.int("away"),
            detailsAvailable = true,
        )
    }

    private fun hockeyEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val league = item.obj("league")
        val teams = item.obj("teams")
        val home = teams.obj("home")
        val away = teams.obj("away")
        val scores = item.obj("scores")
        val status = item.obj("status")
        val id = item.int("id")?.takeIf { it > 0 } ?: return null
        val start = startInstant(item) ?: return null
        return event(
            id = "api-sports:hockey:$id",
            sport = SportType.ICE_HOCKEY,
            competitionId = league.int("id")?.toString() ?: return null,
            competition = league.string("name") ?: return null,
            competitionLogo = league.https("logo"),
            homeName = home.string("name") ?: return null,
            homeLogo = home.https("logo"),
            awayName = away.string("name") ?: return null,
            awayLogo = away.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = status.string("short").orEmpty(),
            elapsed = null,
            homeScore = scores.int("home"),
            awayScore = scores.int("away"),
            detailsAvailable = false,
        )
    }

    private fun teamSportEvent(sport: SportType, item: JsonObject, zoneId: ZoneId): TodayEvent? {
        // NFL games keep their id, status and date inside a "game" object;
        // the other team sports put them at the top level.
        val game = item.obj("game")
        val league = item.obj("league")
        val teams = item.obj("teams")
        val home = teams.obj("home")
        val away = teams.obj("away")
        val scores = item.obj("scores")
        val status = item.objOrNull("status") ?: game.obj("status")
        val id = item.int("id")?.takeIf { it > 0 }
            ?: game.int("id")?.takeIf { it > 0 }
            ?: return null
        val start = startInstant(item)
            ?: startInstant(game)
            ?: startInstant(game.obj("date"))
            ?: return null
        return event(
            id = "api-sports:${sport.providerName}:$id",
            sport = sport,
            competitionId = league.int("id")?.toString() ?: return null,
            competition = league.string("name") ?: return null,
            competitionLogo = league.https("logo"),
            homeName = home.string("name") ?: return null,
            homeLogo = home.https("logo"),
            awayName = away.string("name") ?: return null,
            awayLogo = away.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = status.string("short").orEmpty(),
            elapsed = null,
            homeScore = scores.score("home"),
            awayScore = scores.score("away"),
            detailsAvailable = false,
        )
    }

    /**
     * An MMA fight: two fighters instead of two teams, a weight class instead
     * of a league, and no score. The fight card's name, when the provider
     * gives one, is the competition line; the category is the fallback.
     */
    private fun mmaEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val fighters = item.obj("fighters")
        val first = fighters.obj("first")
        val second = fighters.obj("second")
        val status = item.obj("status")
        val id = item.int("id")?.takeIf { it > 0 } ?: return null
        val start = startInstant(item) ?: return null
        val category = item.string("category") ?: item.obj("category").string("name")
        val eventName = item.string("event") ?: item.obj("event").string("name")
        return event(
            id = "api-sports:mma:$id",
            sport = SportType.MMA,
            competitionId = "",
            competition = listOfNotNull(eventName, category).ifEmpty { listOf("MMA") }.joinToString(" \u00b7 "),
            competitionLogo = null,
            homeName = first.string("name") ?: return null,
            homeLogo = first.https("logo"),
            awayName = second.string("name") ?: return null,
            awayLogo = second.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = status.string("short").orEmpty(),
            elapsed = null,
            homeScore = null,
            awayScore = null,
            detailsAvailable = false,
        )
    }

    /**
     * A Formula 1 session. The Grand Prix takes the home slot and the circuit
     * the away slot, so the card reads "Monaco Grand Prix / Circuit de
     * Monaco" and channel matching can find the Grand Prix name in a
     * programme title. The session type (Race, Qualifying, a practice) rides
     * on the competition line. A live session shows its lap count as the score.
     */
    private fun formulaOneEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val competition = item.obj("competition")
        val circuit = item.obj("circuit")
        val laps = item.obj("laps")
        val id = item.int("id")?.takeIf { it > 0 } ?: return null
        val start = startInstant(item) ?: return null
        val type = item.string("type")
        val statusCode = item.string("status") ?: item.obj("status").string("short").orEmpty()
        val base = event(
            id = "api-sports:formula-1:$id",
            sport = SportType.FORMULA_1,
            competitionId = "",
            competition = listOfNotNull("Formula 1", type).joinToString(" \u00b7 "),
            competitionLogo = null,
            homeName = competition.string("name") ?: return null,
            homeLogo = circuit.https("image"),
            awayName = circuit.string("name") ?: competition.obj("location").string("city") ?: "",
            awayLogo = null,
            start = start,
            zoneId = zoneId,
            statusCode = statusCode,
            elapsed = null,
            homeScore = null,
            awayScore = null,
            detailsAvailable = false,
        )
        val current = laps.int("current")
        val total = laps.int("total")
        return if (base.status == TodayEventStatus.LIVE && current != null && total != null && total > 0) {
            base.copy(score = "$current / $total")
        } else {
            base
        }
    }

    /**
     * The NBA's own feed: the visiting team is "visitors", the status is a
     * number (1 scheduled, 2 live, 3 finished) and the start is date.start.
     * Only the standard league is kept; the feed also carries summer leagues.
     */
    private fun nbaEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val id = item.int("id")?.takeIf { it > 0 } ?: return null
        val league = item.string("league")
        if (league != null && !league.equals("standard", ignoreCase = true)) return null
        val date = item.obj("date")
        val start = date.string("start")?.let { encoded ->
            runCatching { Instant.parse(encoded) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(encoded).toInstant() }.getOrNull()
        } ?: startInstant(item) ?: return null
        val teams = item.obj("teams")
        val home = teams.obj("home")
        val away = teams.objOrNull("visitors") ?: teams.obj("away")
        val scores = item.obj("scores")
        val status = item.obj("status")
        val statusCode = status.string("short") ?: status.int("short")?.toString().orEmpty()
        return event(
            id = "api-sports:nba:$id",
            sport = SportType.NBA,
            competitionId = "",
            competition = "NBA",
            competitionLogo = null,
            homeName = home.string("name") ?: return null,
            homeLogo = home.https("logo"),
            awayName = away.string("name") ?: return null,
            awayLogo = away.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = statusCode,
            elapsed = null,
            homeScore = scores.score("home"),
            awayScore = if (scores.objOrNull("visitors") != null) scores.score("visitors") else scores.score("away"),
            detailsAvailable = false,
        )
    }

    private fun aflEvent(item: JsonObject, zoneId: ZoneId): TodayEvent? {
        val game = item.obj("game")
        val league = item.obj("league")
        val teams = item.obj("teams")
        val home = teams.obj("home")
        val away = teams.obj("away")
        val scores = item.obj("scores")
        val homeScores = scores.obj("home")
        val awayScores = scores.obj("away")
        val status = item.obj("status")
        game.int("id")?.takeIf { it > 0 } ?: return null
        val homeId = home.int("id")?.takeIf { it > 0 } ?: return null
        val awayId = away.int("id")?.takeIf { it > 0 } ?: return null
        val start = startInstant(item) ?: return null
        val state = status(SportType.AUSTRALIAN_FOOTBALL, status.string("short").orEmpty())
        val homeScore = homeScores.int("score")
        val awayScore = awayScores.int("score")
        val base = event(
            id = "api-sports:afl:${start.epochSecond}:$homeId:$awayId",
            sport = SportType.AUSTRALIAN_FOOTBALL,
            competitionId = league.int("id")?.toString() ?: return null,
            competition = "AFL",
            competitionLogo = null,
            homeName = home.string("name") ?: return null,
            homeLogo = home.https("logo"),
            awayName = away.string("name") ?: return null,
            awayLogo = away.https("logo"),
            start = start,
            zoneId = zoneId,
            statusCode = status.string("short").orEmpty(),
            elapsed = null,
            homeScore = homeScore,
            awayScore = awayScore,
            detailsAvailable = false,
        )
        // The total goes where every other sport's score goes, so the card
        // keeps its shape. Goals and behinds are a second line for screens
        // with room for one: "14.11 (95) – 16.9 (105)" in the score slot
        // pushed the team marks off the card.
        val scoreDetail = if (state in setOf(TodayEventStatus.LIVE, TodayEventStatus.FINISHED) &&
            listOf(
                homeScore,
                awayScore,
                homeScores.int("goals"),
                homeScores.int("behinds"),
                awayScores.int("goals"),
                awayScores.int("behinds"),
            )
                .all { it != null }
        ) {
            "${homeScores.int("goals")}.${homeScores.int("behinds")} – " +
                "${awayScores.int("goals")}.${awayScores.int("behinds")}"
        } else {
            null
        }
        return base.copy(scoreDetail = scoreDetail, detailsAvailable = false)
    }

    private fun event(
        id: String,
        sport: SportType,
        competitionId: String,
        competition: String,
        competitionLogo: String?,
        homeName: String,
        homeLogo: String?,
        awayName: String,
        awayLogo: String?,
        start: Instant,
        zoneId: ZoneId,
        statusCode: String,
        elapsed: Int?,
        homeScore: Int?,
        awayScore: Int?,
        detailsAvailable: Boolean,
    ): TodayEvent {
        val local = start.atZone(zoneId)
        val state = status(sport, statusCode)
        return TodayEvent(
            id = id,
            sport = sport,
            competitionId = competitionId,
            competition = competition,
            competitionLogoUrl = competitionLogo,
            home = homeName,
            homeLogoUrl = homeLogo,
            away = awayName,
            awayLogoUrl = awayLogo,
            startEpochMillis = start.toEpochMilli(),
            startMinuteOfDay = local.hour * 60 + local.minute,
            startLabel = timeFormatter.format(local),
            status = state,
            // Only the elapsed minute belongs here. Every other status
            // reads off the enum in TodayScreen.localizedStatusLabel(), so a
            // word written into the model would be stuck in one language.
            statusLabel = elapsed?.takeIf { state == TodayEventStatus.LIVE }
                ?.let { "$it\u2032" }
                .orEmpty(),
            score = if (
                homeScore != null && awayScore != null &&
                (sport != SportType.AUSTRALIAN_FOOTBALL ||
                    state in setOf(TodayEventStatus.LIVE, TodayEventStatus.FINISHED))
            ) {
                "$homeScore – $awayScore"
            } else {
                null
            },
            matchingChannels = 0,
            detailsAvailable = detailsAvailable,
        )
    }

    private fun deduplicateAfl(events: List<TodayEvent>): List<TodayEvent> = events
        .groupBy(TodayEvent::id)
        .values
        .map { duplicates -> duplicates.maxBy { eventRank(it.status) } }

    private fun eventRank(status: TodayEventStatus): Int = when (status) {
        TodayEventStatus.FINISHED -> 4
        TodayEventStatus.LIVE -> 3
        TodayEventStatus.POSTPONED,
        TodayEventStatus.CANCELLED,
        TodayEventStatus.INTERRUPTED,
        -> 2
        TodayEventStatus.SCHEDULED -> 1
        TodayEventStatus.UNKNOWN -> 0
    }

    private fun status(sport: SportType, code: String): TodayEventStatus {
        val value = code.trim().uppercase()
        return when (sport) {
            SportType.FOOTBALL -> when (value) {
                "TBD", "NS" -> TodayEventStatus.SCHEDULED
                "1H", "HT", "2H", "ET", "BT", "P", "LIVE" -> TodayEventStatus.LIVE
                "FT", "AET", "PEN", "AWD", "WO" -> TodayEventStatus.FINISHED
                "PST" -> TodayEventStatus.POSTPONED
                "CANC" -> TodayEventStatus.CANCELLED
                "SUSP", "INT", "ABD" -> TodayEventStatus.INTERRUPTED
                else -> TodayEventStatus.UNKNOWN
            }
            SportType.ICE_HOCKEY -> when (value) {
                "NS" -> TodayEventStatus.SCHEDULED
                "P1", "P2", "P3", "OT", "PT", "BT" -> TodayEventStatus.LIVE
                "FT", "AOT", "AP", "AW" -> TodayEventStatus.FINISHED
                "POST" -> TodayEventStatus.POSTPONED
                "CANC" -> TodayEventStatus.CANCELLED
                "INTR", "ABD" -> TodayEventStatus.INTERRUPTED
                else -> TodayEventStatus.UNKNOWN
            }
            SportType.AUSTRALIAN_FOOTBALL -> when (value) {
                "NS", "TBD" -> TodayEventStatus.SCHEDULED
                "Q1", "1Q", "Q2", "2Q", "HT", "Q3", "3Q", "Q4", "4Q", "OT", "LIVE" -> TodayEventStatus.LIVE
                "FT", "AOT", "AW" -> TodayEventStatus.FINISHED
                "POST", "PST" -> TodayEventStatus.POSTPONED
                "CANC" -> TodayEventStatus.CANCELLED
                "SUSP", "INTR", "ABD" -> TodayEventStatus.INTERRUPTED
                else -> TodayEventStatus.UNKNOWN
            }
            SportType.FORMULA_1 -> when (value) {
                "SCHEDULED", "NS", "TBD" -> TodayEventStatus.SCHEDULED
                "LIVE", "IN PROGRESS", "RUNNING", "STARTED" -> TodayEventStatus.LIVE
                "COMPLETED", "FINISHED", "FT" -> TodayEventStatus.FINISHED
                "POSTPONED", "PST", "DELAYED" -> TodayEventStatus.POSTPONED
                "CANCELLED", "CANCELED", "CANC" -> TodayEventStatus.CANCELLED
                "SUSPENDED", "ABANDONED", "RED FLAG", "INTERRUPTED" -> TodayEventStatus.INTERRUPTED
                else -> TodayEventStatus.UNKNOWN
            }
            SportType.NBA -> when (value) {
                "1", "NS", "SCHEDULED" -> TodayEventStatus.SCHEDULED
                "2", "LIVE", "IN PLAY" -> TodayEventStatus.LIVE
                "3", "FT", "FINISHED" -> TodayEventStatus.FINISHED
                "POST", "PST", "POSTPONED" -> TodayEventStatus.POSTPONED
                "CANC", "CANCELLED" -> TodayEventStatus.CANCELLED
                else -> TodayEventStatus.UNKNOWN
            }
            SportType.BASKETBALL,
            SportType.BASEBALL,
            SportType.HANDBALL,
            SportType.RUGBY,
            SportType.VOLLEYBALL,
            SportType.AMERICAN_FOOTBALL,
            SportType.MMA,
            -> when {
                value in setOf("NS", "TBD") -> TodayEventStatus.SCHEDULED
                value in setOf("FT", "AOT", "AP", "AW") -> TodayEventStatus.FINISHED
                value in setOf("POST", "PST") -> TodayEventStatus.POSTPONED
                value == "CANC" -> TodayEventStatus.CANCELLED
                value in setOf("SUSP", "INTR", "INT", "ABD") -> TodayEventStatus.INTERRUPTED
                value in setOf(
                    "LIVE", "HT", "BT", "OT", "1H", "2H",
                    "1Q", "2Q", "3Q", "4Q", "S1", "S2", "S3", "S4", "S5",
                ) ||
                    value.startsWith("Q") || value.startsWith("P") ||
                    value.startsWith("IN") || value.startsWith("SET") -> TodayEventStatus.LIVE
                else -> TodayEventStatus.UNKNOWN
            }
        }
    }

    private fun startInstant(value: JsonObject): Instant? {
        value.string("date")?.let { encoded ->
            runCatching { OffsetDateTime.parse(encoded).toInstant() }.getOrNull()?.let { return it }
            runCatching { Instant.parse(encoded) }.getOrNull()?.let { return it }
        }
        val timestamp = value.long("timestamp") ?: value.string("timestamp")?.toLongOrNull()
        return timestamp?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
    }

    private fun parseRoot(payload: String): JsonObject = json.parseToJsonElement(payload).jsonObject

    private fun ensureNoProviderErrors(root: JsonObject) {
        val errors = root["errors"] ?: return
        val hasErrors = when (errors) {
            JsonNull -> false
            is JsonArray -> errors.isNotEmpty()
            is JsonObject -> errors.isNotEmpty()
            else -> errors.toString().trim('"').isNotBlank()
        }
        if (hasErrors) throw SportsBackendException(CoreR.string.error_api_sports_service_error)
    }

    private fun JsonObject.obj(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.objOrNull(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    // A field that is an object where a primitive was expected (NFL's "date"
    // object, the NBA's "status" object) reads as absent, not as a crash.
    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
        ?.trim()?.take(4_000)?.takeIf(String::isNotBlank)
    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.score(name: String): Int? {
        val value = this[name] ?: return null
        val scoreObject = value as? JsonObject
        return if (scoreObject != null) {
            scoreObject.int("total") ?: scoreObject.int("score") ?: scoreObject.int("points")
        } else {
            runCatching { value.jsonPrimitive.intOrNull }.getOrNull()
        }
    }
    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.https(name: String): String? = string(name)?.takeIf {
        it.startsWith("https://", ignoreCase = true) && it.length <= 2_048
    }

}

private val SportType.providerName: String
    get() = when (this) {
        SportType.FOOTBALL -> "football"
        SportType.ICE_HOCKEY -> "hockey"
        SportType.AUSTRALIAN_FOOTBALL -> "afl"
        SportType.BASKETBALL -> "basketball"
        SportType.BASEBALL -> "baseball"
        SportType.HANDBALL -> "handball"
        SportType.RUGBY -> "rugby"
        SportType.VOLLEYBALL -> "volleyball"
        SportType.AMERICAN_FOOTBALL -> "american-football"
        SportType.MMA -> "mma"
        SportType.FORMULA_1 -> "formula-1"
        SportType.NBA -> "nba"
    }

private val SportType.sourceName: String
    get() = "api-sports-$providerName"

internal val SportType.competitionQuery: Map<String, String>
    get() = if (this == SportType.FOOTBALL) mapOf("current" to "true") else emptyMap()

private val SportType.selectedCompetitions: Set<String>
    get() = when (this) {
        SportType.FOOTBALL -> DEFAULT_FOOTBALL_COMPETITION_IDS
        SportType.ICE_HOCKEY -> DEFAULT_HOCKEY_COMPETITION_IDS
        SportType.AUSTRALIAN_FOOTBALL -> DEFAULT_AFL_COMPETITION_IDS
        SportType.BASKETBALL,
        SportType.BASEBALL,
        SportType.HANDBALL,
        SportType.RUGBY,
        SportType.VOLLEYBALL,
        SportType.AMERICAN_FOOTBALL,
        SportType.MMA,
        SportType.FORMULA_1,
        SportType.NBA,
        -> emptySet()
    }
