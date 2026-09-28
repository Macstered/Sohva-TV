package com.sohva.tv.core.model.home

/**
 * A row of Home by its id, and whether the profile shows it (spec 02 HOME-FR-86); an added row can
 * show only the titles the library has (HOME-FR-98).
 */
data class HomeRowEntry(val id: String, val shown: Boolean, val libraryOnly: Boolean = false)

/**
 * A profile's Home layout (spec 02 §4.14): its rows in order, each shown or hidden. Stored as one
 * text, the ids comma-separated with a hidden one preceded by `-` and a library-only one followed by
 * `!`; the ids are text rather than a
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

    fun isLibraryOnly(id: String): Boolean = rows.firstOrNull { it.id == id }?.libraryOnly ?: false

    /** HOME-FR-98: an added row shows only the titles the library has; built-in rows never filter. */
    fun withLibraryOnly(id: String, on: Boolean): HomeLayout =
        if (id in BUILT_IN) this else HomeLayout(rows.map { if (it.id == id) it.copy(libraryOnly = on) else it })

    /** The rows the viewer added (HOME-FR-94): every row that is not built in. */
    val added: List<String> get() = rows.map { it.id }.filter { it !in BUILT_IN }

    /** An added row at the end, shown; nothing when it is there already, unknown, or [MAX_ADDED] are there. */
    fun withAdded(id: String): HomeLayout {
        if (id !in ADDABLE || rows.any { it.id == id } || added.size >= MAX_ADDED) return this
        return HomeLayout(rows + HomeRowEntry(id, shown = true))
    }

    /** An added row gone; built-in rows are only ever hidden. */
    fun withRemoved(id: String): HomeLayout = if (id in BUILT_IN) this else HomeLayout(rows.filter { it.id != id })

    /** The stored text; null for the default, which is stored as no value at all. */
    fun encode(): String? = if (this == DEFAULT) {
        null
    } else {
        rows.joinToString(",") { (if (it.shown) "" else "-") + it.id + (if (it.libraryOnly) LIBRARY_ONLY else "") }
    }

    companion object {
        const val CONTINUE: String = "continue-watching"
        const val WATCH_NEXT: String = "watch-next"
        const val SPORT: String = "todays-sport"
        const val RECOMMENDED: String = "recommended"
        const val RECENT: String = "recent-channels"

        /** HOME-FR-01's rows in the default order. */
        val BUILT_IN: List<String> = listOf(CONTINUE, WATCH_NEXT, SPORT, RECOMMENDED, RECENT)

        /** Trakt's public charts (HOME-FR-94): no account needed. */
        const val TRAKT_TRENDING_MOVIES: String = "trakt:trending-movies"
        const val TRAKT_TRENDING_SHOWS: String = "trakt:trending-shows"
        const val TRAKT_POPULAR_MOVIES: String = "trakt:popular-movies"
        const val TRAKT_POPULAR_SHOWS: String = "trakt:popular-shows"
        const val TRAKT_ANTICIPATED_MOVIES: String = "trakt:anticipated-movies"
        const val TRAKT_ANTICIPATED_SHOWS: String = "trakt:anticipated-shows"
        const val TRAKT_BOX_OFFICE: String = "trakt:boxoffice"

        /** The profile's own Trakt watchlist (HOME-FR-94): needs its account. */
        const val TRAKT_WATCHLIST_MOVIES: String = "trakt:watchlist-movies"
        const val TRAKT_WATCHLIST_SHOWS: String = "trakt:watchlist-shows"

        /** Rows the viewer can add, in the order Settings › Home lists them. */
        val ADDABLE: List<String> = listOf(
            TRAKT_WATCHLIST_MOVIES, TRAKT_WATCHLIST_SHOWS, TRAKT_TRENDING_MOVIES, TRAKT_TRENDING_SHOWS, TRAKT_POPULAR_MOVIES,
            TRAKT_POPULAR_SHOWS, TRAKT_ANTICIPATED_MOVIES, TRAKT_ANTICIPATED_SHOWS, TRAKT_BOX_OFFICE,
        )

        /** Rows that need the profile's own Trakt account. */
        val NEEDS_ACCOUNT: Set<String> = setOf(TRAKT_WATCHLIST_MOVIES, TRAKT_WATCHLIST_SHOWS)

        /** The owner's limit (decision "M12"): at most 8 added rows per profile, so Home stays short and quick. */
        const val MAX_ADDED: Int = 8

        val DEFAULT: HomeLayout = HomeLayout(BUILT_IN.map { HomeRowEntry(it, shown = true) })

        /** The mark after an added row's id that shows only titles the library has (HOME-FR-98). */
        private const val LIBRARY_ONLY = '!'

        /** Longest stored text read; anything longer is not a layout this app wrote. */
        private const val MAX_TEXT = 4_096

        /**
         * HOME-FR-86: unknown and repeated ids are dropped, the stored order is kept, and a built-in
         * row the text lacks is added at the end, shown. Added rows past [MAX_ADDED] are dropped
         * (HOME-FR-94). Null, blank or unreadable text is the default.
         */
        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank() || text.length > MAX_TEXT) return DEFAULT
            val seen = HashSet<String>()
            val rows = ArrayList<HomeRowEntry>()
            var added = 0
            for (part in text.split(',')) {
                val raw = part.trim()
                val hidden = raw.startsWith('-')
                val marked = raw.removePrefix("-")
                val libraryOnly = marked.endsWith(LIBRARY_ONLY)
                val id = marked.removeSuffix(LIBRARY_ONLY.toString())
                val builtIn = id in BUILT_IN
                if (!builtIn && (id !in ADDABLE || added >= MAX_ADDED)) continue
                if (!seen.add(id)) continue
                if (!builtIn) added++
                rows += HomeRowEntry(id, shown = !hidden, libraryOnly = libraryOnly && !builtIn)
            }
            for (id in BUILT_IN) if (id !in seen) rows += HomeRowEntry(id, shown = true)
            return HomeLayout(rows)
        }
    }
}
