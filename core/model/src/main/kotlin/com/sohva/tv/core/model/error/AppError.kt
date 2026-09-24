package com.sohva.tv.core.model.error

/**
 * Everything the viewer can see go wrong, as a stable code with typed arguments (plan/03 §4.11).
 *
 * Codes are persisted (refresh states, diagnostics) instead of resource ids, which the old app
 * had to guard because ids are renumbered between builds. Each code maps to exactly one string in
 * the UI layer. Milestones add their codes together with their strings.
 */
sealed interface AppError {
    val code: String

    /** Nothing more specific is known. */
    data object Unknown : AppError {
        override val code: String = "unknown"
    }

    /** A request could not complete; [detail] is already redacted. */
    data class TransportFailed(val detail: String?) : AppError {
        override val code: String = "transport_failed"
    }

    /** The server answered with an HTTP error status. */
    data class HttpStatus(val status: Int) : AppError {
        override val code: String = "http_status"
    }

    /** The device ran out of storage while writing. */
    data object StorageFull : AppError {
        override val code: String = "storage_full"
    }

    /** The Keystore key cannot unwrap the stored data key (spec 73 §8): secrets are unreadable. */
    data object SecretsUnreadable : AppError {
        override val code: String = "secrets_unreadable"
    }
}
