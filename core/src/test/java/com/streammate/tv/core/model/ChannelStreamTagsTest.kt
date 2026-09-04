package com.streammate.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelStreamTagsTest {

    private fun labels(name: String) = ChannelStreamTags.read(name).map(StreamTag::label)

    @Test
    fun `reads the markers real playlists carry`() {
        assertEquals(listOf("FHD", "FI"), labels("TV5 FHD FI"))
        assertEquals(listOf("HD", "FI"), labels("Yle TV1 HD FI"))
        assertEquals(listOf("4K", "50 FPS", "FI"), labels("FI| Sport 1 4K 50FPS"))
        assertEquals(listOf("FHD", "HDR", "EN"), labels("Sky Sports [FHD] [HDR] ENG"))
    }

    @Test
    fun `a name with nothing to say produces nothing`() {
        assertTrue(labels("VisaTV").isEmpty())
        assertTrue(labels("Kanava 1").isEmpty())
    }

    @Test
    fun `a marker has to be its own token`() {
        // The alternative is telling someone SciFi is a Finnish channel and that
        // Discovery is in Danish, which is worse than saying nothing.
        assertTrue("SciFi", labels("SciFi Channel").isEmpty())
        assertTrue("Discovery", labels("Discovery").isEmpty())
        assertTrue("Nordic", labels("Nordic Sport").isEmpty())
    }

    @Test
    fun `synonyms land on one label`() {
        assertEquals(labels("Sport UHD"), labels("Sport 4K"))
        assertEquals(labels("Sport 2160p"), labels("Sport 4K"))
        assertEquals(labels("Sport FULLHD"), labels("Sport FHD"))
        assertEquals(labels("Sport SWE"), labels("Sport SE"))
    }

    @Test
    fun `tags come back in a fixed order regardless of where they sit in the name`() {
        val expected = listOf("4K", "HDR", "60 FPS", "DE")
        assertEquals(expected, labels("GER 60FPS Sport HDR 4K"))
        assertEquals(expected, labels("Sport 4K HDR 60FPS DE"))
    }

    @Test
    fun `each kind is reported once`() {
        val kinds = ChannelStreamTags.read("Sport HD FHD 4K FI SE").map(StreamTag::kind)
        assertEquals(kinds.distinct(), kinds)
    }
}
