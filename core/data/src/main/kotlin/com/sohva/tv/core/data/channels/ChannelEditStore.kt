package com.sohva.tv.core.data.channels

import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.channel.ChannelEdits
import com.sohva.tv.core.model.channel.ChannelMove
import com.sohva.tv.core.model.channel.ChannelPositions
import com.sohva.tv.core.model.channel.MoveTarget
import com.sohva.tv.core.model.channel.Placed
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The editor's fields as the viewer left them (spec 21 CHAN-FR-18); trimmed and cut when saved. */
data class ChannelFields(
    val name: String?,
    val group: String?,
    val logoUrl: String?,
    val number: String?,
    val manualEpgId: String?,
)

/**
 * The household's channel edits (spec 21 §4.3): each write stores the edit row and applies the
 * channel's shown values in one transaction, with the group counts of the groups it left and
 * joined recounted, so the guide shows the change the next time it reads. Runs on the write
 * dispatcher; a first move in a source positions that source in pages of 2,000 (CHAN-NFR-04).
 */
class ChannelEditStore(private val db: SohvaDatabase, private val write: CoroutineDispatcher, private val clock: Clock) {
    private val edits get() = db.channelEdits()

    /** Save (CHAN-FR-25): name, group, logo, number and mapping together, hidden and position kept. */
    suspend fun save(key: String, fields: ChannelFields): Unit = withContext(write) {
        db.runInTransaction {
            val row = edits.providerRow(key) ?: return@runInTransaction
            val old = edits.custom(key)
            val group = ChannelEdits.text(fields.group, ChannelEdits.GROUP_MAX)
            val next = ChannelCustomEntity(
                channelKey = key, sourceId = row.sourceId,
                customName = ChannelEdits.text(fields.name, ChannelEdits.NAME_MAX),
                customGroupTitle = group, customGroupKey = group?.let(ChannelEdits::groupKey),
                hidden = old?.hidden ?: false, position = old?.position,
                manualEpgId = ChannelEdits.text(fields.manualEpgId, ChannelEdits.GROUP_MAX),
                customLogoUrl = ChannelEdits.text(fields.logoUrl, ChannelEdits.LOGO_MAX),
                customNumber = ChannelEdits.number(fields.number), updatedAt = clock.wallMillis(),
            )
            store(next)
            apply(key)
        }
    }

    /** Hide from guide / Show in guide (CHAN-FR-26): the hidden flag alone, at once. */
    suspend fun setHidden(key: String, hidden: Boolean): Unit = withContext(write) {
        db.runInTransaction {
            val row = edits.providerRow(key) ?: return@runInTransaction
            val old = edits.custom(key) ?: empty(key, row.sourceId)
            store(old.copy(hidden = hidden, updatedAt = clock.wallMillis()))
            apply(key)
        }
    }

    /** Reset (CHAN-FR-31): the edit row goes; favourites, recents, locks and lists stay. */
    suspend fun reset(key: String): Unit = withContext(write) {
        db.runInTransaction {
            edits.deleteCustom(key)
            apply(key)
        }
    }

    /** A phone-sent logo's address (CHAN-FR-42), keeping the rest of the edit. */
    suspend fun setLogo(key: String, address: String): Unit = withContext(write) {
        db.runInTransaction {
            val row = edits.providerRow(key) ?: return@runInTransaction
            val old = edits.custom(key) ?: empty(key, row.sourceId)
            store(old.copy(customLogoUrl = address, updatedAt = clock.wallMillis()))
            apply(key)
        }
    }

