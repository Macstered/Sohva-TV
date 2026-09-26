package com.sohva.tv.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvIcons

/** The rail's sections in rail order (spec 70 SET-FR-10). [built] is false until their milestone. */
enum class SettingsSection(@StringRes val label: Int, @DrawableRes val icon: Int, val tag: String, val built: Boolean) {
    GENERAL(R.string.settings_section_general, TvIcons.Settings, "general", built = true),
    SOURCES(R.string.settings_section_sources, TvIcons.Channels, "sources", built = true),
    PLAYBACK(R.string.settings_section_playback, TvIcons.Play, "playback", built = true),
    REMOTE(R.string.settings_section_remote, TvIcons.Aspect, "remote", built = true),
    METADATA(R.string.settings_section_metadata, TvIcons.Info, "metadata", built = true),
    ACCOUNTS(R.string.settings_section_accounts, TvIcons.Link, "accounts", built = false),
    SPORT(R.string.settings_section_sport, TvIcons.Target, "sport", built = true),
    PARENTAL(R.string.settings_section_parental, TvIcons.Lock, "parental", built = true),
    BACKUP(R.string.settings_section_backup, TvIcons.Save, "backup", built = true),
    ABOUT(R.string.settings_section_about, TvIcons.Guide, "about", built = true),
    ;

    companion object {
        /** ACCOUNTS only when the host supplies the Trakt panel (SET-FR-11). */
        fun visible(accounts: Boolean): List<SettingsSection> = entries.filter { it != ACCOUNTS || accounts }
    }
}
