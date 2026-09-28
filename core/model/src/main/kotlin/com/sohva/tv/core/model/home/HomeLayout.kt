package com.sohva.tv.core.model.home

/** A row of Home by its id, and whether the profile shows it (spec 02 HOME-FR-86). */
data class HomeRowEntry(val id: String, val shown: Boolean)

/**
 * A profile's Home layout (spec 02 §4.14): its rows in order, each shown or hidden. Stored as one
 * text, the ids comma-separated with a hidden one preceded by `-`; the ids are text rather than a
 * fixed set, so later row kinds need no new format and a backup reader never meets an unknown enum
 * value (decision "Home layout: per profile, in Settings, in the backup").
 */
data class HomeLayout(val rows: List<HomeRowEntry>) {
    val shownIds: List<String> get() = rows.filter { it.shown }.map { it.id }

    fun isShown(id: String): Boolean = rows.firstOrNull { it.id == id }?.shown ?: false

    fun withShown(id: String, shown: Boolean): HomeLayout = HomeLayout(rows.map { if (it.id == id) it.copy(shown = shown) else it })

    /** The rows in [ids]'s order; rows [ids] leaves out keep their place after them. */
    fun withOrder(ids: List<String>): HomeLayout {
        val byId = rows.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }.distinctBy { it.id }
        return HomeLayout(ordered + rows.filter { r -> ordered.none { it.id == r.id } })
    }

    /** The stored text; null for the default, which is stored as no value at all. */
    fun encode(): String? = if (this == DEFAULT) null else rows.joinToString(",") { if (it.shown) it.id else "-${it.id}" }

    companion object {
        const val CONTINUE: String = "continue-watching"
        const val WATCH_NEXT: String = "watch-next"
        const val SPORT: String = "todays-sport"
        const val RECOMMENDED: String = "recommended"
        const val RECENT: String = "recent-channels"

        /** HOME-FR-01's rows in the default order. */
        val BUILT_IN: List<String> = listOf(CONTINUE, WATCH_NEXT, SPORT, RECOMMENDED, RECENT)

        val DEFAULT: HomeLayout = HomeLayout(BUILT_IN.map { HomeRowEntry(it, shown = true) })

        /** Longest stored text read; anything longer is not a layout this app wrote. */
        private const val MAX_TEXT = 4_096

        /**
         * HOME-FR-86: unknown and repeated ids are dropped, the stored order is kept, and a built-in
         * row the text lacks is added at the end, shown. Null, blank or unreadable text is the default.
         */
        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank() || text.length > MAX_TEXT) return DEFAULT
            val seen = HashSet<String>()
            val rows = ArrayList<HomeRowEntry>()
            for (part in text.split(',')) {
                val raw = part.trim()
                val hidden = raw.startsWith('-')
                val id = if (hidden) raw.substring(1) else raw
                if (id !in BUILT_IN || !seen.add(id)) continue
                rows += HomeRowEntry(id, shown = !hidden)
            }
            for (id in BUILT_IN) if (id !in seen) rows += HomeRowEntry(id, shown = true)
            return HomeLayout(rows)
        }
    }
}