    /**
     * Move up / down relative to the neighbour on screen (CHAN-FR-29), Playlist order only.
     * [onScreen] finds that neighbour under the screen's filters. Returns false at the ends.
     */
    suspend fun move(key: String, up: Boolean, onScreen: suspend (rank: Long, id: Long) -> Pair<Long, String>?): Boolean = withContext(write) {
        val sourceId = edits.providerRow(key)?.sourceId ?: return@withContext false
        if (edits.positioned(sourceId) == 0) renumber(sourceId)
        repeat(2) {
            val moving = db.live().byKey(key) ?: return@withContext false
            val (neighbourId, neighbourKey) = onScreen(moving.rank, moving.id) ?: return@withContext false
            val neighbour = db.live().byKey(neighbourKey) ?: return@withContext false
            // A channel moves inside its group: the guide orders a source group by group (GUIDE-13),
            // and positions are places inside the group, not whole ranks.
            val block = ChannelEffects.blockOf(moving.rank)
            if (ChannelEffects.blockOf(neighbour.rank) != block) return@withContext false
            val far = if (up) {
                edits.before(sourceId, neighbour.rank, neighbourId, key)
            } else {
                edits.after(sourceId, neighbour.rank, neighbourId, key)
            }?.takeIf { ChannelEffects.blockOf(it.rank) == block }
            val place = ChannelEffects::inGroup
            val order = buildList {
                if (up) {
                    far?.let { add(Placed(it.key, place(it.rank), false)) }
                    add(Placed(neighbourKey, place(neighbour.rank), true))
                    add(Placed(key, place(moving.rank), true))
                } else {
                    add(Placed(key, place(moving.rank), true))
                    add(Placed(neighbourKey, place(neighbour.rank), true))
                    far?.let { add(Placed(it.key, place(it.rank), false)) }
                }
            }
            when (val target = ChannelMove.target(order, key, up)) {
                MoveTarget.None -> return@withContext false
                is MoveTarget.At -> {
                    db.runInTransaction {
                        val old = edits.custom(key) ?: empty(key, sourceId)
                        store(old.copy(position = target.position, updatedAt = clock.wallMillis()))
                        apply(key)
                    }
                    return@withContext true
                }
                MoveTarget.Renumber -> renumber(sourceId)
            }
        }
        false
    }

    /**
     * Gives every channel of [sourceId] a position, steps of 1,024 in the current order, in pages
     * of 2,000 rows, one transaction each (CHAN-NFR-04). Used for a source's first move and when a
     * gap runs out. Two walks: the first follows the current order and writes positions only, so
     * the order it walks never changes under it; the second walks the edit rows by key and ranks
     * each channel from its position inside its group.
     */
    private fun renumber(sourceId: String) {
        var afterRank = Long.MIN_VALUE
        var afterId = Long.MIN_VALUE
        var index = 0
        while (true) {
            val page = edits.rankPage(sourceId, afterRank, afterId, BULK_PAGE)
            if (page.isEmpty()) break
            db.runInTransaction {
                // SQLite of API 23–30 takes at most 999 variables in a statement.
                val customs = page.chunked(IN_LIMIT)
                    .flatMap { chunk -> edits.customs(chunk.map { it.key }) }
                    .associateBy { it.channelKey }
                for (c in page) {
                    val position = ChannelPositions.initial(index++)
                    store((customs[c.key] ?: empty(c.key, sourceId)).copy(position = position, updatedAt = clock.wallMillis()))
                }
            }
            afterRank = page.last().rank
            afterId = page.last().id
            if (page.size < BULK_PAGE) break
        }
        var afterKey = ""
        while (true) {
            val page = edits.editedPage(sourceId, afterKey, BULK_PAGE)
            if (page.isEmpty()) return
            // The new positions become ranks inside each channel's group block (GUIDE-13).
            OrgPass(db, OrgRules(db), LibraryPasses(db)).resolveChannels(page.map { it.custom.channelKey })
            afterKey = page.last().custom.channelKey
            if (page.size < BULK_PAGE) return
        }
    }

    private fun empty(key: String, sourceId: String) = ChannelCustomEntity(
        channelKey = key, sourceId = sourceId, customName = null, customGroupTitle = null, customGroupKey = null,
        hidden = false, position = null, manualEpgId = null, customLogoUrl = null, customNumber = null, updatedAt = clock.wallMillis(),
    )

