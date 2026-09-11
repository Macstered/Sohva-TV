package com.streammate.tv.app

/** Package-local safety boundaries. A restored preference cannot enable Lab automation. */
class AppRuntimePolicy private constructor(
    val isLab: Boolean,
    val publicUpdatesAllowed: Boolean,
    val addonsAllowed: Boolean,
) {
    val automaticMaintenanceAllowed: Boolean get() = !isLab
    val automaticSportsRefreshAllowed: Boolean get() = !isLab
    val remindersAllowed: Boolean get() = !isLab

    companion object {
        const val LAB_PACKAGE = "com.streammate.tv.lab"
        fun forPackage(packageName: String): AppRuntimePolicy = AppRuntimePolicy(
            isLab = packageName == LAB_PACKAGE,
            publicUpdatesAllowed = packageName == "com.streammate.tv",
            // Feature availability is independent of Lab's background-work isolation.
            addonsAllowed = packageName in setOf("com.streammate.tv", "com.streammate.tv.debug", LAB_PACKAGE),
        )
    }
}
