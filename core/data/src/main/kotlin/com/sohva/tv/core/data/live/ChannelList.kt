package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveDao
import com.sohva.tv.core.data.database.LiveSql
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Which channels a list holds, in display order (spec 20 GUIDE-FR-30). */
sealed interface ListSpec {
    val sourceId: String

    data class Group(override val sourceId: String, val groupId: Long) : ListSpec

    data class All(override val sourceId: String) : ListSpec

    /** A source's channels without a group: the player's list for them (spec 30 PLAY-FR-50). */
    data class Ungrouped(override val sourceId: String) : ListSpec

    /** Favourites, recents and search results: ids already in their list order. */
    data class Named(override val sourceId: String, val ids: LongArray) : ListSpec {
        override fun equals(other: Any?): Boolean = other is Named && other.sourceId == sourceId && other.ids.contentEquals(ids)

        override fun hashCode(): Int = 31 * sourceId.hashCode() + ids.contentHashCode()
    }
}

/**
 * A list's index, not its rows (spec 20 GUIDE-NFR-10): the size and the keyset key before each page
 * of [PAGE] rows, found by walking the key-only index in pages of [LiveSql.KEY_PAGE]. For 56,000
 * channels that is 280 keys; rows are read a page at a time by whoever shows them.
 */
class ChannelList private constructor(
    val spec: ListSpec,
    val size: Int,
    private val afterRank: LongArray,
    private val afterId: LongArray,
    private val dao: LiveDao,
    private val profile: String,
) {
    val pageCount: Int get() = (size + PAGE - 1) / PAGE

    /** The rows of page [page] (at most [PAGE]). Cancellable between calls. */
    suspend fun page(page: Int): List<LiveChannel> {
        if (page !in 0 until pageCount) return emptyList()
        return when (val s = spec) {
            is ListSpec.Named -> {
                val ids = s.ids.copyOfRange(page * PAGE, minOf(size, (page + 1) * PAGE)).toList()
                val byId = dao.rowsById(ids).associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
            is ListSpec.Group -> dao.groupRows(s.groupId, afterRank[page], afterId[page], PAGE)
            is ListSpec.All -> dao.sourceRows(s.sourceId, profile, afterRank[page], afterId[page], PAGE)
            is ListSpec.Ungrouped -> dao.ungroupedRows(s.sourceId, profile, afterRank[page], afterId[page], PAGE)
        }
    }

    /** The list index of the row with key ([rank], [id]), or -1; one page read at most. */
    suspend fun indexOf(id: Long, rank: Long): Int {
        val s = spec
        if (s is ListSpec.Named) return s.ids.indexOf(id)
        // The page whose "after" key is the last one below the row's key.
        var lo = 0
        var hi = pageCount - 1
        var page = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (before(afterRank[mid], afterId[mid], rank, id)) {
                page = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val at = page(page).indexOfFirst { it.id == id }
        return if (at < 0) -1 else page * PAGE + at
    }

    private fun before(r1: Long, i1: Long, r2: Long, i2: Long): Boolean = r1 < r2 || (r1 == r2 && i1 < i2)

    companion object {
        const val PAGE: Int = LiveSql.PAGE

        /**
         * Builds the index; checks for cancellation between key pages (GUIDE-NFR-14). [profile]'s
         * restriction narrows All channels and the ungrouped list; a group's own check is the caller's.
         */
        suspend fun open(spec: ListSpec, dao: LiveDao, profile: String): ChannelList {
            if (spec is ListSpec.Named) {
                val pages = (spec.ids.size + PAGE - 1) / PAGE
                return ChannelList(spec, spec.ids.size, LongArray(pages), LongArray(pages), dao, profile)
            }
            val ranks = ArrayList<Long>()
            val ids = ArrayList<Long>()
            ranks += Long.MIN_VALUE
            ids += Long.MIN_VALUE
            var count = 0
            var lastRank = Long.MIN_VALUE
            var lastId = Long.MIN_VALUE
            while (true) {
                coroutineContext.ensureActive()
                val keys = when (spec) {
                    is ListSpec.Group -> dao.groupKeys(spec.groupId, lastRank, lastId, LiveSql.KEY_PAGE)
                    is ListSpec.All -> dao.sourceKeys(spec.sourceId, profile, lastRank, lastId, LiveSql.KEY_PAGE)
                    is ListSpec.Ungrouped -> dao.ungroupedKeys(spec.sourceId, profile, lastRank, lastId, LiveSql.KEY_PAGE)
                    is ListSpec.Named -> error("handled above")
                }
                for (k in keys) {
                    count++
                    if (count % PAGE == 0) {
                        ranks += k.rank
                        ids += k.id
                    }
                }
                if (keys.size < LiveSql.KEY_PAGE) break
                lastRank = keys.last().rank
                lastId = keys.last().id
            }
            // A full last page leaves one "after" key too many.
            val pages = (count + PAGE - 1) / PAGE
            return ChannelList(spec, count, ranks.take(maxOf(pages, 1)).toLongArray(), ids.take(maxOf(pages, 1)).toLongArray(), dao, profile)
        }
    }
}
