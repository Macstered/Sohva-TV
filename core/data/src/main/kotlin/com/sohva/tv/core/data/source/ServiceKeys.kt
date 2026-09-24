package com.sohva.tv.core.data.source

import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome

/**
 * The viewer's own service keys, encrypted in the secure settings under the names spec 70 §6.1 and
 * spec 73 give them. M1 writes them from the phone page; the Library (M4) and Sohva Sport (M5)
 * settings read and edit them.
 */
class ServiceKeys(private val secrets: SecretValues) {
    /** Saves a TMDB key and switches TMDB on (spec 11 PHONE-FR-31 item 3): at most 2,048 characters, one line. */
    suspend fun saveTmdb(token: String): Outcome<Unit> {
        if (!fits(token, TMDB_MAX)) return Outcome.Failed(AppError.Unknown)
        val saved = secrets.write(TMDB_TOKEN, token)
        if (saved is Outcome.Failed) return saved
        return secrets.write(TMDB_ENABLED, "true")
    }

    /** At most 512 characters, one line (PHONE-FR-31 item 4). */
    suspend fun saveApiSports(key: String): Outcome<Unit> =
        if (fits(key, API_SPORTS_MAX)) secrets.write(API_SPORTS_KEY, key) else Outcome.Failed(AppError.Unknown)

    private fun fits(value: String, max: Int): Boolean = value.isNotEmpty() && value.length <= max && value.none { it == '\n' || it == '\r' }

    companion object {
        const val TMDB_TOKEN: String = "metadata_tmdb_token_v1"
        const val TMDB_ENABLED: String = "metadata_tmdb_enabled_v1"
        const val API_SPORTS_KEY: String = "sports_api_key_v1"
        private const val TMDB_MAX = 2_048
        private const val API_SPORTS_MAX = 512
    }
}
