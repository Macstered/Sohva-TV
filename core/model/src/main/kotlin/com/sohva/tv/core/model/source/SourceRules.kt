package com.sohva.tv.core.model.source

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppError.FieldLabel
import java.net.URI
import java.util.UUID

/** The limits and validation of spec 10 §4.1 and §4.4, one place for the page, the phone and the importers. */
object SourceRules {
    const val MAX_SOURCES: Int = 100
    const val MAX_NAME_LENGTH: Int = 100
    val CONNECTION_LIMITS: IntRange = 1..16
    const val EPG_OFFSET_STEP: Int = 30
    val EPG_OFFSETS: IntRange = -720..720
    private val ID = Regex("[A-Za-z0-9._-]{1,128}")

    fun isValidId(id: String): Boolean = ID.matches(id)

    fun newId(type: SourceType): String = when (type) {
        SourceType.M3U -> "m3u-${UUID.randomUUID()}"
        SourceType.XTREAM -> "xtream-${UUID.randomUUID()}"
    }

    /** SRC-FR-21: trimmed; http or https (any case) with a host; the stored value is the trimmed text. */
    fun checkAddress(value: String, label: FieldLabel): AppError? {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) null else AppError.SourceUrlInvalid(label)
    }

    /**
     * SRC-FR-20..23 in their order; the first failure, or the normalised config. Blank XMLTV →
     * none; Xtream server loses trailing slashes, username is trimmed, password is kept as typed.
     */
    fun validate(config: SourceConfig): Result {
        val source = config.source
        val name = source.name.trim()
        if (name.isEmpty()) return Result.Invalid(AppError.SourceNameRequired)
        if (name.length > MAX_NAME_LENGTH) return Result.Invalid(AppError.SourceNameTooLong(MAX_NAME_LENGTH))
        val s = config.secrets
        val secrets = when (source.type) {
            SourceType.M3U -> {
                val m3u = s.m3uUrl.orEmpty()
                checkAddress(m3u, FieldLabel.M3U)?.let { return Result.Invalid(it) }
                val xmltv = s.xmlTvUrl?.trim().orEmpty()
                if (xmltv.isNotEmpty()) checkAddress(xmltv, FieldLabel.XMLTV)?.let { return Result.Invalid(it) }
                SourceSecrets(m3uUrl = m3u.trim(), xmlTvUrl = xmltv.ifEmpty { null })
            }
            SourceType.XTREAM -> {
                val server = s.xtreamBaseUrl.orEmpty()
                checkAddress(server, FieldLabel.XTREAM_SERVER)?.let { return Result.Invalid(it) }
                val user = s.xtreamUsername?.trim().orEmpty()
                if (user.isEmpty()) return Result.Invalid(AppError.XtreamUsernameMissing)
                val password = s.xtreamPassword.orEmpty()
                if (password.isEmpty()) return Result.Invalid(AppError.XtreamPasswordMissing)
                SourceSecrets(xtreamBaseUrl = server.trim().trimEnd('/'), xtreamUsername = user, xtreamPassword = password)
            }
        }
        val limit = source.connectionLimit.coerceIn(CONNECTION_LIMITS)
        val offset = source.epgOffsetMinutes.coerceIn(EPG_OFFSETS) / EPG_OFFSET_STEP * EPG_OFFSET_STEP
        return Result.Valid(SourceConfig(source.copy(name = name, connectionLimit = limit, epgOffsetMinutes = offset), secrets))
    }

    sealed interface Result {
        data class Valid(val config: SourceConfig) : Result
        data class Invalid(val error: AppError) : Result
    }
}
