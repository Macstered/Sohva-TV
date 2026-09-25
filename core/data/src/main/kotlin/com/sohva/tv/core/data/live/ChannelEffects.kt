package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.model.text.SortNames

/** What every screen reads for a channel: the playlist's values with the household's edits applied. */
data class ShownChannel(
    val name: String,
    val sortName: String,
    val groupId: Long?,
    val logoUrl: String?,
    val number: Int?,
    val epgId: String?,
    val visible: Boolean,
    val displayRank: Long,
)

/**
 * The shown values of spec 21 CHAN-FR-03 and the channel order of CHAN-FR-14. A channel with a
 * viewer position ranks by it; the others rank after every positioned channel by playlist order,
 * so a source nobody reordered keeps the playlist's order and a reordered one keeps beta 23's
 * "channels without a position last".
 */
object ChannelEffects {
    /** Above every viewer position (positions are sparse steps of 1,024 from the first channel). */
    const val UNPOSITIONED: Long = 1L shl 40
    const val RANK_STEP: Long = 1_024

    fun playlistRank(playlistOrder: Int): Long = UNPOSITIONED + playlistOrder * RANK_STEP

    /** [customGroupId] is the id of the custom group's `content_group` row when [custom] names one. */
    fun shown(
        providerName: String,
        providerGroupId: Long?,
        providerLogoUrl: String?,
        tvgId: String?,
        providerNumber: Int?,
        playlistOrder: Int,
        custom: ChannelCustomEntity?,
        customGroupId: Long?,
    ): ShownChannel {
        val name = custom?.customName ?: providerName
        return ShownChannel(
            name = name,
            sortName = SortNames.of(name),
            groupId = if (custom?.customGroupKey != null) customGroupId else providerGroupId,
            logoUrl = custom?.customLogoUrl ?: providerLogoUrl,
            number = custom?.customNumber ?: providerNumber?.takeIf { it > 0 },
            epgId = custom?.manualEpgId ?: tvgId,
            visible = custom?.hidden != true,
            displayRank = custom?.position ?: playlistRank(playlistOrder),
        )
    }
}
