package com.sohva.tv.core.model

import com.sohva.tv.core.model.text.Initials
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun wordsThatDoNotStartWithALetterAreSkipped() {
        assertEquals("BE", Initials.of("Ben 10"))
        assertEquals("FS", Initials.of("2024 · Fargo: S02"))
    }

    @Test
    fun oneWordGivesItsFirstTwoLetters() {
        assertEquals("YL", Initials.of("Yle"))
        assertEquals("X", Initials.of("x"))
    }

    @Test
    fun twoOrMoreWordsGiveTheirFirstLetters() {
        assertEquals("YT", Initials.of("Yle TV1"))
        assertEquals("MU", Initials.of("MTV3.Uutiset/tänään"))
        assertEquals("ÄÖ", Initials.of("ääni-öljy"))
    }

    @Test
    fun withoutLetterWordsTheFirstTwoCharactersAreUsed() {
        assertEquals("24", Initials.of("24 7"))
        assertEquals("", Initials.of("   "))
    }
}
