package com.sohva.tv.feature.trakt.shelf

import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktJson
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.protocol.fields
import com.sohva.tv.feature.trakt.protocol.ids
import com.sohva.tv.feature.trakt.protocol.items
import com.sohva.tv.feature.trakt.protocol.long
import com.sohva.tv.feature.trakt.protocol.text
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okio.Buffer

/** Home's two Trakt rows (spec 51 FR-25, -26); the store key is the §6 record name. */
enum class TraktShelfKind(val key: String) { WATCH_NEXT("nextup"), RECOMMENDED("recommendations") }

/** One Trakt card (FR-27, -28). Watch next cards carry the next episode's numbers and title. */
data class TraktCard(
    val kind: TraktKind,
    val ids: TraktIds,
    val title: String,
    val year: Int?,
    val overview: String?,
    val poster: String?,
    val fanart: String?,
    val season: Int? = null,
    val number: Int? = null,
    val episodeTitle: String? = null,
    val updatedAt: Long = 0,
) {
    /** FR-28: `<kind>:<trakt|tmdb|imdb id>:<season>:<number>`. */
    val key: String
        get() {
            val id = ids.trakt?.toString() ?: ids.tmdb?.let { "tmdb:$it" } ?: ids.imdb ?: ids.tvdb?.let { "tvdb:$it" }
            return "${kind.name.lowercase()}:$id:${season ?: ""}:${number ?: ""}"
        }
}

/** A stored row: when it was built, its cards, and a public list's name (HOME-FR-99). */
data class TraktShelf(val at: Long, val cards: List<TraktCard>, val title: String? = null)

/**
 * The stored Watch next and Recommended lists (§6 `nextup:`, `recommendations:`), read from the
 * encrypted store once per profile and list and then served from memory (FR-28). The memory
 * holds at most two lists of ≤ 20 cards per profile read this session; [forget] drops a profile's.
 */
class TraktShelves(private val host: TraktHost) {
    private val _shelves = MutableStateFlow<Map<Pair<String, TraktShelfKind>, TraktShelf?>>(emptyMap())

    /** Every list read so far; Home maps it to its rows once Continue watching has settled. */
    val shelves: StateFlow<Map<Pair<String, TraktShelfKind>, TraktShelf?>> = _shelves.asStateFlow()

    suspend fun read(profile: String, kind: TraktShelfKind): TraktShelf? {
        val slot = profile to kind
        _shelves.value[slot]?.let { return it }
        if (slot in _shelves.value) return null
        val read = withContext(host.dispatchers.io) { host.store.secret("${kind.key}:$profile")?.let(::decode) }
        _shelves.update { it + (slot to read) }
        return read
    }

    /** One commit per list per sync (§9 rule). */
    suspend fun save(profile: String, kind: TraktShelfKind, shelf: TraktShelf) {
        withContext(host.dispatchers.io) { host.store.putSecret("${kind.key}:$profile", encode(shelf)) }
        _shelves.update { it + ((profile to kind) to shelf) }
    }

    fun forget(profile: String) = _shelves.update { map -> map.filterKeys { it.first != profile } }

    /** A hidden Home row's list (spec 02 HOME-FR-87): gone from store and memory, so showing it again fetches it afresh. */
    suspend fun drop(profile: String, kind: TraktShelfKind) {
        if (_shelves.value[profile to kind] == null && profile to kind in _shelves.value) return
        withContext(host.dispatchers.io) { host.store.putSecret("${kind.key}:$profile", null) }
        _shelves.update { it + ((profile to kind) to null) }
    }

    /** Device tests. */
    fun clear() = _shelves.update { emptyMap() }

    companion object {
        fun encode(shelf: TraktShelf): String = TraktJson.write {
            beginObject()
            name("at").value(shelf.at)
            shelf.title?.let { name("title").value(it) }
            name("items").beginArray()
            shelf.cards.forEach { write(it) }
            endArray()
            endObject()
        }

        fun decode(text: String): TraktShelf? = runCatching {
            var at = 0L
            var title: String? = null
            val cards = ArrayList<TraktCard>()
            TraktJson.parse(Buffer().writeUtf8(text)) { j ->
                j.fields { f ->
                    when (f) {
                        "at" -> at = j.long() ?: 0L
                        "title" -> title = j.text(200)
                        "items" -> j.items { read(j)?.let(cards::add) }
                        else -> j.skipValue()
                    }
                }
            }
            TraktShelf(at, cards, title)
        }.getOrNull()

        private fun JsonWriter.write(c: TraktCard) {
            beginObject()
            name("kind").value(if (c.kind == TraktKind.MOVIE) "movie" else "show")
            name("title").value(c.title)
            c.year?.let { name("year").value(it.toLong()) }
            c.overview?.let { name("overview").value(it) }
            c.poster?.let { name("poster").value(it) }
            c.fanart?.let { name("fanart").value(it) }
            name("ids").beginObject()
            c.ids.trakt?.let { name("trakt").value(it) }
            c.ids.tmdb?.let { name("tmdb").value(it) }
            c.ids.imdb?.let { name("imdb").value(it) }
            c.ids.tvdb?.let { name("tvdb").value(it) }
            endObject()
            c.season?.let { name("season").value(it.toLong()) }
            c.number?.let { name("number").value(it.toLong()) }
            c.episodeTitle?.let { name("episodeTitle").value(it) }
            name("updatedAt").value(c.updatedAt)
            endObject()
        }

        private fun read(j: JsonReader): TraktCard? {
            var kind: TraktKind? = null
            var title: String? = null
            var year: Int? = null
            var overview: String? = null
            var poster: String? = null
            var fanart: String? = null
            var ids = TraktIds()
            var season: Int? = null
            var number: Int? = null
            var episodeTitle: String? = null
            var updated = 0L
            j.fields { f ->
                when (f) {
                    "kind" -> kind = when (j.text(8)) {
                        "movie" -> TraktKind.MOVIE
                        "show" -> TraktKind.SHOW
                        else -> null
                    }
                    "title" -> title = j.text(200)
                    "year" -> year = j.long()?.toInt()
                    "overview" -> overview = j.text(1_000)
                    "poster" -> poster = j.text(2_048)
                    "fanart" -> fanart = j.text(2_048)
                    "ids" -> ids = j.ids()
                    "season" -> season = j.long()?.toInt()
                    "number" -> number = j.long()?.toInt()
                    "episodeTitle" -> episodeTitle = j.text(200)
                    "updatedAt" -> updated = j.long() ?: 0L
                    else -> j.skipValue()
                }
            }
            if (!ids.any) return null
            return TraktCard(kind ?: return null, ids, title ?: return null, year, overview, poster, fanart, season, number, episodeTitle, updated)
        }
    }
}
