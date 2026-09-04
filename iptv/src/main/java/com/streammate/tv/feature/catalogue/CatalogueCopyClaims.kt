package com.streammate.tv.feature.catalogue

/**
 * A language a provider claims for a copy, by writing it into the name.
 *
 * A claim, not a fact: `FIN` does not guarantee Finnish audio, and a copy
 * without it may still have it. Believing the stream over the filename is a
 * later job, and needs the player rather than the name.
 */
enum class CatalogueCopyLanguage {
    FINNISH,
    SWEDISH,
    ENGLISH,
    DANISH,
    NORWEGIAN,
    GERMAN,
    FRENCH,
    SPANISH,

    /** Sold as covering the Nordics without saying which of them. */
    NORDIC,

    /** Subtitles in several languages, which providers write as `MULTI-SUBS`. */
    SUBTITLED,

    /** Several audio tracks, written as `MULTI-AUDIO`. */
    MULTIPLE_AUDIO,
}

/** What a provider's name for a copy says the copy is. */
data class CatalogueCopyClaims(
    val languages: List<CatalogueCopyLanguage>,
    /**
     * Picture, in the terms the provider used: `4K UHD`, `HDR10`, `1080p`.
     * Left untranslated, because these are names rather than words.
     */
    val picture: List<String>,
)

/**
 * Read a copy's name for what it claims, rather than throwing it away.
 *
 * The matcher already recognises every one of these markers, in order to
 * remove them before asking a provider what a film is. That is what tells two
 * copies of one film apart, so keeping it costs no new parsing - only the
 * decision to keep it.
 *
 * Languages are read only where a provider announced one - see [CLAIM_ZONE].
 * Picture is read out of the whole name, because a resolution or a `4K` is
 * unambiguous wherever it sits, while a language word is not: *Fin del mundo*
 * is not a Finnish copy.
 */
fun catalogueCopyClaims(title: String): CatalogueCopyClaims {
    val claimed = CLAIM_ZONE.findAll(title).joinToString(" ") { it.value }.lowercase()
    val languages = CatalogueCopyLanguage.entries.filter { language ->
        LANGUAGE_MARKERS.getValue(language).containsMatchIn(claimed)
    }
    val resolution = RESOLUTION.find(title)?.value?.lowercase()
    return CatalogueCopyClaims(
        languages = languages,
        picture = catalogueQualityTags(title) + listOfNotNull(resolution),
    )
}

/**
 * Where a claim can be believed: announced at the front and closed by a
 * delimiter, bracketed, or in a technical tail.
 *
 * Deliberately stricter than what the matcher strips. A bare word followed by a
 * space is enough for the matcher, which only has to guess a search term and
 * loses nothing by guessing wide - it reads *Fin del mundo* as a Finnish copy
 * of *del mundo*, and still finds the film. A label shown to somebody has to be
 * surer than that, so a marker only counts where a provider announced it.
 */
private val CLAIM_ZONE = Regex(
    "(?i)^(?:\\s*\\p{L}{2,7}\\s*[|•·:–—-]+)+" +
        "|[\\[({][^\\])}]*[\\])}]" +
        "|(?:[|•·]|\\s[-–—])\\s*(?:multi[- ]?\\w+\\s*)+$",
)

/**
 * The markers providers actually write, in the two forms they write them:
 * a bare code and the three-letter one. Bounded on both sides, so `no` does not
 * fire on the `no` inside a longer word.
 */
private val LANGUAGE_MARKERS = mapOf(
    CatalogueCopyLanguage.FINNISH to marker("fi", "fin", "suomi"),
    CatalogueCopyLanguage.SWEDISH to marker("sv", "swe"),
    CatalogueCopyLanguage.ENGLISH to marker("en", "eng"),
    CatalogueCopyLanguage.DANISH to marker("da", "dan"),
    CatalogueCopyLanguage.NORWEGIAN to marker("no", "nor"),
    CatalogueCopyLanguage.GERMAN to marker("de", "ger"),
    CatalogueCopyLanguage.FRENCH to marker("fr", "fre"),
    CatalogueCopyLanguage.SPANISH to marker("es", "spa"),
    CatalogueCopyLanguage.NORDIC to marker("nordic", "nc"),
    CatalogueCopyLanguage.SUBTITLED to Regex("multi[- ]?(?:subs?|subtitles?)"),
    CatalogueCopyLanguage.MULTIPLE_AUDIO to Regex("multi[- ]?audio"),
)

private fun marker(vararg codes: String) =
    Regex("(?<![a-z0-9])(?:${codes.joinToString("|")})(?![a-z0-9])")

/**
 * Anywhere in the name, and deliberately only what is written down. `FHD` is
 * 1080p to most providers and not to all of them, so it is left alone rather
 * than turned into a number nobody claimed.
 */
private val RESOLUTION = Regex("(?<![A-Za-z0-9])\\d{3,4}[pP](?![A-Za-z0-9])")
