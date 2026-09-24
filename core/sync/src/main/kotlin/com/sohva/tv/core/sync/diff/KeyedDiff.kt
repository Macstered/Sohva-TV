package com.sohva.tv.core.sync.diff

import androidx.room.RoomDatabase
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.KeyedHash
import com.sohva.tv.core.data.database.KeyedId
import com.sohva.tv.core.model.collections.LongHashSet
import com.sohva.tv.core.model.text.Keys

/** A parsed item on its way to a table: key, hash of the stored fields, group, and how to build it. */
internal class Row<E>(
    val key: String,
    val hash: Long,
    val group: GroupRef?,
    /** Builds the entity for row [id] (0 for a new one); sealing happens here, only for rows written. */
    val build: (id: Long, groupId: Long?) -> E,
)

internal data class GroupRef(val key: String, val name: String)

/** The four statements a keyed table offers the diff (see `ImportDaos`). */
internal class KeyedTable<E>(
    val range: KeyRange,
    val hashes: (List<String>) -> List<KeyedHash>,
    val insert: (List<E>) -> Unit,
    val update: (List<E>) -> Unit,
    val keysPage: (from: String, until: String, limit: Int) -> List<KeyedId>,
    val delete: (List<Long>) -> Unit,
)

/**
 * The diff import of one table for one source (plan/04 §15.1 principle 3): a batch looks up its
 * keys' stored hashes, inserts new rows, updates changed ones and leaves unchanged rows alone, so
 * an unchanged catalogue writes nothing. The keys seen are kept as 64-bit hashes; [sweep] deletes
 * the rest, and only the caller decides whether the parse was complete enough to call it.
 *
 * Within a batch a repeated key keeps its last row; across batches the later row updates the
 * earlier one ("the later entry wins", spec 10 SRC-FR-59).
 */
internal class KeyedDiff<E>(
    private val table: KeyedTable<E>,
    private val groups: GroupResolver?,
) {
    private val seen = LongHashSet(4_096)

    var inserted: Int = 0
        private set
    var updated: Int = 0
        private set
    var unchanged: Int = 0
        private set

    /** Distinct keys written or confirmed so far. */
    val seenCount: Int get() = seen.size

    /** Writes one batch; the caller holds the batch's transaction. */
    fun write(rows: List<Row<E>>) {
        if (rows.isEmpty()) return
        val byKey = LinkedHashMap<String, Row<E>>(rows.size * 2)
        for (row in rows) byKey[row.key] = row
        val stored = HashMap<String, KeyedHash>(byKey.size * 2)
        for (hash in table.hashes(ArrayList(byKey.keys))) stored[hash.key] = hash
        val inserts = ArrayList<E>()
        val updates = ArrayList<E>()
        for (row in byKey.values) {
            val first = seen.add(Keys.hash64(row.key))
            val groupId = row.group?.let { groups?.idFor(it, count = first) }
            val old = stored[row.key]
            when {
                old == null -> inserts += row.build(0, groupId)
                old.hash != row.hash -> updates += row.build(old.id, groupId)
                else -> unchanged++
            }
        }
        if (inserts.isNotEmpty()) table.insert(inserts)
        if (updates.isNotEmpty()) table.update(updates)
        inserted += inserts.size
        updated += updates.size
    }

    /**
     * Deletes the source's rows whose keys this import did not see, walking the key index in pages
     * of [PAGE] and deleting in chunks of [DELETE_CHUNK], one short transaction each. Call only
     * after a complete, non-empty parse.
     */
    fun sweep(db: RoomDatabase): Int {
        var after = table.range.from
        var deleted = 0
        while (true) {
            val page = table.keysPage(after, table.range.until, PAGE)
            if (page.isEmpty()) return deleted
            after = page.last().key
            val gone = page.filter { Keys.hash64(it.key) !in seen }.map { it.id }
            for (chunk in gone.chunked(DELETE_CHUNK)) db.runInTransaction { table.delete(chunk) }
            deleted += gone.size
        }
    }

    companion object {
        const val PAGE = 2_000
        const val DELETE_CHUNK = 500
    }
}