    /** An edit equal to the playlist is not kept (CHAN-FR-02: a missing row means "as the playlist says"). */
    private fun store(row: ChannelCustomEntity) {
        val blank = row.customName == null && row.customGroupKey == null && row.customLogoUrl == null && row.customNumber == null &&
            row.manualEpgId == null && !row.hidden && row.position == null
        if (blank) edits.deleteCustom(row.channelKey) else edits.putCustom(row)
    }

    /**
     * After a restore replaced the edit rows (spec 71 BACKUP-FR-18): the shown values of [keys] are
     * written again from their playlist values and edits, in transactions of [BULK_PAGE], then one
     * organisation pass and one recount per touched group, instead of one pass per channel.
     */
    suspend fun reapply(keys: Collection<String>): Unit = withContext(write) {
        val groups = HashSet<Long>()
        val present = ArrayList<String>()
        for (page in keys.chunked(BULK_PAGE)) {
            db.runInTransaction {
                for (key in page) {
                    val row = edits.providerRow(key) ?: continue
                    val custom = edits.custom(key)
                    val customGroupId = custom?.customGroupKey?.let { groupKey -> groupFor(row.sourceId, groupKey, custom.customGroupTitle ?: groupKey) }
                    val shown = ChannelEffects.shown(
                        row.providerName, row.providerGroupId, row.providerLogoUrl, row.tvgId, row.providerNumber, row.playlistOrder, custom, customGroupId,
                    )
                    edits.updateShown(row.id, shown.name, shown.sortName, shown.groupId, shown.logoUrl, shown.number, shown.epgId, shown.visible, shown.displayRank)
                    row.groupId?.let(groups::add)
                    shown.groupId?.let(groups::add)
                    present += key
                }
            }
        }
        OrgPass(db, OrgRules(db), LibraryPasses(db)).resolveChannels(present)
        db.runInTransaction { groups.forEach(edits::recountGroup) }
    }

    /** Writes the channel's shown values from its playlist values and its edit row; recounts both groups. */
    private fun apply(key: String) {
        val row = edits.providerRow(key) ?: return
        val custom = edits.custom(key)
        val customGroupId = custom?.customGroupKey?.let { groupKey -> groupFor(row.sourceId, groupKey, custom.customGroupTitle ?: groupKey) }
        val shown = ChannelEffects.shown(
            row.providerName, row.providerGroupId, row.providerLogoUrl, row.tvgId, row.providerNumber, row.playlistOrder, custom, customGroupId,
        )
        edits.updateShown(row.id, shown.name, shown.sortName, shown.groupId, shown.logoUrl, shown.number, shown.epgId, shown.visible, shown.displayRank)
        // The hidden flag is one input; the organisation rules decide with it (spec 42 ORG-FR-15, -16).
        OrgPass(db, OrgRules(db), LibraryPasses(db)).resolveChannels(listOf(key))
        row.groupId?.let(edits::recountGroup)
        if (shown.groupId != row.groupId) shown.groupId?.let(edits::recountGroup)
    }

    /** The custom group's row in the source's live room, made on first use (CHAN-FR-04), placed after the others. */
    private fun groupFor(sourceId: String, groupKey: String, title: String): Long {
        edits.groupId(sourceId, groupKey)?.let { return it }
        val position = edits.maxGroupPosition(sourceId) + 1
        val id = db.groupImport().insert(
            ContentGroupEntity(
                sourceId = sourceId, room = "LIVE", groupKey = groupKey, name = title, providerOrder = position,
                itemCount = 0, shown = true, position = position, sortMode = null,
            ),
        )
        return if (id > 0) id else edits.groupId(sourceId, groupKey) ?: error("custom group missing")
    }

    private companion object {
        const val BULK_PAGE = 2_000
        const val IN_LIMIT = 500
    }
}
