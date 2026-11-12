package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.core.model.sport.pairing.DictionaryNames
import com.sohva.tv.core.model.sport.pairing.EventNames
import com.sohva.tv.core.model.sport.pairing.MatchText
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Labelled controls shared by the dictionary comparisons; dates stay relative to the clock. */
class DictionaryMatchingCorpusTest {
    private val day = LocalDate.now(ZoneOffset.UTC)
    private val kickoff = day.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

    private data class Case(
        val label: String,
        val sport: SportType,
        val home: String,
        val away: String,
        val text: String,
        val source: MatchSource,
        val offset: Int,
        val expected: Confidence?,
    )

    private fun result(case: Case): Confidence? {
        val event = SportEvent(
            "fixture:${case.label}", case.sport, "fixture-league", "Fixture Cup", null,
            Side(case.home, null), Side(case.away, null), kickoff, 15 * 60 + 30,
            EventStatus.SCHEDULED, null, null, null,
        )
        val candidate = Candidate(
            "fixture-channel", "Fixture", "fixture-programme", case.text, null, null,
            kickoff + case.offset * 60_000L, case.source,
        )
        val qualified = listOf("U21", "W", "Women", "B", "Reserves")
        // These synthetic reviewed identities exist in the dictionary, not the active fixture list.
        val guards = if (case.label.startsWith("qualified:")) EventNames(
            homeGuards = qualified.map { MatchText.normalise("Finland $it") }.toSet(),
            awayGuards = qualified.map { MatchText.normalise("Germany $it") }.toSet(),
        ) else EventNames()
        val names = DictionaryNames(events = mapOf(event.id to guards))
        return StreamMatcher(listOf(event), emptyMap(), emptyMap(), names)
            .apply { add(candidate) }.finish().getValue(event.id).singleOrNull()?.automatic
    }

    @Test
    fun allTwelveProductsRetainNameAndGuideControls() {
        val cases = SportType.entries.flatMap { sport ->
            val home = if (sport == SportType.FORMULA_1) "Northbridge Grand Prix" else "Northbridge"
            val away = if (sport == SportType.FORMULA_1) "Harbor Circuit" else "Harbor"
            listOf(
                Case("${sport.name}:name", sport, home, away, "$home - $away", MatchSource.NAME, 0, Confidence.AVAILABLE),
                Case("${sport.name}:guide-close", sport, home, away, "$home v $away", MatchSource.GUIDE, -15, Confidence.AVAILABLE),
                Case("${sport.name}:guide-early", sport, home, away, "$home v $away", MatchSource.GUIDE, -45, Confidence.POSSIBLE),
                Case("${sport.name}:guide-outside", sport, home, away, "$home v $away", MatchSource.GUIDE, 121, null),
                Case("${sport.name}:unrelated", sport, home, away, "Hilltown v Lakeport", MatchSource.NAME, 0, null),
            )
        }
        cases.forEach { assertEquals(it.label, it.expected, result(it)) }
    }

    @Test
    fun countryClocksAndInvalidDatesKeepTheirExistingMeaning() {
        val cases = listOf(
            Case("country-clock", SportType.ICE_HOCKEY, "Tappara", "KooKoo", "Tappara - KooKoo | $day 06:00 UTC", MatchSource.NAME, 0, Confidence.AVAILABLE),
            Case("old-date", SportType.ICE_HOCKEY, "Tappara", "KooKoo", "Tappara - KooKoo | ${day.minusDays(5)} 18:30", MatchSource.NAME, 0, Confidence.POSSIBLE),
            Case("invalid-date", SportType.ICE_HOCKEY, "Tappara", "KooKoo", "Tappara - KooKoo | Feb 30 18:30", MatchSource.NAME, 0, Confidence.POSSIBLE),
            Case("shared-nickname", SportType.FOOTBALL, "United", "United", "United v United", MatchSource.NAME, 0, null),
        )
        cases.forEach { assertEquals(it.label, it.expected, result(it)) }
    }

    @Test
    fun seniorFixturesCannotConsumeKnownQualifiedNamesWhenThoseFixturesAreAbsent() {
        for (suffix in listOf("U21", "W", "Women", "B", "Reserves")) {
            for (source in MatchSource.entries) {
                val case = Case(
                    "qualified:$suffix:$source", SportType.FOOTBALL, "Finland", "Germany",
                    "Finland $suffix - Germany $suffix", source, 0, null,
                )
                assertEquals(case.label, case.expected, result(case))
            }
        }
    }
}
