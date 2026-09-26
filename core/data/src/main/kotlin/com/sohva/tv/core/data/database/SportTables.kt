package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * One cached API-Sports answer (spec 60 §4.3): a day listing, a competition catalogue or a football
 * game's events. Freshness and the 24-hour stale window are per entry (SPORT-FR-32). A day
 * listing's games are kept normalised in [SportEventEntity] (§9 rule: no raw 4 MiB payloads);
 * only match events, a few kilobytes, keep their text in [payload].
 */
@Entity(tableName = "sport_feed", indices = [Index(value = ["stale_until"])])
data class SportFeedEntity(
    @androidx.room.PrimaryKey val key: String,
    val sport: String,
    /** `events`, `competitions` or `incidents`. */
    val kind: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "stale_until") val staleUntil: Long,
    @ColumnInfo(name = "quota_remaining") val quotaRemaining: Int?,
    val source: String,
    val payload: String?,
)

/** A game of a cached day listing, every competition of the day (toggling one costs no request). */
@Entity(tableName = "sport_event", primaryKeys = ["feed_key", "event_id"])
data class SportEventEntity(
    @ColumnInfo(name = "feed_key") val feedKey: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val sport: String,
    @ColumnInfo(name = "competition_id") val competitionId: String,
    val competition: String,
    @ColumnInfo(name = "competition_logo") val competitionLogo: String?,
    val home: String,
    @ColumnInfo(name = "home_logo") val homeLogo: String?,
    val away: String,
    @ColumnInfo(name = "away_logo") val awayLogo: String?,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    val status: String,
    val score: String?,
    @ColumnInfo(name = "score_detail") val scoreDetail: String?,
    val minute: String?,
)

/** A sport's competition catalogue (SPORT-FR-44), refreshed at most weekly. */
@Entity(tableName = "sport_competition", primaryKeys = ["sport", "competition_id"])
data class SportCompetitionEntity(
    val sport: String,
    @ColumnInfo(name = "competition_id") val competitionId: String,
    val name: String,
    val country: String?,
    val logo: String?,
)

/**
 * A sport API's requests on one UTC day (spec 60 §9 "Network and quota budget"): the count, the
 * remaining quota the provider last reported, and whether it said the quota is spent.
 */
@Entity(tableName = "sport_quota")
data class SportQuotaEntity(
    @androidx.room.PrimaryKey val provider: String,
    @ColumnInfo(name = "utc_day") val utcDay: Long,
    val requests: Int,
    val remaining: Int?,
    val exhausted: Boolean,
)

/** The viewer's choice for one game and channel (SPORT-FR-76): `confirmed` or `rejected`. */
@Entity(tableName = "event_channel_decision", primaryKeys = ["event_id", "channel_key"], indices = [Index(value = ["channel_key"])])
data class EventChannelDecisionEntity(
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "channel_key") val channelKey: String,
    val decision: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** An extra team name for pairing (SPORT-FR-107); both sides normalised. */
@Entity(tableName = "team_alias", primaryKeys = ["sport", "canonical", "alias"])
data class TeamAliasEntity(val sport: String, val canonical: String, val alias: String)

@Dao
interface SportDao {
    @Query("SELECT * FROM sport_feed WHERE `key` = :key")
    suspend fun feed(key: String): SportFeedEntity?

    @Upsert
    suspend fun putFeed(feed: SportFeedEntity)

    /** SPORT-FR-31: entries past their stale window go before a request, with their games. */
    @Transaction
    suspend fun dropStale(now: Long) {
        dropStaleEvents(now)
        dropStaleFeeds(now)
    }

    @Query("DELETE FROM sport_event WHERE feed_key IN (SELECT `key` FROM sport_feed WHERE stale_until < :now)")
    suspend fun dropStaleEvents(now: Long)

    @Query("DELETE FROM sport_feed WHERE stale_until < :now")
    suspend fun dropStaleFeeds(now: Long)

    /** A day's games of the followed competitions; a day of worldwide football is at most a few thousand rows. */
    @Query("SELECT * FROM sport_event WHERE feed_key = :feed AND competition_id IN (:competitions)")
    suspend fun events(feed: String, competitions: List<String>): List<SportEventEntity>

    @Query("SELECT * FROM sport_event WHERE feed_key = :feed")
    suspend fun allEvents(feed: String): List<SportEventEntity>

    /** Replaces a day's games and its entry in one transaction. */
    @Transaction
    suspend fun putDay(feed: SportFeedEntity, events: List<SportEventEntity>) {
        clearEvents(feed.key)
        events.chunked(500).forEach { insertEvents(it) }
        putFeed(feed)
    }

    @Query("DELETE FROM sport_event WHERE feed_key = :feed")
    suspend fun clearEvents(feed: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<SportEventEntity>)

    @Query("SELECT * FROM sport_competition WHERE sport = :sport")
    suspend fun competitions(sport: String): List<SportCompetitionEntity>

    @Transaction
    suspend fun putCompetitions(feed: SportFeedEntity, sport: String, rows: List<SportCompetitionEntity>) {
        clearCompetitions(sport)
        rows.chunked(500).forEach { insertCompetitions(it) }
        putFeed(feed)
    }

    @Query("DELETE FROM sport_competition WHERE sport = :sport")
    suspend fun clearCompetitions(sport: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompetitions(rows: List<SportCompetitionEntity>)

    @Query("SELECT * FROM sport_quota WHERE provider = :provider")
    suspend fun quota(provider: String): SportQuotaEntity?

    @Query("SELECT * FROM sport_quota")
    suspend fun quotas(): List<SportQuotaEntity>

    @Upsert
    suspend fun putQuota(quota: SportQuotaEntity)

    @Query("SELECT * FROM event_channel_decision WHERE event_id IN (:events)")
    suspend fun decisions(events: List<String>): List<EventChannelDecisionEntity>

    @Upsert
    suspend fun putDecision(decision: EventChannelDecisionEntity)

    @Query("DELETE FROM event_channel_decision WHERE event_id = :event AND channel_key = :channel")
    suspend fun deleteDecision(event: String, channel: String)

    /** A removed source's channels take their decisions along (spec 60 §6). */
    @Query("DELETE FROM event_channel_decision WHERE channel_key >= :from AND channel_key < :until")
    fun deleteDecisionsIn(from: String, until: String)

    @Query("SELECT * FROM team_alias WHERE sport IN (:sports)")
    suspend fun aliases(sports: List<String>): List<TeamAliasEntity>

    @Upsert
    suspend fun putAlias(alias: TeamAliasEntity)
}
