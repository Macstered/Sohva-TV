package com.sohva.tv.core.model.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 30 PLAY-FR-75, -76: VOD language preferences. */
class TrackLanguagesTest {
    private val finnish = VodLanguages(audio = "fi", audioSecond = "en", subtitles = "fi", subtitlesSecond = "en")

    @Test
    fun codesAreNormalisedToTheirBase() {
        assertEquals(listOf("fi", "en", "no", "de", "pt", null), listOf("fin", "en-US", "nob", "GER", "pt_BR", " ").map(TrackLanguages::normalise))
    }

    @Test
    fun automaticChangesNothing() {
        assertEquals(TrackChoice(null, SubtitleChoice.Keep), TrackLanguages.choose(VodLanguages(), listOf("en"), listOf("fi"), false, false))
    }

    @Test
    fun theFirstAudioLanguageWinsAndItsPresenceTurnsSubtitlesOff() {
        val choice = TrackLanguages.choose(finnish, listOf("eng", "fin"), listOf("fin"), false, false)
        assertEquals(TrackChoice(1, SubtitleChoice.Off), choice)
    }

    @Test
    fun withoutThePrimaryAudioTheSubtitlePreferencesApply() {
        assertEquals(TrackChoice(0, SubtitleChoice.Track(1)), TrackLanguages.choose(finnish, listOf("en"), listOf("sv", "fi"), false, false))
        assertEquals(TrackChoice(0, SubtitleChoice.Track(0)), TrackLanguages.choose(finnish, listOf("en"), listOf("en", "sv"), false, false))
        // A preference is set but neither language exists: subtitles off.
        assertEquals(TrackChoice(null, SubtitleChoice.Off), TrackLanguages.choose(finnish, listOf("de"), listOf("sv"), false, false))
    }

    @Test
    fun aTrackChosenByHandStays() {
        assertEquals(TrackChoice(null, SubtitleChoice.Track(0)), TrackLanguages.choose(finnish, listOf("fi"), listOf("fi"), audioByHand = true, textByHand = false))
        assertEquals(TrackChoice(1, SubtitleChoice.Keep), TrackLanguages.choose(finnish, listOf("en", "fi"), listOf("fi"), audioByHand = false, textByHand = true))
    }
}
