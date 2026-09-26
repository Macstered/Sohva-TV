package com.sohva.tv.feature.discover.protocol

/** Why an addon operation failed (spec 50 §4.20). Never carries a URL, a body or provider text. */
enum class AddonFailure {
    INVALID_URL,
    INSECURE_URL,
    INVALID_REQUEST,
    INVALID_MANIFEST,
    INVALID_RESPONSE,
    RESPONSE_TOO_LARGE,
    CONFIGURATION_REQUIRED,
    UNSUPPORTED_RESOURCE,
    NETWORK,
    TIMEOUT,
    HTTP_ERROR,
    REDIRECT,
    ACCESS_DENIED,
    NOT_FOUND,
    CONFLICT,
    STORAGE,
    ;

    /** Network, timeouts, 408/425/429/5xx: a stale cached answer may stand in (ADDON-FR-123). */
    fun temporary(status: Int): Boolean = when (this) {
        NETWORK, TIMEOUT -> true
        HTTP_ERROR -> status == 408 || status == 425 || status == 429 || status in 500..599
        else -> false
    }

    /** The operation must stop: the profile, the addon or the request no longer holds (ADDON-FR-03, -04). */
    val revocation: Boolean get() = this == ACCESS_DENIED || this == CONFLICT || this == NOT_FOUND
}

/**
 * An addon failure (ADDON-FR-28): the message is only the kind and status, so no exception can
 * leak a configured URL, a header or a response into a log or the screen.
 */
class AddonException(val failure: AddonFailure, val status: Int = 0) : Exception(if (status > 0) "${failure.name} $status" else failure.name) {
    override fun fillInStackTrace(): Throwable = this
}

internal fun fail(failure: AddonFailure, status: Int = 0): Nothing = throw AddonException(failure, status)
