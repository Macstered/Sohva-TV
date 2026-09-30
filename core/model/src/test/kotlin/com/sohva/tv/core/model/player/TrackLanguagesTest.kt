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

    /** Provider seasons can name the language instead of supplying an ISO code. */
    @Test
    fun finnishAndSuomiLanguageNamesMatchTheFinnishPreference() {
        for (name in listOf("Finnish", "finnish", "Suomi", " SUOMI ")) {
            assertEquals(name, TrackChoice(1, SubtitleChoice.Off),
                TrackLanguages.choose(finnish, listOf("English", name), listOf("fi"), false, false))
        }
    }

    @Test
    fun onlyAnUnspecifiedLanguageUsesTheExactProviderLabel() {
        for (missing in listOf(null, "", "und", "unknown")) {
            assertEquals("fi", TrackLanguages.ofTrack(missing, " Finnish "))
            assertEquals("fi", TrackLanguages.ofTrack(missing, "SUOMI"))
        }
        assertEquals("en", TrackLanguages.ofTrack("en-US", "Finnish"))
        assertEquals("sv", TrackLanguages.ofTrack("swe", "Suomi"))
        assertEquals(null, TrackLanguages.ofTrack(null, "Finnish commentary"))
        assertEquals(null, TrackLanguages.ofTrack(null, "Unknown"))
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

    @Test
    fun laterTracksGetTheirPreferencesWithoutReSelectingSettledTracks() {
        val prefs = VodLanguages(audio = "fi", subtitles = "fi")
        assertEquals(
            TrackChoice(null, SubtitleChoice.Keep),
            TrackLanguages.changes(prefs, listOf("en"), emptyList(), selectedAudio = 0, selectedText = null),
        )
        assertEquals(
            TrackChoice(null, SubtitleChoice.Track(0)),
            TrackLanguages.changes(prefs, listOf("en"), listOf("fin"), selectedAudio = 0, selectedText = null),
        )
        assertEquals(
            TrackChoice(1, SubtitleChoice.Off),
            TrackLanguages.changes(prefs, listOf("en", "fin"), listOf("fin"), selectedAudio = 0, selectedText = 0),
        )
        assertEquals(
            TrackChoice(null, SubtitleChoice.Keep),
            TrackLanguages.changes(prefs, listOf("en", "fin"), listOf("fin"), selectedAudio = 1, selectedText = null),
        )
    }

    @Test
    fun manualAndAddonSubtitleChoicesRemainUntouchedWhenTracksChange() {
        assertEquals(
            TrackChoice(null, SubtitleChoice.Keep),
            TrackLanguages.changes(finnish, listOf("en", "fi"), listOf("fi"), 0, 0, audioByHand = true, textByHand = true),
        )
        assertEquals(
            TrackChoice(1, SubtitleChoice.Keep),
            TrackLanguages.changes(finnish, listOf("en", "fi"), listOf("fi"), 0, 0, addonSubtitleChosen = true),
        )
    }
}
