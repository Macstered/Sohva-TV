package com.sohva.tv.core.model.settings

import com.sohva.tv.core.model.player.VodLanguages

/** The four VOD language rows (spec 70 SET-FR-72), each with the slot it pairs with. */
enum class VodLanguageSlot {
    AUDIO, AUDIO_SECOND, SUBTITLES, SUBTITLES_SECOND;

    val partner: VodLanguageSlot
        get() = when (this) {
            AUDIO -> AUDIO_SECOND
            AUDIO_SECOND -> AUDIO
            SUBTITLES -> SUBTITLES_SECOND
            SUBTITLES_SECOND -> SUBTITLES
        }

    fun of(languages: VodLanguages): String? = when (this) {
        AUDIO -> languages.audio
        AUDIO_SECOND -> languages.audioSecond
        SUBTITLES -> languages.subtitles
        SUBTITLES_SECOND -> languages.subtitlesSecond
    }

    companion object {
        /** The offered languages in picker order after Automatic (SET-FR-72). */
        val CODES: List<String> = listOf("fi", "en", "sv", "da", "no", "et", "de", "fr", "es", "it", "nl")

        /**
         * [slot] set to [code] (null = Automatic). A language equal to the partner slot's clears the
         * partner to Automatic; Automatic never clears (SET-FR-72).
         */
        fun choose(languages: VodLanguages, slot: VodLanguageSlot, code: String?): VodLanguages {
            val value = VodLanguages.stored(code)
            var next = languages.with(slot, value)
            if (value != null && slot.partner.of(next) == value) next = next.with(slot.partner, null)
            return next
        }

        private fun VodLanguages.with(slot: VodLanguageSlot, value: String?): VodLanguages = when (slot) {
            AUDIO -> copy(audio = value)
            AUDIO_SECOND -> copy(audioSecond = value)
            SUBTITLES -> copy(subtitles = value)
            SUBTITLES_SECOND -> copy(subtitlesSecond = value)
        }
    }
}

/** The image cache limit (spec 70 SET-FR-80): stored by name, read when the loader is built. */
enum class ArtworkCacheLimit(val megabytes: Int) {
    SMALL(100), MEDIUM(250), LARGE(500);

    val bytes: Long get() = megabytes * 1024L * 1024L

    companion object {
        fun fromStored(value: String?): ArtworkCacheLimit = entries.firstOrNull { it.name == value } ?: MEDIUM

        /** "%.0f MB" from 1 MiB, whole kB from 1 KiB, else "%d B", 1024-based (SET-FR-81). */
        fun size(bytes: Long): String = when {
            bytes >= MIB -> String.format(java.util.Locale.US, "%.0f MB", bytes / MIB.toDouble())
            bytes >= KIB -> String.format(java.util.Locale.US, "%.0f kB", bytes / KIB.toDouble())
            else -> "$bytes B"
        }

        private const val KIB = 1024L
        private const val MIB = 1024L * 1024L
    }
}
