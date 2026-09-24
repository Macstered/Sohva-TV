package com.sohva.tv.core.model.error

/**
 * Everything the viewer can see go wrong, as a stable code with typed arguments (plan/03 §4.11).
 *
 * Codes and arguments are persisted (source status, diagnostics) instead of resource ids, which
 * beta 23 had to guard because ids are renumbered between builds (spec 10 SRC-FR-105). Each code
 * maps to exactly one string in the UI layer. Milestones add their codes together with their strings.
 */
sealed interface AppError {
    val code: String

    /** Arguments to persist with [code]; [AppErrors.restore] rebuilds the error from both. */
    val args: List<String> get() = emptyList()

    /** Which address field a validation message names. */
    enum class FieldLabel { M3U, XMLTV, XTREAM_SERVER, SOURCE }

    data object Unknown : AppError { override val code = "unknown" }

    /** A request could not complete; [detail] is already redacted. */
    data class TransportFailed(val detail: String?) : AppError {
        override val code = "transport_failed"
        override val args get() = listOfNotNull(detail)
    }

    /** A playlist or guide server answered with an HTTP error status. */
    data class HttpStatus(val status: Int) : AppError {
        override val code = "http_status"
        override val args get() = listOf(status.toString())
    }

    /** A playlist or guide body passed the 1 GiB runaway guard (plan/09 size caps). */
    data object SourceResponseTooLarge : AppError { override val code = "source_response_too_large" }

    /** The Keystore key cannot unwrap the stored data key (spec 73 §8): secrets are unreadable. */
    data object SecretsUnreadable : AppError { override val code = "secrets_unreadable" }

    /** A running import stopped with the process; the previous data is intact. */
    data object Interrupted : AppError { override val code = "interrupted" }

    // Validation (spec 10 §4.4)
    data object SourceNameRequired : AppError { override val code = "source_name_required" }

    data class SourceNameTooLong(val max: Int) : AppError {
        override val code = "source_name_too_long"
        override val args get() = listOf(max.toString())
    }

    data class SourceUrlInvalid(val label: FieldLabel) : AppError {
        override val code = "source_url_invalid"
        override val args get() = listOf(label.name)
    }

    data object SourceUrlMalformed : AppError { override val code = "source_url_malformed" }
    data object XtreamUsernameMissing : AppError { override val code = "xtream_username_missing" }
    data object XtreamPasswordMissing : AppError { override val code = "xtream_password_missing" }

    // Preconditions (SRC-FR-81)
    data object SourceNoLiveTv : AppError { override val code = "source_no_live_tv" }
    data object SourceNoVod : AppError { override val code = "source_no_vod" }

    // Guards (SRC-FR-80)
    data object PlaylistEmpty : AppError { override val code = "playlist_empty" }
    data object PlaylistNotM3u : AppError { override val code = "playlist_not_m3u" }
    data object EpgEmpty : AppError { override val code = "epg_empty" }
    data object EpgUnmatched : AppError { override val code = "epg_unmatched" }
    data object CatalogueEmpty : AppError { override val code = "catalogue_empty" }

    // Xtream (SRC-FR-68..69, §7)
    data object XtreamAuthFailed : AppError { override val code = "xtream_auth_failed" }
    data object XtreamNoUserInfo : AppError { override val code = "xtream_no_user_info" }

    data class XtreamHttp(val status: Int) : AppError {
        override val code = "xtream_http"
        override val args get() = listOf(status.toString())
    }

    data object XtreamResponseTooLarge : AppError { override val code = "xtream_response_too_large" }
    data object XtreamResponseInvalid : AppError { override val code = "xtream_response_invalid" }
    data object SeriesIdInvalid : AppError { override val code = "series_id_invalid" }

    // Playback (SRC-FR-101)
    data class ConnectionLimit(val sourceName: String, val limit: Int) : AppError {
        override val code = "connection_limit"
        override val args get() = listOf(sourceName, limit.toString())
    }
}

/** Rebuilds a stored error; an unknown code (from a newer build) reads as [AppError.Unknown]. */
object AppErrors {
    private val singletons: Map<String, AppError> = listOf(
        AppError.Unknown, AppError.SourceResponseTooLarge, AppError.SecretsUnreadable, AppError.Interrupted, AppError.SourceNameRequired,
        AppError.SourceUrlMalformed, AppError.XtreamUsernameMissing, AppError.XtreamPasswordMissing,
        AppError.SourceNoLiveTv, AppError.SourceNoVod, AppError.PlaylistEmpty, AppError.PlaylistNotM3u,
        AppError.EpgEmpty, AppError.EpgUnmatched, AppError.CatalogueEmpty, AppError.XtreamAuthFailed,
        AppError.XtreamNoUserInfo, AppError.XtreamResponseTooLarge, AppError.XtreamResponseInvalid,
        AppError.SeriesIdInvalid,
    ).associateBy { it.code }

    fun restore(code: String, args: List<String>): AppError {
        singletons[code]?.let { return it }
        fun int(i: Int) = args.getOrNull(i)?.toIntOrNull()
        return when (code) {
            "transport_failed" -> AppError.TransportFailed(args.getOrNull(0))
            "http_status" -> int(0)?.let { AppError.HttpStatus(it) }
            "source_name_too_long" -> int(0)?.let { AppError.SourceNameTooLong(it) }
            "source_url_invalid" -> args.getOrNull(0)
                ?.let { name -> AppError.FieldLabel.entries.firstOrNull { it.name == name } }
                ?.let { AppError.SourceUrlInvalid(it) }
            "xtream_http" -> int(0)?.let { AppError.XtreamHttp(it) }
            "connection_limit" -> int(1)?.let { AppError.ConnectionLimit(args[0], it) }
            else -> null
        } ?: AppError.Unknown
    }
}

/** A failure carried through code that throws; the boundary turns it into an [Outcome.Failed]. */
class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.code, cause)
