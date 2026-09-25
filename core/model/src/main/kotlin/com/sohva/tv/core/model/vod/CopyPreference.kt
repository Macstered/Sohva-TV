package com.sohva.tv.core.model.vod

/**
 * "When a film has more than one version" (spec 40 VOD-FR-32), stored as the enum name under
 * `preferred_catalogue_copy`; an unknown value reads as [NONE].
 */
enum class PreferredCopy {
    NONE, FINNISH_AUDIO, FINNISH_SUBTITLES, LARGEST_PICTURE;

    /**
     * The preference score of VOD-FR-29 from a copy's stored claim mask and picture rank. Copies
     * with equal scores keep wall order; only what is true at import ranks (never completeness,
     * which changes while titles are matched and would move the card and focus with it).
     */
    fun score(languageMask: Int, pictureRank: Int): Int {
        fun has(language: CopyLanguage) = languageMask and language.bit != 0
        return when (this) {
            NONE -> 0
            FINNISH_AUDIO -> when {
                has(CopyLanguage.FINNISH) -> 3
                has(CopyLanguage.MULTIPLE_AUDIO) -> 2
                has(CopyLanguage.NORDIC) -> 1
                else -> 0
            }
            FINNISH_SUBTITLES -> when {
                has(CopyLanguage.SUBTITLED) -> 3
                has(CopyLanguage.FINNISH) -> 2
                has(CopyLanguage.NORDIC) -> 1
                else -> 0
            }
            LARGEST_PICTURE -> pictureRank
        }
    }

    companion object {
        fun of(stored: String?): PreferredCopy = entries.firstOrNull { it.name == stored } ?: NONE
    }
}
