package com.sohva.tv.core.model.sport

/**
 * Why a sports request failed (spec 60 §7 error table). The rebuild names the three cases the
 * viewer can act on apart (§7 rebuild): no key, the day's quota spent, the provider unavailable.
 */
enum class SportsProblem {
    KEY_MISSING,
    HTTP,
    TOO_LARGE,
    UNAVAILABLE,
    SERVICE_ERROR,
    INVALID_DATA,

    /** The provider said the daily quota is spent, or reported 0 remaining (§9 rule). */
    QUOTA_EXHAUSTED,
}

/** A failed sports request; [status] is the HTTP status for [SportsProblem.HTTP]. Never carries the key. */
class SportsException(val problem: SportsProblem, val status: Int = 0, cause: Throwable? = null) :
    Exception("sports: ${problem.name}${if (status > 0) " $status" else ""}", cause)
