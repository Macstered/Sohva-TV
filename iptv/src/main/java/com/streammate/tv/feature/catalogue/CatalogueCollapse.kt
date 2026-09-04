package com.streammate.tv.feature.catalogue

import com.streammate.tv.iptv.metadata.catalogueWorkKey

/**
 * What folding copies into films needs to know about one copy.
 *
 * Deliberately the three things that decide identity and nothing else. What a
 * card ends up showing is the caller's business, handed back through the
 * `fillIn` lambda, which keeps this file free of anything that has to be drawn.
 */
interface CatalogueCopy {
    val contentKey: String
    val title: String
    val year: Int?
}

/**
 * The library seen as films rather than as copies.
 *
 * A film's key is always the real content key of one of its copies, never an
 * invented one, so anything already keyed by content key - a chosen poster, a
 * saved position, a metadata attempt - keeps working against it unchanged.
 */
data class CatalogueFilms<T : CatalogueCopy>(
    /** One entry per film, in the order the copies arrived. */
    val films: List<T>,
    /**
     * Where a copy's key now points. Only copies folded into something else
     * appear here, so a lookup wants [keyOf] rather than a bare `get`.
     */
    val filmKeyByCopy: Map<String, String>,
) {
    /** Nothing was folded, so every map keyed by copy is already keyed by film. */
    val collapsed: Boolean get() = filmKeyByCopy.isNotEmpty()

    fun keyOf(contentKey: String): String = filmKeyByCopy[contentKey] ?: contentKey
}

/**
 * One entry per film, however many playlists carry it.
 *
 * The copy [rank] scores highest stands for the film, and takes from the others
 * whatever it is missing - which is why a poster missing from one playlist is
 * filled in from the other, and why this makes the wall better rather than
 * merely shorter. Copies that nothing separates keep library order, so with no
 * preference expressed the wall stands on the same copy it always did.
 *
 * What it is deliberately not ranked by is which copy looks the most complete.
 * Completeness arrives gradually as titles are matched, and a card that changed
 * identity halfway through browsing would take the focus with it. A ranking has
 * to rest on something already true of a copy the moment the library loads.
 */
fun <T : CatalogueCopy> catalogueFilms(
    copies: List<T>,
    externalIds: Map<String, String>,
    workKey: (T) -> String = { copy ->
        catalogueWorkKey(copy.title, copy.year, externalIds[copy.contentKey])
    },
    rank: (T) -> Int = { 0 },
    fillIn: (primary: T, copies: List<T>) -> T,
): CatalogueFilms<T> {
    val grouped = LinkedHashMap<String, MutableList<T>>(copies.size)
    copies.forEach { copy ->
        grouped.getOrPut(workKey(copy)) { mutableListOf() }.add(copy)
    }
    // A library with nothing duplicated in it pays for the pass above and
    // nothing else: no rebuilt list, no maps, and every lookup downstream
    // answers straight from the map it already had.
    if (grouped.size == copies.size) return CatalogueFilms(copies, emptyMap())

    val films = ArrayList<T>(grouped.size)
    val filmKeyByCopy = HashMap<String, String>()
    grouped.values.forEach { group ->
        if (group.size == 1) {
            films.add(group.first())
            return@forEach
        }
        // maxByOrNull keeps the first of equals, which is what leaves anything
        // the preference cannot separate in the order the playlists gave it.
        val primary = group.maxByOrNull(rank) ?: group.first()
        // Handed over with the copy standing for the film first: the fill-in
        // prefers what the primary already has, and anything reading the group
        // in order then reads it the same way round.
        films.add(fillIn(primary, listOf(primary) + group.filterNot { it === primary }))
        group.forEach { copy ->
            if (copy.contentKey != primary.contentKey) {
                filmKeyByCopy[copy.contentKey] = primary.contentKey
            }
        }
    }
    return CatalogueFilms(films, filmKeyByCopy)
}

/**
 * A map keyed by copy, read as a map keyed by film.
 *
 * Genres, positions and chosen posters are all recorded against the copy that
 * happened to be enriched or played, so without this a film would lose whatever
 * its siblings knew the moment the wall started showing one card for it.
 * [merge] settles what a film gets where two of its copies both have something.
 */
fun <V> Map<String, V>.byFilm(films: CatalogueFilms<*>, merge: (V, V) -> V): Map<String, V> {
    if (!films.collapsed) return this
    val byFilm = HashMap<String, V>(size)
    forEach { (contentKey, value) ->
        val filmKey = films.keyOf(contentKey)
        val existing = byFilm[filmKey]
        byFilm[filmKey] = if (existing == null) value else merge(existing, value)
    }
    return byFilm
}

/** As [byFilm], for the sets that record that something is true of a title. */
fun Set<String>.byFilm(films: CatalogueFilms<*>): Set<String> =
    if (films.collapsed) mapTo(HashSet(size), films::keyOf) else this
