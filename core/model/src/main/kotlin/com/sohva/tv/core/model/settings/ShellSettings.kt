package com.sohva.tv.core.model.settings

import com.sohva.tv.core.model.profile.Household

/** The seven colour themes, in picker order. [id] is what preferences and backups store (design/01 §3). */
enum class ColorThemeId(val id: String) {
    ORIGINAL("original"),
    NORDIC_SLATE("nordic_slate"),
    COZY_HEARTH("cozy_hearth"),
    CYBER_PLUM("cyber_plum"),
    NORD("nord"),
    EVERFOREST("everforest"),
    KANAGAWA("kanagawa"),
    ;

    companion object {
        /** An unknown or missing id falls back to Original. */
        fun fromStored(value: String?): ColorThemeId = entries.firstOrNull { it.id == value } ?: ORIGINAL
    }
}

/**
 * Interface size. It scales density, not font scale, and the launch screen stays unscaled
 * (design/01 §14). Preferences store the enum [name].
 */
enum class InterfaceScale(val factor: Float) {
    NORMAL(1.0f),
    COMPACT(0.9f),
    SMALL(0.8f),
    SMALLER(0.7f),
    ;

    companion object {
        fun fromStored(value: String?): InterfaceScale = entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

/** First screen after launch (spec 01 FR-10..12). Preferences store the enum [name]. */
enum class StartupScreen {
    HOME,
    GUIDE,
    LAST_CHANNEL,
    ;

    companion object {
        fun fromStored(value: String?): StartupScreen = entries.firstOrNull { it.name == value } ?: HOME
    }
}

/** What the first frame needs, read once off the main thread before composing (plan/03 §4.9). */
data class StartSnapshot(
    val theme: ColorThemeId,
    val scale: InterfaceScale,
    val startupScreen: StartupScreen,
    /** The active profile's, for the "Last channel" start screen (spec 01 SHELL-FR-11). */
    val lastChannel: String? = null,
    /** Who is watching at start (spec 04 §9): profiles, the active one, ask at start, the PIN flag. */
    val household: Household = Household(),
)

/** How often playlists and guides refresh in the background (spec 70 SET-26). Stored by [name]. */
enum class RefreshInterval(val hours: Int) {
    ONE_HOUR(1),
    TWO_HOURS(2),
    FOUR_HOURS(4),
    TEN_HOURS(10),
    TWENTY_FOUR_HOURS(24),
    ;

    companion object {
        fun fromStored(value: String?): RefreshInterval = entries.firstOrNull { it.name == value } ?: TWENTY_FOUR_HOURS
    }
}
