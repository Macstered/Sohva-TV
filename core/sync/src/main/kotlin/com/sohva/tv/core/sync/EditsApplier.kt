package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.data.live.ShownChannel
import com.sohva.tv.core.sync.diff.GroupRef
import com.sohva.tv.core.sync.diff.GroupResolver

/**
 * Re-applies the household's channel edits after a playlist import (spec 21 CHAN-FR-02, M3 exit
 * criterion 3). Walks the source's `channel_custom` rows in key pages of ≤ 2,000, each page one
 * transaction, and writes only channels whose shown values differ, so an import that changed
 * nothing an edit touches writes nothing. Runs on the bulk-write dispatcher inside the import.
 */
internal class EditsApplier(private val db: SohvaDatabase, private val groups: GroupResolver) {
    fun apply(sourceId: String) {
        val dao = db.channelEdits()
        var after = ""
        while (true) {
            val page = dao.editedPage(sourceId, after, PAGE)
            if (page.isEmpty()) return
            db.runInTransaction {
                for (row in page) {
                    val custom = row.custom
                    // The parse counted every channel in its playlist group; move the count to where it shows.
                    val movedOrHidden = custom.hidden || custom.customGroupKey != null
                    if (movedOrHidden) row.providerGroupId?.let(groups::uncount)
                    val customGroupId = custom.customGroupKey?.let { key ->
                        groups.idFor(GroupRef(key, custom.customGroupTitle ?: key), count = !custom.hidden)
                    }
                    val shown = ChannelEffects.shown(
                        row.providerName, row.providerGroupId, row.providerLogoUrl, row.tvgId, row.providerNumber,
                        row.playlistOrder, custom, customGroupId,
                    )
                    val current = ShownChannel(
                        row.name, shown.sortName, row.groupId, row.logoUrl, row.number, row.epgId, row.visible, row.displayRank,
                    )
                    if (shown != current) {
                        dao.updateShown(
                            row.channelId, shown.name, shown.sortName, shown.groupId, shown.logoUrl, shown.number,
                            shown.epgId, shown.visible, shown.displayRank,
                        )
                    }
                }
            }
            after = page.last().custom.channelKey
            if (page.size < PAGE) return
        }
    }

    private companion object {
        const val PAGE = 2_000
    }
}
