package com.sohva.tv.core.model.guide

import java.util.Locale

/**
 * One programme as the grid needs it (spec 20 GUIDE-FR-02): times already shifted by the source's
 * EPG offset, no description (the hero reads it by [id], GUIDE-NFR-13). [hasDescription] and
 * [categoryCount] only serve the duplicate rule of GUIDE-FR-53.
 */
data class GuideProgramme(
    val id: Long,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String?,
    val firstCategory: String?,
    val genre: Genre?,
    val hasDescription: Boolean,
    val categoryCount: Int,
    /** The §6 programme id that reminders keep. */
    val key: String,
) {
    fun isLive(now: Long): Boolean = start <= now && now < stop

    fun isPast(now: Long): Boolean = stop <= now

    fun isFuture(now: Long): Boolean = start > now

    /** Progress while live, else 0 (GUIDE-FR-04). */
    fun progress(now: Long): Float =
        if (!isLive(now)) 0f else ((now - start).toFloat() / maxOf(1L, stop - start)).coerceIn(0f, 1f)
}

/** The genre accent of a block (design/guide.md §2), in the table's order. */
enum class Genre(val keywords: List<String>) {
    SPORT(listOf("sport", "urheilu", "football", "jalkapallo", "hockey")),
    NEWS(listOf("news", "uutis", "current affairs", "ajankohtais", "weather")),
    CHILDREN(listOf("children", "kids", "lapset", "lasten", "animation")),
    FILM(listOf("movie", "film", "elokuva", "cinema", "drama", "draama")),
    ;

    companion object {
        /**
         * The first category, lower-cased, that contains a keyword decides; within it the table's
         * order (GUIDE-FR-58). No match, no accent.
         */
        fun of(categories: List<String>): Genre? {
            for (category in categories) {
                val lower = category.lowercase(Locale.ROOT)
                entries.firstOrNull { genre -> genre.keywords.any { lower.contains(it) } }?.let { return it }
            }
            return null
        }
    }
}

/** Per-channel clean-up of a provider's schedule (GUIDE-FR-53). */
object Schedules {
    /** Categories are stored joined with U+001F (plan/04 §15.5). */
    const val CATEGORY_SEPARATOR: Char = '\u001F'

    fun categories(stored: String?): List<String> =
        stored?.split(CATEGORY_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Drops programmes with `stop ≤ start`, keeps one per start time — the one with the most of
     * (subtitle, description, categories), then the longer, then the smaller id — sorted by start.
     */
    fun clean(programmes: List<GuideProgramme>): List<GuideProgramme> {
        if (programmes.isEmpty()) return programmes
        val byStart = HashMap<Long, GuideProgramme>(programmes.size * 2)
        for (p in programmes) {
            if (p.stop <= p.start) continue
            val held = byStart[p.start]
            if (held == null || better(p, held)) byStart[p.start] = p
        }
        return byStart.values.sortedBy { it.start }
    }

    private fun richness(p: GuideProgramme): Int =
        (if (p.subtitle.isNullOrBlank()) 0 else 1) + (if (p.hasDescription) 1 else 0) + p.categoryCount

    private fun better(a: GuideProgramme, b: GuideProgramme): Boolean {
        val ra = richness(a)
        val rb = richness(b)
        if (ra != rb) return ra > rb
        val la = a.stop - a.start
        val lb = b.stop - b.start
        if (la != lb) return la > lb
        return a.id < b.id
    }
}

