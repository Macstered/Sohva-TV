package com.sohva.tv.core.model.sport

/**
 * The twelve sports (spec 60 SPORT-03), in the order of the follow menu and the Today tabs. The
 * names are beta 23's: they are stored in preferences, competition keys and backups.
 * [provider] names the API-Sports product (cache keys and status labels); [quotaLabel] is the
 * short name in the Settings status line (SPORT-FR-12).
 */
enum class SportType(val provider: String, val hasCompetitions: Boolean, val quotaLabel: String) {
    FOOTBALL("football", true, "F"),
    ICE_HOCKEY("hockey", true, "H"),
    AUSTRALIAN_FOOTBALL("afl", true, "AFL"),
    BASKETBALL("basketball", true, "BK"),
    BASEBALL("baseball", true, "BB"),
    HANDBALL("handball", true, "HB"),
    RUGBY("rugby", true, "R"),
    VOLLEYBALL("volleyball", true, "V"),
    AMERICAN_FOOTBALL("american-football", true, "NFL"),
    MMA("mma", false, "MMA"),
    FORMULA_1("formula-1", false, "F1"),
    NBA("nba", false, "NBA"),
    ;

    companion object {
        fun fromStored(name: String?): SportType? = entries.firstOrNull { it.name == name }
    }
}

/** A game's state (SPORT-FR-17). */
enum class EventStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    POSTPONED,
    CANCELLED,
    INTERRUPTED,
    UNKNOWN,
    ;

    /** The Today order (SPORT-FR-49): live, scheduled, disrupted, finished. */
    val order: Int
        get() = when (this) {
            LIVE -> 0
            SCHEDULED -> 1
            POSTPONED, INTERRUPTED, UNKNOWN -> 2
            FINISHED, CANCELLED -> 3
        }

    /** AFL duplicates keep the most advanced record (SPORT-FR-42). */
    val progress: Int
        get() = when (this) {
            FINISHED -> 4
            LIVE -> 3
            POSTPONED, CANCELLED, INTERRUPTED -> 2
            SCHEDULED -> 1
            UNKNOWN -> 0
        }
}

/**
 * The provider's status codes per sport (SPORT-FR-43). Codes are trimmed and upper-cased; anything
 * unknown is [EventStatus.UNKNOWN]. The generic table is read in the order scheduled, finished,
 * postponed, cancelled, interrupted, live, so `PST` is postponed although it starts with P.
 */
object SportStatuses {
    private val football = table(
        scheduled = "TBD NS", live = "1H HT 2H ET BT P LIVE", finished = "FT AET PEN AWD WO",
        postponed = "PST", cancelled = "CANC", interrupted = "SUSP INT ABD",
    )
    private val hockey = table(
        scheduled = "NS", live = "P1 P2 P3 OT PT BT", finished = "FT AOT AP AW",
        postponed = "POST", cancelled = "CANC", interrupted = "INTR ABD",
    )
    private val afl = table(
        scheduled = "NS TBD", live = "Q1 1Q Q2 2Q HT Q3 3Q Q4 4Q OT LIVE", finished = "FT AOT AW",
        postponed = "POST PST", cancelled = "CANC", interrupted = "SUSP INTR ABD",
    )
    private val formula1 = mapOf(
        EventStatus.SCHEDULED to setOf("SCHEDULED", "NS", "TBD"),
        EventStatus.LIVE to setOf("LIVE", "IN PROGRESS", "RUNNING", "STARTED"),
        EventStatus.FINISHED to setOf("COMPLETED", "FINISHED", "FT"),
        EventStatus.POSTPONED to setOf("POSTPONED", "PST", "DELAYED"),
        EventStatus.CANCELLED to setOf("CANCELLED", "CANCELED", "CANC"),
        EventStatus.INTERRUPTED to setOf("SUSPENDED", "ABANDONED", "RED FLAG", "INTERRUPTED"),
    )
    private val nba = mapOf(
        EventStatus.SCHEDULED to setOf("1", "NS", "SCHEDULED"),
        EventStatus.LIVE to setOf("2", "LIVE", "IN PLAY"),
        EventStatus.FINISHED to setOf("3", "FT", "FINISHED"),
        EventStatus.POSTPONED to setOf("POST", "PST", "POSTPONED"),
        EventStatus.CANCELLED to setOf("CANC", "CANCELLED"),
    )
    private val genericLive = setOf("LIVE", "HT", "BT", "OT", "1H", "2H", "1Q", "2Q", "3Q", "4Q", "S1", "S2", "S3", "S4", "S5")
    private val genericLivePrefixes = listOf("Q", "P", "IN", "SET")

    fun of(sport: SportType, raw: String?): EventStatus {
        val code = raw?.trim()?.uppercase() ?: return EventStatus.UNKNOWN
        if (code.isEmpty()) return EventStatus.UNKNOWN
        val table = when (sport) {
            SportType.FOOTBALL -> football
            SportType.ICE_HOCKEY -> hockey
            SportType.AUSTRALIAN_FOOTBALL -> afl
            SportType.FORMULA_1 -> formula1
            SportType.NBA -> nba
            else -> return generic(code)
        }
        return table.entries.firstOrNull { code in it.value }?.key ?: EventStatus.UNKNOWN
    }

    private fun generic(code: String): EventStatus = when {
        code == "NS" || code == "TBD" -> EventStatus.SCHEDULED
        code in setOf("FT", "AOT", "AP", "AW") -> EventStatus.FINISHED
        code == "POST" || code == "PST" -> EventStatus.POSTPONED
        code == "CANC" -> EventStatus.CANCELLED
        code in setOf("SUSP", "INTR", "INT", "ABD") -> EventStatus.INTERRUPTED
        code in genericLive || genericLivePrefixes.any { code.startsWith(it) } -> EventStatus.LIVE
        else -> EventStatus.UNKNOWN
    }

    private fun table(scheduled: String, live: String, finished: String, postponed: String, cancelled: String, interrupted: String) = mapOf(
        EventStatus.SCHEDULED to words(scheduled),
        EventStatus.LIVE to words(live),
        EventStatus.FINISHED to words(finished),
        EventStatus.POSTPONED to words(postponed),
        EventStatus.CANCELLED to words(cancelled),
        EventStatus.INTERRUPTED to words(interrupted),
    )

    private fun words(list: String): Set<String> = list.split(' ').toSet()
}
