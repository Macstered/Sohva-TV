package com.sohva.tv.core.sync.diff

import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.GroupImportDao

/** The rooms of `content_group`. */
internal enum class Room { LIVE, MOVIES, SERIES }

/**
 * A source's groups in one room during an import (plan/04 §15.2 `content_group`): a few thousand
 * rows at most, so they are held in memory. New groups are inserted when first met (the writer
 * needs their ids); names, provider order and item counts are written once at [finish], which also
 * deletes groups the import did not meet when the parse was complete.
 *
 * `shown` and `position` of existing groups are the viewer's (resolved by organisation rules in
 * M3/M5) and are kept; a new group is shown at its provider position.
 */
internal class GroupResolver(private val dao: GroupImportDao, private val sourceId: String, private val room: Room) {
    private val existing: MutableMap<String, ContentGroupEntity> =
        dao.groups(sourceId, room.name).associateByTo(HashMap()) { it.groupKey }
    private val met = LinkedHashMap<String, Met>()

    private class Met(val id: Long, var name: String, val order: Int, var count: Int)

    /** The group's id; creates it when new. [count] adds one item to it. Writer thread, inside a transaction. */
    fun idFor(ref: GroupRef, count: Boolean): Long {
        val known = met[ref.key]
        if (known != null) {
            if (count) known.count++
            return known.id
        }
        val order = met.size
        val id = existing[ref.key]?.id ?: insert(ref, order)
        met[ref.key] = Met(id, ref.name, order, if (count) 1 else 0)
        return id
    }

    /** Moves one counted item out of [groupId] (a channel moved or hidden by the household's edits). */
    fun uncount(groupId: Long) {
        met.values.firstOrNull { it.id == groupId }?.let { if (it.count > 0) it.count-- }
    }

    private fun insert(ref: GroupRef, order: Int): Long {
        val row = ContentGroupEntity(
            sourceId = sourceId, room = room.name, groupKey = ref.key, name = ref.name, providerOrder = order,
            itemCount = 0, shown = true, position = order, sortMode = null,
        )
        val id = dao.insert(row)
        if (id > 0) return id
        // Present after all (another room's import of this source cannot race: one lock per kind).
        return dao.groups(sourceId, room.name).first { it.groupKey == ref.key }.id
    }

    /** Writes names, order and counts; deletes unmet groups when [complete]. Inside a transaction. */
    fun finish(complete: Boolean) {
        val current = dao.groups(sourceId, room.name).associateBy { it.groupKey }
        val changed = ArrayList<ContentGroupEntity>()
        for ((key, m) in met) {
            val row = current[key] ?: continue
            val next = row.copy(name = m.name, providerOrder = m.order, itemCount = m.count)
            if (next != row) changed += next
        }
        if (changed.isNotEmpty()) dao.update(changed)
        if (complete) {
            val gone = current.values.filter { it.groupKey !in met }.map { it.id }
            for (chunk in gone.chunked(KeyedDiff.DELETE_CHUNK)) dao.delete(chunk)
        }
    }
}
