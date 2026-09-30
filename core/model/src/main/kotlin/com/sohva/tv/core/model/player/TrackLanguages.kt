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
 * tested on the JVM; the player checks changed track snapshots and never replaces a viewer's
 * manual choice of that type.
 */
object TrackLanguages {
    private val threeLetter = mapOf(
        "fin" to "fi", "eng" to "en", "swe" to "sv", "dan" to "da", "nor" to "no", "nob" to "no", "nno" to "no",
        "est" to "et", "deu" to "de", "ger" to "de", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it",
        "nld" to "nl", "dut" to "nl",
    )

    // Some providers write names in the language field, or supply only a track label. Keep this
    // to exact English/native names for the offered preferences: no locale scan or fuzzy guesses.
    private val names = mapOf(
        "finnish" to "fi", "suomi" to "fi", "english" to "en",
        "swedish" to "sv", "svenska" to "sv", "danish" to "da", "dansk" to "da",
        "norwegian" to "no", "norsk" to "no", "estonian" to "et", "eesti" to "et",
        "german" to "de", "deutsch" to "de", "french" to "fr", "français" to "fr",
        "spanish" to "es", "español" to "es", "italian" to "it", "italiano" to "it",
        "dutch" to "nl", "nederlands" to "nl",
    )
    private val unspecified = setOf("und", "unknown", "none", "off")

    /** The base code, accepting ISO codes and exact language names supplied by providers. */
    fun normalise(code: String?): String? {
        val base = code?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotEmpty() } ?: return null
        if (base in unspecified) return null
        return threeLetter[base] ?: names[base] ?: base
    }

    /** A language tag takes precedence; an unspecified tag can use an exact known track label. */
    fun ofTrack(language: String?, label: String?): String? =
        normalise(language) ?: label?.trim()?.lowercase(Locale.ROOT)?.let(names::get)

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

    /**
     * The track list may grow after its first callback (notably text after audio). Calculate only
     * changes still needed for the current list, so each callback can safely check again without
     * restarting a settled track or replacing a subtitle chosen through an addon.
     */
    fun changes(
        prefs: VodLanguages,
        audio: List<String?>,
        text: List<String?>,
        selectedAudio: Int?,
        selectedText: Int?,
        audioByHand: Boolean = false,
        textByHand: Boolean = false,
        addonSubtitleChosen: Boolean = false,
    ): TrackChoice {
        val wanted = choose(prefs, audio, text, audioByHand, textByHand || addonSubtitleChosen)
        val audioChange = wanted.audio?.takeUnless { it == selectedAudio }
        val subtitleChange = when (val subtitle = wanted.subtitles) {
            SubtitleChoice.Keep -> SubtitleChoice.Keep
            SubtitleChoice.Off -> if (selectedText == null) SubtitleChoice.Keep else SubtitleChoice.Off
            is SubtitleChoice.Track -> if (subtitle.index == selectedText) SubtitleChoice.Keep else subtitle
        }
        return TrackChoice(audioChange, subtitleChange)
    }

    private fun firstOf(codes: List<String?>, first: String?, second: String?): Int? {
        first?.let { wanted -> codes.indexOf(wanted).takeIf { it >= 0 }?.let { return it } }
        second?.let { wanted -> codes.indexOf(wanted).takeIf { it >= 0 }?.let { return it } }
        return null
    }
}
