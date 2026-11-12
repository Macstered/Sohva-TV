package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner's 2 October photos. Pure date logic has no expiring guide/repository window. */
class NumericNameDateTest {
    private val year = LocalDate.now(ZoneOffset.UTC).year
    private val photoDay = LocalDate.of(year, 10, 2)

    private fun matches(home: String, away: String, title: String, day: LocalDate = photoDay,
                        hour: Int = 19, minute: Int = 45, sport: SportType = SportType.FOOTBALL): List<StreamMatch> {
        val event = SportEvent("fixture:photo", sport, "fixture", "Fixture league", null,
            Side(home, null), Side(away, null), day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli(),
            hour * 60 + minute, EventStatus.SCHEDULED, null, null, null)
        val candidate = Candidate("fixture:channel", title, "m3u-name:fixture", title, null, null, 0, MatchSource.NAME)
        return StreamMatcher(listOf(event), emptyMap(), emptyMap()).apply { add(candidate) }.finish().getValue(event.id)
    }

    private fun available(home: String, away: String, titles: List<String>, sport: SportType = SportType.FOOTBALL) {
        titles.forEach { assertEquals(it, Confidence.AVAILABLE, matches(home, away, it, sport = sport).single().automatic) }
    }

    @Test
    fun ukraineNorthernIrelandPhotoListingsAreAvailable() = available("Ukraine", "Northern Ireland", listOf(
        "3:45 PM EDT Ukraine vs. Northern Ireland 10/2 [DAZN CA]",
        "[daznCA] (2/10) 18:45 Soccer: Ukraine vs. Northern Ireland",
    ))

    @Test
    fun faroeSlovakiaPhotoListingsAreAvailable() = available("Faroe Islands", "Slovakia", listOf(
        "3:45 PM EDT Faroe Islands vs. Slovakia 10/2 [DAZN CA]",
        "[daznCA] (2/10) 18:45 Soccer: Faroe Islands vs. Slovakia",
    ))

    @Test
    fun bosniaSwedenPhotoListingsAreAvailable() = available("Bosnia & Herzegovina", "Sweden", listOf(
        "3:45 PM EDT Bosnia-Herzegovina vs. Sweden 10/2 [DAZN CA]",
        "[daznCA] (2/10) 18:45 Soccer: Bosnia-Herzegovina vs. Sweden",
    ))

    @Test
    fun tapparaJokeritPhotoListingsAreAvailable() = available("Tappara", "Jokerit", listOf(
        "19:15 Tappara - Jokerit Liiga 10/2 [MTV Urheilu FI]",
        "19:15 Tappara - Jokerit Liiga 10/2 [TV4 Play]",
    ), SportType.ICE_HOCKEY)

    @Test
    fun espooKarpatPhotoListingsAreAvailable() = available("Kiekko-Espoo", "Karpat", listOf(
        "19:15 K-Espoo - Kärpät Liiga 10/2 [MTV Urheilu FI]",
        "18:15 Kiekko-Espoo - Kärpät Liiga 10/2 [TV4 Play]",
    ), SportType.ICE_HOCKEY)

    @Test
    fun eitherNumericOrderCanSupportEachFixtureDayWithoutAssumingProviderLocale() {
        for (month in 1..12) for (day in 1..12) {
            val date = LocalDate.of(year, month, day)
            for (text in setOf("$month/$day", "$day/$month", "$month/$day/$year", "$day/$month/$year")) {
                for (clock in listOf("18:45", "3:45 PM UTC-4", "06:00 EEST")) {
                    assertEquals("$date: $text $clock", Confidence.AVAILABLE,
                        matches("Northbridge", "Harbor", "Northbridge - Harbor $text $clock", date).single().automatic)
                }
            }
        }
    }

    @Test
    fun unrelatedDatesYearsAndOpponentsAreNotPromoted() {
        for (date in listOf("9/3", "3/9", "10/2/${year - 1}", "2/10/${year + 1}", "0/2", "31/2")) {
            assertEquals(date, Confidence.POSSIBLE,
                matches("Tappara", "Jokerit", "Tappara - Jokerit $date 19:15").single().automatic)
        }
        assertTrue(matches("Tappara", "Jokerit", "Tappara - Ilves 10/2 19:15").isEmpty())
        assertTrue(matches("Tappara", "Jokerit", "Tappara 10/2 19:15").none { it.automatic == Confidence.AVAILABLE })
    }

    @Test
    fun statedEdtDeterminesTheDateAcrossMidnightButNeverTheConfidenceClockWindow() {
        val localDate = photoDay.minusDays(1)
        val title = "11:30 PM EDT Northbridge - Harbor ${localDate.monthValue}/${localDate.dayOfMonth}"
        val result = matches("Northbridge", "Harbor", title, hour = 2, minute = 30).single()
        assertEquals(Confidence.AVAILABLE, result.automatic)
        assertEquals(true, result.explicitStart)
        assertEquals(60L, result.offsetMinutes)
    }

    @Test
    fun numericDatesCanBridgeOnlyTheExistingZonedMidnightWindow() {
        for ((clock, expected) in listOf("23:45 UTC" to Confidence.AVAILABLE, "20:00 UTC" to Confidence.POSSIBLE, "23:45" to Confidence.POSSIBLE)) {
            assertEquals(clock, expected,
                matches("Northbridge", "Harbor", "Northbridge - Harbor 1/10 $clock", hour = 0, minute = 15).single().automatic)
        }
    }
}
