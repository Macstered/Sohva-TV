package com.sohva.tv.core.data.migration

import android.content.Context
import com.sohva.tv.core.data.database.AppMetaDao
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.data.security.WrappedKeyStore
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.Beta23SourceCodec
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The first part of the one-time import from beta 23 (plan/04 §17 option B, decision A1): the
 * encrypted source list and the service keys from `streammate_secure_sources`, or the single
 * source of StreamMate's first builds from `sportmate_secure_settings` (spec 10 SRC-FR-107,
 * SEC-04). It runs once, after the first frame, and only reads the old files: they stay until the
 * whole importer has run in its later milestones (the rest of plan/04 §17's table lives there).
 *
 * A store that cannot be read (Keystore key gone, damaged list) is reported through the marker,
 * never read as "no sources", and nothing is written over it (spec 10 SRC-FR-109, spec 73 §8).
 */
class Beta23SourceImport(
    private val context: Context,
    private val sources: SourceStore,
    private val keys: ServiceKeys,
    private val meta: AppMetaDao,
    private val io: CoroutineDispatcher,
    /** The old envelope; replaceable in tests. */
    private val cipher: () -> EnvelopeCipher = { readOnlyCipher(context) },
) {
    sealed interface Result {
        /** Already done, or nothing to import. */
        data object Nothing : Result

        data class Imported(val sourceIds: List<String>) : Result

        data class Failed(val error: AppError) : Result
    }

    suspend fun run(): Result = withContext(io) {
        if (meta.value(MARKER) != null) return@withContext Result.Nothing
        val result = importOnce()
        val marker = when (result) {
            Result.Nothing -> "nothing"
            is Result.Imported -> "done:${result.sourceIds.size}"
            is Result.Failed -> "failed:${result.error.code}"
        }
        meta.put(AppMetaEntity(MARKER, marker))
        result
    }

    /** The failure a finished import recorded, for Settings › Playlists' status line. */
    suspend fun problem(): AppError? = withContext(io) {
        meta.value(MARKER)?.takeIf { it.startsWith("failed:") }?.let { AppError.SecretsUnreadable }
    }

    private suspend fun importOnce(): Result {
        // Only files that exist are opened: getSharedPreferences would otherwise create an empty one.
        val current = prefsFile(FILE)
        val legacy = prefsFile(LEGACY_FILE)
        if (!current.exists() && !legacy.exists()) return Result.Nothing
        val configs = try {
            read(current.exists(), legacy.exists())
        } catch (_: Exception) {
            return Result.Failed(AppError.SecretsUnreadable)
        } ?: return Result.Nothing
        val imported = ArrayList<String>()
        for (config in configs) {
            if (sources.source(config.source.id) != null) continue
            if (sources.save(config) is Outcome.Ok) imported += config.source.id
        }
        return Result.Imported(imported)
    }

    /** The sources to import, or null when the old files hold none. Throws when they cannot be read. */
    private suspend fun read(hasCurrent: Boolean, hasLegacy: Boolean): List<SourceConfig>? {
        val cipher = cipher()
        if (hasCurrent) {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            importKeys(prefs, cipher)
            prefs.getString(KEY_SOURCES, null)?.let { return Beta23SourceCodec.decode(cipher.decrypt(it)) }
        }
        if (!hasLegacy) return null
        // The earliest builds: one M3U source, both addresses required, as beta 23 migrated it.
        val old = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val m3u = old.getString(LEGACY_M3U, null) ?: return null
        val xmltv = old.getString(LEGACY_XMLTV, null) ?: return null
        val secrets = SourceSecrets(m3uUrl = cipher.decrypt(m3u), xmlTvUrl = cipher.decrypt(xmltv).ifBlank { null })
        return listOf(SourceConfig(Source(LEGACY_ID, LEGACY_NAME, SourceType.M3U), secrets))
    }

    /** The TMDB token (with its switch) and the API-Sports key, when beta 23 had them. */
    private suspend fun importKeys(prefs: android.content.SharedPreferences, cipher: EnvelopeCipher) {
        val tmdb = prefs.getString(ServiceKeys.TMDB_TOKEN, null)?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }
        if (tmdb != null && prefs.getBoolean(ServiceKeys.TMDB_ENABLED, false)) keys.saveTmdb(tmdb)
        prefs.getString(ServiceKeys.API_SPORTS_KEY, null)?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }?.let { keys.saveApiSports(it) }
    }

    private fun prefsFile(name: String): File = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")

    companion object {
        const val MARKER: String = "import.beta23.secure_sources"
        const val FILE: String = "streammate_secure_sources"
        const val LEGACY_FILE: String = "sportmate_secure_settings"
        private const val KEY_SOURCES = "sources_v1"
        private const val LEGACY_M3U = "m3u"
        private const val LEGACY_XMLTV = "xmltv"
        private const val LEGACY_ID = "m3u-primary"
        private const val LEGACY_NAME = "IPTV"

        /** Beta 23's envelope, opened without creating a key or writing a wrapped key. */
        fun readOnlyCipher(context: Context): EnvelopeCipher {
            val spec = EnvelopeSpec.BETA23_MAIN
            val wrapped = PrefsWrappedKeyStore(context, spec)
            return EnvelopeCipher(
                spec = spec,
                keyProvider = AndroidKeystoreKeyProvider(spec.keystoreAlias, createIfMissing = false),
                wrappedKeyStore = object : WrappedKeyStore {
                    override fun read(): String? = wrapped.read()

                    override fun write(value: String): Boolean = false
                },
            )
        }
    }
}
