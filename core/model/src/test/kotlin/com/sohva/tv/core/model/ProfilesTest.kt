package com.sohva.tv.core.model

import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.ParentalPin
import com.sohva.tv.core.model.profile.Profile
import com.sohva.tv.core.model.profile.Profiles
import com.sohva.tv.core.model.profile.Restriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 04 §11 "Unit": keys, encoding, the shown list, adding, restrictions and the PIN. */
class ProfilesTest {
    @Test
    fun theFirstViewerKeepsTheBareKeyAndOthersGetTheirId() {
        assertEquals("recent_channel_ids", Profiles.key("recent_channel_ids", "default"))
        assertEquals("recent_channel_ids:p1726000000000", Profiles.key("recent_channel_ids", "p1726000000000"))
    }

    @Test
    fun encodingRoundTripsAndBrokenRecordsAreDropped() {
        val profiles = listOf(Profile("p1", "Aino", 1), Profile("p2", "Eero", 2))
        assertEquals(profiles, Profiles.decode(Profiles.encode(profiles)))
        val stored = "p1\u001FAino\u001F9\u001E\u001FNo id\u001F1\u001Ep1\u001FAgain\u001F2\u001Ep3\u001F" + "x".repeat(30) + "\u001Fx"
        val decoded = Profiles.decode(stored)
        assertEquals(listOf("p1", "p3"), decoded.map { it.id })
        assertEquals(5, decoded[0].colorIndex)
        assertEquals(24, decoded[1].name!!.length)
        assertEquals(0, decoded[1].colorIndex)
        assertEquals(emptyList<Profile>(), Profiles.decode(null))
    }

    @Test
    fun separatorsInANameBecomeSpaces() {
        val encoded = Profiles.encode(listOf(Profile("p1", "A\u001EB\u001FC", 0)))
        assertEquals("A B C", Profiles.decode(encoded)[0].name)
    }

    @Test
    fun theFirstViewerIsShownFirstNamedOrNot() {
        val added = listOf(Profile("p1", "Aino", 1))
        assertEquals(listOf("default", "p1"), Profiles.shown(added).map { it.id })
        assertEquals("Everyone", Profiles.shown(added)[0].displayName("Everyone"))
        val named = listOf(Profile("p1", "Aino", 1), Profile("default", "Mum", 0))
        assertEquals(listOf("Mum", "Aino"), Profiles.shown(named).map { it.displayName("Everyone") })
        assertEquals("p9", Profile("p9", null, 0).displayName("Everyone"))
    }

    @Test
    fun addingGivesTheNextColourAndRefusesASeventhProfile() {
        var stored = emptyList<Profile>()
        for (i in 1..5) stored = Profiles.add(stored, "  Viewer $i  ", "p$i")!!
        assertEquals(listOf(1, 2, 3, 4, 5), stored.map { it.colorIndex })
        assertEquals("Viewer 1", stored[0].name)
        assertNull(Profiles.add(stored, "Seventh", "p7"))
        assertNull(Profiles.add(emptyList(), "   ", "p1"))
        assertEquals(24, Profiles.add(emptyList(), "y".repeat(40), "p1")!![0].name!!.length)
    }

    @Test
    fun anEmptyRoomAllowsEverythingAndAChosenSetOnlyItsKeys() {
        val r = Restriction().with(OrgRoom.LIVE, setOf("name:news"))
        assertTrue(r.restricted)
        assertTrue(r.allows(OrgRoom.LIVE, "name:news"))
        assertFalse(r.allows(OrgRoom.LIVE, "name:sport"))
        assertFalse(r.allows(OrgRoom.LIVE, null))
        assertTrue(r.allows(OrgRoom.MOVIES, "name:anything"))
        assertFalse(r.with(OrgRoom.LIVE, emptySet()).restricted)
    }

    @Test
    fun thePinGuardsUnrestrictedProfilesOnlyOnceARestrictionAndAPinExist() {
        assertTrue(ParentalPin.entryNeedsPin(pinConfigured = true, anyRestricted = true, targetRestricted = false))
        assertFalse(ParentalPin.entryNeedsPin(pinConfigured = true, anyRestricted = true, targetRestricted = true))
        assertFalse(ParentalPin.entryNeedsPin(pinConfigured = false, anyRestricted = true, targetRestricted = false))
        assertFalse(ParentalPin.entryNeedsPin(pinConfigured = true, anyRestricted = false, targetRestricted = false))
    }

    @Test
    fun aPinIsFourToEightDigitsAndComparesOnlyEqualStrings() {
        assertTrue(ParentalPin.valid("1234"))
        assertTrue(ParentalPin.valid("12345678"))
        assertFalse(ParentalPin.valid("123"))
        assertFalse(ParentalPin.valid("123456789"))
        assertFalse(ParentalPin.valid("12a4"))
        assertFalse(ParentalPin.valid("١٢٣٤"))
        assertEquals("12345678", ParentalPin.cut("12-34 5678 9"))
        assertTrue(ParentalPin.same("2468", "2468"))
        assertFalse(ParentalPin.same("2468", "24680"))
        assertFalse(ParentalPin.same("2468", "2469"))
        assertFalse(ParentalPin.same(null, "2468"))
    }
}
