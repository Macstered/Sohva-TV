package com.sohva.tv.core.model.vod

/**
 * Small text rules of spec 40 that are computed at import or once per page, never in composition
 * (§9.4): the year a title carries, the breadcrumb's group, an episode's display title and the
 * leading number of a provider rating.
 */
object VodText {
    private val bracketYear = Regex("""[\[(]((?:19|20)\d{2})[\])]""")
    private val decoration = Regex("""\[[^\[\]]*]|\([^()]*\)""")
    private val spaces = Regex("""\s{2,}""")
    private const val TITLE_TRIM = " -–—:."

    /** The provider's year, else a `(19xx|20xx)` year in brackets or parentheses (VOD-FR-21). */
    fun year(providerYear: Int?, title: String): Int? =
        providerYear ?: bracketYear.find(title)?.groupValues?.get(1)?.toInt()

    /**
     * The breadcrumb's group (VOD-FR-62): every `[…]` and `(…)` removed and runs of spaces
     * collapsed; the raw name when nothing is left (" Movies [Multi-Sub] (4K) " → "Movies",
     * "[4K]" → "[4K]").
     */
    fun breadcrumbGroup(group: String): String {
        val cleaned = spaces.replace(decoration.replace(group, ""), " ").trim()
        return cleaned.ifEmpty { group.trim() }
    }

    /**
     * An episode's display title (VOD-FR-85): the provider title trimmed; when it carries the
     * `S<season>E<episode>` marker, the text after it trimmed of spaces and dashes. Null means the
     * caller shows the translated "Episode %1$d".
     */
    fun episodeTitle(providerTitle: String?, season: Int, episode: Int): String? {
        val title = providerTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        val marker = Regex("""(?i)S0*$season\s*E0*$episode(?!\d)""").find(title) ?: return title
        return title.substring(marker.range.last + 1).trim { it in TITLE_TRIM || it.isWhitespace() }.ifEmpty { null }
    }

    /**
     * The leading number of a provider rating (VOD-FR-11): the text must start with a digit, a
     * comma counts as a decimal point, and `e`/`E` never make an exponent ("1e5" reads as 1).
     * "7,5" → 7.5, "8/10" → 8.0, "N/A" → null.
     */
    fun ratingNumber(rating: String?): Double? {
        val text = rating?.trim() ?: return null
        if (text.isEmpty() || !text[0].isDigit()) return null
        var end = 0
        var dot = false
        while (end < text.length) {
            val c = text[end]
            if (c.isDigit()) {
                end++
            } else if ((c == '.' || c == ',') && !dot && end + 1 < text.length && text[end + 1].isDigit()) {
                dot = true
                end++
            } else {
                break
            }
        }
        return text.substring(0, end).replace(',', '.').toDoubleOrNull()
    }

    /** The rating as a sortable integer (tenths), null when there is none: `rating_x10`. */
    fun ratingTenths(rating: String?): Int? = ratingNumber(rating)?.takeIf { it <= 1_000 }?.let { Math.round(it * 10).toInt() }
}
