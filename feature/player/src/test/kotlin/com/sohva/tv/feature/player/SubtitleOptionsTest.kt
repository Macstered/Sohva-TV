package com.sohva.tv.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 50 FR-97: the options Discover's subtitle picker lists. */
class SubtitleOptionsTest {
    /**
     * The owner's Shield, 28 September 2026: the same subtitle file from the stream and an addon (or
     * twice from one addon) has one key, and the picker's list crashed on the repeated key. Each file
     * is listed once, where it came first.
     */
    @Test
    fun aFileOfferedTwiceIsListedOnce() {
        val results = SubtitleResults(
            listOf(
                SubtitleCandidate("4006dc4a33cc4015", null, "fi", 1),
                SubtitleCandidate("4006dc4a33cc4015", "OpenSubtitles", "fi", 1),
                SubtitleCandidate("77aa", "OpenSubtitles", "fi", 2),
                SubtitleCandidate("77aa", "OpenSubtitles", "fi", 3),
            ),
        )
        val options = SubtitleOptions.of(Tracks(), results, listOf("fi"), showAll = false)
        assertEquals(listOf("a4006dc4a33cc4015", "a77aa"), options.map { it.id })
        assertEquals("the stream's own copy is kept", null, options.first().candidate?.provider)
    }
}
