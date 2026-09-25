package com.sohva.tv.core.model.channel

import java.util.Locale

/**
 * The household's edits of one channel (spec 21 CHAN-FR-02). Null or blank means "as the playlist
 * says"; values are trimmed and cut to the spec's lengths when made.
 */
data class ChannelEdits(
    val customName: String? = null,
    val customGroupTitle: String? = null,
    val customLogoUrl: String? = null,
    val customNumber: Int? = null,
    val manualEpgId: String? = null,
    val hidden: Boolean = false,
    val position: Long? = null,
) {
    /** True when nothing differs from the playlist, so no row needs keeping. */
    val isEmpty: Boolean
        get() = customName == null && customGroupTitle == null && customLogoUrl == null && customNumber == null &&
            manualEpgId == null && !hidden && position == null

    companion object {
        const val NAME_MAX: Int = 100
        const val GROUP_MAX: Int = 100
        const val LOGO_MAX: Int = 500
        const val NUMBER_DIGITS: Int = 5

        /** A field as stored: trimmed, cut to [max], blank = null (CHAN-FR-20…22). */
        fun text(value: String?, max: Int): String? = value?.trim()?.take(max)?.trim()?.takeIf { it.isNotEmpty() }

        /** The number field: digits only, at most five, stored only when above zero (CHAN-FR-23). */
        fun number(value: String?): Int? {
            val digits = value?.trim().orEmpty()
            if (digits.isEmpty() || digits.length > NUMBER_DIGITS || !digits.all { it in '0'..'9' }) return null
            return digits.toInt().takeIf { it > 0 }
        }

        /** The organisation key of a custom group (CHAN-FR-04), so rules and profiles treat it as that group. */
        fun groupKey(title: String): String = "name:" + title.trim().lowercase(Locale.ROOT)
    }
}

/** The shown value of each field: the viewer's edit, else the playlist's (CHAN-FR-03). */
object ShownValues {
    fun name(provider: String, edits: ChannelEdits?): String = edits?.customName ?: provider

    fun logo(provider: String?, edits: ChannelEdits?): String? = edits?.customLogoUrl ?: provider

    fun number(provider: Int?, edits: ChannelEdits?): Int? = edits?.customNumber ?: provider?.takeIf { it > 0 }

    fun epgId(tvgId: String?, edits: ChannelEdits?): String? = edits?.manualEpgId ?: tvgId
}

/**
 * Sparse channel positions (CHAN-NFR-04): steps of [STEP] in the current order, so a move writes the
 * moved channel's row only, until a gap runs out.
 */
object ChannelPositions {
    const val STEP: Long = 1_024

    /** Positions for a source's channels in their current order, first to last. */
    fun initial(index: Int): Long = (index + 1L) * STEP

    /**
     * A position strictly between [before] and [after] (either may be null at the ends), or null
     * when there is no room and the neighbourhood must be renumbered first.
     */
    fun between(before: Long?, after: Long?): Long? = when {
        before == null && after == null -> STEP
        before == null -> after!! - STEP
        after == null -> before + STEP
        after - before < 2 -> null
        else -> before + (after - before) / 2
    }
}

/** A channel in the source's full order, shown or not under the current filters. */
data class Placed(val key: String, val position: Long, val shown: Boolean)

sealed interface MoveTarget {
    /** At the top or bottom of what is shown: nothing happens. */
    data object None : MoveTarget

    data class At(val position: Long) : MoveTarget

    /** No gap left next to the neighbour: renumber, then ask again. */
    data object Renumber : MoveTarget
}

/**
 * Move up / down (CHAN-FR-29): the channel goes just before (up) or just after (down) the channel
 * next to it **on screen**; channels the filters hide between them keep their order. [order] is
 * the neighbourhood in full order, the moving channel included.
 */
object ChannelMove {
    fun target(order: List<Placed>, moving: String, up: Boolean): MoveTarget {
        val i = order.indexOfFirst { it.key == moving }
        if (i < 0) return MoveTarget.None
        val range = if (up) (i - 1 downTo 0) else (i + 1 until order.size)
        val j = range.firstOrNull { order[it].shown } ?: return MoveTarget.None
        val position = if (up) {
            ChannelPositions.between(order.getOrNull(j - 1)?.position, order[j].position)
        } else {
            ChannelPositions.between(order[j].position, order.getOrNull(j + 1)?.position)
        }
        return position?.let { MoveTarget.At(it) } ?: MoveTarget.Renumber
    }
}
