package com.streammate.tv.core.model

/**
 * What a title is, in one vocabulary the whole library can be grouped by.
 *
 * Metadata providers do not agree on a list, and TMDB does not even agree with
 * itself: its television list has *Action & Adventure* and *Sci-Fi & Fantasy*
 * where its film list has four separate entries, so a library grouped by the
 * provider's own genres ends up with near-duplicate rows sitting next to each
 * other. Everything is folded into this list on the way in, and it is this list
 * the rail, the counts and any group of your own are built from.
 *
 * [wireValue] is what goes in the database. It is spelled out rather than taken
 * from [name] so that renaming a constant here cannot quietly orphan every row
 * already stored under the old spelling.
 */
enum class CatalogueGenre(val wireValue: String) {
    ACTION("action"),
    ADVENTURE("adventure"),
    ANIMATION("animation"),
    COMEDY("comedy"),
    CRIME("crime"),
    DOCUMENTARY("documentary"),
    DRAMA("drama"),
    FAMILY("family"),
    FANTASY("fantasy"),
    HISTORY("history"),
    HORROR("horror"),
    MUSIC("music"),
    MYSTERY("mystery"),
    NEWS("news"),
    REALITY("reality"),
    ROMANCE("romance"),
    SCIENCE_FICTION("science_fiction"),
    SOAP("soap"),
    TALK("talk"),
    THRILLER("thriller"),
    WAR("war"),
    WESTERN("western"),
    ;

    companion object {
        /**
         * Bumped whenever this list changes, or whenever a provider's ids are
         * folded into it differently.
         *
         * Titles are stamped with the version they were sorted under, and the
         * metadata pass revisits anything stamped older. Without it a library
         * enriched before a genre existed would never learn about it, and a
         * correction to the mapping would only reach titles nobody had matched
         * yet.
         */
        const val VERSION = 2

        private val byWireValue = entries.associateBy(CatalogueGenre::wireValue)

        /** Null for anything stored by a version that knew a genre this one does not. */
        fun fromWireValue(value: String?): CatalogueGenre? = value?.let(byWireValue::get)
    }
}
