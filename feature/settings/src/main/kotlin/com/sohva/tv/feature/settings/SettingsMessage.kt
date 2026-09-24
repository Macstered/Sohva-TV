package com.sohva.tv.feature.settings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.errorMessage

/**
 * A section's status message, kept as resources and values so it reads in the interface language
 * of the moment (spec 10 SRC-FR-105; spec 70 SET-11: every section has its own status line).
 */
@Immutable
sealed interface SettingsMessage {
    data class Text(@StringRes val id: Int, val args: List<Any> = emptyList()) : SettingsMessage

    data class Count(@PluralsRes val id: Int, val count: Int) : SettingsMessage

    data class Failure(val error: AppError) : SettingsMessage

    data class Catalogue(val films: Int, val series: Int) : SettingsMessage

    data class ConnectionOk(val serverLimit: Int?) : SettingsMessage

    data class IntervalSaved(val interval: RefreshInterval) : SettingsMessage
}

@Composable
internal fun SettingsMessage.resolve(): String = when (this) {
    is SettingsMessage.Text -> stringResource(id, *args.toTypedArray())
    is SettingsMessage.Count -> pluralStringResource(id, count, count)
    is SettingsMessage.Failure -> errorMessage(error)
    is SettingsMessage.Catalogue -> stringResource(
        R.string.source_imported_catalogue,
        pluralStringResource(R.plurals.source_imported_movies, films, films),
        pluralStringResource(R.plurals.source_imported_series, series, series),
    )
    is SettingsMessage.ConnectionOk -> stringResource(
        R.string.source_connection_ok,
        serverLimit?.let { stringResource(R.string.source_server_limit, it) }.orEmpty(),
    )
    is SettingsMessage.IntervalSaved -> stringResource(R.string.source_refresh_schedule_saved, interval.label())
}

@Composable
internal fun RefreshInterval.label(): String = pluralStringResource(R.plurals.source_refresh_interval_hours, hours, hours)
