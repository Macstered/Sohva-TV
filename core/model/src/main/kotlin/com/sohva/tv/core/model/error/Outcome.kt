package com.sohva.tv.core.model.error

/** Result of an operation at a module boundary. Cancellation is never an [Outcome.Failed]. */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Failed(val error: AppError) : Outcome<Nothing>
}

/**
 * State of one screen section (plan/03 §4.11). A section that fails does not blank the rest of
 * the screen. Retrying is a screen event, not a lambda in the state, so the state stays stable
 * for Compose.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<out T>(val value: T) : LoadState<T>
    data class Empty(val reason: String) : LoadState<Nothing>
    data class Failed(val error: AppError, val canRetry: Boolean) : LoadState<Nothing>
}
