package com.sohva.tv.core.model.player

import java.util.Locale

/**
 * The viewer's VOD language preferences (spec 30 PLAY-FR-75, spec 70 SET-39): each slot a base
 * language code ("fi", "en", …) or null for Automatic.
 */
data class VodLanguages(
    val audio: String? = null,
    val audioSecond: String? = null,
    val subtitles: String? = null,
    val subtitlesSecond: String? = null,
) {
    val automatic: Boolean get() = audio == null && audioSecond == null && subtitles == null && subtitlesSecond == null

    companion object {
        /** Stored values are trimmed and lower-cased; blank is Automatic (spec 70). */
        fun stored(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    }
}

/** What to do with the subtitles once the audio is settled. */
sealed interface SubtitleChoice {
    /** Leave the stream's own choice. */
    data object Keep : SubtitleChoice

    data object Off : SubtitleChoice

    /** The text track at this index of the list given. */
    data class Track(val index: Int) : SubtitleChoice
}

/** The audio track to select (null: keep the stream's default) and the subtitle choice. */
data class TrackChoice(val audio: Int?, val subtitles: SubtitleChoice)

/**
 * VOD language preferences applied to a stream's tracks (PLAY-FR-75, -76). Pure, so the rule is
 * tested on the JVM; the player applies the answer once per item and never after the viewer chose
 * a track of that type.
 */
object TrackLanguages {
    private val threeLetter = mapOf(
        "fin" to "fi", "eng" to "en", "swe" to "sv", "dan" to "da", "nor" to "no", "nob" to "no", "nno" to "no",
        "est" to "et", "deu" to "de", "ger" to "de", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it",
        "nld" to "nl", "dut" to "nl",
    )

    /** The lower-case base code before `-` or `_`, three-letter codes mapped (PLAY-FR-76). */
    fun normalise(code: String?): String? {
        val base = code?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotEmpty() } ?: return null
        return threeLetter[base] ?: base
    }

    /**
     * [audio] and [text] are the tracks' language codes in list order. [audioByHand] and
     * [textByHand] say the viewer already chose a track of that type: that choice stays.
     */
    fun choose(prefs: VodLanguages, audio: List<String?>, text: List<String?>, audioByHand: Boolean, textByHand: Boolean): TrackChoice {
        if (prefs.automatic) return TrackChoice(null, SubtitleChoice.Keep)
        val audioCodes = audio.map(::normalise)
        val textCodes = text.map(::normalise)
        val audioIndex = if (audioByHand) null else firstOf(audioCodes, prefs.audio, prefs.audioSecond)
        val subtitles = when {
            textByHand -> SubtitleChoice.Keep
            // The viewer understands the audio: no subtitles.
            !audioByHand && prefs.audio != null && prefs.audio in audioCodes -> SubtitleChoice.Off
            prefs.subtitles == null && prefs.subtitlesSecond == null -> SubtitleChoice.Keep
            else -> firstOf(textCodes, prefs.subtitles, prefs.subtitlesSecond)?.let { SubtitleChoice.Track(it) } ?: SubtitleChoice.Off
        }
        return TrackChoice(audioIndex, subtitles)
    }

    private fun firstOf(codes: List<String?>, first: String?, second: String?): Int? {
        first?.let { wanted -> codes.indexOf(wanted).takeIf { it >= 0 }?.let { return it } }
        second?.let { wanted -> codes.indexOf(wanted).takeIf { it >= 0 }?.let { return it } }
        return null
    }
}
