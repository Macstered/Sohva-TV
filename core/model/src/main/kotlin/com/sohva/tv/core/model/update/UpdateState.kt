package com.sohva.tv.core.model.update

/** The updater's phases (spec 72 §4.6). */
enum class UpdatePhase { DISABLED, IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, NEEDS_PERMISSION, FAILED }

/**
 * Why the updater failed (§4.6). [DOWNLOAD_FAILED] is the rebuild's own: beta 23 said "Could not
 * reach the release list" for a failed download too (decision "Update failure texts").
 */
enum class UpdateFailure { NETWORK, DOWNLOAD_FAILED, NO_CHECKSUMS, CHECKSUM_MISMATCH, INSTALL_BLOCKED }

/**
 * What About shows (§4.6, ABOUT-FR-19). [notes] are the offered release's notes while an update is
 * in hand, else the installed build's own, already plain text (converted once, off the main thread).
 */
data class UpdateState(
    val phase: UpdatePhase,
    val update: OfferedUpdate? = null,
    val percent: Int = 0,
    val failure: UpdateFailure? = null,
    val notesVersion: String? = null,
    val notes: String? = null,
)
