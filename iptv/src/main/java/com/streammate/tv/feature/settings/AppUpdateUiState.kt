package com.streammate.tv.feature.settings

/** What the About section shows about a newer beta; the app maps its checker onto this. */
data class AppUpdateUiState(
    val phase: Phase = Phase.IDLE,
    val installedVersionName: String = "",
    val versionName: String? = null,
    val notes: String? = null,
    val percent: Int? = null,
    val failure: Failure? = null,
) {
    enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, NEEDS_PERMISSION, FAILED }

    enum class Failure { NETWORK, NO_CHECKSUMS, CHECKSUM_MISMATCH, INSTALL_BLOCKED }
}

/** The presses the About section can make about an update. */
data class AppUpdateActions(
    val onCheck: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onOpenInstallPermission: () -> Unit = {},
)
