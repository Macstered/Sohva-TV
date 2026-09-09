package com.streammate.tv.app

import com.streammate.tv.core.model.LibraryRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRestrictionTest {
    @Test
    fun `an empty room allows everything and a chosen set allows only its keys`() {
        val none = ProfileRestriction.NONE
        assertFalse(none.restricted)
        assertTrue(none.allows(LibraryRoom.LIVE, "name:news"))

        val kids = ProfileRestriction(live = setOf("name:kids"))
        assertTrue(kids.restricted)
        assertTrue(kids.allows(LibraryRoom.LIVE, "name:kids"))
        assertFalse(kids.allows(LibraryRoom.LIVE, "name:news"))
        // Films were never limited, so every film group stays.
        assertTrue(kids.allows(LibraryRoom.MOVIES, "name:horror"))
    }

    @Test
    fun `with replaces one room and leaves the others`() {
        val kids = ProfileRestriction(live = setOf("name:kids")).with(LibraryRoom.SERIES, setOf("name:cartoons"))
        assertEquals(setOf("name:kids"), kids.allowed(LibraryRoom.LIVE))
        assertEquals(setOf("name:cartoons"), kids.allowed(LibraryRoom.SERIES))
        assertEquals(emptySet<String>(), kids.allowed(LibraryRoom.MOVIES))
        assertEquals(ProfileRestriction.NONE, kids.with(LibraryRoom.LIVE, emptySet()).with(LibraryRoom.SERIES, emptySet()))
    }

    @Test
    fun `the pin guards unrestricted profiles once a restriction and a pin exist`() {
        val restrictions = mapOf("kids" to ProfileRestriction(live = setOf("name:kids")))
        // The restricted profile is always free to enter; the others sit behind the PIN.
        assertFalse(Profiles.entryNeedsPin("kids", restrictions, pinConfigured = true))
        assertTrue(Profiles.entryNeedsPin(Profiles.DEFAULT_ID, restrictions, pinConfigured = true))
        // Without a PIN there is nothing to ask for; without a restriction nothing to guard.
        assertFalse(Profiles.entryNeedsPin(Profiles.DEFAULT_ID, restrictions, pinConfigured = false))
        assertFalse(Profiles.entryNeedsPin(Profiles.DEFAULT_ID, emptyMap(), pinConfigured = true))
    }
}
