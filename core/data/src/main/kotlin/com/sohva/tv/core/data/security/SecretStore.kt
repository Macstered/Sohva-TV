package com.sohva.tv.core.data.security

import android.content.Context
import android.content.SharedPreferences
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Encrypted key/value settings: source credentials, PIN, API keys (spec 73 §4.3). Every call runs
 * on [io], never on the main thread (SEC-NFR-01); the UI observes derived flags instead.
 */
class SecretStore(
    context: Context,
    private val cipher: EnvelopeCipher,
    private val io: CoroutineDispatcher,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    suspend fun read(key: String): Outcome<String?> = withContext(io) {
        val stored = prefs.getString(key, null) ?: return@withContext Outcome.Ok(null)
        guarded { cipher.decrypt(stored) }
    }

    suspend fun write(key: String, value: String?): Outcome<Unit> = withContext(io) {
        // Synchronous commits: we are already off the main thread, and a secret must be on disk
        // before the caller moves on.
        guarded {
            if (value == null) {
                check(prefs.edit().remove(key).commit()) { "commit failed" }
                return@guarded
            }
            val sealed = cipher.encrypt(value)
            check(prefs.edit().putString(key, sealed).commit()) { "commit failed" }
        }
    }

    // A key that cannot be used makes every secret unreadable; one damaged value is only that value.
    private inline fun <T> guarded(block: () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (_: SecretsUnreadableException) {
        Outcome.Failed(AppError.SecretsUnreadable)
    } catch (_: GeneralSecurityException) {
        Outcome.Failed(AppError.Unknown)
    } catch (_: IllegalArgumentException) {
        Outcome.Failed(AppError.Unknown)
    }

    companion object {
        const val FILE_NAME: String = "sohva_secure_settings"
    }
}
