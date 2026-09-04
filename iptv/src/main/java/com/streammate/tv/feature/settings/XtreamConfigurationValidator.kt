package com.streammate.tv.feature.settings

import com.streammate.tv.core.error.ResourceArgument
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.network.IptvSourceUrlPolicy

data class ValidatedXtreamConfiguration(
    val baseUrl: String,
    val username: String,
    val password: String,
)

object XtreamConfigurationValidator {
    fun validate(baseUrl: String, username: String, password: String): Result<ValidatedXtreamConfiguration> =
        runCatching {
            val normalizedBase = IptvSourceUrlPolicy.normalize(
                baseUrl,
                ResourceArgument(CoreR.string.error_label_xtream_server),
            )
            if (username.isBlank()) {
                throw LocalizedException(CoreR.string.error_xtream_username_missing)
            }
            if (password.isBlank()) {
                throw LocalizedException(CoreR.string.error_xtream_password_missing)
            }
            ValidatedXtreamConfiguration(
                baseUrl = normalizedBase.trimEnd('/'),
                username = username.trim(),
                password = password,
            )
        }
}
