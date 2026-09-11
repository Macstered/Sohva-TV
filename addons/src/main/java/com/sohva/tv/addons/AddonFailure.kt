package com.sohva.tv.addons

enum class AddonFailure {
    INVALID_URL, INSECURE_URL, INVALID_MANIFEST, INVALID_REQUEST, UNSUPPORTED_RESOURCE,
    NETWORK, TIMEOUT, HTTP_ERROR, REDIRECT, RESPONSE_TOO_LARGE, CONFIGURATION_REQUIRED,
    ACCESS_DENIED, NOT_FOUND, CONFLICT, STORAGE, INVALID_RESPONSE,
}

/** Never attach raw HTTP/parser/cipher exceptions: their messages may contain credentials. */
class AddonException(val failure: AddonFailure, val httpStatus: Int? = null) :
    Exception("Addon operation failed: ${failure.name}")

internal fun fail(failure: AddonFailure): Nothing = throw AddonException(failure)
