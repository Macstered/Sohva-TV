package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataLanguagesTest {
    @Test
    fun `the default follows the interface language`() {
        assertEquals("fi-FI", MetadataLanguages.defaultFor("fi"))
        assertEquals("fi-FI", MetadataLanguages.defaultFor("fi-FI"))
        assertEquals("en-US", MetadataLanguages.defaultFor("en"))
        assertEquals("en-US", MetadataLanguages.defaultFor("sv"))
        assertEquals("en-US", MetadataLanguages.defaultFor(null))
    }

    @Test
    fun `only listed tags are supported`() {
        assertTrue(MetadataLanguages.isSupported("sv-SE"))
        assertFalse(MetadataLanguages.isSupported("sv"))
        assertFalse(MetadataLanguages.isSupported(null))
        assertEquals(MetadataLanguages.TAGS.size, MetadataLanguages.TAGS.toSet().size)
    }
}
