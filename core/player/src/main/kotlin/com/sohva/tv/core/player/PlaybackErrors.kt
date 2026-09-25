package com.sohva.tv.core.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.player.PlaybackCause

/** A failure the viewer reads (spec 30 PLAY-FR-93..94): the cause in words and the technical detail. */
data class PlaybackFailure(val cause: PlaybackCause, val detail: String)

@OptIn(UnstableApi::class)
object PlaybackErrors {
    /** Error codes of the engine's own failures, carried to the screen in a session error. */
    const val CONNECTION_LIMIT: Int = 1001
    const val NO_LONGER_AVAILABLE: Int = 1002

    /** No archive address can be built for the programme now (spec 22 CATCH-FR-42). */
    const val ARCHIVE_UNAVAILABLE: Int = 1003
    const val EXTRA_SOURCE_NAME: String = "com.sohva.tv.player.SOURCE_NAME"
    const val EXTRA_LIMIT: String = "com.sohva.tv.player.LIMIT"

    fun of(error: PlaybackException): PlaybackFailure = PlaybackFailure(cause(error), detail(error))

    /** The plain-language cause of Media3's error groups (PLAY-FR-93). BEHIND_LIVE_WINDOW is handled apart. */
    fun cause(error: PlaybackException): PlaybackCause = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackCause.NETWORK
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackCause.ofHttpStatus(httpStatus(error) ?: 0)
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        -> PlaybackCause.BROKE_OFF
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> PlaybackCause.NOT_A_STREAM
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> PlaybackCause.DECODER
        else -> PlaybackCause.OTHER
    }

    private fun httpStatus(error: Throwable): Int? {
        var t: Throwable? = error
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
        }
        return null
    }

    /**
     * The code name, then the innermost cause with a message that is not the code name, as
     * " · SimpleName: message", redacted and cut to 140 characters (PLAY-FR-94).
     */
    fun detail(error: PlaybackException): String {
        val code = error.errorCodeName
        var innermost: Throwable? = null
        var t: Throwable? = error.cause
        while (t != null) {
            if (!t.message.isNullOrBlank() && t.message != code) innermost = t
            t = t.cause
        }
        val tail = innermost?.let { " · " + it.javaClass.simpleName + ": " + ipv4.replace(Redactor.redact(it.message).orEmpty(), Redactor.REDACTED).take(MAX_MESSAGE) }.orEmpty()
        return code + tail
    }

    private const val MAX_MESSAGE = 140

    /** Socket messages name the provider's address outside any URL ("failed to connect to /192.0.2.7"). */
    private val ipv4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
}
