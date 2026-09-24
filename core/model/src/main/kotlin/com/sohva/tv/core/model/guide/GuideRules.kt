package com.sohva.tv.core.model.guide

/** Small decisions of spec 20 that need no data layer, each tested on its own. */
object GuideRules {
    /**
     * The source the guide opens on (GUIDE-FR-11): the opened-for channel's, the saved one, the
     * last watched channel's, then the first. Ids that are not among [sources] are skipped.
     */
    fun chooseSource(sources: List<String>, openedFor: String?, saved: String?, lastWatched: String?): String? =
        listOf(openedFor, saved, lastWatched).firstOrNull { it != null && it in sources } ?: sources.firstOrNull()

    /**
     * The first group (GUIDE-FR-14): the lowest manual position when any group has one (unplaced
     * after placed, ties in rail order), else the first in rail order. [groups] are in rail order.
     */
    fun <G> firstGroup(groups: List<G>, position: (G) -> Int?): G? {
        var best: G? = null
        var bestPosition = Int.MAX_VALUE
        for (g in groups) {
            val p = position(g) ?: continue
            if (p < bestPosition) {
                best = g
                bestPosition = p
            }
        }
        return best ?: groups.firstOrNull()
    }

    /**
     * The rows whose programmes are read (GUIDE-FR-50): from the first visible row − 30 snapped down
     * to 10, to first visible + visible + 30 snapped down to 10, plus 10, capped at [size]. A scroll
     * inside one 10-row step keeps the same range.
     */
    fun programmeRows(firstVisible: Int, visibleCount: Int, size: Int): IntRange {
        if (size <= 0 || visibleCount <= 0) return IntRange.EMPTY
        val from = (maxOf(0, firstVisible - READ_AROUND) / STEP) * STEP
        val to = ((firstVisible + visibleCount + READ_AROUND) / STEP) * STEP + STEP
        return from until minOf(to, size)
    }

    /**
     * The programme a channel shows after a page or a refresh (GUIDE-FR-45, -61): the held id if it
     * is still there and inside the window; else the live one inside the window; else the one
     * covering the window start; else the first starting inside; else none.
     */
    fun keepSelection(
        schedule: List<GuideProgramme>,
        heldId: Long?,
        heldStart: Long?,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
    ): GuideProgramme? {
        fun inside(p: GuideProgramme) = p.stop > windowStart && p.start < windowEnd
        if (heldId != null) schedule.firstOrNull { it.id == heldId && inside(it) }?.let { return it }
        if (heldStart != null) schedule.firstOrNull { it.start <= heldStart && heldStart < it.stop && inside(it) }?.let { return it }
        schedule.firstOrNull { it.isLive(now) && inside(it) }?.let { return it }
        schedule.firstOrNull { it.start <= windowStart && windowStart < it.stop }?.let { return it }
        return schedule.firstOrNull { it.start >= windowStart && it.start < windowEnd }
    }

    /** The programme a channel cell stands for (GUIDE-FR-60): live, else the first loaded, else none. */
    fun channelSelection(schedule: List<GuideProgramme>, now: Long): GuideProgramme? =
        schedule.firstOrNull { it.isLive(now) } ?: schedule.firstOrNull()

    /**
     * Up/Down from a block (GUIDE-FR-72): among the target row's blocks overlapping [from]
     * horizontally, the one whose centre is nearest; if none overlaps, the nearest centre; -1 when
     * the row has no blocks (its filler). Spans are drawn spans in any unit.
     */
    fun verticalTarget(from: Span, blocks: List<Span>): Int {
        if (blocks.isEmpty()) return -1
        val centre = from.centre
        var best = -1
        var bestDistance = Float.MAX_VALUE
        var bestOverlaps = false
        for (i in blocks.indices) {
            val b = blocks[i]
            val overlaps = b.left < from.right && b.right > from.left
            val distance = kotlin.math.abs(b.centre - centre)
            val wins = when {
                overlaps && !bestOverlaps -> true
                overlaps == bestOverlaps -> distance < bestDistance
                else -> false
            }
            if (wins) {
                best = i
                bestDistance = distance
                bestOverlaps = overlaps
            }
        }
        return best
    }

    data class Span(val left: Float, val right: Float) {
        val centre: Float get() = (left + right) / 2f
    }

    private const val READ_AROUND = 30
    private const val STEP = 10
}
