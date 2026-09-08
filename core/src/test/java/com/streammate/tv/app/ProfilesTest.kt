package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesTest {
    @Test
    fun `the default profile keeps the bare key and others get a suffix`() {
        assertEquals("favourite_channel_ids", Profiles.keyName("favourite_channel_ids", Profiles.DEFAULT_ID))
        assertEquals("favourite_channel_ids:p123", Profiles.keyName("favourite_channel_ids", "p123"))
    }

    @Test
    fun `profiles survive a round trip and shed what is broken`() {
        val profiles = listOf(Profile("default", "Sami", 1), Profile("p1", "Kids", 4))
        assertEquals(profiles, Profiles.decode(Profiles.encode(profiles)))
        assertEquals(emptyList<Profile>(), Profiles.decode(null))
        assertEquals(emptyList<Profile>(), Profiles.decode(""))
        assertEquals(listOf(Profile("p1", "Kids", 5)), Profiles.decode("p1Kids9no id1"))
    }

    @Test
    fun `the default profile is shown first, named or not`() {
        assertEquals(
            listOf(Profile("default", "Everyone"), Profile("p1", "Kids")),
            Profiles.withDefault(listOf(Profile("p1", "Kids")), "Everyone"),
        )
        assertEquals(
            listOf(Profile("default", "Sami"), Profile("p1", "Kids")),
            Profiles.withDefault(listOf(Profile("p1", "Kids"), Profile("default", "Sami")), "Everyone"),
        )
        assertEquals("Everyone", Profiles.displayName(emptyList(), "default", "Everyone"))
        assertEquals("Kids", Profiles.displayName(listOf(Profile("p1", "Kids")), "p1", "Everyone"))
    }
}
