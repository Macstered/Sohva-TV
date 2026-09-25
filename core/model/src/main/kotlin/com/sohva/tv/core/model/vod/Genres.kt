package com.sohva.tv.core.model.vod

/**
 * The 22-genre vocabulary (spec 40 VOD-FR-12). [wire] is what the database stores; the enum order
 * is the Genres rail's order. [VERSION] is bumped whenever the list or its mapping changes, so the
 * metadata pass revisits every title (spec 41).
 */
enum class Genre(val wire: String) {
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
        const val VERSION: Int = 2

        private val byWire: Map<String, Genre> = entries.associateBy { it.wire }

        /** An unknown stored value is ignored (null). */
        fun ofWire(wire: String?): Genre? = wire?.let(byWire::get)
    }
}
