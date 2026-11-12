package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.feature.sport.provider.SportsParsers
import java.time.LocalDate
import java.time.ZoneOffset
import okio.Buffer
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Equal names must not erase distinct source identities, including fighters and race roles. */
@RunWith(Parameterized::class)
class ParticipantReferenceRegressionTest(private val sport: SportType) {
    private val date = "${LocalDate.now(ZoneOffset.UTC)}T15:30:00Z"

    @Test
    fun changingOnlyAParticipantIdChangesItsParsedIdentityInEveryProduct() {
        fun parse(id: Int) = SportsParsers.events(sport, Buffer().writeUtf8(payload(sport, id, date)), ZoneOffset.UTC).single()
        assertNotEquals(sport.name, parse(701).home, parse(702).home)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun sports(): List<Array<SportType>> = SportType.entries.map { arrayOf(it) }

    internal fun payload(sport: SportType, id: Int, date: String): String {
        val teams = """"teams":{"home":{"id":$id,"name":"Northbridge"},"away":{"id":801,"name":"Harbor"}}"""
        val item = when (sport) {
            SportType.FOOTBALL -> """{"fixture":{"id":1,"date":"$date","status":{"short":"NS"}},"league":{"id":39,"name":"Fixture Cup"},$teams}"""
            SportType.AUSTRALIAN_FOOTBALL -> """{"game":{"id":1},"date":"$date","league":{"id":1,"name":"Fixture Cup"},$teams}"""
            SportType.MMA -> """{"id":1,"date":"$date","fighters":{"first":{"id":$id,"name":"Northbridge"},"second":{"id":801,"name":"Harbor"}}}"""
            SportType.FORMULA_1 -> """{"id":1,"date":"$date","competition":{"id":$id,"name":"Northbridge"},"circuit":{"id":801,"name":"Harbor"}}"""
            SportType.NBA -> """{"id":1,"league":"standard","date":{"start":"$date"},"teams":{"home":{"id":$id,"name":"Northbridge"},"visitors":{"id":801,"name":"Harbor"}}}"""
            else -> """{"id":1,"date":"$date","league":{"id":1,"name":"Fixture Cup"},$teams}"""
        }
        return """{"response":[$item]}"""
    }

    }
}
