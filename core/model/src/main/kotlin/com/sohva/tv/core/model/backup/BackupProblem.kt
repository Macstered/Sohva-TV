package com.sohva.tv.core.model.backup

/**
 * Why a backup could not be saved or restored (spec 71 §4.6): each has its own sentence. [field]
 * names the payload field for the field-level problems.
 */
enum class BackupProblem {
    OPEN,
    TOO_LARGE,
    PASSWORD_TOO_SHORT,
    NOT_SOHVA,
    ENVELOPE_VERSION,
    KEY_FORMAT,
    STRUCTURE,
    TRAILING_DATA,
    WRONG_PASSWORD,
    FORMAT_VERSION,
    PIN,
    TOO_MANY_PREFERENCES,
    TOO_MANY_LISTS,
    TOO_MANY_MEMBERS,
    DUPLICATE_PREFERENCE,
    MISSING_SOURCE,
    DUPLICATE_LIST,
    MISSING_LIST,
    DUPLICATE_MEMBER,
    STARTUP,
    REMOTE,
    MISSING_FIELD,
    BLANK_FIELD,
    LONG_FIELD,
}

/** A backup failure carried to the screen; never a stack trace (AGENTS.md §5 rule 6). */
class BackupException(val problem: BackupProblem, val field: String? = null, cause: Throwable? = null) :
    Exception("backup: ${problem.name}${field?.let { " ($it)" }.orEmpty()}", cause)
