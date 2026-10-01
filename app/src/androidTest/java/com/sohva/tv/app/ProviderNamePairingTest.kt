package com.sohva.tv.app

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import com.sohva.tv.feature.sport.pairing.PairingScan
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android's regex implementation and bulk-thread CPU, with dates anchored to the emulator clock. */
@RunWith(AndroidJUnit4::class)
class ProviderNamePairingTest {
    @get:Rule
    val clearState = ClearStateRule()
    private val graph get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SohvaApplication).graph
    private val day = LocalDate.now(ZoneOffset.UTC)
    private val game = SportEvent(
        "api-sports:hockey:1", SportType.ICE_HOCKEY, "1", "Fixture league", null,
        Side("Tappara", null), Side("KooKoo", null), day.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        18 * 60 + 30, EventStatus.SCHEDULED, null, null, null,
    )
    private val tpsGame = game.copy(home = Side("Kiekko-Espoo", null), away = Side("TPS Turku", null))

    private fun tpsCandidate(i: Int, date: String): Candidate {
        val title = "NEXT | KIEKKO-ESPOO - TPS | $date 18:15 EEST (FI) | 8K EXCLUSIVE | FI: PLAY+ PPV ${i % CHANNELS}"
        return Candidate("channel-${i % CHANNELS}", title, "m3u-name:${i % CHANNELS}", title, null, null, 0, MatchSource.NAME)
    }

    @Test
    fun shortTpsNamesAreAvailableInThePagedDatabaseScan() = runBlocking(graph.dispatchers.bulk) {
        GuideFixture.seed(graph, groups = 1, perGroup = 2, withGuide = false)
        val db = graph.data.database
        val named = tpsCandidate(0, day.toString())
        db.openHelper.writableDatabase.execSQL(
            "UPDATE channel SET name = ?, provider_name = ? WHERE `key` = ?",
            arrayOf(named.title, named.title, "fixture-0:c0"),
        )
        val started = tpsGame.startMillis - 15 * 60_000
        val now = System.currentTimeMillis()
        db.sourceStatus().upsert(SourceStatusEntity("fixture-0", "epg", "success", now, now, null, null, null, 1, 0, 1, 1, 110 * 60_000))
        db.guideImport().insertProgrammes(listOf(ProgrammeEntity(
            sourceId = "fixture-0", snapshot = 1, epgId = "e0-1", startAt = started, stopAt = started + 110 * 60_000,
            title = "Liiga: Kiekko-Espoo – TPS", subtitle = null, description = null, categories = null, programmeKey = "tps-guide",
        )))
        val results = PairingScan(db.pairing()).run(listOf(tpsGame), TeamVariants.aliases(emptyMap()), emptyMap()).getValue(tpsGame.id)
        assertEquals(setOf("fixture-0:c0", "fixture-0:c1"), results.map { it.channelKey }.toSet())
        assertEquals(setOf(MatchSource.NAME, MatchSource.GUIDE), results.map { it.source }.toSet())
        assertEquals(listOf(Confidence.AVAILABLE), results.map { it.confidence }.distinct())
    }

    @Test
    fun reportedClubAliasesAreAvailableInBothRoomSources() = runBlocking(graph.dispatchers.bulk) {
        val clubs = listOf(
            Triple(SportType.ICE_HOCKEY, "IFK Helsinki", "HIFK"),
            Triple(SportType.ICE_HOCKEY, "Hameenlinna", "HPK"),
            Triple(SportType.ICE_HOCKEY, "Vaasan Sport", "Sport"),
            Triple(SportType.FOOTBALL, "Manchester United", "ManU"),
        )
        GuideFixture.seed(graph, groups = 1, perGroup = clubs.size * 2, withGuide = false)
        val db = graph.data.database
        val now = System.currentTimeMillis()
        val started = game.startMillis - 15 * 60_000
        val games = clubs.mapIndexed { i, (sport, apiName, streamName) ->
            val opponent = "Fixture Rovers $i"
            val title = "$streamName - $opponent | $day 18:15 EEST"
            db.openHelper.writableDatabase.execSQL(
                "UPDATE channel SET name = ?, provider_name = ? WHERE `key` = ?",
                arrayOf(title, title, "fixture-0:c${i * 2}"),
            )
            db.guideImport().insertProgrammes(listOf(ProgrammeEntity(
                sourceId = "fixture-0", snapshot = 1, epgId = "e0-${i * 2 + 1}", startAt = started, stopAt = started + 110 * 60_000,
                title = "$streamName - $opponent", subtitle = null, description = null, categories = null, programmeKey = "alias-$i",
            )))
            game.copy(id = "alias-$i", sport = sport, home = Side(apiName, null), away = Side(opponent, null))
        }
        db.sourceStatus().upsert(SourceStatusEntity("fixture-0", "epg", "success", now, now, null, null, null, clubs.size, 0, 1, 1, 110 * 60_000))
        val results = PairingScan(db.pairing()).run(games, TeamVariants.aliases(emptyMap()), emptyMap())
        games.forEachIndexed { i, event ->
            val found = results.getValue(event.id)
            assertEquals(event.home.name, setOf("fixture-0:c${i * 2}", "fixture-0:c${i * 2 + 1}"), found.map { it.channelKey }.toSet())
            assertEquals(setOf(MatchSource.NAME, MatchSource.GUIDE), found.map { it.source }.toSet())
            assertEquals(event.home.name, listOf(Confidence.AVAILABLE), found.map { it.confidence }.distinct())
        }
    }

    @Test
    fun matchingShortTeamNamesStaysBounded() = runBlocking(graph.dispatchers.bulk) {
        val aliases = TeamVariants.aliases(emptyMap())
        repeat(6) { sample ->
            val matcher = StreamMatcher(listOf(tpsGame), aliases, emptyMap())
            val cpuStart = Debug.threadCpuTimeNanos()
            repeat(CANDIDATES) { i -> matcher.add(tpsCandidate(i, day.toString())) }
            val results = matcher.finish().getValue(tpsGame.id)
            val cpuMs = (Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000
            assertEquals(CHANNELS, results.size)
            instrumentationStatus("TPS alias sample=$sample candidates=$CANDIDATES results=${results.size} cpuMs=$cpuMs")
        }
    }

    private fun instrumentationStatus(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "$message\n") })
    }

    private fun candidate(i: Int, date: String, clock: String): Candidate {
        val title = "NEXT | TAPPARA - KOOKOO | $date $clock (FI) | 8K EXCLUSIVE | FI: PLAY+ PPV ${i % CHANNELS}"
        return Candidate("channel-${i % CHANNELS}", title, "m3u-name:${i % CHANNELS}", title, null, null, 0, MatchSource.NAME)
    }

    @Test
    fun datedCountryStreamsAreAvailableOnAndroid() = runBlocking(graph.dispatchers.bulk) {
        val matcher = StreamMatcher(listOf(game), emptyMap(), emptyMap())
        listOf("19:15 EEST", "18:15 CEST", "12:15 PM UTC-4", "06:00 GMT").forEachIndexed { i, clock ->
            matcher.add(candidate(i, day.toString(), clock))
        }
        val results = matcher.finish().getValue(game.id)
        assertEquals(4, results.size)
        assertEquals(listOf(Confidence.AVAILABLE), results.map { it.confidence }.distinct())
    }

    @Test
    fun matchingProviderNamesStaysBounded() = runBlocking(graph.dispatchers.bulk) {
        val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[day.monthValue - 1]
        val labels = listOf(
            "${day.dayOfMonth} $month", "$month ${day.dayOfMonth}", day.toString(), "(${day.dayOfMonth}/${day.monthValue})",
        )
        // Every candidate mentions teams: this stresses the schedule parser more than the catalogue scan.
        // Only 120 channel winners survive each run, irrespective of the 12,000 input rows.
        repeat(6) { sample ->
            val matcher = StreamMatcher(listOf(game), emptyMap(), emptyMap())
            val cpuStart = Debug.threadCpuTimeNanos()
            val started = SystemClock.elapsedRealtime()
            repeat(CANDIDATES) { i -> matcher.add(candidate(i, labels[i % labels.size], "19:15 EEST")) }
            val results = matcher.finish().getValue(game.id)
            val cpuMs = (Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000
            val wallMs = SystemClock.elapsedRealtime() - started
            assertEquals(CHANNELS, results.size)
            Log.i("ProviderNamePairing", "sample=$sample candidates=$CANDIDATES results=${results.size} cpuMs=$cpuMs wallMs=$wallMs")
        }
    }

    private companion object {
        const val CHANNELS = 120
        const val CANDIDATES = 12_000
    }
}
